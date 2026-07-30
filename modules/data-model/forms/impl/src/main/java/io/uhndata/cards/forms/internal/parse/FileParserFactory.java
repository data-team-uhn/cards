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
 * PDF, DOCX, and DOC all use {@link SimpleDocumentParser}: Java stages the upload under the shared
 * docs volume and the Docling daemon (LibreOffice + Docling) writes all derived files. Any other
 * extension returns {@code null} (caller skips the file).
 * </p>
 *
 * @version $Id$
 */
public class FileParserFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileParserFactory.class);

    private final FileParser documentParser = new SimpleDocumentParser();

    /**
     * Choose parser by filename extension.
     *
     * @param fileName the uploaded filename; must not be {@code null} or blank
     * @return matching parser, or {@code null} if the format is unsupported
     */
    public FileParser getParser(final String fileName)
    {
        final String normalizedName = fileName.toLowerCase(Locale.ROOT);
        if (normalizedName.endsWith(".pdf") || normalizedName.endsWith(".docx")
            || normalizedName.endsWith(".doc")) {
            LOGGER.info("Selected parser {} for '{}'", this.documentParser.getClass().getSimpleName(), fileName);
            return this.documentParser;
        }
        LOGGER.info("No document parser registered for '{}'", fileName);
        return null;
    }
}
