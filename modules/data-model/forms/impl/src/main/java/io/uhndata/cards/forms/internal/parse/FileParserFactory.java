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
 *   <li>PDF, DOCX, and DOC — parsed with Docling (DOC/DOCX LibreOffice prep runs in Python)</li>
 *   <li>any other extension — returns {@code null} (caller skips the file)</li>
 * </ul>
 *
 * @version $Id$
 */
public class FileParserFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileParserFactory.class);

    private final FileParser pdfParser = new PdfParser();

    private final FileParser docxParser = new DocxParser();

    private final FileParser docParser = new DocParser();

    /**
     * Choose parser by filename extension.
     *
     * @param fileName the uploaded filename; must not be {@code null} or blank
     * @return matching parser, or {@code null} if the format is unsupported
     */
    public FileParser getParser(final String fileName)
    {
        final String normalizedName = fileName.toLowerCase(Locale.ROOT);
        final FileParser parser;
        if (normalizedName.endsWith(".pdf")) {
            parser = this.pdfParser;
        } else if (normalizedName.endsWith(".docx")) {
            parser = this.docxParser;
        } else if (normalizedName.endsWith(".doc")) {
            parser = this.docParser;
        } else {
            LOGGER.info("No document parser registered for '{}'", fileName);
            return null;
        }
        LOGGER.info("Selected parser {} for '{}'", parser.getClass().getSimpleName(), fileName);
        return parser;
    }
}
