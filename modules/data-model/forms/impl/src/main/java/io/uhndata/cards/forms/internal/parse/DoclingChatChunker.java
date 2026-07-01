/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.forms.internal.parse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Triggers the Docling section-aware chat chunker for a proposal answer, asynchronously and without
 * ever blocking the caller.
 * <p>
 * The chunker builds the {@code ChatChunks} tree and {@code aggregated_chunked.md} that back the
 * proposal chat feature. It is independent of the per-answer field-extraction chunks produced by
 * {@link MarkdownChunker}; both run, for different purposes.
 * </p>
 * <p>
 * The request is first sent to the long-running Docling daemon at {@code cards.docling.daemon.url}
 * via {@code POST /chunk}. If that call fails (daemon down, error response, or transport error) and
 * fallback is enabled, a detached {@code docling_chunker.py} CLI process is started instead. Neither
 * path blocks the calling thread: the daemon call is fired with
 * {@link HttpClient#sendAsync(HttpRequest, HttpResponse.BodyHandler)} and the CLI process is left to
 * run on its own. Chunking can take a while; callers fire it after the aggregate is written and
 * carry on.
 * </p>
 *
 * @version $Id$
 */
public final class DoclingChatChunker
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingChatChunker.class);

    private static final String DEFAULT_DAEMON_URL = "http://127.0.0.1:18765";

    private static final String DEFAULT_SCRIPT_NAME = "Utilities/Parsing/docling_chunker.py";

    private static final String DEFAULT_PYTHON_COMMAND = "python";

    private static final String DAEMON_URL_PROPERTY = "cards.docling.daemon.url";

    private static final String ENABLED_PROPERTY = "cards.docling.chunk.enabled";

    private static final String FALLBACK_PROPERTY = "cards.docling.chunk.fallback";

    private static final String SCRIPT_PATH_PROPERTY = "cards.docling.chunk.script";

    private static final String PYTHON_COMMAND_PROPERTY = "cards.docling.python";

    private static final long REQUEST_TIMEOUT_MINUTES = 30L;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /** Drains stdout of detached CLI fallback processes so their pipe buffers never block them. */
    private static final ExecutorService CLI_DRAIN_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        final Thread thread = new Thread(runnable, "docling-chat-chunker");
        thread.setDaemon(true);
        return thread;
    });

    /** Per-answer generation counters used to discard stale asynchronous chunk results. */
    private static final ConcurrentHashMap<String, AtomicLong> CHUNK_GENERATIONS = new ConcurrentHashMap<>();

    /**
     * Optional summarization callback invoked with the answer folder and the chunk generation once chunking
     * completes successfully. Set by the summarization service while it is active (see its {@code @Activate});
     * {@code null} when no such service is registered, in which case chunking simply produces a tree with empty
     * summaries. {@code volatile} because it is written from the service's lifecycle thread and read from the
     * async chunking threads.
     */
    private static volatile BiConsumer<Path, Long> summarizationHook;

    private DoclingChatChunker()
    {
        // Utility class, never instantiated.
    }

    /**
     * Invalidate any in-flight chat chunking for an answer folder, for example after a parse failure.
     * Callers should also clear on-disk chat chunk output via {@link ParsedMarkdownStore#clearChunks(String)}.
     *
     * @param answerDir the absolute parse output folder of the answer
     */
    public static void invalidateChunking(final Path answerDir)
    {
        if (answerDir == null) {
            return;
        }
        nextGeneration(answerDir);
        LOGGER.debug("Invalidated in-flight Docling chat chunking for {}", answerDir);
    }

    /**
     * Register (or clear) the callback run with the answer folder and chunk generation once chunking completes
     * successfully. The summarization service sets this while active and clears it on deactivation, coupling
     * chunking completion to summary generation without the chunker depending on that service directly.
     *
     * @param hook the callback to invoke after successful chunking, or {@code null} to clear it
     */
    public static void setSummarizationHook(final BiConsumer<Path, Long> hook)
    {
        summarizationHook = hook;
    }

    /**
     * Whether a summarization job started for the given chunk generation is still current. Summarization should
     * abort when this returns {@code false}, for example after a re-parse or a newer chunking request.
     *
     * @param answerDir the absolute parse output folder of the answer
     * @param generation the generation captured when chunking completed
     * @return {@code true} when no newer chunking has superseded this generation
     */
    public static boolean isSummarizationCurrent(final Path answerDir, final long generation)
    {
        return isCurrentGeneration(folderKey(answerDir), generation);
    }

    /**
     * Request chat chunking for an answer folder, returning immediately. Does nothing when chunking is
     * disabled or the folder is {@code null}. The chunker always uses its own single hardcoded tokenizer
     * model, so no model is passed here. Any failure is logged, never thrown.
     *
     * @param answerDir the absolute parse output folder of the answer, as produced by
     *            {@link ParsedMarkdownStore#resolveAnswerDir(String)}
     */
    public static void requestChunking(final Path answerDir)
    {
        if (answerDir == null) {
            return;
        }
        if (!isEnabled()) {
            LOGGER.debug("Docling chat chunking is disabled; skipping {}", answerDir);
            return;
        }
        final String folder = folderKey(answerDir);
        final long generation = nextGeneration(answerDir);
        try {
            sendDaemonRequest(folder, answerDir, generation);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not start Docling chat chunking for {}: {}", folder, e.getMessage());
        }
    }

    private static void sendDaemonRequest(final String folder, final Path answerDir, final long generation)
    {
        final String url = resolveDaemonUrl() + "/chunk";
        final String body = "{\"folder_path\":" + jsonString(folder) + "}";
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(REQUEST_TIMEOUT_MINUTES))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        LOGGER.info("Requesting Docling chat chunking for {} via daemon", folder);
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .whenComplete((response, error) -> handleDaemonResult(answerDir, folder, generation, response, error));
    }

    private static void handleDaemonResult(final Path answerDir, final String folder, final long generation,
        final HttpResponse<String> response, final Throwable error)
    {
        if (error == null && response != null && response.statusCode() == 200) {
            if (!isCurrentGeneration(folder, generation)) {
                LOGGER.warn("Discarding stale Docling chat chunking result for {}", folder);
                ParsedMarkdownStore.clearChatChunkOutput(answerDir);
                return;
            }
            LOGGER.info("Docling chat chunking finished for {}: {}", folder, response.body());
            runSummarization(answerDir, folder, generation);
            return;
        }
        final String reason = error != null ? error.getMessage()
            : "HTTP " + (response == null ? "no response" : response.statusCode() + " " + response.body());
        if (!isFallbackEnabled()) {
            LOGGER.warn("Docling chat chunking via daemon failed for {} ({}); fallback disabled", folder, reason);
            return;
        }
        LOGGER.warn("Docling chat chunking via daemon failed for {} ({}); falling back to CLI", folder, reason);
        runCliFallback(answerDir, folder, generation);
    }

    private static void runSummarization(final Path answerDir, final String folder, final long generation)
    {
        final BiConsumer<Path, Long> hook = summarizationHook;
        if (hook == null) {
            LOGGER.warn("Chunking finished for {} but no summarization hook is registered; summaries will NOT be "
                + "generated. Is the CatalogSummarizationService active?", folder);
            return;
        }
        LOGGER.info("Chunking finished for {}; scheduling catalog summarization (generation {})", folder,
            generation);
        CLI_DRAIN_EXECUTOR.submit(() -> {
            if (!isCurrentGeneration(folder, generation)) {
                LOGGER.warn("Skipping stale Docling chat summarization for {}", folder);
                return;
            }
            try {
                hook.accept(answerDir, generation);
            } catch (RuntimeException e) {
                LOGGER.warn("Docling chat summarization failed for {}: {}", folder, e.getMessage(), e);
            }
        });
    }

    private static void runCliFallback(final Path answerDir, final String folder, final long generation)
    {
        try {
            final Process process = new ProcessBuilder(resolvePythonCommand(), resolveScriptPath(), folder)
                .redirectErrorStream(true)
                .start();
            drainProcessOutput(answerDir, folder, generation, process);
        } catch (IOException e) {
            LOGGER.warn("Could not start Docling chat chunker CLI for {}: {}", folder, e.getMessage());
        }
    }

    private static void drainProcessOutput(final Path answerDir, final String folder, final long generation,
        final Process process)
    {
        CLI_DRAIN_EXECUTOR.submit(() -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    LOGGER.debug("Docling chat chunker [{}]: {}", folder, line);
                    line = reader.readLine();
                }
            } catch (IOException e) {
                LOGGER.debug("Docling chat chunker output closed for {}: {}", folder, e.getMessage());
            }
            final int exitCode = waitForProcess(process);
            if (exitCode == 0 && !isCurrentGeneration(folder, generation)) {
                LOGGER.warn("Discarding stale Docling chat chunking CLI result for {}", folder);
                ParsedMarkdownStore.clearChatChunkOutput(answerDir);
            } else if (exitCode != 0) {
                LOGGER.warn("Docling chat chunker CLI exited with code {} for {}", exitCode, folder);
            } else {
                runSummarization(answerDir, folder, generation);
            }
        });
    }

    private static int waitForProcess(final Process process)
    {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static long nextGeneration(final Path answerDir)
    {
        return CHUNK_GENERATIONS.computeIfAbsent(folderKey(answerDir), ignored -> new AtomicLong(0L))
            .incrementAndGet();
    }

    private static boolean isCurrentGeneration(final String folder, final long generation)
    {
        final AtomicLong current = CHUNK_GENERATIONS.get(folder);
        return current != null && current.get() == generation;
    }

    private static String folderKey(final Path answerDir)
    {
        return answerDir.toAbsolutePath().normalize().toString();
    }

    private static boolean isEnabled()
    {
        final String configured = System.getProperty(ENABLED_PROPERTY);
        return StringUtils.isBlank(configured) || Boolean.parseBoolean(configured);
    }

    private static boolean isFallbackEnabled()
    {
        final String configured = System.getProperty(FALLBACK_PROPERTY);
        return StringUtils.isBlank(configured) || Boolean.parseBoolean(configured);
    }

    private static String resolveDaemonUrl()
    {
        final String configured = System.getProperty(DAEMON_URL_PROPERTY);
        return StringUtils.isNotBlank(configured) ? configured : DEFAULT_DAEMON_URL;
    }

    private static String resolveScriptPath()
    {
        final String configured = System.getProperty(SCRIPT_PATH_PROPERTY);
        return StringUtils.isNotBlank(configured) ? configured : DEFAULT_SCRIPT_NAME;
    }

    private static String resolvePythonCommand()
    {
        final String configured = System.getProperty(PYTHON_COMMAND_PROPERTY);
        return StringUtils.isNotBlank(configured) ? configured : DEFAULT_PYTHON_COMMAND;
    }

    /**
     * Encode a string as a JSON string literal, escaping the characters that would otherwise break the
     * payload. Sufficient for file system paths, which is all this class sends.
     *
     * @param value the string to encode
     * @return the value wrapped in double quotes with control and quote characters escaped
     */
    private static String jsonString(final String value)
    {
        final StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            appendJsonChar(builder, character);
        }
        builder.append('"');
        return builder.toString();
    }

    private static void appendJsonChar(final StringBuilder builder, final char character)
    {
        switch (character) {
            case '"':
                builder.append("\\\"");
                break;
            case '\\':
                builder.append("\\\\");
                break;
            case '\n':
                builder.append("\\n");
                break;
            case '\r':
                builder.append("\\r");
                break;
            case '\t':
                builder.append("\\t");
                break;
            default:
                appendPlainOrEscaped(builder, character);
                break;
        }
    }

    private static void appendPlainOrEscaped(final StringBuilder builder, final char character)
    {
        if (character < 0x20) {
            builder.append(String.format("\\u%04x", (int) character));
        } else {
            builder.append(character);
        }
    }
}
