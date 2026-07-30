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

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document parser that stages the upload under the shared docs volume and lets the Docling daemon
 * (LibreOffice + Docling + {@code write_chunk_files}) produce all derived files.
 * <p>
 * When the daemon cannot be reached or returns empty or insufficient markdown the parse fails with
 * a {@link DocumentParseException} — there is no other processor.
 * </p>
 *
 * @version $Id$
 */
public class SimpleDocumentParser implements FileParser
{
    private static final int MIN_CONTENT_CHARS = 50;

    /**
     * The active LLM model's small-document chunking threshold, for the duration of one parse.
     * <p>
     * The editor sets this before parsing on the same thread and clears it afterwards. Unset means
     * the daemon applies its own default.
     * </p>
     */
    private static final ThreadLocal<Long> CHUNKING_THRESHOLD = new ThreadLocal<>();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final DoclingMarkdownGenerator doclingGenerator = new DoclingMarkdownGenerator();

    @Override
    public String parse(final InputStream stream, final String fileName, final String outputSubfolder)
    {
        final long startTimestamp = System.currentTimeMillis();
        final byte[] content;
        try {
            content = stream.readAllBytes();
        } catch (IOException e) {
            this.logger.warn("Failed to read document stream for '{}': {}", fileName, e.getMessage());
            throw new DocumentParseException("Failed to read document stream", e);
        }
        if (content.length == 0) {
            this.logger.warn("Failed to read stream for '{}', document is empty", fileName);
            throw new DocumentParseException("Document is empty", null);
        }
        final String markdown = runParseSafely(content, fileName, outputSubfolder);
        if (!isSufficient(markdown)) {
            this.logger.warn("Docling produced no usable output for '{}', parsing failed", fileName);
            throw new DocumentParseException("Generated output is empty", null);
        }
        logParseFinished(fileName, startTimestamp, markdown);
        return markdown;
    }

    private String runParseSafely(final byte[] content, final String fileName, final String outputSubfolder)
    {
        try {
            final Long threshold = CHUNKING_THRESHOLD.get();
            return this.doclingGenerator.parse(content, fileName, outputSubfolder,
                threshold == null ? 0L : threshold);
        } catch (RuntimeException | LinkageError e) {
            this.logger.warn("Docling parse failed for '{}': {}", fileName, e.getMessage());
            return null;
        }
    }

    private static boolean isSufficient(final String result)
    {
        if (StringUtils.isBlank(result)) {
            return false;
        }
        final String stripped = result.replaceAll("<!--.*?-->", "").replaceAll("## Page \\d+", "").trim();
        return stripped.length() >= MIN_CONTENT_CHARS;
    }

    private void logParseFinished(final String fileName, final long startTimestamp, final String result)
    {
        final long endTimestamp = System.currentTimeMillis();
        this.logger.info(
            "Document parsing finished for '{}' using Docling at {} (total {} ms, result {} chars)",
            fileName, endTimestamp, endTimestamp - startTimestamp, result.length());
    }

    /**
     * Set the small-document chunking threshold for parses on this thread.
     *
     * @param minStructureTokens the active LLM model's {@code wholeDocumentTokenLimit}; {@code 0} or less leaves
     *            the daemon's own default in force
     */
    public static void setChunkingThreshold(final long minStructureTokens)
    {
        CHUNKING_THRESHOLD.set(minStructureTokens);
    }

    /** Clear the thread's chunking threshold once parsing is done. */
    public static void clearChunkingThreshold()
    {
        CHUNKING_THRESHOLD.remove();
    }
}
