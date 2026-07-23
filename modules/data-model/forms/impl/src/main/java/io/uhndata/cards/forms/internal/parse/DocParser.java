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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for legacy DOC files.
 * <p>
 * Converts the DOC file to DOCX with headless LibreOffice ({@link LibreOfficeConverter}), then processes the
 * resulting DOCX with {@link DocxParser}. In parallel, the original DOC is rendered to a PDF beside the parse
 * output ({@link LibreOfficeConverter#convertToPdfAsync}); that runs in the background and never delays
 * parsing. The DOCX sub-parse is told not to render its own PDF (via
 * {@link LibreOfficeConverter#SKIP_DOCX_PDF}) so the document is rendered to PDF exactly once, from the
 * original DOC.
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
            // Render the original DOC to PDF in the background, beside the parse output. Fire-and-forget so it
            // never delays parsing; the DOCX sub-parse below is told not to render a second PDF.
            LibreOfficeConverter.convertToPdfAsync(content, "doc", fileName, outputSubfolder);
            final byte[] docxContent = Files.readAllBytes(docxFile.toPath());
            return parseDocxSuppressingPdf(docxContent, fileName, outputSubfolder);
        } catch (IOException | SecurityException e) {
            LOGGER.warn("DOC to DOCX conversion failed for '{}': {}", fileName, e.getMessage());
            return "";
        } finally {
            deleteSilently(docFile);
            deleteDirSilently(outputDir);
        }
    }

    private String parseDocxSuppressingPdf(final byte[] docxContent, final String fileName,
        final String outputSubfolder)
    {
        LibreOfficeConverter.suppressDocxPdf();
        try {
            return this.docxParser.parse(new ByteArrayInputStream(docxContent), fileName, outputSubfolder);
        } finally {
            LibreOfficeConverter.clearDocxPdfSuppression();
        }
    }

    private File convertToDocx(final File docFile, final File outputDir)
        throws IOException
    {
        return LibreOfficeConverter.convert(docFile, outputDir, "docx", false);
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
}
