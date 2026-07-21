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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.internal.extraction.ProposalCatalog.Chunk;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldResult;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldSpec;
import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMClientFactory;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMRequestOptions;

/**
 * Stage 1.1 of the proposal pipeline: one structured LLM call that extracts the protocol-plausible intake fields
 * and tags the catalog chunks sent in full. It assembles the CATALOG and CHUNK blocks from the chunk catalog
 * (see {@link IntakePayload}), sizes {@code max_tokens} from the chunk count so the tag map is never
 * truncated, sends the call with the intake JSON schema, then does the code-side work the model is never asked
 * for: {@link FieldResponseParser} verifies every evidence quote and enum-validates the controlled-vocabulary
 * fields, and this service stamps each fulltext chunk's {@code rubric_tags}, {@code tag_basis="fulltext"},
 * {@code tag_confidence}, {@code uncertain} and derived {@code extraction_hints} (the Stage 1.15 join, see
 * {@link FieldTagMap}) into the catalog. Chunks not sent in full were already tagged
 * {@code tag_basis="heading"} by the Stage 0.5 gate and are left untouched here, except a chunk the gate never
 * tagged at all (for example a generic default heading, or a heading outside the extracted array), which still
 * gets a heading-keyword fallback so no chunk is left without a tag. A call that cannot be parsed after one
 * re-ask degrades gracefully: no field values are written and every chunk keeps whatever tag it already had,
 * never blocking the upload pipeline.
 *
 * @version $Id$
 */
