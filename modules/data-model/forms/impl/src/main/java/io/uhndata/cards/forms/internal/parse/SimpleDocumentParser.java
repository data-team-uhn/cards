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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for document parsers that apply a primary generator with a two-minute timeout
 * and fall back to a secondary generator when the primary produces insufficient output.
 * <p>
 * Subclasses implement {@link #runPrimaryGenerator} to plug in the format-specific primary generator.
 * Subclasses may also override {@link #runFallbackGenerator} to supply a custom fallback; the default
 * fallback delegates to {@link DoclingFallbackMarkdownGenerator}.
 * All shared orchestration logic — stream reading, timeout enforcement, and sufficiency check — lives here.
 * </p>
 *
 * @version $Id$
 */
public abstract class SimpleDocumentParser implements DocumentParser
{
    private static final long PRIMARY_TIMEOUT_MINUTES = 2L;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final DoclingFallbackMarkdownGenerator fallbackGenerator = new DoclingFallbackMarkdownGenerator();

    @Override
    public final String parse(final InputStream stream, final String fileName)
    {
        final byte[] content;
        try {
            content = stream.readAllBytes();
        } catch (IOException e) {
            this.logger.warn("Failed to read document stream for '{}': {}", fileName, e.getMessage());
            return "";
        }
        final String primary = tryPrimaryWithTimeout(content, fileName);
        if (DoclingFallbackMarkdownGenerator.isSufficient(primary)) {
            return primary;
        }
        this.logger.info("Primary generator produced insufficient output for '{}', using fallback", fileName);
        return runFallbackGenerator(content, fileName);
    }

    /**
     * Run the format-specific primary generator against the document bytes.
     * Must not throw — return an empty string on any generator error.
     *
     * @param content raw document bytes
     * @param fileName source file name
     * @return markdown text, or an empty string on failure
     */
    protected abstract String runPrimaryGenerator(byte[] content, String fileName);

    /**
     * Run the fallback generator when the primary produces insufficient output.
     * The default implementation delegates to {@link DoclingFallbackMarkdownGenerator}.
     * Subclasses may override to supply a format-specific fallback.
     * Must not throw — return an empty string on any generator error.
     *
     * @param content raw document bytes
     * @param fileName source file name
     * @return markdown text, or an empty string on failure
     */
    protected String runFallbackGenerator(final byte[] content, final String fileName)
    {
        return this.fallbackGenerator.toMarkdown(new ByteArrayInputStream(content), fileName);
    }

    private String tryPrimaryWithTimeout(final byte[] content, final String fileName)
    {
        final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        final CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            return this.runPrimaryGenerator(content, fileName);
        });
        try {
            return future.get(PRIMARY_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            this.logger.warn("Primary generator interrupted for '{}'", fileName);
            return "";
        } catch (TimeoutException | ExecutionException e) {
            future.cancel(true);
            this.logger.warn("Primary generator timed out or failed for '{}': {}", fileName, e.getMessage());
            return "";
        }
    }
}
