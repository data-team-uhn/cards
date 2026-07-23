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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMRequestOptions;

/**
 * Stage 0.5 of the proposal pipeline: one cheap, structured LLM call that decides whether an uploaded document
 * is actually a research protocol before any extraction runs, and — in the same call — assigns each catalog
 * chunk its single most probable rubric tag. The input follows the chunker's recorded routing decision
 * ({@link ProposalParseFolder#isChunked()}): an unchunked (small) document is always sent whole; a chunked
 * document is represented by its detected table of contents alone (a TOC already maps the whole structure),
 * else its catalog outline ({@code chunkNNN: heading} lines) plus the first catalog chunk's text. The document
 * head is never sent; a chunked document with neither a TOC nor a catalog produces no input and fails open. A
 * chunked
 * document also carries a {@code CATALOG} block listing every chunk's {@code chunkNNN: heading} so the model
 * can tag each chunk. Every form is sent with the full protocol-structure
 * reference and the gate system prompt, and the model returns
 * {@code {is_protocol, confidence, reasoning, chunk_tags}} constrained to a JSON schema.
 * <p>
 * The gate never blocks the pipeline: a transport error or a response that cannot be parsed after one re-ask
 * fails <em>open</em> (treated as a protocol, flagged so the caller can mark the answer unreviewed), because a
 * legitimate proposal must not be rejected over an LLM hiccup while a junk upload that slips through only wastes
 * one intake call. The stop/continue decision and persistence of the result belong to the caller; this service
 * only produces the decision and records the call in {@code llm_call_tracker.jsonl}. Stamping {@code chunk_tags}
 * onto the catalog is a separate step ({@link #stampCatalog(ProposalParseFolder, GateDecision)}), left to the
 * caller to invoke once {@link GateDecision#isProtocol()} is confirmed.
 * </p>
 * <p>
 * Stamping records each returned chunk tag directly onto its {@code chunk_id} in {@code catalog.json}.
 * </p>
 *
 * @version $Id$
 */
