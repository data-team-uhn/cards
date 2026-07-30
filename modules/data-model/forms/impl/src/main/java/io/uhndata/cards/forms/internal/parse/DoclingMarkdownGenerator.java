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
 * Markdown generator that delegates parsing to the Docling daemon.
 * <p>
 * The document is sent to {@code POST /parse} as bytes and the daemon returns the Markdown together with the
 * chunk tree, so the daemon needs no access to this JVM's filesystem and runs in its own container. See
 * {@link DoclingParseClient} for the transport.
 * </p>
 * <p>
 * There is no local-Python path. Running {@code docling_parser.py} as a subprocess used to be the fallback when
 * the daemon was unreachable, but it required Docling installed next to the JVM — the very dependency the
 * container removes — and it passed filesystem paths, which cannot work across a container boundary. When the
 * daemon cannot be reached this returns an empty result and {@link SimpleDocumentParser} fails the parse; there
 * is no other processor.
 * </p>
 *
 * @version $Id$
 */
public class DoclingMarkdownGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingMarkdownGenerator.class);

    /**
     * Convert a document to Markdown and build its chunk tree in one daemon call.
     *
     * @param stream the input document stream
     * @param fileName source file name; its extension selects the Docling backend
     * @param minStructureTokens documents under this many estimated tokens are left unchunked; pass {@code 0}
     *            or less to let the daemon apply its own default
     * @return the parsed document, or {@code null} on any failure
     */
    public DoclingParseClient.ParsedDocument parse(final InputStream stream, final String fileName,
        final long minStructureTokens)
    {
        final long startTimestamp = System.currentTimeMillis();
        LOGGER.info("Docling parse request started for '{}'", fileName);
        DoclingParseClient.ParsedDocument result = null;
        try {
            final byte[] content;
            try {
                content = stream.readAllBytes();
            } catch (IOException e) {
                LOGGER.warn("Failed to read document stream for '{}': {}", fileName, e.getMessage());
                return null;
            }
            result = DoclingParseClient.parse(content, fileName, true, minStructureTokens);
            return result;
        } finally {
            final long endTimestamp = System.currentTimeMillis();
            LOGGER.info("Docling parse response received for '{}' at {} (total {} ms, {} chars, {} chunk(s))",
                fileName, endTimestamp, endTimestamp - startTimestamp,
                result == null ? 0 : result.getMarkdown().length(),
                result == null ? 0 : result.getChunks().size());
            // The daemon's per-request log carries the page-shard timings and the resolved
            // parallelism. Inside a container its own stdout goes to `docker logs`, unattached to
            // any document, so this reply field is the only path by which per-document parse
            // timings reach error.log — log it or the numbers are collected and thrown away.
            if (result != null && StringUtils.isNotBlank(result.getLogs())) {
                LOGGER.info("Docling daemon log for '{}':\n{}", fileName, result.getLogs());
            }
        }
    }

    /**
     * Convert a document to Markdown, discarding the chunk tree.
     *
     * @param stream the input document stream
     * @param fileName source file name; its extension selects the Docling backend
     * @return markdown text, or an empty string on any failure
     */
    public String toMarkdown(final InputStream stream, final String fileName)
    {
        final DoclingParseClient.ParsedDocument parsed = parse(stream, fileName, 0L);
        return parsed == null ? "" : parsed.getMarkdown();
    }
}
