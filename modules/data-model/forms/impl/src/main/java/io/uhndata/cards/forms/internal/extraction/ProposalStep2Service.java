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

import io.uhndata.cards.forms.internal.extraction.ProposalCatalog.Chunk;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldResult;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldSpec;
import io.uhndata.cards.forms.internal.extraction.Step2Planner.Batch;
import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMClientFactory;
import io.uhndata.cards.llm.LLMConfigurationService;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMRequestOptions;
import io.uhndata.cards.llm.LLMSettings;

/**
 * Stage 1.2 of the proposal pipeline: targeted extraction of the intake fields that came back missing or
 * unverified, then a fallback sweep. It re-reads the chunks {@link Step2Planner} groups by shared
 * {@code extraction_hints}, sending each group only its relevant chunks (token-bounded), with a per-batch
 * structured-output schema built for exactly that batch's fields. Every re-ask is fresh — a re-asked field
 * never sees its earlier answer, to avoid anchoring — and the higher-confidence result wins across passes.
 * Each call is recorded in {@code llm_call_tracker.jsonl} so the sweep never re-sends a chunk already examined
 * for a field, and so {@code found_answer=false} is only reached once a field's non-excluded chunks have all
 * been read. An unchunked (small) document — the chunker recorded {@code chunked: false} — skips the planner:
 * all still-pending fields get one focused re-ask over the whole document as a single {@link WholeDocument}
 * chunk.
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

    private static final int CHARS_PER_TOKEN = 4;

    private static final long BASE_TOKENS = 500L;

    private static final long PER_FIELD_TOKENS = 400L;

    private static final String CORRECTION = "\n\n# Correction\n\nYour previous response was not a valid JSON "
        + "object matching the required schema. Return only the JSON object.";

    private static final String TYPE = "type";

    @Reference
    private LLMClientFactory llmClientFactory;

    @Reference
    private LLMConfigurationService configurationService;

    /**
     * Run targeted extraction and a fallback sweep for the fields still pending after intake. The input follows
     * the chunker's recorded routing decision: a chunked document is re-read through the hint-driven planner
     * over its catalog chunks; an unchunked (small) document gets one fresh focused re-ask of all still-pending
     * fields over the whole document (there is no chunk selection to plan, and everything was already read
     * once, so no sweep follows).
     *
     * @param folder the located parse folder of the proposal
     * @param fields the extraction fields, in questionnaire order
     * @param intakeResults the field results from the intake pass
     * @return the merged field results (intake results updated by any better answers found in Stage 1.2)
     * @throws IOException if the catalog or document cannot be read or the active LLM client cannot be resolved
     */
    public Map<String, FieldResult> run(final ProposalParseFolder folder, final List<FieldSpec> fields,
        final Map<String, FieldResult> intakeResults) throws IOException
    {
        final Map<String, FieldResult> merged = new LinkedHashMap<>(intakeResults);
        final List<String> pending = pending(fields, merged);
        if (pending.isEmpty()) {
            return merged;
        }
        final LlmCallTracker tracker = LlmCallTracker.open(folder.trackerFile());
        final long tokenCap = wholeDocumentTokenLimit();
        if (!folder.isChunked()) {
            final Map<String, String> documentText = Map.of(WholeDocument.CHUNK_ID, WholeDocument.read(folder));
            final Context context =
                new Context(this.llmClientFactory.getActiveClient(), index(fields), documentText, tracker, tokenCap);
            runBatches(context, List.of(new Batch(pending, List.of(WholeDocument.CHUNK_ID))), merged,
                LlmCallTracker.STEP_EXTRACT);
            return merged;
        }
        final ProposalCatalog catalog = ProposalCatalog.read(folder.catalogFile());
        final List<Chunk> chunks = catalog.chunks();
        final Map<String, String> texts = readChunkTexts(folder.chunksDir(), chunks);
        final Context context =
            new Context(this.llmClientFactory.getActiveClient(), index(fields), texts, tracker, tokenCap);
        runBatches(context, Step2Planner.planTargeted(chunks, pending, tracker), merged,
            LlmCallTracker.STEP_EXTRACT);
        runBatches(context, Step2Planner.planSweep(chunks, pending(fields, merged), tracker), merged,
            LlmCallTracker.STEP_SWEEP);
        return merged;
    }

    /**
     * The active model's {@code wholeDocumentTokenLimit}, reused here as the per-batch chunk token cap so Stage
     * 1.2 never invents a second threshold beside the chunker's routing decision.
     *
     * @return the configured limit in estimated tokens
     */
    private long wholeDocumentTokenLimit()
    {
        try {
            return this.configurationService.getActiveSettings().getWholeDocumentTokenLimit();
        } catch (final IOException e) {
            LOGGER.warn("Could not read the active LLM settings for the step-2 token budget: {}", e.getMessage());
            return LLMSettings.DEFAULT_WHOLE_DOCUMENT_TOKEN_LIMIT;
        }
    }

    private void runBatches(final Context context, final List<Batch> batches,
        final Map<String, FieldResult> merged, final String step) throws IOException
    {
        for (final Batch batch : batches) {
            final List<FieldSpec> batchFields = specs(batch.fields(), context.specByKey());
            for (final List<String> ids : splitByTokens(batch.chunkIds(), context.texts(), context.tokenCap())) {
                mergeResults(merged, extract(context, batchFields, ids));
                context.tracker().append(step, batch.fields(), ids);
            }
        }
    }

    private Map<String, FieldResult> extract(final Context context, final List<FieldSpec> batchFields,
        final List<String> chunkIds) throws IOException
    {
        final List<String> keys = keys(batchFields);
        final String system = PipelinePrompts.load(PipelinePrompts.STEP2_EXTRACTION_SYSTEM);
        final String schema = buildSchema(keys);
        final String userMessage = buildMessage(batchFields, chunkIds, context.texts());
        final LLMRequestOptions options = LLMRequestOptions.builder()
            .maxOutputTokens(BASE_TOKENS + PER_FIELD_TOKENS * keys.size())
            .jsonSchema(SCHEMA_NAME, schema)
            .build();
        final JsonObject parsed = request(context.client(), system, userMessage, options);
        return FieldResponseParser.parseFields(parsed, keys, context.texts());
    }

    private JsonObject request(final LLMClient client, final String system, final String userMessage,
        final LLMRequestOptions options) throws IOException
    {
        final JsonObject first = requestOnce(client, system, userMessage, options);
        return first != null ? first : requestOnce(client, system, userMessage + CORRECTION, options);
    }

    private JsonObject requestOnce(final LLMClient client, final String system, final String userMessage,
        final LLMRequestOptions options) throws IOException
    {
        // Transport / HTTP errors propagate so the extract servlet can return them to the UI.
        // Unparseable replies still return null and trigger the caller's one re-ask.
        final String reply = client.chat(system, List.of(new LLMMessage("user", userMessage)), options);
        return FieldResponseParser.parseJsonObject(reply);
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

    private static List<List<String>> splitByTokens(final List<String> chunkIds, final Map<String, String> texts,
        final long tokenCap)
    {
        final List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int used = 0;
        final long cap = Math.max(0L, tokenCap);
        for (final String id : chunkIds) {
            final int tokens = tokenEstimate(texts.get(id));
            if (!current.isEmpty() && used + tokens > cap) {
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

    private static String buildMessage(final List<FieldSpec> batchFields, final List<String> chunkIds,
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
        final StringBuilder chunksBlock = new StringBuilder();
        for (final String id : chunkIds) {
            chunksBlock.append("[chunk:").append(id).append("]\n")
                .append(StringUtils.trimToEmpty(texts.get(id))).append("\n\n");
        }
        return "## SCHEMA\n\n" + schema.toString().strip()
            + "\n\n## CHUNKS (untrusted data)\n\n" + chunksBlock.toString().strip();
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
            .add(TYPE, "object")
            .add("additionalProperties", false)
            .add("required", required)
            .add("properties", properties)
            .add("$defs", Json.createObjectBuilder().add("field", fieldDef()))
            .build().toString();
    }

    private static JsonObjectBuilder fieldDef()
    {
        return Json.createObjectBuilder()
            .add(TYPE, "object")
            .add("additionalProperties", false)
            .add("required", strings("found_answer", "confidence", "value", "reasoning", "evidence"))
            .add("properties", Json.createObjectBuilder()
                .add("found_answer", type("boolean"))
                .add("confidence", type("number"))
                .add("value", nullableType("string"))
                .add("reasoning", type("string"))
                .add("evidence", Json.createObjectBuilder()
                    .add(TYPE, "array")
                    .add("items", evidenceItem())));
    }

    private static JsonObjectBuilder evidenceItem()
    {
        return Json.createObjectBuilder()
            .add(TYPE, "object")
            .add("additionalProperties", false)
            .add("required", strings("quote", "chunk_id", "page"))
            .add("properties", Json.createObjectBuilder()
                .add("quote", type("string"))
                .add("chunk_id", type("string"))
                .add("page", nullableType("integer")));
    }

    private static JsonObjectBuilder type(final String type)
    {
        return Json.createObjectBuilder().add(TYPE, type);
    }

    private static JsonObjectBuilder nullableType(final String type)
    {
        return Json.createObjectBuilder().add(TYPE, Json.createArrayBuilder().add(type).add("null"));
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

    private static Map<String, String> readChunkTexts(final Path chunksDir, final List<Chunk> chunks)
    {
        final Map<String, String> texts = new LinkedHashMap<>();
        for (final Chunk chunk : chunks) {
            texts.put(chunk.id(), readChunk(chunksDir.resolve(chunk.file())));
        }
        return texts;
    }

    private static String readChunk(final Path chunkFile)
    {
        try {
            return Files.isRegularFile(chunkFile) ? Files.readString(chunkFile, StandardCharsets.UTF_8) : "";
        } catch (final IOException e) {
            LOGGER.warn("Could not read chunk file {}: {}", chunkFile, e.getMessage());
            return "";
        }
    }

    private static int tokenEstimate(final String text)
    {
        return text == null ? 0 : text.length() / CHARS_PER_TOKEN;
    }

    /**
     * Per-run state shared across batches: the client, the field lookup, the chunk texts, the tracker and the
     * per-batch token cap from the active model's {@code wholeDocumentTokenLimit}.
     */
    private record Context(LLMClient client, Map<String, FieldSpec> specByKey, Map<String, String> texts,
        LlmCallTracker tracker, long tokenCap)
    {
    }
}
