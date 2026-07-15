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
 * Reads, updates and rewrites a single {@code catalog.json} produced by the section splitter. A catalog is a
 * JSON object shaped like {@code {"fileId": "protocol.pdf", "sections": [{"section_id": "s001", "file":
 * "Section-1.md", "heading": "...", "summary": "", ...}, ...]}}. This class preserves every property of the
 * root object and of every section entry, mutating only the {@code summary} fields, and writes the file back
 * atomically (temporary file plus move) so an interrupted write never leaves a torn catalog.
 *
 * @version $Id$
 */
public final class SummaryCatalog
{
    /** The JSON property holding a section entry's identifier (e.g. {@code s001}). */
    private static final String SECTION_ID = "section_id";

    /** The JSON property holding a section entry's Markdown file name (e.g. {@code Section-1.md}). */
    private static final String FILE = "file";

    /** The JSON property holding a section entry's summary, filled in by the summarization service. */
    private static final String SUMMARY = "summary";

    /** The root JSON property holding the ordered array of section entries. */
    private static final String SECTIONS = "sections";

    /** Shared writer factory that pretty-prints catalogs so the on-disk output stays human-readable. */
    private static final JsonWriterFactory WRITER_FACTORY =
        Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));

    /** The catalog file on disk. */
    private final Path file;

    /** The catalog root object; its non-{@code sections} properties are preserved verbatim on write. */
    private final JsonObject root;

    /** The section entries in document order; each is replaced wholesale when its summary is set. */
    private final List<JsonObject> entries;

    private SummaryCatalog(final Path catalogFile, final JsonObject rootObject,
        final List<JsonObject> catalogEntries)
    {
        this.file = catalogFile;
        this.root = rootObject;
        this.entries = catalogEntries;
    }

    /**
     * Read and parse a catalog file.
     *
     * @param catalogFile the absolute path to a {@code catalog.json}
     * @return the parsed catalog
     * @throws IOException if the file cannot be read or is not a JSON object with a {@code sections} array
     */
    public static SummaryCatalog read(final Path catalogFile) throws IOException
    {
        try (JsonReader reader =
            Json.createReader(Files.newBufferedReader(catalogFile, StandardCharsets.UTF_8))) {
            final JsonValue parsedRoot = reader.readValue();
            if (parsedRoot.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IOException("Catalog is not a JSON object: " + catalogFile);
            }
            final JsonObject rootObject = parsedRoot.asJsonObject();
            final JsonValue sections = rootObject.get(SECTIONS);
            if (sections == null || sections.getValueType() != JsonValue.ValueType.ARRAY) {
                throw new IOException("Catalog has no sections array: " + catalogFile);
            }
            final List<JsonObject> parsed = new ArrayList<>();
            for (final JsonValue value : sections.asJsonArray()) {
                if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IOException("Catalog section entry is not a JSON object: " + catalogFile);
                }
                parsed.add(value.asJsonObject());
            }
            return new SummaryCatalog(catalogFile, rootObject, parsed);
        } catch (final RuntimeException e) {
            throw new IOException("Could not parse catalog " + catalogFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * The section identifiers in document order.
     *
     * @return an unmodifiable list of {@code section_id} values
     */
    public List<String> ids()
    {
        final List<String> ids = new ArrayList<>(this.entries.size());
        for (final JsonObject entry : this.entries) {
            ids.add(entry.getString(SECTION_ID, ""));
        }
        return Collections.unmodifiableList(ids);
    }

    /**
     * The Markdown file name of a section entry, relative to the catalog's folder.
     *
     * @param id the section identifier
     * @return the entry's {@code file} property, or an empty string when the entry or property is absent
     */
    public String fileOf(final String id)
    {
        final JsonObject entry = find(id);
        return entry == null ? "" : entry.getString(FILE, "");
    }

    /**
     * The current summary of a section entry.
     *
     * @param id the section identifier
     * @return the summary text, or an empty string when the entry is absent or has no summary
     */
    public String summaryOf(final String id)
    {
        final JsonObject entry = find(id);
        return entry == null ? "" : entry.getString(SUMMARY, "");
    }

    /**
     * Whether a section entry already has a non-blank summary and can be skipped.
     *
     * @param id the section identifier
     * @return {@code true} when the entry's summary is present and not blank
     */
    public boolean isSummarized(final String id)
    {
        return !this.summaryOf(id).isBlank();
    }

    /**
     * The summaries of all section entries, in document order, skipping entries whose summary is blank.
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
     * Whether every section entry has a non-blank summary.
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
     * Set a section entry's summary, replacing the entry in place while preserving its other properties.
     *
     * @param id the section identifier
     * @param summary the summary text to store
     */
    public void setSummary(final String id, final String summary)
    {
        for (int index = 0; index < this.entries.size(); index++) {
            final JsonObject entry = this.entries.get(index);
            if (id.equals(entry.getString(SECTION_ID, ""))) {
                this.entries.set(index, withSummary(entry, summary));
                return;
            }
        }
    }

    /**
     * Atomically rewrite the catalog file with the current entries, preserving every other root property.
     *
     * @throws IOException if the file cannot be written or moved into place
     */
    public void write() throws IOException
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        for (final Map.Entry<String, JsonValue> property : this.root.entrySet()) {
            if (SECTIONS.equals(property.getKey())) {
                builder.add(SECTIONS, this.toArray());
            } else {
                builder.add(property.getKey(), property.getValue());
            }
        }
        final JsonObject updated = builder.build();
        final Path temp = Files.createTempFile(this.file.getParent(), "catalog", ".tmp");
        try {
            try (JsonWriter writer =
                WRITER_FACTORY.createWriter(Files.newBufferedWriter(temp, StandardCharsets.UTF_8))) {
                writer.writeObject(updated);
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
            if (id.equals(entry.getString(SECTION_ID, ""))) {
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
