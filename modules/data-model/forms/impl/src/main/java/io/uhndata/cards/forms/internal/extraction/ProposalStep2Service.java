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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.internal.extraction.ProposalCatalog.Section;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldResult;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldSpec;
import io.uhndata.cards.forms.internal.extraction.Step2Planner.Batch;
import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMClientFactory;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMRequestOptions;

/**
 * Stage 1.2 of the proposal pipeline: targeted extraction of the intake fields that came back missing or
 * unverified, then a fallback sweep. It re-reads the sections {@link Step2Planner} groups by shared
 * {@code extraction_hints}, sending each group only its relevant sections (token-bounded), with a per-batch
 * structured-output schema built for exactly that batch's fields. Every re-ask is fresh — a re-asked field
 * never sees its earlier answer, to avoid anchoring — and the higher-confidence result wins across passes.
 * Each call is recorded in {@code llm_call_tracker.jsonl} so the sweep never re-sends a section already examined
 * for a field, and so {@code found_answer=false} is only reached once a field's non-excluded sections have all
 * been read.
 *
 * @version $Id$
 */
@Component(service = ProposalStep2Service.class)
public class ProposalStep2Service
{
    /** Below this confidence a found field is still treated as pending and re-extracted. */
    static final double PENDING_CONFIDENCE = 0.75;

