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
 * Parser for PDF files.
 * <p>
 * Attempts extraction with the primary Java-based {@link PdfMarkdownGenerator} first, enforcing a
 * two-minute timeout. Falls back to {@link DoclingFallbackMarkdownGenerator} when the primary
 * generator fails, times out, or produces insufficient output.
 * </p>
 *
 * @version $Id$
 */
public class PdfParser implements DocumentParser
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PdfParser.class);

    private static final long PRIMARY_TIMEOUT_MINUTES = 2L;

    private final PdfMarkdownGenerator markdownGenerator = new PdfMarkdownGenerator();

    private final DoclingFallbackMarkdownGenerator fallbackGenerator = new DoclingFallbackMarkdownGenerator();

    @Override
    public String parse(final InputStream stream, final String fileName)
    {
        final byte[] content;
        try {
            content = stream.readAllBytes();
        } catch (IOException e) {
            LOGGER.warn("Failed to read PDF stream for '{}': {}", fileName, e.getMessage());
            return "";
        }
        final String primary = tryPrimaryWithTimeout(content, fileName);
        if (DoclingFallbackMarkdownGenerator.isSufficient(primary)) {
            return primary;
        }
        LOGGER.info("Primary PDF generator produced insufficient output for '{}', using Docling fallback", fileName);
        return this.fallbackGenerator.toMarkdown(new ByteArrayInputStream(content), fileName);
    }

    private String tryPrimaryWithTimeout(final byte[] content, final String fileName)
    {
        final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        final CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            return runPrimaryGenerator(content, fileName);
        });
        try {
            return future.get(PRIMARY_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            LOGGER.warn("Primary PDF generator interrupted for '{}'", fileName);
            return "";
        } catch (TimeoutException | ExecutionException e) {
            future.cancel(true);
            LOGGER.warn("Primary PDF generator timed out or failed for '{}': {}", fileName, e.getMessage());
            return "";
        }
    }

    private String runPrimaryGenerator(final byte[] content, final String fileName)
    {
        try {
            return this.markdownGenerator.toMarkdown(new ByteArrayInputStream(content), fileName);
        } catch (IOException | LinkageError e) {
            LOGGER.debug("Primary PDF generator error for '{}': {}", fileName, e.getMessage());
            return "";
        }
    }
}
