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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Markdown generator that delegates file parsing to the Docling Python stack.
 * <p>
 * When enabled, requests are sent to the long-running Docling HTTP daemon
 * ({@code Utilities/Parsing/docling_daemon.py}) so PDF worker processes and models stay warm
 * between conversions. If the daemon is unavailable and fallback is enabled, the legacy
 * per-request {@code docling_parser.py} CLI path is used instead.
 * </p>
 * <p>
 * Conversion timeout defaults to 15 minutes and can be overridden with
 * {@code cards.docling.timeout.minutes}.
 * </p>
 *
 * @version $Id$
 */
public class DoclingMarkdownGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingMarkdownGenerator.class);

    private static final long DEFAULT_TIMEOUT_MINUTES = 15L;

    private static final long OUTPUT_COLLECT_TIMEOUT_SECONDS = 10L;

    private static final String DEFAULT_SCRIPT_NAME = "Utilities/Parsing/docling_parser.py";

    private static final String DEFAULT_DAEMON_URL = "http://127.0.0.1:18765";

    private static final String SCRIPT_PATH_PROPERTY = "cards.docling.script";

    private static final String PYTHON_COMMAND_PROPERTY = "cards.docling.python";

    private static final String TIMEOUT_MINUTES_PROPERTY = "cards.docling.timeout.minutes";

    private static final String DAEMON_URL_PROPERTY = "cards.docling.daemon.url";

    private static final String DAEMON_ENABLED_PROPERTY = "cards.docling.daemon.enabled";

    private static final String DAEMON_FALLBACK_PROPERTY = "cards.docling.daemon.fallback";

    private static final String DEFAULT_PYTHON_COMMAND = "python";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /**
     * Convert the document stream to markdown by delegating to Docling.
     * Returns an empty string on any failure.
     *
     * @param stream the input document stream
     * @param fileName source file name; the extension is used to infer the document type
     * @return markdown text, or an empty string on any failure
     */
    public String toMarkdown(final InputStream stream, final String fileName)
    {
        final long startTimestamp = System.currentTimeMillis();
        LOGGER.info("Docling parse request started for '{}'", fileName);
        String result = "";
        try {
            final byte[] content;
            try {
                content = stream.readAllBytes();
            } catch (IOException e) {
                LOGGER.warn("Failed to read document stream for '{}': {}", fileName, e.getMessage());
                return "";
            }
            final String extension = extractExtension(fileName);
            File tmpFile = null;
            try {
                tmpFile = writeToTempFile(content, extension);
                result = runDocling(tmpFile, fileName);
                return result;
            } catch (IOException e) {
                LOGGER.warn("Failed to create temp file for Docling on '{}': {}", fileName, e.getMessage());
                return "";
            } finally {
                deleteSilently(tmpFile);
            }
        } finally {
            final long endTimestamp = System.currentTimeMillis();
            LOGGER.info("Docling parse response received for '{}' at {} (total {} ms, result {} chars)",
                fileName, endTimestamp, endTimestamp - startTimestamp, result.length());
        }
    }

    private File writeToTempFile(final byte[] content, final String extension)
        throws IOException
    {
        final File tmpFile = File.createTempFile("cards-docling-", "." + extension);
        Files.write(tmpFile.toPath(), content);
        return tmpFile;
    }

    private void deleteSilently(final File file)
    {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            LOGGER.debug("Could not delete temp file {}: {}", file.getAbsolutePath(), e.getMessage());
        }
    }

    private String runDocling(final File tmpFile, final String fileName)
    {
        if (isDaemonEnabled()) {
            final String daemonResult = runDoclingDaemon(tmpFile, fileName);
            if (daemonResult != null) {
                LOGGER.info("Docling parsed '{}' via daemon at {}", fileName, resolveDaemonUrl());
                return daemonResult;
            }
            if (!isDaemonFallbackEnabled()) {
                return "";
            }
            LOGGER.warn("Docling daemon unavailable for '{}'; falling back to CLI", fileName);
        } else {
            LOGGER.info("Docling daemon disabled; parsing '{}' via CLI", fileName);
        }

        try {
            final Process process = startDoclingProcess(tmpFile, fileName);
            final CompletableFuture<String> progressFuture = readProcessStdoutAsync(process);
            if (!waitForProcess(process, progressFuture, fileName)) {
                return "";
            }
            final String result = readOutputFile(tmpFile, fileName);
            if (StringUtils.isNotEmpty(result)) {
                LOGGER.info("Docling parsed '{}' via CLI", fileName);
            }
            return result;
        } catch (IOException e) {
            LOGGER.error("Failed to start Docling process for '{}': {}", fileName, e.getMessage());
            return "";
        }
    }

    private String runDoclingDaemon(final File tmpFile, final String fileName)
    {
        final String daemonUrl = resolveDaemonUrl();
        final long timeoutMinutes = resolveTimeoutMinutes();
        LOGGER.info("Sending Docling convert request for '{}' to daemon at {} (timeout {} min)",
            fileName, daemonUrl, timeoutMinutes);
        try {
            final JsonObjectBuilder bodyBuilder = Json.createObjectBuilder()
                .add("input_path", tmpFile.getAbsolutePath());
            if (StringUtils.isNotBlank(fileName)) {
                bodyBuilder.add("source_file", fileName);
            }
            final JsonObject requestBody = bodyBuilder.build();
            final HttpRequest request = HttpRequest.newBuilder(URI.create(daemonUrl + "/convert"))
                .timeout(Duration.ofMinutes(timeoutMinutes))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();
            final HttpResponse<String> response =
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                final String error = readDaemonError(response.body());
                LOGGER.error("Docling daemon returned HTTP {} for '{}': {}", response.statusCode(), fileName, error);
                return null;
            }

            try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
                final JsonObject payload = reader.readObject();
                if (payload.containsKey("logs")) {
                    LOGGER.info("Docling daemon logs for '{}':\n{}", fileName, payload.getString("logs"));
                }
                if (payload.containsKey("markdown")) {
                    return payload.getString("markdown");
                }
            }
            LOGGER.error("Docling daemon response for '{}' did not contain markdown", fileName);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Docling daemon request interrupted for '{}'", fileName);
            return null;
        } catch (IOException e) {
            LOGGER.warn("Docling daemon request failed for '{}': {}", fileName, e.getMessage());
            return null;
        }
    }

    private Process startDoclingProcess(final File tmpFile, final String fileName)
        throws IOException
    {
        final String pythonCmd = resolvePythonCommand();
        final String scriptPath = resolveScriptPath();
        LOGGER.debug("Starting Docling CLI process for '{}' with script '{}'", fileName, scriptPath);
        final ProcessBuilder builder;
        if (StringUtils.isNotBlank(fileName)) {
            builder = new ProcessBuilder(pythonCmd, scriptPath,
                tmpFile.getAbsolutePath(),
                "--source-file", fileName);
        } else {
            builder = new ProcessBuilder(pythonCmd, scriptPath,
                tmpFile.getAbsolutePath());
        }
        return builder.redirectErrorStream(true).start();
    }

    private CompletableFuture<String> readProcessStdoutAsync(final Process process)
    {
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException e) {
                LOGGER.debug("Error reading Docling process stdout: {}", e.getMessage());
                return "";
            }
        });
    }

    private boolean waitForProcess(final Process process, final CompletableFuture<String> progressFuture,
        final String fileName)
    {
        final long timeoutMinutes = resolveTimeoutMinutes();
        try {
            final boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                progressFuture.cancel(true);
                LOGGER.error("Docling process timed out after {} minutes for '{}'", timeoutMinutes, fileName);
                return false;
            }
            final int exitCode = process.exitValue();
            if (exitCode != 0) {
                final String output = collectOutputSilently(progressFuture);
                LOGGER.error("Docling process exited with code {} for '{}':\n{}", exitCode, fileName, output);
                return false;
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Docling process output for '{}':\n{}", fileName, collectOutputSilently(progressFuture));
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            LOGGER.error("Docling process interrupted for '{}'", fileName);
            return false;
        }
    }

    private String readOutputFile(final File tmpFile, final String fileName)
    {
        final File outputFile = new File(tmpFile.getParentFile(), stripExtension(tmpFile.getName()) + ".md");
        try {
            if (!outputFile.exists()) {
                LOGGER.error("Docling output file not found for '{}': {}", fileName, outputFile.getAbsolutePath());
                return "";
            }
            return Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to read Docling output file for '{}': {}", fileName, e.getMessage());
            return "";
        } finally {
            try {
                Files.deleteIfExists(outputFile.toPath());
            } catch (IOException e) {
                LOGGER.debug("Could not delete Docling output file {}: {}", outputFile.getAbsolutePath(),
                    e.getMessage());
            }
        }
    }

    private static String readDaemonError(final String body)
    {
        if (StringUtils.isBlank(body)) {
            return "(empty response)";
        }
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            final JsonObject payload = reader.readObject();
            if (payload.containsKey("error")) {
                return payload.getString("error");
            }
        } catch (RuntimeException e) {
            return body;
        }
        return body;
    }

    private static String stripExtension(final String fileName)
    {
        final int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private static String collectOutputSilently(final CompletableFuture<String> outputFuture)
    {
        try {
            return outputFuture.get(OUTPUT_COLLECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return "(output unavailable)";
        }
    }

    private static String extractExtension(final String fileName)
    {
        if (StringUtils.isBlank(fileName)) {
            return "bin";
        }
        final int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "bin";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isDaemonEnabled()
    {
        final String configured = System.getProperty(DAEMON_ENABLED_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private static boolean isDaemonFallbackEnabled()
    {
        final String configured = System.getProperty(DAEMON_FALLBACK_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private static String resolveDaemonUrl()
    {
        final String configured = System.getProperty(DAEMON_URL_PROPERTY);
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        return DEFAULT_DAEMON_URL;
    }

    private static String resolveScriptPath()
    {
        final String configured = System.getProperty(SCRIPT_PATH_PROPERTY);
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        return DEFAULT_SCRIPT_NAME;
    }

    private static String resolvePythonCommand()
    {
        final String configured = System.getProperty(PYTHON_COMMAND_PROPERTY);
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        return DEFAULT_PYTHON_COMMAND;
    }

    private static long resolveTimeoutMinutes()
    {
        final String configured = System.getProperty(TIMEOUT_MINUTES_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            return DEFAULT_TIMEOUT_MINUTES;
        }
        try {
            final long timeoutMinutes = Long.parseLong(configured);
            if (timeoutMinutes > 0L) {
                return timeoutMinutes;
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid {} value '{}'; using default {} minutes",
                TIMEOUT_MINUTES_PROPERTY, configured, DEFAULT_TIMEOUT_MINUTES);
        }
        return DEFAULT_TIMEOUT_MINUTES;
    }
}
