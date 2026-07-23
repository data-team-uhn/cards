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
package io.uhndata.cards.forms.internal.extraction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * The parsed contents of an {@code outline.json} written by the chunker under a document's {@code Chunks/}
 * folder. It holds everything the extraction pipeline's routing needs without re-parsing the document: the
 * chunker's recorded {@code chunked} decision (small documents are deliberately left unchunked and sent to the
 * LLM whole), the document's estimated token count, and the detected table-of-contents entry lines from the
 * {@code toc} array (written by the chunker when a TOC was found).
 *
 * @version $Id$
 */
public final class ParseOutline
{
    private static final String FILE_ID = "fileId";

    private static final String TOKENS = "tokens";

    private static final String CHUNKED = "chunked";

    private static final String TOC = "toc";

    private final String fileId;

    private final long tokens;

    private final boolean chunked;

    private final List<String> toc;

    private ParseOutline(final String documentFileId, final long documentTokens, final boolean documentChunked,
        final List<String> tocList)
    {
        this.fileId = documentFileId;
        this.tokens = documentTokens;
        this.chunked = documentChunked;
        this.toc = tocList;
    }

    /**
     * Read and parse an {@code outline.json} file.
     *
     * @param outlineFile the absolute path to an {@code outline.json}
     * @return the parsed outline
     * @throws IOException if the file cannot be read or is not a JSON object
     */
    public static ParseOutline read(final Path outlineFile) throws IOException
    {
        try (JsonReader reader = Json.createReader(Files.newBufferedReader(outlineFile, StandardCharsets.UTF_8))) {
            final JsonValue parsed = reader.readValue();
            if (parsed.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IOException("Outline is not a JSON object: " + outlineFile);
            }
            final JsonObject root = parsed.asJsonObject();
            return new ParseOutline(string(root, FILE_ID), longValue(root, TOKENS), booleanValue(root, CHUNKED),
                stringList(root, TOC));
        } catch (final RuntimeException e) {
            throw new IOException("Could not parse outline " + outlineFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * The source document's file name, e.g. {@code protocol.pdf}.
     *
     * @return the file id, or an empty string when absent
     */
    public String fileId()
    {
        return this.fileId;
    }

    /**
     * The document's estimated token count ({@code len(markdown) / 4}).
     *
     * @return the token estimate, or {@code 0} when absent
     */
    public long tokens()
    {
        return this.tokens;
    }

    /**
     * The chunker's recorded routing decision: whether the document was split into catalog chunks. Small
     * documents (under the active model's {@code wholeDocumentTokenLimit}) are deliberately left unchunked —
     * {@code Chunks/} then holds only this outline, and every extraction stage sends the whole document instead
     * of selecting chunks. Outlines predating this property (which were only ever written beside a catalog)
     * default to {@code true}.
     *
     * @return {@code true} when the document has a chunk catalog, {@code false} for whole-document mode
     */
    public boolean chunked()
    {
        return this.chunked;
    }

    /**
     * The detected table-of-contents entry lines (one cleaned TOC entry per element), written by the chunker
     * into {@code outline.json}'s {@code toc} array.
     *
     * @return the TOC lines in document order, empty when no TOC was detected, never {@code null}
     */
    public List<String> toc()
    {
        return this.toc;
    }

    private static List<String> stringList(final JsonObject root, final String key)
    {
        if (!root.containsKey(key) || root.get(key).getValueType() != JsonValue.ValueType.ARRAY) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>();
        for (final JsonValue value : root.getJsonArray(key)) {
            if (value.getValueType() == JsonValue.ValueType.STRING) {
                result.add(((JsonString) value).getString());
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static String string(final JsonObject root, final String key)
    {
        return root.containsKey(key) && root.get(key).getValueType() == JsonValue.ValueType.STRING
            ? root.getString(key) : "";
    }

    private static long longValue(final JsonObject root, final String key)
    {
        return root.containsKey(key) && root.get(key).getValueType() == JsonValue.ValueType.NUMBER
            ? root.getJsonNumber(key).longValue() : 0L;
    }

    private static boolean booleanValue(final JsonObject root, final String key)
    {
        // Absent property means a legacy outline, which was only ever written beside a catalog.
        return root.getBoolean(key, true);
    }
}
