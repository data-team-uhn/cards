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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.internal.parse.pdf.PdfMarkdownGenerator;

/**
 * Parser for PDF files. Delegates orchestration to {@link SimpleDocumentParser} and supplies
 * {@link DoclingMarkdownGenerator} as the primary generator, with
 * {@link PdfMarkdownGenerator} (PDFBox) as the fallback.
 *
 * @version $Id$
 */
public class PdfParser extends SimpleDocumentParser
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PdfParser.class);

    private final PdfMarkdownGenerator pdfBoxGenerator = new PdfMarkdownGenerator();

    @Override
    protected void onDocumentBytes(final byte[] content, final String fileName, final String outputSubfolder)
    {
        // Persist the uploaded PDF beside its parsed markdown — mirroring the DOCX -> PDF rendition
        // office sources get — so the chunker can mine its bookmark outline into bookmarks.json.
        Path uploadedPdfPath = null;
        try {
            uploadedPdfPath = Files.createTempFile("cards-pdf-source-", ".pdf");
            Files.write(uploadedPdfPath, content);
            ParsedMarkdownStore.saveArtifact(outputSubfolder, fileName, "pdf", uploadedPdfPath);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not co-locate source PDF for '{}': {}", fileName, e.getMessage());
        } finally {
            if (uploadedPdfPath != null) {
                try {
                    Files.deleteIfExists(uploadedPdfPath);
                } catch (IOException e) {
                    LOGGER.debug("Could not delete temp PDF source {}: {}", uploadedPdfPath, e.getMessage());
                }
            }
        }
    }

    @Override
    protected String runFallbackGenerator(final byte[] content, final String fileName)
    {
        try {
            setActiveGenerator("PDFBox");
            return this.pdfBoxGenerator.toMarkdown(new ByteArrayInputStream(content), fileName);
        } catch (IOException | LinkageError e) {
            return "";
        }
    }
}
