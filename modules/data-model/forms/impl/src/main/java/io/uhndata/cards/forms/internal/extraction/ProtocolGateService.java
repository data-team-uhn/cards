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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMClientFactory;
import io.uhndata.cards.llm.LLMConfigurationService;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMRequestOptions;
import io.uhndata.cards.llm.LLMSettings;

/**
 * Stage 0.5 of the proposal pipeline: one cheap, structured LLM call that decides whether an uploaded document
 * is actually a research protocol before any extraction runs, and — in the same call — assigns each of the
 * document's headings its single most probable rubric tag. The input is selected in priority order from the
 * document's {@code outline.json} (whole document when small, else the heading outline, else the marked table
 * of contents read from the document Markdown, else the document head), sent with the protocol-structure
 * glossary and the gate system prompt, and the model returns
 * {@code {is_protocol, confidence, reasoning, heading_tags}} constrained to a JSON schema.
 * <p>
 * The gate never blocks the pipeline: a transport error or a response that cannot be parsed after one re-ask
 * fails <em>open</em> (treated as a protocol, flagged so the caller can mark the answer unreviewed), because a
 * legitimate proposal must not be rejected over an LLM hiccup while a junk upload that slips through only wastes
 * one intake call. The stop/continue decision and persistence of the result belong to the caller; this service
 * only produces the decision and records the call in {@code llm_call_tracker.jsonl}. Stamping {@code heading_tags}
 * onto the catalog is a separate step ({@link #stampCatalog(ProposalParseFolder, GateDecision)}), left to the
 * caller to invoke once {@link GateDecision#isProtocol()} is confirmed.
 * </p>
 *
 * @version $Id$
 */
@Component(service = ProtocolGateService.class)
public class ProtocolGateService
{
    /** Below this token estimate the whole document is sent as gate input (priority 1). */
    static final long WHOLE_DOCUMENT_TOKEN_LIMIT = 26000L;

