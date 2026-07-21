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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

/**
 * Reads, updates and rewrites a proposal's {@code catalog.json} produced by the chunker, exposing the
 * fields the gate/intake passes need (id, file, heading, pages) and letting them stamp the tagging fields
 * ({@code rubric_tags}, {@code tag_basis}, {@code tag_confidence}, {@code uncertain}, {@code excluded},
 * {@code extraction_hints}) onto each chunk entry. Every other property of the root object and of each entry
 * is preserved verbatim, and the file is written back atomically (temporary file plus move) so an interrupted
 * write never leaves a torn catalog. This complements
 * {@link io.uhndata.cards.forms.internal.summary.SummaryCatalog}, which owns only the {@code summary} field.
 *
 * @version $Id$
 */
public final class ProposalCatalog
{
    /** Chunk-entry property: the chunk identifier (e.g. {@code s001}). */
    public static final String CHUNK_ID = "chunk_id";

    /** Chunk-entry property: the chunk's Markdown file name (e.g. {@code Chunk-1.md}). */
    public static final String FILE = "file";

    /** Chunk-entry property: the chunk's heading(s). */
    public static final String HEADING = "heading";

    /** Chunk-entry property: the 1-based <!-- page: N--> numbers the chunk spans (empty for DOCX). */
    public static final String PAGES = "pages";

    /** Chunk-entry property: the rubric tags (B.1–B.17). */
    public static final String RUBRIC_TAGS = "rubric_tags";

    /** Chunk-entry property: how the tag was derived — {@code heading}, {@code fulltext} or {@code deep}. */
    public static final String TAG_BASIS = "tag_basis";

    /** Chunk-entry property: the tagging pass's confidence in {@code rubric_tags}, in {@code [0, 1]}. */
    public static final String TAG_CONFIDENCE = "tag_confidence";

    /** Chunk-entry property: whether the tag is a weak or truncation-filled guess. */
    public static final String UNCERTAIN = "uncertain";

    /** Chunk-entry property: whether a deeper look at the chunk's full text invalidated its tag. */
    public static final String EXCLUDED = "excluded";

    /** Chunk-entry property: why the chunk's tag was excluded (blank when not excluded). */
    public static final String EXCLUSION_REASON = "exclusion_reason";

    /** Chunk-entry property: the derived field keys this chunk is a candidate source for. */
    public static final String EXTRACTION_HINTS = "extraction_hints";

    private static final String CHUNKS = "chunks";

