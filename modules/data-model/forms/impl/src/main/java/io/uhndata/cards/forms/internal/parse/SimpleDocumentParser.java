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
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for document parsers that apply a primary generator and fall back to a secondary
 * generator when the primary fails, returns empty output, or produces insufficient content.
 * <p>
 * Subclasses implement {@link #runFallbackGenerator} to supply a format-specific fallback.
 * All shared orchestration logic — stream reading, error handling, and sufficiency check — lives here.
 * </p>
 *
 * @version $Id$
 */
public abstract class SimpleDocumentParser implements FileParser
{
    private static final int MIN_CONTENT_CHARS = 50;

    /** Name of the generator that produced the current parse result (request-scoped). */
    private static final ThreadLocal<String> ACTIVE_GENERATOR = new ThreadLocal<>();

    /**
     * The active LLM model's small-document chunking threshold, for the duration of one parse.
     * <p>
     * Chunking now happens inside the parse call, but the threshold comes from the LLM configuration, which only
     * the editor can resolve — these parsers are plain classes, not OSGi components, so they cannot reference the
     * configuration service. The editor sets this before parsing on the same thread and clears it afterwards,
     * mirroring how {@link #ACTIVE_GENERATOR} is already scoped. Unset means the daemon applies its own default.
     * </p>
     */
    private static final ThreadLocal<Long> CHUNKING_THRESHOLD = new ThreadLocal<>();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final DoclingMarkdownGenerator doclingGenerator = new DoclingMarkdownGenerator();

    @Override
    public final String parse(final InputStream stream, final String fileName, final String outputSubfolder)
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
        onDocumentBytes(content, fileName, outputSubfolder);
        try {
            final DoclingParseClient.ParsedDocument parsed = runPrimaryParseSafely(content, fileName);
            final String primary = parsed == null ? "" : parsed.getMarkdown();
            if (isSufficient(primary)) {
                ParsedMarkdownStore.save(outputSubfolder, fileName, primary);
                // The daemon returned the chunk tree with the Markdown, so it is written here rather
                // than fetched by a second, path-based call.
                ParsedMarkdownStore.saveChunkTree(outputSubfolder, parsed.getOutline(), parsed.getCatalog(),
                    parsed.getChunks());
                logParseFinished(fileName, startTimestamp, primary, "primary");
                return primary;
            }
            this.logger.info("Primary generator '{}' failed or produced insufficient output for '{}', using fallback",
                currentGeneratorName("primary"), fileName);
            final String fallback = runFallbackGenerator(content, fileName);
            if (isSufficient(fallback)) {
                ParsedMarkdownStore.save(outputSubfolder, fileName, fallback);
                // The pure-Java generators produce Markdown but cannot chunk — that logic exists only in
                // Python. Record an outline saying so, rather than leaving no Chunks/ at all: downstream
                // must be able to tell "no chunker ran" from "document was too small to chunk", which is
                // also an unchunked state but a legitimate send-it-whole one.
                ParsedMarkdownStore.saveUnchunkedOutline(outputSubfolder, fileName, fallback,
                    ParsedMarkdownStore.UNCHUNKED_CHUNKER_UNAVAILABLE);
                logParseFinished(fileName, startTimestamp, fallback, "fallback");
                return fallback;
            }
            this.logger.warn("Fallback generator '{}' produced insufficient output for '{}', document is empty",
                currentGeneratorName("fallback"), fileName);
            throw new DocumentParseException("Generated output is empty", null);
        } finally {
            ACTIVE_GENERATOR.remove();
        }
    }

    private DoclingParseClient.ParsedDocument runPrimaryParseSafely(final byte[] content, final String fileName)
    {
        try {
            return runPrimaryParse(content, fileName);
        } catch (RuntimeException | LinkageError e) {
            this.logger.warn("Primary generator failed for '{}': {}", fileName, e.getMessage());
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

    private void logParseFinished(final String fileName, final long startTimestamp, final String result,
        final String path)
    {
        final long endTimestamp = System.currentTimeMillis();
        this.logger.info(
            "Document parsing finished for '{}' using generator '{}' ({} path) at {} (total {} ms, result {} chars)",
            fileName, currentGeneratorName(path), path, endTimestamp, endTimestamp - startTimestamp,
            result.length());
    }

    private static String currentGeneratorName(final String pathFallback)
    {
        final String named = ACTIVE_GENERATOR.get();
        return StringUtils.isNotBlank(named) ? named : pathFallback;
    }

    /**
     * Record which concrete markdown generator is about to run for this request.
     *
     * @param generatorName human-readable generator label (e.g. {@code Docling}, {@code Apache POI})
     */
    protected static void setActiveGenerator(final String generatorName)
    {
        ACTIVE_GENERATOR.set(generatorName);
    }

    /**
     * Hook invoked once the document bytes have been read, before any generator runs. The default does
     * nothing; subclasses override it to trigger format-specific side work (such as an asynchronous PDF
     * rendition) that must run in parallel with, and must never delay or interfere with, the parse itself.
     *
     * @param content the raw document bytes
     * @param fileName the source file name
     * @param outputSubfolder the owning answer's parse subfolder
     */
    protected void onDocumentBytes(final byte[] content, final String fileName, final String outputSubfolder)
    {
        // No-op by default; format-specific parsers may override.
    }

    /**
     * Run the primary generator against the document bytes.
     * The default implementation delegates to {@link DoclingMarkdownGenerator}, which returns the Markdown and
     * the chunk tree from one daemon call.
     * Subclasses may override to supply a format-specific primary generator.
     * Must not throw — return {@code null} on any generator error.
     *
     * @param content raw document bytes
     * @param fileName source file name
     * @return the parsed document, or {@code null} on failure
     */
    protected DoclingParseClient.ParsedDocument runPrimaryParse(final byte[] content, final String fileName)
    {
        setActiveGenerator("Docling");
        final Long threshold = CHUNKING_THRESHOLD.get();
        return this.doclingGenerator.parse(new ByteArrayInputStream(content), fileName,
            threshold == null ? 0L : threshold);
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

    /**
     * Run the fallback generator when the primary fails, returns empty output, or produces
     * insufficient content.
     * Must not throw — return an empty string on any generator error.
     *
     * @param content raw document bytes
     * @param fileName source file name
     * @return markdown text, or an empty string on failure
     */
    protected abstract String runFallbackGenerator(byte[] content, String fileName);
}
