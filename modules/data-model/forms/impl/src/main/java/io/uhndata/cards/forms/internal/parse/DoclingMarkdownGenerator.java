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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Markdown generator that stages the document under the shared docs volume and asks the Docling
 * daemon to parse it by absolute path.
 * <p>
 * The daemon writes {@code {stem}.md} and {@code Chunks/}; this class only reads the markdown back
 * for the caller. When the daemon cannot be reached this returns {@code null}.
 * </p>
 *
 * @version $Id$
 */
public class DoclingMarkdownGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingMarkdownGenerator.class);

    /**
     * Stage {@code content} beside the answer folder, ask the daemon to parse that path, and read
     * the written markdown.
     *
     * @param content raw document bytes
     * @param fileName source file name; its extension selects the Docling / LibreOffice path
     * @param outputSubfolder answer UUID subfolder under the shared docs root
     * @param minStructureTokens documents under this many estimated tokens are left unchunked;
     *            pass {@code 0} or less to let the daemon apply its own default
     * @return the written markdown, or {@code null} on any failure
     */
    public String parse(final byte[] content, final String fileName, final String outputSubfolder,
        final long minStructureTokens)
    {
        final long startTimestamp = System.currentTimeMillis();
        LOGGER.info("Docling parse request started for '{}'", fileName);
        DoclingParseClient.ParseSummary summary = null;
        String markdown = null;
        try {
            final Path staged = ParsedMarkdownStore.stageSourceFile(outputSubfolder, fileName, content);
            if (staged == null) {
                LOGGER.warn("Could not stage document '{}' for Docling", fileName);
                return null;
            }
            summary = DoclingParseClient.parse(staged.toAbsolutePath().toString(), true, minStructureTokens);
            if (summary == null) {
                return null;
            }
            markdown = readMarkdown(summary.getMarkdownPath(), fileName);
            return markdown;
        } finally {
            final long endTimestamp = System.currentTimeMillis();
            LOGGER.info("Docling parse response received for '{}' at {} (total {} ms, {} chars, chunked={})",
                fileName, endTimestamp, endTimestamp - startTimestamp,
                markdown == null ? 0 : markdown.length(),
                summary != null && summary.isChunked());
            if (summary != null && StringUtils.isNotBlank(summary.getLogs())) {
                LOGGER.info("Docling daemon log for '{}':\n{}", fileName, summary.getLogs());
            }
        }
    }

    private static String readMarkdown(final String markdownPath, final String fileName)
    {
        try {
            final Path path = Path.of(markdownPath);
            if (!Files.isRegularFile(path)) {
                LOGGER.warn("Docling reported markdown at '{}' for '{}' but the file is missing",
                    markdownPath, fileName);
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read Docling markdown for '{}': {}", fileName, e.getMessage());
            return null;
        }
    }
}
