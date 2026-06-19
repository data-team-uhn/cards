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

/**
 * Factory returning the appropriate parser for a given file extension.
 * <p>
 * Routing rules:
 * </p>
 * <ul>
 *   <li>PDF — Docling primary with PDFBox fallback; DOCX — Docling primary with Apache POI fallback</li>
 *   <li>DOC — LibreOffice conversion to DOCX, then processed with DocxMarkdownGenerator</li>
 *   <li>any other extension — returns {@code null} (caller skips the file)</li>
 * </ul>
 *
 * @version $Id$
 */
public class FileParserFactory
{
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
        if (normalizedName.endsWith(".pdf")) {
            return this.pdfParser;
        }
        if (normalizedName.endsWith(".docx")) {
            return this.docxParser;
        }
        if (normalizedName.endsWith(".doc")) {
            return this.docParser;
        }
        return null;
    }
}
