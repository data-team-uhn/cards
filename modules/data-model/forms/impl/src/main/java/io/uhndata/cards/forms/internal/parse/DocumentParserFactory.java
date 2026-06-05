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

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory returning the appropriate parser for a given file extension.
 * <p>
 * Routing rules:
 * </p>
 * <ul>
 *   <li>PDF, DOCX — primary Java generator with a two-minute timeout and Docling fallback</li>
 *   <li>DOC — LibreOffice conversion to DOCX, then processed with DocxMarkdownGenerator</li>
 *   <li>any other extension — logs an error and returns {@code null} (file skipped)</li>
 * </ul>
 *
 * @version $Id$
 */
public class DocumentParserFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentParserFactory.class);

    private final DocumentParser pdfParser = new PdfParser();

    private final DocumentParser docxParser = new DocxParser();

    private final DocumentParser docParser = new DocParser();

    /**
     * Choose parser by filename extension.
     *
     * @param fileName the uploaded filename
     * @return matching parser, or {@code null} if the format is unsupported
     */
    public DocumentParser getParser(final String fileName)
    {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        final String normalizedName = fileName.toLowerCase(Locale.ROOT);
        if (normalizedName.endsWith(".pdf")) {
            return this.pdfParser;
        }
        if (normalizedName.endsWith(".docx")) {
            return this.docxParser;
        }
        if (normalizedName.endsWith(".doc")) {
            return this.docParser;
        }
        LOGGER.error("Unsupported file format, skipping: '{}'", fileName);
        return null;
    }
}
