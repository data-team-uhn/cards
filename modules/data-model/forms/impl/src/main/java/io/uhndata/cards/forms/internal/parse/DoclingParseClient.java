/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.forms.internal.parse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for the Docling daemon's {@code /parse} endpoint, which converts a document and builds its chunk tree in
 * one call and returns everything in the response.
 *
 * <p>Unlike {@code /convert} + {@code /chunk}, this passes the document as <em>bytes</em> rather than a path, so
 * the daemon needs no access to this JVM's filesystem. That is what allows it to run in its own container: a
 * path-based request cannot work across a container boundary, because the caller's absolute paths do not exist
 * inside the container (and on a Windows host they cannot be made to). It also removes the write-then-read-back
 * of the Markdown between converting and chunking, and with it the risk of the two sides disagreeing about the
 * document's reserved markers.</p>
 *
 * <p>Writing the result stays this side: Markdown is produced by either Docling or the pure-Java fallback
 * generators, so a single writer in Java is the only way to keep one definition of the output layout.</p>
 *
 * @version $Id$
 */
public final class DoclingParseClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingParseClient.class);

    private static final String DAEMON_URL_PROPERTY = "cards.docling.daemon.url";

    private static final String AUTH_TOKEN_PROPERTY = "cards.docling.auth.token";

    private static final String TIMEOUT_MINUTES_PROPERTY = "cards.docling.timeout.minutes";

    private static final String DEFAULT_DAEMON_URL = "http://127.0.0.1:18765";

    private static final long DEFAULT_TIMEOUT_MINUTES = 30;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private DoclingParseClient()
    {
        // Utility class, never instantiated.
    }

    /**
     * Convert and chunk a document in a single daemon call.
     *
     * @param content the document bytes
     * @param fileName the original document name; its extension selects the Docling backend, and it is recorded
     *            as the {@code source_file} header and the catalog's {@code fileId}
     * @param chunk whether to also build the chunk tree
     * @return the parsed document, or {@code null} when the daemon could not be reached or refused the request,
     *         so the caller can fall back exactly as it does for {@code /convert}
     */
    public static ParsedDocument parse(final byte[] content, final String fileName, final boolean chunk)
    {
        return parse(content, fileName, chunk, 0L);
    }

    /**
     * Convert and chunk a document in a single daemon call, with an explicit chunking threshold.
     *
     * @param content the document bytes
     * @param fileName the original document name
     * @param chunk whether to also build the chunk tree
     * @param minStructureTokens documents under this many estimated tokens are left unchunked; pass {@code 0} or
     *            less to let the daemon apply its own default
     * @return the parsed document, or {@code null} when the daemon could not be reached or refused the request
     */
    public static ParsedDocument parse(final byte[] content, final String fileName, final boolean chunk,
        final long minStructureTokens)
    {
        final String daemonUrl = resolveDaemonUrl();
        final long timeoutMinutes = resolveTimeoutMinutes();
        String target = daemonUrl + "/parse?filename="
            + URLEncoder.encode(StringUtils.defaultString(fileName), StandardCharsets.UTF_8)
            + "&chunk=" + chunk;
        if (minStructureTokens > 0L) {
            target = target + "&min_structure_tokens=" + minStructureTokens;
        }
        LOGGER.info("Sending Docling parse request for '{}' to daemon at {} ({} bytes, timeout {} min)",
            fileName, daemonUrl, content.length, timeoutMinutes);
        try {
            final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofMinutes(timeoutMinutes))
                .header("Content-Type", "application/octet-stream")
                // The response carries the Markdown and every chunk's text, so it is megabytes of
                // highly compressible text.
                .header("Accept-Encoding", "gzip")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content));
            final String token = System.getProperty(AUTH_TOKEN_PROPERTY);
            if (StringUtils.isNotBlank(token)) {
                builder.header("Authorization", "Bearer " + token);
            }
            final HttpResponse<byte[]> response =
                HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            final String body = decodeBody(response);
            if (response.statusCode() != 200) {
                LOGGER.error("Docling daemon returned HTTP {} for '{}': {}", response.statusCode(), fileName,
                    StringUtils.abbreviate(body, 500));
                return null;
            }
            return toParsedDocument(body, fileName);
        } catch (IOException e) {
            LOGGER.warn("Docling daemon parse request failed for '{}': {}", fileName, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while parsing '{}' via the Docling daemon", fileName);
            return null;
        }
    }

    private static String decodeBody(final HttpResponse<byte[]> response) throws IOException
    {
        final byte[] raw = response.body();
        final boolean gzipped = response.headers().firstValue("Content-Encoding")
            .map(value -> value.toLowerCase(java.util.Locale.ROOT).contains("gzip"))
            .orElse(false);
        if (!gzipped) {
            return new String(raw, StandardCharsets.UTF_8);
        }
        try (GZIPInputStream unzipped = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            return new String(unzipped.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ParsedDocument toParsedDocument(final String body, final String fileName)
    {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            final JsonObject json = reader.readObject();
            final String markdown = json.getString("markdown", "");
            if (StringUtils.isBlank(markdown)) {
                LOGGER.warn("Docling daemon returned no markdown for '{}'", fileName);
                return null;
            }
            final boolean chunked = json.getBoolean("chunked", false);
            final List<ChunkFile> chunks = new ArrayList<>();
            if (chunked && json.containsKey("chunks")) {
                json.getJsonArray("chunks").stream()
                    .map(JsonObject.class::cast)
                    .forEach(entry -> chunks.add(
                        new ChunkFile(entry.getString("file", ""), entry.getString("text", ""))));
            }
            return new ParsedDocument(markdown, chunked, json.getJsonObject("outline"),
                json.getJsonObject("catalog"), chunks, json.getString("logs", ""));
        } catch (RuntimeException e) {
            LOGGER.error("Could not read the Docling daemon's parse response for '{}': {}", fileName,
                e.getMessage());
            return null;
        }
    }

    private static String resolveDaemonUrl()
    {
        final String configured = System.getProperty(DAEMON_URL_PROPERTY);
        return StringUtils.isNotBlank(configured) ? configured : DEFAULT_DAEMON_URL;
    }

    private static long resolveTimeoutMinutes()
    {
        final String configured = System.getProperty(TIMEOUT_MINUTES_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            return DEFAULT_TIMEOUT_MINUTES;
        }
        try {
            final long minutes = Long.parseLong(configured);
            return minutes > 0L ? minutes : DEFAULT_TIMEOUT_MINUTES;
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MINUTES;
        }
    }

    /** One chunk file's name and content, as returned by the daemon. */
    public static final class ChunkFile
    {
        private final String file;

        private final String text;

        /**
         * @param file the chunk file's name, e.g. {@code Chunk-1.2.md}
         * @param text the chunk's Markdown, without a trailing newline
         */
        public ChunkFile(final String file, final String text)
        {
            this.file = file;
            this.text = text;
        }

        /**
         * @return the chunk file's name
         */
        public String getFile()
        {
            return this.file;
        }

        /**
         * @return the chunk's Markdown
         */
        public String getText()
        {
            return this.text;
        }
    }

    /** Everything the daemon produced for one document. */
    public static final class ParsedDocument
    {
        private final String markdown;

        private final boolean chunked;

        private final JsonObject outline;

        private final JsonObject catalog;

        private final List<ChunkFile> chunks;

        private final String logs;

        /**
         * @param markdown the cleaned Markdown
         * @param chunked whether the document was large enough to chunk
         * @param outline the outline to write as {@code Chunks/outline.json}, possibly {@code null}
         * @param catalog the catalog to write as {@code Chunks/catalog.json}, {@code null} when unchunked
         * @param chunks the chunk files, empty when unchunked
         * @param logs the daemon's per-request log lines
         */
        public ParsedDocument(final String markdown, final boolean chunked, final JsonObject outline,
            final JsonObject catalog, final List<ChunkFile> chunks, final String logs)
        {
            this.markdown = markdown;
            this.chunked = chunked;
            this.outline = outline;
            this.catalog = catalog;
            this.chunks = chunks == null ? Collections.emptyList() : List.copyOf(chunks);
            this.logs = logs;
        }

        /**
         * @return the cleaned Markdown
         */
        public String getMarkdown()
        {
            return this.markdown;
        }

        /**
         * @return whether the document was chunked
         */
        public boolean isChunked()
        {
            return this.chunked;
        }

        /**
         * @return the outline, or {@code null} when the daemon returned none
         */
        public JsonObject getOutline()
        {
            return this.outline;
        }

        /**
         * @return the catalog, or {@code null} when the document was left unchunked
         */
        public JsonObject getCatalog()
        {
            return this.catalog;
        }

        /**
         * @return the chunk files, in document order
         */
        public List<ChunkFile> getChunks()
        {
            return this.chunks;
        }

        /**
         * @return the daemon's log lines for this request
         */
        public String getLogs()
        {
            return this.logs;
        }
    }
}
