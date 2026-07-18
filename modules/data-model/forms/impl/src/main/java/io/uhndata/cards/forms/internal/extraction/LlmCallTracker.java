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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Append-only per-call coverage log written beside a proposal's {@code catalog.json} as
 * {@code llm_call_tracker.jsonl} — one JSON object per line recording which fields an LLM call asked for and
 * which section ids' full text was in its payload. Appending (never rewriting) keeps parallel Stage 1.2 batches
 * from racing: each writer only ever adds its own line. The coverage math used by the sweep planner is derived
 * from these records: {@code examined(field)} is the union of {@code sections} over every line whose
 * {@code fields} contain that field.
 *
 * @version $Id$
 */
public final class LlmCallTracker
{
    /** Gate step label (Stage 0.5). */
    public static final String STEP_GATE = "gate";

    /** Intake step label (Stage 1.1). */
    public static final String STEP_INTAKE = "intake";

    /** Targeted-extraction step label (Stage 1.2). */
    public static final String STEP_EXTRACT = "extract";

    /** Blind consecutive-batch sweep step label (Stage 1.2 fallback). */
    public static final String STEP_SWEEP = "sweep";

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmCallTracker.class);

    private final Path trackerFile;

    private final AtomicInteger callCounter;

    private LlmCallTracker(final Path file, final int nextCall)
    {
        this.trackerFile = file;
        this.callCounter = new AtomicInteger(nextCall);
    }

    /**
     * Open (or start) the tracker at a given path, resuming the call numbering after any lines already present so
     * a re-run continues the sequence rather than restarting it.
     *
     * @param trackerFile the absolute path to {@code llm_call_tracker.jsonl}
     * @return a tracker ready to append further calls
     */
    public static LlmCallTracker open(final Path trackerFile)
    {
        return new LlmCallTracker(trackerFile, countExistingLines(trackerFile) + 1);
    }

    /**
     * Append one call record. The record's {@code call} number is assigned automatically and returned. A write
     * failure is logged and swallowed — coverage tracking is best-effort and must never abort the pipeline.
     *
     * @param step one of {@link #STEP_GATE}, {@link #STEP_INTAKE}, {@link #STEP_EXTRACT}, {@link #STEP_SWEEP}
     * @param fields the field keys this call asked for
     * @param sections the section ids whose full text was in this call's payload
     * @return the assigned call number
     */
    public int append(final String step, final List<String> fields, final List<String> sections)
    {
        final int call = this.callCounter.getAndIncrement();
        final JsonArrayBuilder fieldArray = Json.createArrayBuilder();
        for (final String field : nullToEmpty(fields)) {
            fieldArray.add(field);
        }
        final JsonArrayBuilder sectionArray = Json.createArrayBuilder();
        for (final String section : nullToEmpty(sections)) {
            sectionArray.add(section);
        }
        final JsonObject record = Json.createObjectBuilder()
            .add("call", call)
            .add("step", step)
            .add("fields", fieldArray)
            .add("sections", sectionArray)
            .build();
        try {
            Files.writeString(this.trackerFile, record.toString() + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final IOException e) {
            LOGGER.warn("Could not append call {} ({}) to tracker {}: {}", call, step, this.trackerFile,
                e.getMessage());
        }
        return call;
    }

    /**
     * The union of section ids examined for a field across every recorded call that asked for it — the coverage
     * set the sweep planner uses to avoid re-sending a section already read for that field.
     *
     * @param field the field key
     * @return the section ids examined for the field, in first-seen order
     */
    public List<String> examined(final String field)
    {
        final List<String> result = new ArrayList<>();
        for (final JsonObject record : readRecords(this.trackerFile)) {
            if (containsString(record.get("fields"), field)) {
                addNewStrings(result, record.get("sections"));
            }
        }
        return result;
    }

    private static int countExistingLines(final Path trackerFile)
    {
        return readRecords(trackerFile).size();
    }

    private static List<JsonObject> readRecords(final Path trackerFile)
    {
        final List<JsonObject> records = new ArrayList<>();
        if (!Files.isRegularFile(trackerFile)) {
            return records;
        }
        try {
            for (final String line : Files.readAllLines(trackerFile, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    parseLine(line).ifPresent(records::add);
                }
            }
        } catch (final IOException e) {
            LOGGER.warn("Could not read tracker {}: {}", trackerFile, e.getMessage());
        }
        return records;
    }

    private static Optional<JsonObject> parseLine(final String line)
    {
        try (JsonReader reader = Json.createReader(new StringReader(line))) {
            final JsonValue value = reader.readValue();
            return value.getValueType() == JsonValue.ValueType.OBJECT
                ? Optional.of(value.asJsonObject()) : Optional.empty();
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean containsString(final JsonValue array, final String target)
    {
        if (array == null || array.getValueType() != JsonValue.ValueType.ARRAY) {
            return false;
        }
        for (final JsonValue value : array.asJsonArray()) {
            if (value.getValueType() == JsonValue.ValueType.STRING
                && target.equals(((JsonString) value).getString())) {
                return true;
            }
        }
        return false;
    }

    private static void addNewStrings(final List<String> target, final JsonValue array)
    {
        if (array == null || array.getValueType() != JsonValue.ValueType.ARRAY) {
            return;
        }
        for (final JsonValue value : array.asJsonArray()) {
            if (value.getValueType() == JsonValue.ValueType.STRING) {
                final String text = ((JsonString) value).getString();
                if (!target.contains(text)) {
                    target.add(text);
                }
            }
        }
    }

    private static List<String> nullToEmpty(final List<String> list)
    {
        return list == null ? List.of() : list;
    }
}
