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

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldResult;

/**
 * Shared parsing of the servlet-contract field objects ({@code {found_answer, confidence, value, reasoning,
 * evidence}}) that both the intake call and the Stage 1.2 targeted-extraction call return. It turns each raw
 * field object into a {@link FieldResult}, applying the two code-side checks the model is never trusted to do
 * itself: enum validation of the controlled-vocabulary fields ({@link StudyTaxonomy}) and verification of each
 * evidence quote against the section text it claims to come from ({@link QuoteVerifier}), penalizing a value's
 * confidence when no quote can be verified.
 *
 * @version $Id$
 */
public final class FieldResponseParser
{
    /** Confidence multiplier applied when no evidence quote could be verified against the section text. */
    static final double UNVERIFIED_PENALTY = 0.5;

    private FieldResponseParser()
    {
        // Utility class, never instantiated.
    }

    /**
     * Parse the requested fields out of a model response object.
     *
     * @param parsed the model's response object
     * @param fieldKeys the field keys to read
     * @param texts a map from section id to that section's full Markdown text, for quote verification
     * @return the per-field results, in the requested order; fields absent from the response are omitted
     */
    public static Map<String, FieldResult> parseFields(final JsonObject parsed, final List<String> fieldKeys,
        final Map<String, String> texts)
    {
        final Map<String, FieldResult> results = new LinkedHashMap<>();
        if (parsed == null) {
            return results;
        }
        for (final String key : fieldKeys) {
            final JsonObject fieldObject = readObject(parsed, key);
            if (fieldObject != null) {
                results.put(key, finalizeField(key, fieldObject, texts));
            }
        }
        return results;
    }

    /**
     * Turn one raw field object into a result, applying enum validation and quote verification.
     *
     * @param key the field key
     * @param fieldObject the raw field object
     * @param texts a map from section id to section text
     * @return the finalized field result
     */
    public static FieldResult finalizeField(final String key, final JsonObject fieldObject,
        final Map<String, String> texts)
    {
        boolean found = fieldObject.getBoolean("found_answer", false);
        double confidence = clamp(readDouble(fieldObject, "confidence"));
        String value = readString(fieldObject, "value");
        final String reasoning = readString(fieldObject, "reasoning");
        final String evidence = readArrayString(fieldObject, "evidence");

        value = validateEnum(key, value);
        if (found && value == null) {
            found = false;
        }
        if (found && !verifyEvidence(fieldObject, texts)) {
            confidence *= UNVERIFIED_PENALTY;
        }
        return new FieldResult(value, reasoning, evidence, fieldObject.toString(), confidence, found);
    }

    /**
     * Parse a JSON object out of a possibly-decorated model reply, tolerating leading/trailing prose.
     *
     * @param text the raw model reply
     * @return the parsed object, or {@code null} when none could be read
     */
    public static JsonObject parseJsonObject(final String text)
    {
        if (text == null || text.isBlank()) {
            return null;
        }
        final String trimmed = text.strip();
        final int start = trimmed.indexOf('{');
        final int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try (JsonReader reader = Json.createReader(new StringReader(trimmed.substring(start, end + 1)))) {
            final JsonValue value = reader.readValue();
            return value.getValueType() == JsonValue.ValueType.OBJECT ? value.asJsonObject() : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static String validateEnum(final String key, final String value)
    {
        if (value == null) {
            return null;
        }
        final String validated;
        switch (key) {
            case null:
                validated = value;
                break;
            case "study_category":
                validated = StudyTaxonomy.isCategory(value) ? value : null;
                break;
            case "study_category_flags":
                validated = StudyTaxonomy.filter(value, StudyTaxonomy.CATEGORY_FLAGS);
                break;
            case "lead_institution":
                validated = StudyTaxonomy.INSTITUTIONS.contains(value.strip()) ? value : null;
                break;
            case "participating_institutions":
                validated = StudyTaxonomy.filter(value, StudyTaxonomy.INSTITUTIONS);
                break;
            default:
                validated = value;
                break;
        }
        return validated;
    }

    private static boolean verifyEvidence(final JsonObject fieldObject, final Map<String, String> texts)
    {
        final JsonValue evidence = fieldObject.get("evidence");
        if (evidence == null || evidence.getValueType() != JsonValue.ValueType.ARRAY) {
            return false;
        }
        for (final JsonValue item : evidence.asJsonArray()) {
            if (item.getValueType() == JsonValue.ValueType.OBJECT && verifyItem(item.asJsonObject(), texts)) {
                return true;
            }
        }
        return false;
    }

    private static boolean verifyItem(final JsonObject item, final Map<String, String> texts)
    {
        final String quote = readString(item, "quote");
        final String sectionId = readString(item, "section_id");
        final String text = sectionId == null ? null : texts.get(sectionId);
        return QuoteVerifier.verify(quote, text);
    }

    private static JsonObject readObject(final JsonObject parent, final String key)
    {
        return parent.containsKey(key) && parent.get(key).getValueType() == JsonValue.ValueType.OBJECT
            ? parent.getJsonObject(key) : null;
    }

    private static String readString(final JsonObject object, final String key)
    {
        if (!object.containsKey(key) || object.isNull(key)) {
            return null;
        }
        final JsonValue value = object.get(key);
        return value.getValueType() == JsonValue.ValueType.STRING
            ? ((JsonString) value).getString() : value.toString();
    }

    private static double readDouble(final JsonObject object, final String key)
    {
        final JsonValue value = object.get(key);
        return value != null && value.getValueType() == JsonValue.ValueType.NUMBER
            ? ((JsonNumber) value).doubleValue() : 0.0;
    }

    private static String readArrayString(final JsonObject object, final String key)
    {
        if (!object.containsKey(key) || object.get(key).getValueType() != JsonValue.ValueType.ARRAY) {
            return null;
        }
        return object.get(key).toString();
    }

    private static double clamp(final double value)
    {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
