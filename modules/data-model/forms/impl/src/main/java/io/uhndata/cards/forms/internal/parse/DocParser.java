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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for legacy DOC files.
 * <p>
 * Converts the DOC file to DOCX by invoking LibreOffice in headless mode, then processes the
 * resulting DOCX with {@link DocxMarkdownGenerator}. If the LibreOffice conversion itself fails
 * or times out, the original DOC content is passed directly to
 * {@link DoclingMarkdownGenerator}.
 * </p>
 * <p>
 * The LibreOffice executable path can be overridden via the system property
 * {@code cards.libreoffice.soffice}; it defaults to {@code soffice} (assumed to be on the PATH).
 * On Windows set it to the full path, e.g.
 * {@code C:/Program Files/LibreOffice/program/soffice.exe}.
 * </p>
 *
 * @version $Id$
 */
public class DocParser implements FileParser
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DocParser.class);

    private static final long CONVERSION_TIMEOUT_MINUTES = 2L;

    private static final String SOFFICE_PROPERTY = "cards.libreoffice.soffice";

    private static final String DEFAULT_SOFFICE = "soffice";

    private final DocxParser docxParser = new DocxParser();

    @Override
    public String parse(final InputStream stream, final String fileName, final String outputSubfolder)
    {
        final byte[] content;
        try {
            content = stream.readAllBytes();
        } catch (IOException e) {
            LOGGER.error("Failed to read DOC stream for '{}': {}", fileName, e.getMessage());
            return "";
        }
        return processDocAsDocx(content, fileName, outputSubfolder);
    }

    private String processDocAsDocx(final byte[] content, final String fileName, final String outputSubfolder)
    {
        File docFile = null;
        File outputDir = null;
        try {
            docFile = writeToTempFile(content, "doc");
            outputDir = Files.createTempDirectory("cards-doc-out-").toFile();
            final File docxFile = convertToDocx(docFile, outputDir);
            final byte[] docxContent = Files.readAllBytes(docxFile.toPath());
            return this.docxParser.parse(new ByteArrayInputStream(docxContent), fileName, outputSubfolder);
        } catch (IOException | SecurityException e) {
            LOGGER.warn("DOC to DOCX conversion failed for '{}': {}", fileName, e.getMessage());
            return "";
        } finally {
            deleteSilently(docFile);
            deleteDirSilently(outputDir);
        }
    }

    private File convertToDocx(final File docFile, final File outputDir)
        throws IOException
    {
        final Process process = new ProcessBuilder(
            resolveSofficePath(), "--headless",
            "--convert-to", "docx",
            "--outdir", outputDir.getAbsolutePath(),
            docFile.getAbsolutePath())
            .redirectErrorStream(true)
            .start();
        final CompletableFuture<String> outputFuture = readProcessOutputAsync(process);
        return verifyProcessing(process, outputFuture, outputDir, docFile.getName());
    }

    private CompletableFuture<String> readProcessOutputAsync(final Process process)
    {
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException e) {
                LOGGER.debug("Error reading LibreOffice output: {}", e.getMessage());
                return "";
            }
        });
    }

    private File verifyProcessing(final Process process, final CompletableFuture<String> outputFuture,
        final File outputDir, final String originalFileName)
        throws IOException
    {
        try {
            final boolean finished = process.waitFor(CONVERSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                throw new IOException("LibreOffice conversion timed out for '" + originalFileName + "'");
            }
            final int exitCode = process.exitValue();
            if (exitCode != 0) {
                final String output = collectOutput(outputFuture);
                throw new IOException(
                    "LibreOffice conversion failed (exit " + exitCode + ") for '"
                    + originalFileName + "': " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("LibreOffice conversion interrupted for '" + originalFileName + "'", e);
        }
        final File docxFile = new File(outputDir, stripExtension(originalFileName) + ".docx");
        if (!docxFile.exists()) {
            throw new IOException(
                "Conversion succeeded but output file not found: " + docxFile.getAbsolutePath());
        }
        return docxFile;
    }

    private static String collectOutput(final CompletableFuture<String> outputFuture)
    {
        try {
            return outputFuture.get(5L, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return "(output unavailable)";
        }
    }

    private File writeToTempFile(final byte[] content, final String extension)
        throws IOException
    {
        final File tmpFile = File.createTempFile("cards-doc-", "." + extension);
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

    private void deleteDirSilently(final File dir)
    {
        if (dir == null) {
            return;
        }
        try {
            final File[] files = dir.listFiles();
            if (files != null) {
                for (final File file : files) {
                    Files.deleteIfExists(file.toPath());
                }
            }
            Files.deleteIfExists(dir.toPath());
        } catch (IOException e) {
            LOGGER.debug("Could not delete temp dir {}: {}", dir.getAbsolutePath(), e.getMessage());
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

    private static String resolveSofficePath()
    {
        final String configured = System.getProperty(SOFFICE_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return DEFAULT_SOFFICE;
    }
}