    private static final JsonWriterFactory WRITER_FACTORY =
        Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));

    private final Path file;

    private final JsonObject root;

    private final List<JsonObject> entries;

    private ProposalCatalog(final Path catalogFile, final JsonObject rootObject, final List<JsonObject> catalogEntries)
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
     * @throws IOException if the file cannot be read or is not a JSON object with a {@code chunks} array
     */
    public static ProposalCatalog read(final Path catalogFile) throws IOException
    {
        try (JsonReader reader = Json.createReader(Files.newBufferedReader(catalogFile, StandardCharsets.UTF_8))) {
            final JsonValue parsed = reader.readValue();
            if (parsed.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IOException("Catalog is not a JSON object: " + catalogFile);
            }
            final JsonObject rootObject = parsed.asJsonObject();
            final JsonValue chunks = rootObject.get(CHUNKS);
            if (chunks == null || chunks.getValueType() != JsonValue.ValueType.ARRAY) {
                throw new IOException("Catalog has no chunks array: " + catalogFile);
            }
            final List<JsonObject> parsedEntries = new ArrayList<>();
            for (final JsonValue value : chunks.asJsonArray()) {
                if (value.getValueType() == JsonValue.ValueType.OBJECT) {
                    parsedEntries.add(value.asJsonObject());
                }
            }
            return new ProposalCatalog(catalogFile, rootObject, parsedEntries);
        } catch (final RuntimeException e) {
            throw new IOException("Could not parse catalog " + catalogFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * The chunk entries in document order.
     *
     * @return an unmodifiable list of chunk views
     */
    public List<Section> sections()
    {
        final List<Section> result = new ArrayList<>(this.entries.size());
        for (final JsonObject entry : this.entries) {
            result.add(toSection(entry));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Set a chunk's rubric tags.
     *
     * @param id the chunk identifier
     * @param tags the rubric tags to store
     */
    public void setRubricTags(final String id, final List<String> tags)
    {
        set(id, RUBRIC_TAGS, stringArray(tags));
    }

    /**
     * Set a chunk's tag basis.
     *
     * @param id the chunk identifier
     * @param basis {@code heading}, {@code fulltext} or {@code deep}
     */
    public void setTagBasis(final String id, final String basis)
    {
        set(id, TAG_BASIS, Json.createValue(basis));
    }

    /**
     * Set a chunk's tag confidence.
     *
     * @param id the chunk identifier
     * @param confidence the tagging pass's confidence in {@code rubric_tags}, in {@code [0, 1]}
     */
    public void setTagConfidence(final String id, final double confidence)
    {
        set(id, TAG_CONFIDENCE, Json.createValue(confidence));
    }

    /**
     * Set a chunk's uncertain flag.
     *
     * @param id the chunk identifier
     * @param uncertain whether the tag is a weak or truncation-filled guess
     */
    public void setUncertain(final String id, final boolean uncertain)
    {
        set(id, UNCERTAIN, uncertain ? JsonValue.TRUE : JsonValue.FALSE);
    }

    /**
     * Mark a chunk's current tag as excluded — invalidated by a deeper look at its full text (Stage 2.1) after
     * having been assigned from its heading alone.
     *
     * @param id the chunk identifier
     * @param excluded whether the previously-assigned tag no longer holds
     * @param reason why the tag was excluded, or blank when {@code excluded} is {@code false}
     */
    public void setExcluded(final String id, final boolean excluded, final String reason)
    {
        set(id, EXCLUDED, excluded ? JsonValue.TRUE : JsonValue.FALSE);
        set(id, EXCLUSION_REASON, Json.createValue(reason == null ? "" : reason));
    }

    /**
     * Set a chunk's derived extraction hints.
     *
     * @param id the chunk identifier
     * @param hints the field keys this chunk is a candidate source for
     */
    public void setExtractionHints(final String id, final List<String> hints)
    {
        set(id, EXTRACTION_HINTS, stringArray(hints));
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
            if (CHUNKS.equals(property.getKey())) {
                final JsonArrayBuilder array = Json.createArrayBuilder();
                this.entries.forEach(array::add);
                builder.add(CHUNKS, array);
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

    private void set(final String id, final String key, final JsonValue value)
    {
        for (int index = 0; index < this.entries.size(); index++) {
            final JsonObject entry = this.entries.get(index);
            if (id.equals(entry.getString(CHUNK_ID, ""))) {
                final JsonObjectBuilder builder = Json.createObjectBuilder();
                for (final Map.Entry<String, JsonValue> property : entry.entrySet()) {
                    if (!key.equals(property.getKey())) {
                        builder.add(property.getKey(), property.getValue());
                    }
                }
                builder.add(key, value);
                this.entries.set(index, builder.build());
                return;
            }
        }
    }

    private static JsonArray stringArray(final List<String> values)
    {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        if (values != null) {
            values.forEach(builder::add);
        }
        return builder.build();
    }

    private static Section toSection(final JsonObject entry)
    {
        final List<Integer> pages = new ArrayList<>();
        if (entry.containsKey(PAGES) && entry.get(PAGES).getValueType() == JsonValue.ValueType.ARRAY) {
            for (final JsonValue value : entry.getJsonArray(PAGES)) {
                if (value.getValueType() == JsonValue.ValueType.NUMBER) {
                    pages.add(((JsonNumber) value).intValue());
                }
            }
        }
        final TagMetadata tag = new TagMetadata(stringList(entry, RUBRIC_TAGS), entry.getString(TAG_BASIS, ""),
            doubleValue(entry, TAG_CONFIDENCE), entry.getBoolean(UNCERTAIN, false),
            entry.getBoolean(EXCLUDED, false), entry.getString(EXCLUSION_REASON, ""));
        return new Section(entry.getString(CHUNK_ID, ""), entry.getString(FILE, ""),
            stringList(entry, HEADING), Collections.unmodifiableList(pages), tag,
            stringList(entry, EXTRACTION_HINTS));
    }

    private static List<String> stringList(final JsonObject entry, final String key)
    {
        final List<String> result = new ArrayList<>();
        if (entry.containsKey(key) && entry.get(key).getValueType() == JsonValue.ValueType.ARRAY) {
            for (final JsonValue value : entry.getJsonArray(key)) {
                if (value.getValueType() == JsonValue.ValueType.STRING) {
                    result.add(((JsonString) value).getString());
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static double doubleValue(final JsonObject entry, final String key)
    {
        return entry.containsKey(key) && entry.get(key).getValueType() == JsonValue.ValueType.NUMBER
            ? entry.getJsonNumber(key).doubleValue() : 0.0;
    }

    private static void moveIntoPlace(final Path temp, final Path target) throws IOException
    {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException atomicFailure) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Tagging fields stamped onto a catalog chunk by the gate/intake passes.
     *
     * @param rubricTags the stamped rubric tags (empty before gate/intake tagging)
     * @param tagBasis how the tag was derived ({@code heading}, {@code fulltext}, {@code deep} or empty)
     * @param tagConfidence the tagging pass's confidence in {@code rubricTags}, in {@code [0, 1]}
     * @param uncertain whether the tag is a weak or truncation-filled guess
     * @param excluded whether a deeper look at the chunk's full text invalidated its tag
     * @param exclusionReason why the tag was excluded, or blank when {@code excluded} is {@code false}
     */
    public record TagMetadata(List<String> rubricTags, String tagBasis, double tagConfidence, boolean uncertain,
        boolean excluded, String exclusionReason)
    {
    }

    /**
     * A read-only view of one catalog chunk entry.
     *
     * @param id the chunk identifier (e.g. {@code s001})
     * @param file the chunk's Markdown file name, relative to the catalog folder
     * @param heading the chunk's heading(s)
     * @param pages the 1-based <!-- page: N--> numbers the chunk spans (empty for DOCX-origin documents)
     * @param tag the stamped tagging metadata
     * @param extractionHints the derived field keys this chunk is a candidate source for (empty before the join)
     */
    public record Section(String id, String file, List<String> heading, List<Integer> pages, TagMetadata tag,
        List<String> extractionHints)
    {
        /**
         * @return the stamped rubric tags
         */
        public List<String> rubricTags()
        {
            return this.tag.rubricTags();
        }

        /**
         * @return how the tag was derived
         */
        public String tagBasis()
        {
            return this.tag.tagBasis();
        }

        /**
         * @return the tagging pass's confidence
         */
        public double tagConfidence()
        {
            return this.tag.tagConfidence();
        }

        /**
         * @return whether the tag is a weak or truncation-filled guess
         */
        public boolean uncertain()
        {
            return this.tag.uncertain();
        }

        /**
         * @return whether the tag was invalidated
         */
        public boolean excluded()
        {
            return this.tag.excluded();
        }

        /**
         * @return why the tag was excluded, or blank when not excluded
         */
        public String exclusionReason()
        {
            return this.tag.exclusionReason();
        }
    }
}
