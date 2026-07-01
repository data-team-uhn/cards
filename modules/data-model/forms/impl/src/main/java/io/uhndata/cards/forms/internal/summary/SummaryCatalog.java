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
package io.uhndata.cards.forms.internal.summary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

/**
 * Reads, updates and rewrites a single {@code catalog.json} produced by the Docling chat chunker. A catalog is
 * an ordered JSON array of entries shaped like
 * {@code {"id": "Chunk1", "summary": "", "pages": [88], "sectionId": "Section1", "fileId": "35p"}}. This class
 * preserves every entry and every property, mutating only the {@code summary} field, and writes the file back
 * atomically (temporary file plus move) so an interrupted write never leaves a torn catalog.
 *
 * @version $Id$
 */
public final class SummaryCatalog
{
    /** The JSON property holding an entry's identifier (e.g. {@code Chunk1}, {@code Section1}, {@code File-35p}). */
    private static final String ID = "id";

    /** The JSON property holding an entry's summary, filled in by the summarization service. */
    private static final String SUMMARY = "summary";

    /** Shared writer factory that pretty-prints catalogs so the on-disk output stays human-readable. */
    private static final JsonWriterFactory WRITER_FACTORY =
        Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));

    /** The catalog file on disk. */
    private final Path file;

    /** The catalog entries in document order; each is replaced wholesale when its summary is set. */
    private final List<JsonObject> entries;

    private SummaryCatalog(final Path catalogFile, final List<JsonObject> catalogEntries)
    {
        this.file = catalogFile;
        this.entries = catalogEntries;
    }

    /**
     * Read and parse a catalog file.
     *
     * @param catalogFile the absolute path to a {@code catalog.json}
     * @return the parsed catalog
     * @throws IOException if the file cannot be read or does not contain a JSON array of objects
     */
    public static SummaryCatalog read(final Path catalogFile) throws IOException
    {
        try (JsonReader reader =
            Json.createReader(Files.newBufferedReader(catalogFile, StandardCharsets.UTF_8))) {
            final JsonValue root = reader.readValue();
            if (root.getValueType() != JsonValue.ValueType.ARRAY) {
                throw new IOException("Catalog is not a JSON array: " + catalogFile);
            }
            final List<JsonObject> parsed = new ArrayList<>();
            for (final JsonValue value : root.asJsonArray()) {
                if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IOException("Catalog entry is not a JSON object: " + catalogFile);
                }
                parsed.add(value.asJsonObject());
            }
            return new SummaryCatalog(catalogFile, parsed);
        } catch (final RuntimeException e) {
            throw new IOException("Could not parse catalog " + catalogFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * The entry identifiers in document order.
     *
     * @return an unmodifiable list of {@code id} values
     */
    public List<String> ids()
    {
        final List<String> ids = new ArrayList<>(this.entries.size());
        for (final JsonObject entry : this.entries) {
            ids.add(entry.getString(ID, ""));
        }
        return Collections.unmodifiableList(ids);
    }

    /**
     * The current summary of an entry.
     *
     * @param id the entry identifier
     * @return the summary text, or an empty string when the entry is absent or has no summary
     */
    public String summaryOf(final String id)
    {
        final JsonObject entry = find(id);
        return entry == null ? "" : entry.getString(SUMMARY, "");
    }

    /**
     * Whether an entry already has a non-blank summary and can be skipped.
     *
     * @param id the entry identifier
     * @return {@code true} when the entry's summary is present and not blank
     */
    public boolean isSummarized(final String id)
    {
        return !this.summaryOf(id).isBlank();
    }

    /**
     * The summaries of all entries, in document order, skipping entries whose summary is blank.
     *
     * @return the non-blank summaries in order
     */
    public List<String> summaries()
    {
        final List<String> result = new ArrayList<>(this.entries.size());
        for (final JsonObject entry : this.entries) {
            final String summary = entry.getString(SUMMARY, "");
            if (!summary.isBlank()) {
                result.add(summary);
            }
        }
        return result;
    }

    /**
     * Whether every entry has a non-blank summary.
     *
     * @return {@code true} when the catalog has at least one entry and all entries are summarized
     */
    public boolean allSummarized()
    {
        if (this.entries.isEmpty()) {
            return false;
        }
        for (final JsonObject entry : this.entries) {
            if (entry.getString(SUMMARY, "").isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The summary of every entry in document order. Call only when {@link #allSummarized()} is {@code true}.
     *
     * @return the summaries in order
     */
    public List<String> orderedSummaries()
    {
        final List<String> result = new ArrayList<>(this.entries.size());
        for (final JsonObject entry : this.entries) {
            result.add(entry.getString(SUMMARY, ""));
        }
        return result;
    }

    /**
     * Set an entry's summary, replacing the entry in place while preserving its other properties.
     *
     * @param id the entry identifier
     * @param summary the summary text to store
     */
    public void setSummary(final String id, final String summary)
    {
        for (int index = 0; index < this.entries.size(); index++) {
            final JsonObject entry = this.entries.get(index);
            if (id.equals(entry.getString(ID, ""))) {
                this.entries.set(index, withSummary(entry, summary));
                return;
            }
        }
    }

    /**
     * Atomically rewrite the catalog file with the current entries.
     *
     * @throws IOException if the file cannot be written or moved into place
     */
    public void write() throws IOException
    {
        final JsonArray array = toArray();
        final Path temp = Files.createTempFile(this.file.getParent(), "catalog", ".tmp");
        try {
            try (JsonWriter writer =
                WRITER_FACTORY.createWriter(Files.newBufferedWriter(temp, StandardCharsets.UTF_8))) {
                writer.writeArray(array);
            }
            moveIntoPlace(temp, this.file);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void moveIntoPlace(final Path temp, final Path target) throws IOException
    {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException atomicFailure) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private JsonArray toArray()
    {
        final var builder = Json.createArrayBuilder();
        for (final JsonObject entry : this.entries) {
            builder.add(entry);
        }
        return builder.build();
    }

    private JsonObject find(final String id)
    {
        for (final JsonObject entry : this.entries) {
            if (id.equals(entry.getString(ID, ""))) {
                return entry;
            }
        }
        return null;
    }

    private static JsonObject withSummary(final JsonObject entry, final String summary)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        for (final Map.Entry<String, JsonValue> property : entry.entrySet()) {
            if (SUMMARY.equals(property.getKey())) {
                builder.add(SUMMARY, summary == null ? "" : summary);
            } else {
                builder.add(property.getKey(), property.getValue());
            }
        }
        if (!entry.containsKey(SUMMARY)) {
            builder.add(SUMMARY, summary == null ? "" : summary);
        }
        return builder.build();
    }
}