@Component(service = ProposalIntakeService.class)
public class ProposalIntakeService
{
    /** Name the provider associates with the intake structured-output schema. */
    static final String SCHEMA_NAME = "cards_step1_intake";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProposalIntakeService.class);

    /** Base output-token allowance for the ten fields. */
    private static final long BASE_TOKENS = 2000L;

    /** Additional output tokens reserved per catalog chunk for its tag entry. */
    private static final long PER_CHUNK_TOKENS = 30L;

    /** Output-token safety margin. */
    private static final long MARGIN_TOKENS = 500L;

    /** Maximum rubric tags kept per chunk. */
    private static final int MAX_TAGS = 2;

    private static final String CORRECTION = "\n\n# Correction\n\nYour previous response was not a valid JSON "
        + "object matching the required schema. Return only the JSON object.";

    /** Rubric tag for eligibility / participant chunks. */
    private static final String TAG_B5 = "B.5";

    /** Coarse heading-keyword to rubric-tag rules for filling chunk-tag holes; first match wins. */
    private static final String[][] HEADING_TAG_RULES = {
        {"reference", "B.17"}, {"bibliograph", "B.17"}, {"appendix", "B.17"},
        {"background", "B.2"}, {"rationale", "B.2"},
        {"objective", "B.3"}, {"hypothes", "B.3"}, {"aim", "B.3"},
        {"design", "B.4"}, {"randomiz", "B.4"}, {"blinding", "B.4"}, {"endpoint", "B.4"},
        {"eligib", TAG_B5}, {"inclusion", TAG_B5}, {"exclusion", TAG_B5}, {"participant", TAG_B5},
        {"recruit", TAG_B5}, {"withdraw", "B.6"}, {"discontinu", "B.6"},
        {"treatment", "B.7"}, {"intervention", "B.7"}, {"dosing", "B.7"},
        {"efficacy", "B.8"}, {"safety", "B.9"}, {"adverse", "B.9"},
        {"statistic", "B.10"}, {"analysis", "B.10"}, {"sample size", "B.10"},
        {"monitor", "B.11"}, {"quality", "B.12"}, {"ethic", "B.13"}, {"consent", "B.13"},
        {"data", "B.14"}, {"confidential", "B.14"}, {"fund", "B.15"}, {"budget", "B.15"},
        {"publication", "B.16"}
    };

    @Reference
    private LLMClientFactory llmClientFactory;

    /**
     * Run the intake call for a proposal, persisting the chunk tags and hints to the catalog and returning the
     * extracted field results.
     *
     * @param folder the located parse folder of the proposal
     * @param fields the extraction fields (one per extraction-enabled question), in questionnaire order
     * @param categoriesDocument the Research Study Description taxonomy (STUDY_CATEGORIES block)
     * @return the intake result: the per-field results and whether the call degraded
     * @throws IOException if the catalog cannot be read or the active LLM client cannot be resolved
     */
    public IntakeResult run(final ProposalParseFolder folder, final List<FieldSpec> fields,
        final String categoriesDocument) throws IOException
    {
        final ProposalCatalog catalog = ProposalCatalog.read(folder.catalogFile());
        final List<Chunk> chunks = catalog.chunks();
        final Map<String, String> texts = readChunkTexts(folder.chunksDir(), chunks);
        final IntakePayload payload = IntakePayload.build(chunks, texts, fields, categoriesDocument);
        final JsonObject parsed = call(payload.userMessage(), chunks.size());
        final List<String> keys = fieldKeys(fields);
        final Map<String, FieldResult> results = FieldResponseParser.parseFields(parsed, keys, texts);
        stampTags(catalog, chunks, parsed, new HashSet<>(payload.fullTextChunkIds()), keys);
        writeCatalog(catalog, folder.catalogFile());
        LlmCallTracker.open(folder.trackerFile())
            .append(LlmCallTracker.STEP_INTAKE, keys, payload.fullTextChunkIds());
        return new IntakeResult(results, parsed == null);
    }

    private JsonObject call(final String userMessage, final int chunkCount) throws IOException
    {
        final LLMClient client = this.llmClientFactory.getActiveClient();
        final String system = PipelinePrompts.load(PipelinePrompts.STEP1_INTAKE_SYSTEM);
        final String schema = PipelinePrompts.load(PipelinePrompts.STEP1_INTAKE_SCHEMA);
        final long maxTokens = BASE_TOKENS + PER_CHUNK_TOKENS * chunkCount + MARGIN_TOKENS;
        final LLMRequestOptions options = LLMRequestOptions.builder()
            .maxOutputTokens(maxTokens)
            .jsonSchema(SCHEMA_NAME, schema)
            .build();
        final JsonObject first = request(client, system, userMessage, options);
        return first != null ? first : request(client, system, userMessage + CORRECTION, options);
    }

    private JsonObject request(final LLMClient client, final String system, final String userMessage,
        final LLMRequestOptions options)
    {
        try {
            final String reply = client.chat(system, List.of(new LLMMessage("user", userMessage)), options);
            return FieldResponseParser.parseJsonObject(reply);
        } catch (final IOException e) {
            LOGGER.warn("Intake LLM request failed: {}", e.getMessage());
            return null;
        }
    }

    private void stampTags(final ProposalCatalog catalog, final List<Chunk> chunks, final JsonObject parsed,
        final Set<String> fullTextIds, final List<String> fieldKeys)
    {
        final Map<String, TagEntry> tagMap = parseTagMap(parsed);
        for (final Chunk chunk : chunks) {
            final List<String> tags;
            final String basis;
            final boolean uncertain;
            if (fullTextIds.contains(chunk.id())) {
                final TagEntry entry = tagMap.get(chunk.id());
                final List<String> valid = entry == null ? List.of() : StudyTaxonomy.validRubricTags(entry.tags());
                tags = valid.isEmpty()
                    ? List.of(guessTag(chunk.heading())) : valid.subList(0, Math.min(MAX_TAGS, valid.size()));
                uncertain = valid.isEmpty() || entry == null || entry.uncertain();
                basis = "fulltext";
                catalog.setRubricTags(chunk.id(), tags);
                catalog.setTagBasis(chunk.id(), basis);
                catalog.setTagConfidence(chunk.id(), entry == null ? 0.0 : entry.confidence());
                catalog.setUncertain(chunk.id(), uncertain);
            } else if (chunk.rubricTags().isEmpty()) {
                // Safety net: the gate never tagged this heading (failed open, no HEADINGS block, or a
                // heading outside the extracted array such as a default or backmatter heading).
                tags = List.of(guessTag(chunk.heading()));
                basis = "heading";
                uncertain = true;
                catalog.setRubricTags(chunk.id(), tags);
                catalog.setTagBasis(chunk.id(), basis);
                catalog.setTagConfidence(chunk.id(), 0.0);
                catalog.setUncertain(chunk.id(), uncertain);
            } else {
                // Already tagged by the gate from its heading; leave the tag itself untouched.
                tags = chunk.rubricTags();
                basis = chunk.tagBasis();
                uncertain = chunk.uncertain();
            }
            catalog.setExtractionHints(chunk.id(),
                FieldTagMap.hintsForChunk(tags, basis, uncertain, fieldKeys));
        }
    }

    private static Map<String, TagEntry> parseTagMap(final JsonObject parsed)
    {
        final Map<String, TagEntry> tagMap = new HashMap<>();
        if (parsed == null) {
            return tagMap;
        }
        final JsonValue tags = parsed.get("chunk_tags");
        if (tags == null || tags.getValueType() != JsonValue.ValueType.ARRAY) {
            return tagMap;
        }
        for (final JsonValue value : tags.asJsonArray()) {
            if (value.getValueType() == JsonValue.ValueType.OBJECT) {
                addTagEntry(tagMap, value.asJsonObject());
            }
        }
        return tagMap;
    }

    private static void addTagEntry(final Map<String, TagEntry> tagMap, final JsonObject entry)
    {
        final String id = readString(entry, "chunk_id");
        if (id != null && !tagMap.containsKey(id)) {
            tagMap.put(id, new TagEntry(readStringList(entry, "tags"), entry.getBoolean("uncertain", false),
                clamp(readDouble(entry, "confidence"))));
        }
    }

    private static double readDouble(final JsonObject object, final String key)
    {
        final JsonValue value = object.get(key);
        return value != null && value.getValueType() == JsonValue.ValueType.NUMBER
            ? object.getJsonNumber(key).doubleValue() : 0.0;
    }

    private static double clamp(final double value)
    {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Guess a rubric tag from a chunk heading when the model supplied none — a coarse keyword map that always
     * returns a valid tag so a hole never crashes the catalog; the chunk is flagged uncertain by the caller.
     */
    private static String guessTag(final List<String> heading)
    {
        final String lower = heading == null ? "" : String.join(" ", heading).toLowerCase(Locale.ROOT);
        for (final String[] rule : HEADING_TAG_RULES) {
            if (lower.contains(rule[0])) {
                return rule[1];
            }
        }
        return "B.1";
    }

    private static Map<String, String> readChunkTexts(final Path chunksDir, final List<Chunk> chunks)
    {
        final Map<String, String> texts = new HashMap<>();
        for (final Chunk chunk : chunks) {
            texts.put(chunk.id(), readChunk(chunksDir.resolve(chunk.file())));
        }
        return texts;
    }

    private static String readChunk(final Path chunkFile)
    {
        try {
            return Files.isRegularFile(chunkFile)
                ? Files.readString(chunkFile, StandardCharsets.UTF_8) : "";
        } catch (final IOException e) {
            LOGGER.warn("Could not read chunk file {}: {}", chunkFile, e.getMessage());
            return "";
        }
    }

    private static void writeCatalog(final ProposalCatalog catalog, final Path catalogFile)
    {
        try {
            catalog.write();
        } catch (final IOException e) {
            LOGGER.warn("Could not write catalog {}: {}", catalogFile, e.getMessage());
        }
    }

    private static List<String> fieldKeys(final List<FieldSpec> fields)
    {
        final List<String> keys = new ArrayList<>(fields.size());
        for (final FieldSpec field : fields) {
            keys.add(field.key());
        }
        return keys;
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

    private static List<String> readStringList(final JsonObject object, final String key)
    {
        final List<String> result = new ArrayList<>();
        if (object.containsKey(key) && object.get(key).getValueType() == JsonValue.ValueType.ARRAY) {
            for (final JsonValue value : object.getJsonArray(key)) {
                if (value.getValueType() == JsonValue.ValueType.STRING) {
                    result.add(((JsonString) value).getString());
                }
            }
        }
        return result;
    }

    /** A parsed {@code chunk_tags} entry: the raw tags, the model's uncertain flag, and its confidence. */
    private record TagEntry(List<String> tags, boolean uncertain, double confidence)
    {
    }

    /**
     * The outcome of an intake call.
     *
     * @param fields the per-field results keyed by field key; empty when the call degraded
     * @param degraded whether the call could not be parsed after a re-ask, so no field values were extracted and
     *            every chunk was left flagged uncertain
     */
    public record IntakeResult(Map<String, FieldResult> fields, boolean degraded)
    {
    }
}
