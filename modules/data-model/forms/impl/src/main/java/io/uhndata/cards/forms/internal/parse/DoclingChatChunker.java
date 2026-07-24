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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Triggers the Docling chunk-aware chat chunker for a proposal answer, asynchronously and without
 * ever blocking the caller.
 * <p>
 * The chunker splits the answer's single parsed Markdown file (MVP: one proposal file per answer)
 * into a flat {@code Chunks/} tree (chunk files plus {@code catalog.json}) that backs the downstream
 * extraction and proposal chat features. It is independent of the per-answer field-extraction chunks
 * produced by {@link MarkdownChunker}; both run, for different purposes.
 * </p>
 * <p>
 * The request is first sent to the long-running Docling daemon at {@code cards.docling.daemon.url}
 * via {@code POST /chunk}. If that call fails (daemon down, error response, or transport error) and
 * fallback is enabled, a detached {@code chunker.py} CLI process is started instead.
 * Neither path blocks the calling thread: the daemon call is fired with
 * {@link HttpClient#sendAsync(HttpRequest, HttpResponse.BodyHandler)} and the CLI process is left to
 * run on its own. Chunking can take a while; callers fire it after the per-file markdown is written
 * and carry on. Catalog summarization is deliberately <em>not</em> started here — it runs only after
 * the extraction phase has finished and saved, so the two LLM workloads never race on the shared
 * catalog.
 * </p>
 *
 * @version $Id$
 */
public final class DoclingChatChunker
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingChatChunker.class);

    private static final String DEFAULT_DAEMON_URL = "http://127.0.0.1:18765";

    private static final String DEFAULT_SCRIPT_NAME = "modules/parsing/src/main/python/chunker.py";

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
     * The current chunk generation for an answer folder. Downstream work that must abort when a newer chunking
     * run supersedes this one (for example catalog summarization after extraction) should capture this value
     * when it starts and poll {@link #isSummarizationCurrent(Path, long)}.
     *
     * @param answerDir the absolute parse output folder of the answer
     * @return the current generation, or {@code 0} when no chunking has been requested for this folder yet
     */
    public static long currentGeneration(final Path answerDir)
    {
        if (answerDir == null) {
            return 0L;
        }
        final AtomicLong current = CHUNK_GENERATIONS.get(folderKey(answerDir));
        return current == null ? 0L : current.get();
    }

    /**
     * Whether a summarization job started for the given chunk generation is still current. Summarization should
     * abort when this returns {@code false}, for example after a re-parse or a newer chunking request.
     *
     * @param answerDir the absolute parse output folder of the answer
     * @param generation the generation captured when scheduling summarization
     * @return {@code true} when no newer chunking has superseded this generation
     */
    public static boolean isSummarizationCurrent(final Path answerDir, final long generation)
    {
        return isCurrentGeneration(folderKey(answerDir), generation);
    }

    /**
     * Request chat chunking for an answer folder, returning immediately. Does nothing when chunking is
     * disabled, the folder is {@code null}, or a single parsed {@code .md} file cannot be resolved under
     * it (MVP: one proposal file per answer). The chunker always uses its own single hardcoded tokenizer
     * model, so no model is passed here. Any failure is logged, never thrown.
     *
     * @param answerDir the absolute parse output folder of the answer, as produced by
     *            {@link ParsedMarkdownStore#resolveAnswerDir(String)}
     * @param minStructureTokens the small-document threshold (the active LLM model's
     *            {@code wholeDocumentTokenLimit}): documents under this many estimated tokens are not chunked
     *            and are recorded as {@code chunked: false} in {@code outline.json}; pass {@code 0} or a
     *            negative value to let the chunker use its own default
     */
    public static void requestChunking(final Path answerDir, final long minStructureTokens)
    {
        if (answerDir == null) {
            return;
        }
        if (!isEnabled()) {
            LOGGER.debug("Docling chat chunking is disabled; skipping {}", answerDir);
            return;
        }
        final Path documentFile = resolveSingleDocumentFile(answerDir);
        if (documentFile == null) {
            LOGGER.warn("Could not resolve a single parsed .md file to chunk under {}; skipping", answerDir);
            return;
        }
        final String folder = folderKey(answerDir);
        final long generation = nextGeneration(answerDir);
        try {
            sendDaemonRequest(documentFile, folder, answerDir, generation, minStructureTokens);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not start Docling chat chunking for {}: {}", folder, e.getMessage());
        }
    }

    /**
     * Resolve the single parsed {@code .md} file directly under an answer folder (excluding
     * {@value ParsedMarkdownStore#AGGREGATED_FILE_NAME}), the document the chunker should split.
     *
     * @return the resolved file, or {@code null} when there is not exactly one candidate
     */
    private static Path resolveSingleDocumentFile(final Path answerDir)
    {
        try (Stream<Path> children = Files.list(answerDir)) {
            final List<Path> candidates = children
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                .filter(path -> !ParsedMarkdownStore.AGGREGATED_FILE_NAME.equals(path.getFileName().toString()))
                .collect(Collectors.toList());
            return candidates.size() == 1 ? candidates.get(0) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void sendDaemonRequest(final Path documentFile, final String folder, final Path answerDir,
        final long generation, final long minStructureTokens)
    {
        final String url = resolveDaemonUrl() + "/chunk";
        final String filePath = documentFile.toAbsolutePath().normalize().toString();
        final StringBuilder body = new StringBuilder("{\"file_path\":").append(jsonString(filePath));
        if (minStructureTokens > 0) {
            body.append(",\"min_structure_tokens\":").append(minStructureTokens);
        }
        body.append('}');
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(REQUEST_TIMEOUT_MINUTES))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        LOGGER.info("Requesting Docling chat chunking for {} via daemon", filePath);
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .whenComplete((response, error) -> handleDaemonResult(documentFile, answerDir, folder, generation,
                minStructureTokens, response, error));
    }

    private static void handleDaemonResult(final Path documentFile, final Path answerDir, final String folder,
        final long generation, final long minStructureTokens, final HttpResponse<String> response,
        final Throwable error)
    {
        if (error == null && response != null && response.statusCode() == 200) {
            if (!isCurrentGeneration(folder, generation)) {
                LOGGER.warn("Discarding stale Docling chat chunking result for {}", folder);
                ParsedMarkdownStore.clearChunkTree(answerDir);
                return;
            }
            LOGGER.info("Docling chat chunking finished for {}: {}", folder, response.body());
            return;
        }
        final String reason = error != null ? error.getMessage()
            : "HTTP " + (response == null ? "no response" : response.statusCode() + " " + response.body());
        if (!isFallbackEnabled()) {
            LOGGER.warn("Docling chat chunking via daemon failed for {} ({}); fallback disabled", folder, reason);
            return;
        }
        LOGGER.warn("Docling chat chunking via daemon failed for {} ({}); falling back to CLI", folder, reason);
        runCliFallback(documentFile, answerDir, folder, generation, minStructureTokens);
    }

    private static void runCliFallback(final Path documentFile, final Path answerDir, final String folder,
        final long generation, final long minStructureTokens)
    {
        try {
            final List<String> command = new ArrayList<>(List.of(resolvePythonCommand(), resolveScriptPath(),
                documentFile.toAbsolutePath().normalize().toString()));
            if (minStructureTokens > 0) {
                command.add("--min-structure-tokens");
                command.add(Long.toString(minStructureTokens));
            }
            final Process process = new ProcessBuilder(command)
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
                ParsedMarkdownStore.clearChunkTree(answerDir);
            } else if (exitCode != 0) {
                LOGGER.warn("Docling chat chunker CLI exited with code {} for {}", exitCode, folder);
            } else {
                LOGGER.info("Docling chat chunking CLI finished for {}", folder);
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
