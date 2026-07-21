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
        try {
            final String primary = runPrimaryGeneratorSafely(content, fileName);
            if (isSufficient(primary)) {
                ParsedMarkdownStore.save(outputSubfolder, fileName, primary);
                logParseFinished(fileName, startTimestamp, primary, "primary");
                return primary;
            }
            this.logger.info("Primary generator '{}' failed or produced insufficient output for '{}', using fallback",
                currentGeneratorName("primary"), fileName);
            final String fallback = runFallbackGenerator(content, fileName);
            if (isSufficient(fallback)) {
                ParsedMarkdownStore.save(outputSubfolder, fileName, fallback);
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

    private String runPrimaryGeneratorSafely(final byte[] content, final String fileName)
    {
        try {
            return runPrimaryGenerator(content, fileName);
        } catch (RuntimeException | LinkageError e) {
            this.logger.warn("Primary generator failed for '{}': {}", fileName, e.getMessage());
            return "";
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
     * Run the primary generator against the document bytes.
     * The default implementation delegates to {@link DoclingMarkdownGenerator}.
     * Subclasses may override to supply a format-specific primary generator.
     * Must not throw — return an empty string on any generator error.
     *
     * @param content raw document bytes
     * @param fileName source file name
     * @return markdown text, or an empty string on failure
     */
    protected String runPrimaryGenerator(final byte[] content, final String fileName)
    {
        setActiveGenerator("Docling");
        return this.doclingGenerator.toMarkdown(new ByteArrayInputStream(content), fileName);
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
