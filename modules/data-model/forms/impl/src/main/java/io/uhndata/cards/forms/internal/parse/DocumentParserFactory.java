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
 * Factory returning the parser for a known file extension.
 *
 * @version $Id$
 */
public class DocumentParserFactory
{
    private final DocumentParser pdfParser = new PdfParser();

    private final DocumentParser docxParser = new DocxParser();

    /**
     * Choose parser by filename extension.
     *
     * @param fileName the uploaded filename
     * @return matching parser, or {@code null} if unsupported
     */
    public DocumentParser getParser(final String fileName)
    {
        String normalizedName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (normalizedName.endsWith(".pdf")) {
            return this.pdfParser;
        } else if (normalizedName.endsWith(".docx")) {
            return this.docxParser;
        }
        return null;
    }
}
