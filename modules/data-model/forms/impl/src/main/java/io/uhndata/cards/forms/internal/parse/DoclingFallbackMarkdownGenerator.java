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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback markdown generator that delegates to the Docling Python script via CLI.
 * <p>
 * Invoked when the primary Java generators fail, produce empty or insufficient output, or exceed the
 * configured time limit, and for formats not handled by any primary Java generator (DOC, PPTX, XLSX,
 * HTML, CSV). Calls {@code docling_parse.py} via the configured Python interpreter.
 * </p>
 * <p>
 * The script path and Python command can be customised via system properties
 * {@code cards.docling.script} and {@code cards.docling.python}; they default to
 * {@code docling_parse.py} and {@code python} respectively.
 * </p>
 *
 * @version $Id$
 */
public class DoclingFallbackMarkdownGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingFallbackMarkdownGenerator.class);

    private static final long TIMEOUT_MINUTES = 2L;

    private static final long OUTPUT_COLLECT_TIMEOUT_SECONDS = 10L;

    private static final int MIN_CONTENT_CHARS = 50;

    private static final String DEFAULT_SCRIPT_NAME = "docling_parser.py";

    private static final String SCRIPT_PATH_PROPERTY = "cards.docling.script";

    private static final String PYTHON_COMMAND_PROPERTY = "cards.docling.python";

    private static final String DEFAULT_PYTHON_COMMAND = "python";

    /**
     * Convert the document stream to markdown by delegating to the Docling Python script.
     * Returns an empty string on any failure.
     *
     * @param stream the input document stream
     * @param fileName source file name; the extension is used to infer the document type
     * @return markdown text, or an empty string on any failure
     */
    public String toMarkdown(final InputStream stream, final String fileName)
    {
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
            return runDocling(tmpFile, fileName);
        } catch (IOException e) {
            LOGGER.warn("Failed to create temp file for Docling fallback on '{}': {}", fileName, e.getMessage());
            return "";
        } finally {
            deleteSilently(tmpFile);
        }
    }

    /**
     * Determine whether a generated markdown result contains enough content to be considered useful.
     *
     * @param result the markdown string to evaluate
     * @return {@code true} if the result contains at least the minimum required amount of content
     */
    static boolean isSufficient(final String result)
    {
        if (StringUtils.isBlank(result)) {
            return false;
        }
        final String stripped = result.replaceAll("<!--.*?-->", "").replaceAll("## Page \\d+", "").trim();
        return stripped.length() >= MIN_CONTENT_CHARS;
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
        try {
            final Process process = startDoclingProcess(tmpFile, fileName);
            final CompletableFuture<String> progressFuture = readProcessStdoutAsync(process);
            if (!waitForProcess(process, progressFuture, fileName)) {
                return "";
            }
            return readOutputFile(tmpFile, fileName);
        } catch (IOException e) {
            LOGGER.error("Failed to start Docling process for '{}': {}", fileName, e.getMessage());
            return "";
        }
    }

    private Process startDoclingProcess(final File tmpFile, final String fileName)
        throws IOException
    {
        final String pythonCmd = resolvePythonCommand();
        final String scriptPath = resolveScriptPath();
        LOGGER.debug("Starting Docling process for '{}' with script '{}'", fileName, scriptPath);
        return new ProcessBuilder(pythonCmd, scriptPath,
            tmpFile.getAbsolutePath())
            .redirectErrorStream(true)
            .start();
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
        try {
            final boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                progressFuture.cancel(true);
                LOGGER.error("Docling process timed out after {} minutes for '{}'", TIMEOUT_MINUTES, fileName);
                return false;
            }
            final int exitCode = process.exitValue();
            if (exitCode != 0) {
                final String output = collectOutputSilently(progressFuture);
                LOGGER.error("Docling process exited with code {} for '{}':\n{}", exitCode, fileName, output);
                return false;
            }
            LOGGER.debug("Docling process output for '{}':\n{}", fileName, collectOutputSilently(progressFuture));
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
}