@Component(service = ProtocolGateService.class)
public class ProtocolGateService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolGateService.class);

    /** Base output token budget for the gate's small JSON object, before any per-chunk tagging cost. */
    private static final long GATE_BASE_TOKENS = 400L;

    /** Additional output tokens reserved per chunk needing a {@code chunk_tags} entry. */
    private static final long GATE_TOKENS_PER_CHUNK = 30L;

    /** Name the provider associates with the gate's structured-output schema. */
    private static final String SCHEMA_NAME = "cards_is_protocol_gate";

    private static final String INPUT_FULL_DOCUMENT = "full document";

    private static final String INPUT_TOC = "table of contents";

    private static final String INPUT_CATALOG_OUTLINE = "catalog outline + first chunk";

    /** Sub-header separating the first catalog chunk's text from the structure part of the INPUT block. */
    private static final String FIRST_CHUNK_HEADER = "### FIRST CHUNK";

    @Reference
    private LLMClientFactory llmClientFactory;

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
        final List<CatalogHeading> catalogHeadings = readCatalogHeadings(folder);
        final GateInput input = selectInput(folder, outline, catalogHeadings);
        final LlmCallTracker tracker = LlmCallTracker.open(folder.trackerFile());
        tracker.append(LlmCallTracker.STEP_GATE, List.of("is_protocol"), List.of());
        if (input == null) {
            LOGGER.warn("No gate input could be assembled for {}; failing open", folder.chunksDir());
            return GateDecision.failOpen();
        }
        final LLMClient client = this.llmClientFactory.getActiveClient();
        return callGate(client, input, catalogHeadings);
    }

    /**
     * Read the {@code chunkNNN: heading} correspondence from the folder's {@code catalog.json}. This is both
     * the chunk-tagging target the gate returns tags for and, for a chunked document with no detected TOC, the
     * outline sent as the gate's protocol-decision INPUT.
     *
     * @param folder the proposal parse folder
     * @return one {@link CatalogHeading} per catalog chunk in document order, empty when the document was left
     *         unchunked or the catalog is unavailable
     */
    private static List<CatalogHeading> readCatalogHeadings(final ProposalParseFolder folder)
    {
        if (!folder.isChunked()) {
            return List.of();
        }
        final Path catalogFile = folder.catalogFile();
        if (!Files.isRegularFile(catalogFile)) {
            return List.of();
        }
        try {
            final ProposalCatalog catalog = ProposalCatalog.read(catalogFile);
            final List<CatalogHeading> result = new ArrayList<>();
            for (final ProposalCatalog.Chunk chunk : catalog.chunks()) {
                result.add(new CatalogHeading(chunk.id(), String.join(", ", chunk.heading())));
            }
            return result;
        } catch (final IOException e) {
            LOGGER.warn("Could not read catalog headings under {}: {}", folder.chunksDir(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Stamp the gate's per-chunk tags onto the catalog as the initial {@code tag_basis="heading"} guess. Each
     * returned {@code chunk_id} that exists in the catalog gets its tag; unknown ids and chunks the gate did not
     * tag are left untouched for {@link ProposalIntakeService}'s own fallback tagging to cover. A chunk id
     * repeated in the response keeps only its first tag.
     *
     * @param folder the proposal parse folder whose catalog should be stamped
     * @param decision the gate decision carrying the per-chunk tags
     * @throws IOException if the catalog cannot be read or written
     */
    public void stampCatalog(final ProposalParseFolder folder, final GateDecision decision) throws IOException
    {
        if (decision.chunkTags().isEmpty()) {
            return;
        }
        final Path catalogFile = folder.catalogFile();
        if (!Files.isRegularFile(catalogFile)) {
            return;
        }
        final ProposalCatalog catalog = ProposalCatalog.read(catalogFile);
        final Set<String> catalogIds = new HashSet<>();
        for (final ProposalCatalog.Chunk chunk : catalog.chunks()) {
            catalogIds.add(chunk.id());
        }
        final Set<String> stamped = new HashSet<>();
        boolean changed = false;
        for (final ChunkTag tag : decision.chunkTags()) {
            final String id = tag.chunkId();
            if (!catalogIds.contains(id) || !stamped.add(id)) {
                continue;
            }
            catalog.setRubricTags(id, List.of(tag.tag()));
            catalog.setTagBasis(id, "heading");
            catalog.setTagConfidence(id, tag.confidence());
            catalog.setUncertain(id, true);
            changed = true;
        }
        if (changed) {
            catalog.write();
        }
    }

    private GateDecision callGate(final LLMClient client, final GateInput input,
        final List<CatalogHeading> catalogHeadings) throws IOException
    {
        final String system = PipelinePrompts.load(PipelinePrompts.IS_PROTOCOL_SYSTEM);
        final String schema = PipelinePrompts.load(PipelinePrompts.IS_PROTOCOL_SCHEMA);
        final long maxTokens = GATE_BASE_TOKENS + GATE_TOKENS_PER_CHUNK * catalogHeadings.size();
        final LLMRequestOptions options = LLMRequestOptions.builder()
            .maxOutputTokens(maxTokens)
            .jsonSchema(SCHEMA_NAME, schema)
            .build();
        final String userMessage = buildUserMessage(input, catalogHeadings);
        GateDecision decision = requestOnce(client, system, userMessage, options);
        if (decision == null) {
            final String retry = userMessage + "\n\n# Correction\n\nYour previous response was not a valid JSON "
                + "object matching the required schema. Return only the JSON object with keys is_protocol, "
                + "confidence, reasoning and chunk_tags.";
            decision = requestOnce(client, system, retry, options);
        }
        if (decision == null) {
            LOGGER.warn("Gate response could not be parsed after a re-ask; failing open");
            return GateDecision.failOpen();
        }
        return decision;
    }

    private GateDecision requestOnce(final LLMClient client, final String system, final String userMessage,
        final LLMRequestOptions options) throws IOException
    {
        // Transport / HTTP errors propagate so the extract servlet can return them to the UI.
        // Unparseable replies still return null and trigger the caller's one re-ask / fail-open path.
        final String reply = client.chat(system, List.of(new LLMMessage("user", userMessage)), options);
        return parse(reply);
    }

    private static String buildUserMessage(final GateInput input, final List<CatalogHeading> catalogHeadings)
    {
        final String structure = PipelinePrompts.load(PipelinePrompts.PROTOCOL_STRUCTURE);
        final StringBuilder message = new StringBuilder();
        message.append("## PROTOCOL_STRUCTURE\n\n").append(structure.strip())
            .append("\n\n## INPUT (").append(input.header()).append(") (untrusted data)\n\n")
            .append(input.text());
        // The catalog-outline INPUT already carries the chunkNNN: heading lines, so a CATALOG block would only
        // repeat them; every other chunked form appends CATALOG so chunk tagging always has its targets.
        if (!catalogHeadings.isEmpty() && !INPUT_CATALOG_OUTLINE.equals(input.header())) {
            message.append("\n\n## CATALOG (untrusted data)\n\n").append(catalogLines(catalogHeadings));
        }
        return message.toString();
    }

    private static String catalogLines(final List<CatalogHeading> catalogHeadings)
    {
        final StringBuilder builder = new StringBuilder();
        for (final CatalogHeading entry : catalogHeadings) {
            builder.append(entry.chunkId()).append(": ").append(entry.heading()).append('\n');
        }
        return builder.toString().strip();
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
        final List<ChunkTag> chunkTags = parseChunkTags(json);
        return new GateDecision(isProtocol, confidence, reasoning, json.toString(), false, chunkTags);
    }

    private static List<ChunkTag> parseChunkTags(final JsonObject json)
    {
        final List<ChunkTag> tags = new ArrayList<>();
        if (!json.containsKey("chunk_tags")
            || json.get("chunk_tags").getValueType() != JsonValue.ValueType.ARRAY) {
            return tags;
        }
        for (final JsonValue value : json.getJsonArray("chunk_tags")) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                continue;
            }
            final JsonObject entry = value.asJsonObject();
            final String chunkId = entry.getString("chunk_id", "");
            final String tag = entry.getString("tag", "");
            if (chunkId.isBlank() || tag.isBlank()) {
                continue;
            }
            tags.add(new ChunkTag(chunkId, tag, clamp(readDouble(entry, "confidence"))));
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
     * Select the gate input following the chunker's recorded routing decision: (1) an unchunked (small)
     * document is sent whole — the only form for whole-document mode; (2) a chunked document is represented by
     * its detected table of contents alone when the outline recorded a TOC (no first chunk — the TOC already
     * maps the whole structure), (3) else by the catalog outline ({@code chunkNNN: heading} lines) plus the
     * first catalog chunk's text.
     * The document head is never sent: a chunked document with neither a TOC nor a catalog yields {@code null},
     * which the caller treats as fail-open.
     *
     * @param folder the proposal parse folder, carrying the chunker's {@code chunked} decision
     * @param outline the already-read outline, or {@code null} when absent/unreadable
     * @param catalogHeadings the {@code chunkNNN: heading} correspondence from the catalog, empty when unchunked
     * @return the selected input, or {@code null} when none can be built
     */
    private GateInput selectInput(final ProposalParseFolder folder, final ParseOutline outline,
        final List<CatalogHeading> catalogHeadings)
    {
        if (!folder.isChunked()) {
            final String document = readDocument(folder.documentMarkdown());
            return document == null ? null : new GateInput(INPUT_FULL_DOCUMENT, document);
        }
        final String toc = tocText(outline);
        if (toc != null) {
            // A TOC already maps the whole document's structure — the first chunk adds nothing here.
            return new GateInput(INPUT_TOC, toc);
        }
        if (!catalogHeadings.isEmpty()) {
            final String firstChunk = readFirstChunk(folder);
            return new GateInput(INPUT_CATALOG_OUTLINE, withFirstChunk(catalogLines(catalogHeadings), firstChunk));
        }
        return null;
    }

    /**
     * The document's detected table of contents from the {@code toc} array in {@code outline.json}.
     *
     * @param outline the already-read outline, or {@code null}
     * @return the TOC text, one entry per line, or {@code null} when no TOC was recorded
     */
    private static String tocText(final ParseOutline outline)
    {
        if (outline == null || outline.toc().isEmpty()) {
            return null;
        }
        return String.join("\n", outline.toc());
    }

    private static String withFirstChunk(final String structure, final String firstChunk)
    {
        if (firstChunk == null || firstChunk.isBlank()) {
            return structure;
        }
        return structure + "\n\n" + FIRST_CHUNK_HEADER + "\n\n" + firstChunk.strip();
    }

    /**
     * Read the text of the document's first catalog chunk (the entry with the lowest {@code chunk_id}).
     *
     * @param folder the proposal parse folder
     * @return the first chunk's Markdown text, or {@code null} when the catalog or chunk file is unavailable
     */
    private static String readFirstChunk(final ProposalParseFolder folder)
    {
        try {
            final ProposalCatalog catalog = ProposalCatalog.read(folder.catalogFile());
            ProposalCatalog.Chunk first = null;
            for (final ProposalCatalog.Chunk chunk : catalog.chunks()) {
                if (chunk.file().isBlank()) {
                    continue;
                }
                if (first == null || chunk.id().compareTo(first.id()) < 0) {
                    first = chunk;
                }
            }
            if (first == null) {
                return null;
            }
            final Path chunkFile = folder.chunksDir().resolve(first.file());
            return Files.isRegularFile(chunkFile) ? Files.readString(chunkFile, StandardCharsets.UTF_8) : null;
        } catch (final IOException e) {
            LOGGER.warn("Could not read first catalog chunk under {}: {}", folder.chunksDir(), e.getMessage());
            return null;
        }
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
     * One catalog chunk's {@code chunkNNN: heading} correspondence, used both as the chunk-tagging target sent
     * to the gate and as the catalog-outline INPUT for a chunked document with no detected TOC.
     *
     * @param chunkId the chunk identifier (e.g. {@code chunk001})
     * @param heading the chunk's heading(s), joined into one line
     */
    private record CatalogHeading(String chunkId, String heading)
    {
    }

    /**
     * One chunk's rubric guess from the gate's coarse tagging pass over the {@code chunkNNN: heading} catalog.
     *
     * @param chunkId the chunk identifier the tag applies to (e.g. {@code chunk001})
     * @param tag the single most probable rubric tag (B.1–B.17)
     * @param confidence the model's confidence in this one chunk's tag, in {@code [0, 1]}
     */
    public record ChunkTag(String chunkId, String tag, double confidence)
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
     * @param chunkTags the per-chunk rubric guesses, empty when none were given or the call failed open
     */
    public record GateDecision(boolean isProtocol, double confidence, String reasoning, String rawResponse,
        boolean failedOpen, List<ChunkTag> chunkTags)
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
