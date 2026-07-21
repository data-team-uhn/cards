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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMClientFactory;

/**
 * Builds a structured-extraction prompt from a set of fields and a parsed study document, sends it to the
 * active LLM one chunk at a time, and merges the best result for each field across chunks. The prompt format
 * and the progressive, confidence-driven chunk loop mirror the standalone Prompter extraction script: each
 * field returns a small JSON object holding {@code found_answer}, {@code confidence}, {@code value},
 * {@code reasoning} and {@code evidence}, the loop stops early once every field is found with high confidence,
 * and the value with the highest confidence wins when a field appears in more than one chunk.
 *
 * @version $Id$
 */
@Component(service = ProposalExtractionService.class)
public class ProposalExtractionService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProposalExtractionService.class);

    /** Minimum confidence at which a field is considered settled and no further chunks are needed for it. */
    private static final double CONFIDENCE_THRESHOLD = 0.75;

    /** The system prompt establishing the model's role and the JSON-only output contract. */
    private static final String SYSTEM_PROMPT =
        "You are an ethics review classifier and study metadata extractor. Use ONLY information present in "
        + "the provided document and categories. Do not invent missing information. Return ONLY valid JSON, "
        + "with no markdown, comments or text outside the JSON.";

    @Reference
    private LLMClientFactory llmClientFactory;

    /**
     * Extract every requested field from the document chunks.
     *
     * @param fields the fields to extract, each carrying the JSON key, a task label and the field rules
     * @param categoriesDocument an optional classification reference document included in the prompt; may be
     *            {@code null} or blank when not needed
     * @param chunks the document chunks to send, in order
     * @return a map from field key to the best result found for it; fields never seen are absent
     * @throws IOException if the active LLM client cannot be resolved or a request fails
     */
    public Map<String, FieldResult> extract(final List<FieldSpec> fields, final String categoriesDocument,
        final List<String> chunks) throws IOException
    {
        final Map<String, FieldResult> best = new HashMap<>();
        if (fields == null || fields.isEmpty() || chunks == null || chunks.isEmpty()) {
            return best;
        }
        final LLMClient client = this.llmClientFactory.getActiveClient();
        final String taskPrompt = buildTaskPrompt(fields, categoriesDocument);
        final int total = chunks.size();
        for (int index = 0; index < total; index++) {
            final String userMessage = buildUserMessage(taskPrompt, chunks.get(index), index + 1, total);
            final JsonObject parsed = requestChunk(client, userMessage, index + 1);
            if (parsed == null) {
                continue;
            }
            mergeBest(best, fields, parsed);
            if (allConfident(best, fields)) {
                LOGGER.info("All fields high-confidence after chunk {}/{}, stopping early", index + 1, total);
                break;
            }
        }
        if (best.isEmpty()) {
            LOGGER.warn("LLM extraction produced no recognized JSON fields from {} chunk(s); the active model "
                + "may not be returning the requested JSON structure keyed by the field names", total);
        }
        return best;
    }

    private JsonObject requestChunk(final LLMClient client, final String userMessage, final int chunkNumber)
        throws IOException
    {
        final String reply = client.chat(SYSTEM_PROMPT, userMessage);
        final JsonObject parsed = parseJsonObject(reply);
        if (parsed == null) {
            LOGGER.warn("Could not parse a JSON object from the LLM reply for chunk {}; raw reply: {}",
                chunkNumber, snippet(reply));
        }
        return parsed;
    }

    private static String snippet(final String text)
    {
        if (text == null) {
            return "<null>";
        }
        final String trimmed = text.strip();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000) + "...";
    }

    private static void mergeBest(final Map<String, FieldResult> best, final List<FieldSpec> fields,
        final JsonObject parsed)
    {
        for (final FieldSpec field : fields) {
            final JsonObject fieldObject = readObject(parsed, field.key());
            if (fieldObject == null) {
                continue;
            }
            final FieldResult candidate = toResult(fieldObject);
            final FieldResult existing = best.get(field.key());
            if (existing == null || candidate.confidence() > existing.confidence()) {
                best.put(field.key(), candidate);
            }
        }
    }

    private static boolean allConfident(final Map<String, FieldResult> best, final List<FieldSpec> fields)
    {
        for (final FieldSpec field : fields) {
            final FieldResult result = best.get(field.key());
            if (result == null || !result.found() || result.confidence() < CONFIDENCE_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    private static FieldResult toResult(final JsonObject fieldObject)
    {
        final boolean found = fieldObject.getBoolean("found_answer", false);
        final double confidence = readConfidence(fieldObject);
        final String value = readString(fieldObject, "value");
        final String reasoning = readString(fieldObject, "reasoning");
        final String evidence = readJsonArray(fieldObject, "evidence");
        return new FieldResult(value, reasoning, evidence, fieldObject.toString(), confidence, found);
    }

    private static String readJsonArray(final JsonObject object, final String key)
    {
        if (!object.containsKey(key) || object.isNull(key)) {
            return null;
        }
        final JsonValue value = object.get(key);
        return value.getValueType() == JsonValue.ValueType.ARRAY ? value.toString() : null;
    }

    private static double readConfidence(final JsonObject fieldObject)
    {
        final JsonValue value = fieldObject.get("confidence");
        if (value == null || value.getValueType() != JsonValue.ValueType.NUMBER) {
            return 0.0;
        }
        return ((JsonNumber) value).doubleValue();
    }

    private static String readString(final JsonObject object, final String key)
    {
        if (!object.containsKey(key) || object.isNull(key)) {
            return null;
        }
        final JsonValue value = object.get(key);
        if (value.getValueType() == JsonValue.ValueType.STRING) {
            return object.getString(key);
        }
        return value.toString();
    }

    private static JsonObject readObject(final JsonObject parsed, final String key)
    {
        if (!parsed.containsKey(key) || parsed.get(key).getValueType() != JsonValue.ValueType.OBJECT) {
            return null;
        }
        return parsed.getJsonObject(key);
    }

    private static JsonObject parseJsonObject(final String text)
    {
        if (text == null || text.isBlank()) {
            return null;
        }
        final String trimmed = text.trim();
        final JsonObject direct = tryParse(trimmed);
        if (direct != null) {
            return direct;
        }
        final int start = trimmed.indexOf('{');
        final int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return tryParse(trimmed.substring(start, end + 1));
        }
        return null;
    }

    private static JsonObject tryParse(final String text)
    {
        try (JsonReader reader = Json.createReader(new StringReader(text))) {
            return reader.readObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String buildTaskPrompt(final List<FieldSpec> fields, final String categoriesDocument)
    {
        final boolean hasCategories = categoriesDocument != null && !categoriesDocument.isBlank();
        final StringBuilder builder = new StringBuilder();
        if (hasCategories) {
            builder.append("# Project Categories\n\n").append(categoriesDocument.trim()).append("\n\n");
        }
        builder.append("# Task\n\nPerform the following tasks using the study document chunk.\n\n");
        appendTaskList(builder, fields);
        builder.append("\nUse ONLY information present in the study chunk");
        if (hasCategories) {
            builder.append(" and the Project Categories");
        }
        builder.append(".\nDo not invent missing information.\n");
        builder.append("Return ONLY valid JSON. Do not include markdown, comments or text outside JSON.\n\n");
        appendFieldRules(builder, fields);
        appendJsonStructure(builder, fields);
        appendEvidenceRules(builder);
        return builder.toString();
    }

    private static void appendTaskList(final StringBuilder builder, final List<FieldSpec> fields)
    {
        int number = 1;
        for (final FieldSpec field : fields) {
            builder.append(number).append(". ").append(field.taskLabel()).append("\n");
            number++;
        }
    }

    private static void appendFieldRules(final StringBuilder builder, final List<FieldSpec> fields)
    {
        for (final FieldSpec field : fields) {
            if (field.rules() == null || field.rules().isBlank()) {
                continue;
            }
            builder.append("## ").append(field.key()).append("\n")
                .append(field.rules().trim()).append("\n\n");
        }
    }

    private static void appendJsonStructure(final StringBuilder builder, final List<FieldSpec> fields)
    {
        builder.append("Return this exact JSON structure:\n\n{\n");
        final List<String> entries = new ArrayList<>(fields.size());
        for (final FieldSpec field : fields) {
            entries.add("  \"" + field.key() + "\": { \"found_answer\": true/false, "
                + "\"confidence\": 0.0-1.0, \"value\": \"...\", \"reasoning\": \"...\", "
                + "\"evidence\": [ { \"quote\": \"...\", \"page\": 1 } ] }");
        }
        builder.append(String.join(",\n", entries)).append("\n}\n\n");
    }

    private static void appendEvidenceRules(final StringBuilder builder)
    {
        builder.append("Support every extracted value with a short, direct quote from the study chunk.\n");
        builder.append("If a quote appears after a page marker such as '<!-- page: 75-->', include that page.\n");
        builder.append("If a field cannot be determined from this chunk, set found_answer=false, ");
        builder.append("confidence=0.0, value=null and evidence=[].\n");
    }

    private static String buildUserMessage(final String taskPrompt, final String chunk, final int chunkNumber,
        final int totalChunks)
    {
        return taskPrompt
            + "\n# Study Chunk\n\nChunk number: " + chunkNumber + "\nTotal chunks: " + totalChunks + "\n\n"
            + chunk + "\n";
    }

    /**
     * Specification of one field to extract.
     *
     * @param key the JSON key the model must use for this field, e.g. {@code study_title}
     * @param taskLabel a short task description shown in the prompt's numbered task list
     * @param rules the field-specific extraction rules, taken from the question's {@code prompt} property
     */
    public record FieldSpec(String key, String taskLabel, String rules)
    {
    }

    /**
     * The best result extracted for one field across all chunks.
     *
     * @param value the extracted value, or {@code null} when not found
     * @param reasoning the model's argumentation for the value, or {@code null} when absent
     * @param evidence a JSON array (as a string) of supporting quotes, or {@code null} when absent
     * @param rawJson the raw JSON object the model returned for this field
     * @param confidence the model's confidence, between 0 and 1
     * @param found whether the model reported that it found an answer
     */
    public record FieldResult(String value, String reasoning, String evidence, String rawJson, double confidence,
        boolean found)
    {
    }
}
