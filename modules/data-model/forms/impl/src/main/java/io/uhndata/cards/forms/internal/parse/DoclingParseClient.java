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

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for the Docling daemon's path-based {@code POST /parse} endpoint.
 * <p>
 * Java stages the upload under the shared docs volume and sends that absolute path. The daemon
 * runs LibreOffice prep, Docling, and {@code write_chunk_files}, then returns a small summary —
 * not the Markdown or chunk payloads.
 * </p>
 *
 * @version $Id$
 */
public final class DoclingParseClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingParseClient.class);

    private static final String DAEMON_URL_PROPERTY = "cards.docling.daemon.url";

    private static final String TIMEOUT_MINUTES_PROPERTY = "cards.docling.timeout.minutes";

    private static final String DEFAULT_DAEMON_URL = "http://127.0.0.1:18765";

    private static final long DEFAULT_TIMEOUT_MINUTES = 30;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private DoclingParseClient()
    {
        // Utility class, never instantiated.
    }

    /**
     * Ask the daemon to parse a document already on the shared volume.
     *
     * @param absolutePath absolute path under the shared docs root
     * @param chunk whether to also build the chunk tree
     * @param minStructureTokens documents under this many estimated tokens are left unchunked;
     *            pass {@code 0} or less to let the daemon apply its own default
     * @return the parse summary, or {@code null} when the daemon could not be reached or refused
     */
    public static ParseSummary parse(final String absolutePath, final boolean chunk,
        final long minStructureTokens)
    {
        final String daemonUrl = resolveDaemonUrl();
        final long timeoutMinutes = resolveTimeoutMinutes();
        String target = daemonUrl + "/parse?path="
            + URLEncoder.encode(StringUtils.defaultString(absolutePath), StandardCharsets.UTF_8)
            + "&chunk=" + chunk;
        if (minStructureTokens > 0L) {
            target = target + "&min_structure_tokens=" + minStructureTokens;
        }
        LOGGER.info("Sending Docling parse request for '{}' to daemon at {} (timeout {} min)",
            absolutePath, daemonUrl, timeoutMinutes);
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofMinutes(timeoutMinutes))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            final HttpResponse<String> response =
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            final String body = response.body();
            if (response.statusCode() != 200) {
                LOGGER.error("Docling daemon returned HTTP {} for '{}': {}", response.statusCode(), absolutePath,
                    StringUtils.abbreviate(body, 500));
                return null;
            }
            return toSummary(body, absolutePath);
        } catch (IOException e) {
            LOGGER.warn("Docling daemon parse request failed for '{}': {}", absolutePath, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while parsing '{}' via the Docling daemon", absolutePath);
            return null;
        }
    }

    private static ParseSummary toSummary(final String body, final String absolutePath)
    {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            final JsonObject json = reader.readObject();
            if (!json.getBoolean("ok", false)) {
                LOGGER.warn("Docling daemon returned ok=false for '{}': {}", absolutePath,
                    StringUtils.abbreviate(json.getString("error", body), 500));
                return null;
            }
            final String markdownPath = json.getString("markdown_path", "");
            if (StringUtils.isBlank(markdownPath)) {
                LOGGER.warn("Docling daemon returned no markdown_path for '{}'", absolutePath);
                return null;
            }
            return new ParseSummary(
                markdownPath,
                json.getBoolean("chunked", false),
                json.getString("chunks_dir", null),
                json.getString("logs", ""),
                json.getString("filename", ""));
        } catch (RuntimeException e) {
            LOGGER.error("Could not read the Docling daemon's parse response for '{}': {}", absolutePath,
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

    /** Small summary returned by the daemon after it has written the parse artifacts. */
    public static final class ParseSummary
    {
        private final String markdownPath;

        private final boolean chunked;

        private final String chunksDir;

        private final String logs;

        private final String filename;

        /**
         * @param markdownPath absolute path to the written {@code .md}
         * @param chunked whether the document was chunked
         * @param chunksDir absolute path to {@code Chunks/}, possibly {@code null}
         * @param logs daemon log lines for this request
         * @param filename original file name recorded by the daemon
         */
        public ParseSummary(final String markdownPath, final boolean chunked, final String chunksDir,
            final String logs, final String filename)
        {
            this.markdownPath = markdownPath;
            this.chunked = chunked;
            this.chunksDir = chunksDir;
            this.logs = logs;
            this.filename = filename;
        }

        /**
         * @return absolute path to the written markdown
         */
        public String getMarkdownPath()
        {
            return this.markdownPath;
        }

        /**
         * @return whether the document was chunked
         */
        public boolean isChunked()
        {
            return this.chunked;
        }

        /**
         * @return absolute path to {@code Chunks/}, or {@code null}
         */
        public String getChunksDir()
        {
            return this.chunksDir;
        }

        /**
         * @return daemon log lines
         */
        public String getLogs()
        {
            return this.logs;
        }

        /**
         * @return original file name
         */
        public String getFilename()
        {
            return this.filename;
        }
    }
}