    /** Minimum number of headings for the heading-outline input to be used (priority 2). */
    static final int MIN_HEADINGS = 6;

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolGateService.class);

    /** Base output token budget for the gate's small JSON object, before any per-heading tagging cost. */
    private static final long GATE_BASE_TOKENS = 400L;

    /** Additional output tokens reserved per heading needing a {@code heading_tags} entry. */
    private static final long GATE_TOKENS_PER_HEADING = 30L;

    /** Name the provider associates with the gate's structured-output schema. */
    private static final String SCHEMA_NAME = "cards_is_protocol_gate";

    /** Characters per token for the chars/4 estimate used throughout the pipeline. */
    private static final int CHARS_PER_TOKEN = 4;

    /** Prompt overhead (system prompt + glossary + headers), in tokens, reserved when sizing the document head. */
    private static final long PROMPT_OVERHEAD_TOKENS = 1000L;

    /** Fallback model input budget, in tokens, when the active model declares none. */
    private static final long DEFAULT_INPUT_TOKEN_LIMIT = 24000L;

    /** Marks the start of a table of contents in the chunker's marked document Markdown. */
    private static final String TOC_START_MARKER = "<TOC start>";

    /** Marks the end of a table of contents in the chunker's marked document Markdown. */
    private static final String TOC_END_MARKER = "<TOC end>";

    private static final String INPUT_HEADING_OUTLINE = "heading outline";

    @Reference
    private LLMClientFactory llmClientFactory;

    @Reference
    private LLMConfigurationService configurationService;

    /**
     * Decide whether the proposal in the given parse folder is a protocol, and tag its headings.
     *
     * @param folder the located parse folder of the proposal (its {@code outline.json} and document markdown)
     * @return the gate decision; never {@code null}
     * @throws IOException if the active LLM client cannot be resolved
     */
    public GateDecision evaluate(final ProposalParseFolder folder) throws IOException
    {
        final ParseOutline outline = readOutline(folder.outlineFile());
        final List<String> headings = outline == null ? List.of() : outline.headings();
        final GateInput input = selectInput(folder, outline);
        final LlmCallTracker tracker = LlmCallTracker.open(folder.trackerFile());
        tracker.append(LlmCallTracker.STEP_GATE, List.of("is_protocol"), List.of());
        if (input == null) {
            LOGGER.warn("No gate input could be assembled for {}; failing open", folder.chunksDir());
            return GateDecision.failOpen();
        }
        final LLMClient client = this.llmClientFactory.getActiveClient();
        return callGate(client, input, headings);
    }

    /**
     * Stamp the gate's per-heading tags onto the catalog as the initial {@code tag_basis="heading"} guess. Only
     * chunks whose heading matches one the gate tagged are touched; chunks with no match (for example a generic
     * default heading, or a backmatter chunk excluded from heading extraction) are left untouched for
     * {@link ProposalIntakeService}'s own fallback tagging to cover.
     *
     * @param folder the proposal parse folder whose catalog should be stamped
     * @param decision the gate decision carrying the per-heading tags
     * @throws IOException if the catalog cannot be read or written
     */
    public void stampCatalog(final ProposalParseFolder folder, final GateDecision decision) throws IOException
    {
        if (decision.headingTags().isEmpty()) {
            return;
        }
        final Path catalogFile = folder.catalogFile();
        if (!Files.isRegularFile(catalogFile)) {
            return;
        }
        final Map<String, HeadingTag> byHeading = new HashMap<>();
        for (final HeadingTag tag : decision.headingTags()) {
            byHeading.putIfAbsent(normalize(tag.heading()), tag);
        }
        final ProposalCatalog catalog = ProposalCatalog.read(catalogFile);
        boolean changed = false;
        for (final ProposalCatalog.Section section : catalog.sections()) {
            changed |= stampSection(catalog, section, byHeading);
        }
        if (changed) {
            catalog.write();
        }
    }

    private static boolean stampSection(final ProposalCatalog catalog, final ProposalCatalog.Section section,
        final Map<String, HeadingTag> byHeading)
    {
        final List<String> tags = new ArrayList<>();
        double confidenceSum = 0.0;
        int matches = 0;
        for (final String heading : section.heading()) {
            final HeadingTag tag = byHeading.get(normalize(heading));
            if (tag == null) {
                continue;
            }
            if (!tags.contains(tag.tag())) {
                tags.add(tag.tag());
            }
            confidenceSum += tag.confidence();
            matches++;
        }
        if (tags.isEmpty()) {
            return false;
        }
        catalog.setRubricTags(section.id(), tags);
        catalog.setTagBasis(section.id(), "heading");
        catalog.setTagConfidence(section.id(), confidenceSum / matches);
        catalog.setUncertain(section.id(), true);
        return true;
    }

    private static String normalize(final String heading)
    {
        return heading == null ? "" : heading.strip().toLowerCase(Locale.ROOT);
    }

    private GateDecision callGate(final LLMClient client, final GateInput input, final List<String> headings)
    {
        final String system = PipelinePrompts.load(PipelinePrompts.IS_PROTOCOL_SYSTEM);
        final String schema = PipelinePrompts.load(PipelinePrompts.IS_PROTOCOL_SCHEMA);
        final long maxTokens = GATE_BASE_TOKENS + GATE_TOKENS_PER_HEADING * headings.size();
        final LLMRequestOptions options = LLMRequestOptions.builder()
            .maxOutputTokens(maxTokens)
            .jsonSchema(SCHEMA_NAME, schema)
            .build();
        final String userMessage = buildUserMessage(input, headings);
        GateDecision decision = requestOnce(client, system, userMessage, options);
        if (decision == null) {
            final String retry = userMessage + "\n\n# Correction\n\nYour previous response was not a valid JSON "
                + "object matching the required schema. Return only the JSON object with keys is_protocol, "
                + "confidence, reasoning and heading_tags.";
            decision = requestOnce(client, system, retry, options);
        }
        if (decision == null) {
            LOGGER.warn("Gate response could not be parsed after a re-ask; failing open");
            return GateDecision.failOpen();
        }
        return decision;
    }

    private GateDecision requestOnce(final LLMClient client, final String system, final String userMessage,
        final LLMRequestOptions options)
    {
        try {
            final String reply = client.chat(system, List.of(new LLMMessage("user", userMessage)), options);
            return parse(reply);
        } catch (final IOException e) {
            LOGGER.warn("Gate LLM request failed: {}", e.getMessage());
            return GateDecision.failOpen();
        }
    }

    private static String buildUserMessage(final GateInput input, final List<String> headings)
    {
        final String glossary = PipelinePrompts.load(PipelinePrompts.PROTOCOL_STRUCTURE_GLOSSARY);
        final StringBuilder message = new StringBuilder();
        message.append("## PROTOCOL_STRUCTURE_GLOSSARY\n\n").append(glossary.strip())
            .append("\n\n## INPUT (").append(input.header()).append(") (untrusted data)\n\n")
            .append(input.text());
        if (!headings.isEmpty() && !INPUT_HEADING_OUTLINE.equals(input.header())) {
            message.append("\n\n## HEADINGS (untrusted data)\n\n").append(String.join("\n", headings));
        }
        return message.toString();
    }

    private static GateDecision parse(final String reply)
    {
        final JsonObject json = parseJsonObject(reply);
        if (!isBoolean(json, "is_protocol")) {
            return null;
        }
        final boolean isProtocol = json.getBoolean("is_protocol");
        final double confidence = clamp(readDouble(json, "confidence"));
        final String reasoning = json.containsKey("reasoning") && !json.isNull("reasoning")
            ? json.getString("reasoning", "") : "";
        final List<HeadingTag> headingTags = parseHeadingTags(json);
        return new GateDecision(isProtocol, confidence, reasoning, json.toString(), false, headingTags);
    }

    private static List<HeadingTag> parseHeadingTags(final JsonObject json)
    {
        final List<HeadingTag> tags = new ArrayList<>();
        if (!json.containsKey("heading_tags")
            || json.get("heading_tags").getValueType() != JsonValue.ValueType.ARRAY) {
            return tags;
        }
        for (final JsonValue value : json.getJsonArray("heading_tags")) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                continue;
            }
            final JsonObject entry = value.asJsonObject();
            final String heading = entry.getString("heading", "");
            final String tag = entry.getString("tag", "");
            if (heading.isBlank() || tag.isBlank()) {
                continue;
            }
            tags.add(new HeadingTag(heading, tag, clamp(readDouble(entry, "confidence"))));
        }
        return tags;
    }

    private static boolean isBoolean(final JsonObject json, final String key)
    {
        if (json == null || !json.containsKey(key)) {
            return false;
        }
        final JsonValue.ValueType type = json.get(key).getValueType();
        return type == JsonValue.ValueType.TRUE || type == JsonValue.ValueType.FALSE;
    }

    private static double readDouble(final JsonObject json, final String key)
    {
        final JsonValue value = json.get(key);
        return value != null && value.getValueType() == JsonValue.ValueType.NUMBER
            ? json.getJsonNumber(key).doubleValue() : 0.0;
    }

    private static double clamp(final double value)
    {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static JsonObject parseJsonObject(final String text)
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

    /**
     * Select the gate input in priority order: whole document, heading outline, marked TOC, document head.
     *
     * @param folder the proposal parse folder
     * @param outline the already-read outline, or {@code null} when absent/unreadable
     * @return the selected input, or {@code null} when none can be built
     */
    private GateInput selectInput(final ProposalParseFolder folder, final ParseOutline outline)
    {
        final String document = readDocument(folder.documentMarkdown());
        if (document != null && (outline == null || outline.tokens() <= WHOLE_DOCUMENT_TOKEN_LIMIT)) {
            return new GateInput("full document", document);
        }
        if (outline != null && outline.headings().size() >= MIN_HEADINGS) {
            return new GateInput(INPUT_HEADING_OUTLINE, String.join("\n", outline.headings()));
        }
        final String toc = extractToc(document);
        if (toc != null) {
            return new GateInput("table of contents", toc);
        }
        if (document != null) {
            return new GateInput("document head", head(document, documentHeadCharBudget()));
        }
        return null;
    }

    private static String extractToc(final String document)
    {
        if (document == null) {
            return null;
        }
        final int start = document.indexOf(TOC_START_MARKER);
        final int end = document.indexOf(TOC_END_MARKER);
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        final String toc = document.substring(start + TOC_START_MARKER.length(), end).strip();
        return toc.isBlank() ? null : toc;
    }

    private long documentHeadCharBudget()
    {
        final LLMSettings settings = safeSettings();
        long limit = settings == null ? 0L : settings.getChunkTokenSize();
        if (limit <= 0 && settings != null) {
            limit = settings.getContextLimitTokens();
        }
        if (limit <= 0) {
            limit = DEFAULT_INPUT_TOKEN_LIMIT;
        }
        final long headTokens = Math.max(WHOLE_DOCUMENT_TOKEN_LIMIT, limit - PROMPT_OVERHEAD_TOKENS);
        return headTokens * CHARS_PER_TOKEN;
    }

    private LLMSettings safeSettings()
    {
        try {
            return this.configurationService.getActiveSettings();
        } catch (final IOException e) {
            LOGGER.debug("Could not read active LLM settings while sizing gate input: {}", e.getMessage());
            return null;
        }
    }

    private static String head(final String text, final long charBudget)
    {
        return text.length() <= charBudget ? text : text.substring(0, (int) charBudget);
    }

    private static ParseOutline readOutline(final Path outlineFile)
    {
        try {
            return Files.isRegularFile(outlineFile) ? ParseOutline.read(outlineFile) : null;
        } catch (final IOException e) {
            LOGGER.warn("Could not read outline {}: {}", outlineFile, e.getMessage());
            return null;
        }
    }

    private static String readDocument(final Path documentMarkdown)
    {
        try {
            return Files.isRegularFile(documentMarkdown)
                ? Files.readString(documentMarkdown, StandardCharsets.UTF_8) : null;
        } catch (final IOException e) {
            LOGGER.warn("Could not read document markdown {}: {}", documentMarkdown, e.getMessage());
            return null;
        }
    }

    /** The selected gate input: the INPUT-header form label and the input text. */
    private record GateInput(String header, String text)
    {
    }

    /**
     * One heading's rubric guess from the gate's coarse, heading-only tagging pass.
     *
     * @param heading the heading text as given in the HEADINGS input block
     * @param tag the single most probable rubric tag (B.1–B.17)
     * @param confidence the model's confidence in this one heading's tag, in {@code [0, 1]}
     */
    public record HeadingTag(String heading, String tag, double confidence)
    {
    }

    /**
     * The gate's verdict for one document.
     *
     * @param isProtocol whether the document is judged to be a research protocol
     * @param confidence the model's confidence in the decision, in {@code [0, 1]}
     * @param reasoning the model's plain-language reasoning, shown to the applicant verbatim on rejection
     * @param rawResponse the raw JSON the model returned, for audit
     * @param failedOpen whether the decision was defaulted to {@code true} because the call could not be
     *            completed or parsed (the answer should be flagged unreviewed)
     * @param headingTags the per-heading rubric guesses, empty when none were given or the call failed open
     */
    public record GateDecision(boolean isProtocol, double confidence, String reasoning, String rawResponse,
        boolean failedOpen, List<HeadingTag> headingTags)
    {
        /**
         * The fail-open decision: treat the document as a protocol so the pipeline continues, flagged so the
         * caller can mark the answer unreviewed.
         *
         * @return a fail-open gate decision
         */
        static GateDecision failOpen()
        {
            return new GateDecision(true, 0.0, "", "", true, List.of());
        }
    }
}