    /** Name the provider associates with the targeted-extraction structured-output schema. */
    static final String SCHEMA_NAME = "cards_step2_extract";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProposalStep2Service.class);

    /** Token cap on the sections sent in one batch (a cap, not a target). */
    private static final int SECTION_TOKEN_CAP = 20000;

    private static final int CHARS_PER_TOKEN = 4;

    private static final long BASE_TOKENS = 500L;

    private static final long PER_FIELD_TOKENS = 400L;

    private static final String CORRECTION = "\n\n# Correction\n\nYour previous response was not a valid JSON "
        + "object matching the required schema. Return only the JSON object.";

    @Reference
    private LLMClientFactory llmClientFactory;

    /**
     * Run targeted extraction and a fallback sweep for the fields still pending after intake.
     *
     * @param folder the located parse folder of the proposal
     * @param fields the extraction fields, in questionnaire order
     * @param intakeResults the field results from the intake pass
     * @return the merged field results (intake results updated by any better answers found in Stage 1.2)
     * @throws IOException if the catalog cannot be read or the active LLM client cannot be resolved
     */
    public Map<String, FieldResult> run(final ProposalParseFolder folder, final List<FieldSpec> fields,
        final Map<String, FieldResult> intakeResults) throws IOException
    {
        final ProposalCatalog catalog = ProposalCatalog.read(folder.catalogFile());
        final List<Section> sections = catalog.sections();
        final Map<String, FieldResult> merged = new LinkedHashMap<>(intakeResults);
        final List<String> pending = pending(fields, merged);
        if (pending.isEmpty()) {
            return merged;
        }
        final Map<String, String> texts = readSectionTexts(folder.chunksDir(), sections);
        final LlmCallTracker tracker = LlmCallTracker.open(folder.trackerFile());
        final Context context = new Context(this.llmClientFactory.getActiveClient(), index(fields), texts, tracker);
        runBatches(context, Step2Planner.planTargeted(sections, pending, tracker), merged,
            LlmCallTracker.STEP_EXTRACT);
        runBatches(context, Step2Planner.planSweep(sections, pending(fields, merged), tracker), merged,
            LlmCallTracker.STEP_SWEEP);
        return merged;
    }

    private void runBatches(final Context context, final List<Batch> batches,
        final Map<String, FieldResult> merged, final String step)
    {
        for (final Batch batch : batches) {
            final List<FieldSpec> batchFields = specs(batch.fields(), context.specByKey());
            for (final List<String> ids : splitByTokens(batch.sectionIds(), context.texts())) {
                mergeResults(merged, extract(context, batchFields, ids));
                context.tracker().append(step, batch.fields(), ids);
            }
        }
    }

    private Map<String, FieldResult> extract(final Context context, final List<FieldSpec> batchFields,
        final List<String> sectionIds)
    {
        final List<String> keys = keys(batchFields);
        final String system = PipelinePrompts.load(PipelinePrompts.STEP2_EXTRACTION_SYSTEM);
        final String schema = buildSchema(keys);
        final String userMessage = buildMessage(batchFields, sectionIds, context.texts());
        final LLMRequestOptions options = LLMRequestOptions.builder()
            .maxOutputTokens(BASE_TOKENS + PER_FIELD_TOKENS * keys.size())
            .jsonSchema(SCHEMA_NAME, schema)
            .build();
        final JsonObject parsed = request(context.client(), system, userMessage, options);
        return FieldResponseParser.parseFields(parsed, keys, context.texts());
    }

    private JsonObject request(final LLMClient client, final String system, final String userMessage,
        final LLMRequestOptions options)
    {
        final JsonObject first = requestOnce(client, system, userMessage, options);
        return first != null ? first : requestOnce(client, system, userMessage + CORRECTION, options);
    }

    private JsonObject requestOnce(final LLMClient client, final String system, final String userMessage,
        final LLMRequestOptions options)
    {
        try {
            final String reply = client.chat(system, List.of(new LLMMessage("user", userMessage)), options);
            return FieldResponseParser.parseJsonObject(reply);
        } catch (final IOException e) {
            LOGGER.warn("Step-2 extraction request failed: {}", e.getMessage());
            return null;
        }
    }

    private static void mergeResults(final Map<String, FieldResult> merged,
        final Map<String, FieldResult> results)
    {
        for (final Map.Entry<String, FieldResult> entry : results.entrySet()) {
            final FieldResult candidate = entry.getValue();
            final FieldResult existing = merged.get(entry.getKey());
            if (wins(candidate, existing)) {
                merged.put(entry.getKey(), candidate);
            }
        }
    }

    private static boolean wins(final FieldResult candidate, final FieldResult existing)
    {
        if (!candidate.found()) {
            return false;
        }
        return existing == null || !existing.found() || candidate.confidence() > existing.confidence();
    }

    private static List<String> pending(final List<FieldSpec> fields, final Map<String, FieldResult> merged)
    {
        final List<String> pending = new ArrayList<>();
        for (final FieldSpec field : fields) {
            final FieldResult result = merged.get(field.key());
            if (result == null || !result.found() || result.confidence() < PENDING_CONFIDENCE) {
                pending.add(field.key());
            }
        }
        return pending;
    }

    private static List<List<String>> splitByTokens(final List<String> sectionIds, final Map<String, String> texts)
    {
        final List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int used = 0;
        for (final String id : sectionIds) {
            final int tokens = tokenEstimate(texts.get(id));
            if (!current.isEmpty() && used + tokens > SECTION_TOKEN_CAP) {
                batches.add(current);
                current = new ArrayList<>();
                used = 0;
            }
            current.add(id);
            used += tokens;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    private static String buildMessage(final List<FieldSpec> batchFields, final List<String> sectionIds,
        final Map<String, String> texts)
    {
        final StringBuilder schema = new StringBuilder();
        for (final FieldSpec field : batchFields) {
            schema.append(field.key()).append(":\n");
            if (StringUtils.isNotBlank(field.rules())) {
                schema.append(field.rules().strip()).append('\n');
            }
            schema.append('\n');
        }
        final StringBuilder sections = new StringBuilder();
        for (final String id : sectionIds) {
            sections.append("[section:").append(id).append("]\n")
                .append(StringUtils.trimToEmpty(texts.get(id))).append("\n\n");
        }
        return "## SCHEMA\n\n" + schema.toString().strip()
            + "\n\n## SECTIONS (untrusted data)\n\n" + sections.toString().strip();
    }

    /**
     * Build a strict structured-output schema for exactly the given field keys: an object requiring each key,
     * every field sharing the {@code $defs/field} servlet-contract shape.
     *
     * @param keys the field keys the batch extracts
     * @return the schema as a JSON string
     */
    private static String buildSchema(final List<String> keys)
    {
        final JsonArrayBuilder required = Json.createArrayBuilder();
        final JsonObjectBuilder properties = Json.createObjectBuilder();
        for (final String key : keys) {
            required.add(key);
            properties.add(key, Json.createObjectBuilder().add("$ref", "#/$defs/field"));
        }
        return Json.createObjectBuilder()
            .add("type", "object")
            .add("additionalProperties", false)
            .add("required", required)
            .add("properties", properties)
            .add("$defs", Json.createObjectBuilder().add("field", fieldDef()))
            .build().toString();
    }

    private static JsonObjectBuilder fieldDef()
    {
        return Json.createObjectBuilder()
            .add("type", "object")
            .add("additionalProperties", false)
            .add("required", strings("found_answer", "confidence", "value", "reasoning", "evidence"))
            .add("properties", Json.createObjectBuilder()
                .add("found_answer", type("boolean"))
                .add("confidence", type("number"))
                .add("value", nullableType("string"))
                .add("reasoning", type("string"))
                .add("evidence", Json.createObjectBuilder()
                    .add("type", "array")
                    .add("items", evidenceItem())));
    }

    private static JsonObjectBuilder evidenceItem()
    {
        return Json.createObjectBuilder()
            .add("type", "object")
            .add("additionalProperties", false)
            .add("required", strings("quote", "section_id", "page"))
            .add("properties", Json.createObjectBuilder()
                .add("quote", type("string"))
                .add("section_id", type("string"))
                .add("page", nullableType("integer")));
    }

    private static JsonObjectBuilder type(final String type)
    {
        return Json.createObjectBuilder().add("type", type);
    }

    private static JsonObjectBuilder nullableType(final String type)
    {
        return Json.createObjectBuilder().add("type", Json.createArrayBuilder().add(type).add("null"));
    }

    private static JsonArrayBuilder strings(final String... values)
    {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        for (final String value : values) {
            builder.add(value);
        }
        return builder;
    }

    private static Map<String, FieldSpec> index(final List<FieldSpec> fields)
    {
        final Map<String, FieldSpec> byKey = new LinkedHashMap<>();
        for (final FieldSpec field : fields) {
            byKey.put(field.key(), field);
        }
        return byKey;
    }

    private static List<FieldSpec> specs(final List<String> keys, final Map<String, FieldSpec> byKey)
    {
        final List<FieldSpec> result = new ArrayList<>(keys.size());
        for (final String key : keys) {
            final FieldSpec spec = byKey.get(key);
            if (spec != null) {
                result.add(spec);
            }
        }
        return result;
    }

    private static List<String> keys(final List<FieldSpec> fields)
    {
        final List<String> keys = new ArrayList<>(fields.size());
        for (final FieldSpec field : fields) {
            keys.add(field.key());
        }
        return keys;
    }

    private static Map<String, String> readSectionTexts(final Path sectionsDir, final List<Section> sections)
    {
        final Map<String, String> texts = new LinkedHashMap<>();
        for (final Section section : sections) {
            texts.put(section.id(), readSection(sectionsDir.resolve(section.file())));
        }
        return texts;
    }

    private static String readSection(final Path sectionFile)
    {
        try {
            return Files.isRegularFile(sectionFile) ? Files.readString(sectionFile, StandardCharsets.UTF_8) : "";
        } catch (final IOException e) {
            LOGGER.warn("Could not read section file {}: {}", sectionFile, e.getMessage());
            return "";
        }
    }

    private static int tokenEstimate(final String text)
    {
        return text == null ? 0 : text.length() / CHARS_PER_TOKEN;
    }

    /** Per-run state shared across batches: the client, the field lookup, the section texts and the tracker. */
    private record Context(LLMClient client, Map<String, FieldSpec> specByKey, Map<String, String> texts,
        LlmCallTracker tracker)
    {
    }
}
