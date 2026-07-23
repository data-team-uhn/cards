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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared LibreOffice (headless {@code soffice}) document conversion, used both for the synchronous
 * DOC&nbsp;&rarr;&nbsp;DOCX step of {@link DocParser} and for producing a PDF rendition of every parsed
 * DOC/DOCX alongside its markdown.
 * <p>
 * The PDF rendition is fire-and-forget on a background daemon executor ({@link #convertToPdfAsync}): the main
 * parsing flow never waits for it, and the heavy work runs in a separate {@code soffice} OS process so it does
 * not compete with parsing for the JVM. The PDF is written into the same answer folder as the parse markdown
 * (see {@link ParsedMarkdownStore#saveArtifact}), named after the source file with a {@code .pdf} extension —
 * the same naming the DOC&nbsp;&rarr;&nbsp;DOCX step uses. A failure is logged and swallowed; it never affects
 * parsing.
 * </p>
 * <p>
 * The {@code soffice} executable path can be overridden via the system property
 * {@code cards.libreoffice.soffice}; it defaults to {@code soffice} (assumed to be on the PATH).
 * </p>
 *
 * @version $Id$
 */
final class LibreOfficeConverter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LibreOfficeConverter.class);

    private static final long CONVERSION_TIMEOUT_MINUTES = 2L;

    private static final String SOFFICE_PROPERTY = "cards.libreoffice.soffice";

    private static final String DEFAULT_SOFFICE = "soffice";

    /** Explicit PDF export filter, so LibreOffice uses the Writer PDF exporter rather than guessing. */
    private static final String PDF_CONVERT_TO = "pdf:writer_pdf_Export";

    private static final long OUTPUT_COLLECT_TIMEOUT_SECONDS = 5L;

    /**
     * Set on the parsing thread while a DOC file is being processed, so the DOCX sub-parse does not also fire a
     * PDF conversion: the DOC path converts the original DOC to PDF itself (see {@link DocParser}).
     */
    private static final ThreadLocal<Boolean> SKIP_DOCX_PDF = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Background executor for PDF conversions: a single daemon, minimum-priority thread so PDF rendering stays
     * out of the way of parsing and never blocks JVM shutdown. Conversions run one at a time.
     */
    private static final ExecutorService PDF_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "cards-pdf-convert");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private LibreOfficeConverter()
    {
        // Utility class, never instantiated.
    }

    /**
     * Suppress the automatic DOCX&nbsp;&rarr;&nbsp;PDF rendition on the current thread. The DOC path calls this
     * around its DOCX sub-parse, because it renders the original DOC to PDF itself.
     */
    static void suppressDocxPdf()
    {
        SKIP_DOCX_PDF.set(Boolean.TRUE);
    }

    /**
     * Clear the DOCX&nbsp;&rarr;&nbsp;PDF suppression set by {@link #suppressDocxPdf()} on the current thread.
     */
    static void clearDocxPdfSuppression()
    {
        SKIP_DOCX_PDF.remove();
    }

    /**
     * Whether the automatic DOCX&nbsp;&rarr;&nbsp;PDF rendition is suppressed on the current thread.
     *
     * @return {@code true} when a DOCX&nbsp;&rarr;&nbsp;PDF rendition should be skipped
     */
    static boolean isDocxPdfSuppressed()
    {
        return Boolean.TRUE.equals(SKIP_DOCX_PDF.get());
    }

    /**
     * Convert a file to another format with headless LibreOffice, synchronously.
     *
     * @param inputFile the source file to convert
     * @param outputDir the directory LibreOffice writes the converted file into
     * @param targetFormat the target format passed to {@code --convert-to} (e.g. {@code docx}, {@code pdf})
     * @param isolatedProfile when {@code true}, run with a dedicated, temporary user-profile directory so the
     *            invocation cannot clash with a concurrent {@code soffice} run over the shared default profile
     * @return the converted file inside {@code outputDir}
     * @throws IOException if the conversion fails, times out, or the expected output file is not produced
     */
    static File convert(final File inputFile, final File outputDir, final String targetFormat,
        final boolean isolatedProfile) throws IOException
    {
        File profileDir = null;
        try {
            final List<String> command = new ArrayList<>();
            command.add(resolveSofficePath());
            command.add("--headless");
            if (isolatedProfile) {
                profileDir = Files.createTempDirectory("cards-lo-profile-").toFile();
                command.add("-env:UserInstallation=" + profileDir.toURI());
            }
            command.add("--convert-to");
            command.add(targetFormat);
            command.add("--outdir");
            command.add(outputDir.getAbsolutePath());
            command.add(inputFile.getAbsolutePath());
            final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final CompletableFuture<String> outputFuture = drainOutputAsync(process);
            return awaitOutput(process, outputFuture, outputDir, inputFile.getName(), targetFormat);
        } finally {
            deleteDirSilently(profileDir);
        }
    }

    /**
     * Render a parsed DOC/DOCX to PDF in the background and save it beside the parse markdown. Returns
     * immediately; the conversion runs on the {@link #PDF_EXECUTOR}. Does nothing when the source is empty or
     * when there is no answer folder to save into.
     *
     * @param sourceBytes the original document bytes (DOC or DOCX)
     * @param sourceExtension the source file extension without a dot (e.g. {@code doc}, {@code docx})
     * @param fileName the original source file name; the PDF takes this base name with a {@code .pdf} extension
     * @param outputSubfolder the owning answer's parse subfolder (where the markdown is saved)
     */
    static void convertToPdfAsync(final byte[] sourceBytes, final String sourceExtension, final String fileName,
        final String outputSubfolder)
    {
        if (sourceBytes == null || sourceBytes.length == 0) {
            return;
        }
        if (ParsedMarkdownStore.resolveAnswerDir(outputSubfolder) == null) {
            LOGGER.debug("No answer folder for '{}'; skipping background PDF conversion", fileName);
            return;
        }
        PDF_EXECUTOR.execute(() -> toPdf(sourceBytes, sourceExtension, fileName, outputSubfolder));
    }

    private static void toPdf(final byte[] sourceBytes, final String sourceExtension, final String fileName,
        final String outputSubfolder)
    {
        File input = null;
        File outputDir = null;
        try {
            input = writeTempFile(sourceBytes, sourceExtension);
            outputDir = Files.createTempDirectory("cards-pdf-out-").toFile();
            final File pdf = convert(input, outputDir, PDF_CONVERT_TO, true);
            ParsedMarkdownStore.saveArtifact(outputSubfolder, fileName, "pdf", pdf.toPath());
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Background PDF conversion failed for '{}': {}", fileName, e.getMessage());
        } finally {
            deleteSilently(input);
            deleteDirSilently(outputDir);
        }
    }

    private static CompletableFuture<String> drainOutputAsync(final Process process)
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

    private static File awaitOutput(final Process process, final CompletableFuture<String> outputFuture,
        final File outputDir, final String inputName, final String targetFormat) throws IOException
    {
        try {
            final boolean finished = process.waitFor(CONVERSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                throw new IOException("LibreOffice conversion timed out for '" + inputName + "'");
            }
            final int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("LibreOffice conversion failed (exit " + exitCode + ") for '"
                    + inputName + "': " + collectOutput(outputFuture));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("LibreOffice conversion interrupted for '" + inputName + "'", e);
        }
        final File output = new File(outputDir, stripExtension(inputName) + "." + outputExtension(targetFormat));
        if (!output.exists()) {
            throw new IOException("Conversion succeeded but output file not found: " + output.getAbsolutePath());
        }
        return output;
    }

    /**
     * The output file extension LibreOffice writes for a {@code --convert-to} argument: the part before any
     * {@code :} export-filter suffix (e.g. {@code pdf} for {@code pdf:writer_pdf_Export}).
     *
     * @param targetFormat the {@code --convert-to} argument
     * @return the resulting file extension
     */
    private static String outputExtension(final String targetFormat)
    {
        final int colon = targetFormat.indexOf(':');
        return colon > 0 ? targetFormat.substring(0, colon) : targetFormat;
    }

    private static String collectOutput(final CompletableFuture<String> outputFuture)
    {
        try {
            return outputFuture.get(OUTPUT_COLLECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "(output unavailable)";
        } catch (ExecutionException | TimeoutException e) {
            return "(output unavailable)";
        }
    }

    private static File writeTempFile(final byte[] content, final String extension) throws IOException
    {
        final File tempFile = File.createTempFile("cards-pdfsrc-", "." + extension);
        Files.write(tempFile.toPath(), content);
        return tempFile;
    }

    private static String resolveSofficePath()
    {
        final String configured = System.getProperty(SOFFICE_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return DEFAULT_SOFFICE;
    }

    private static String stripExtension(final String fileName)
    {
        final int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private static void deleteSilently(final File file)
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

    private static void deleteDirSilently(final File dir)
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
}
