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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import io.uhndata.cards.forms.internal.extraction.ProposalCatalog.Chunk;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldSpec;

/**
 * Assembles the per-document blocks of the Stage 1.1 intake user message: the SCHEMA (the extraction fields'
 * rules), the CATALOG (one {@code sNNN: heading} line per chunk, plus an opening snippet for chunks not sent
 * in full), and the CHUNK (the excerpt sent in full, each chunk prefixed with a {@code [chunk:sNNN]} marker
 * with its {@code <!-- page: N-->} markers preserved). Chunk selection follows the design: reference lists are
 * dropped, the whole document is sent when it fits the budget, otherwise chunks are taken in document order
 * (front first) up to the chunk budget. The set of chunk ids sent in full is exposed so the caller can stamp
 * {@code tag_basis} and record coverage.
 *
 * @version $Id$
 */
public final class IntakePayload
{
    /** When the selectable document fits this many tokens, send it whole and omit CATALOG snippets. */
    static final int WHOLE_DOCUMENT_TOKEN_LIMIT = 22000;

    /** Soft cap on the CHUNK excerpt size, in tokens; a chunk that starts under it is included whole. */
    static final int CHUNK_TOKEN_BUDGET = 20000;

    /** Opening-text snippet length, in characters, for chunks listed in CATALOG but not sent in CHUNK. */
    static final int SNIPPET_CHARS = 150;

    /** Fraction of the document, from the end, in which a references heading marks a droppable reference list. */
    private static final double REFERENCE_TAIL_FRACTION = 0.6;

    private static final int CHARS_PER_TOKEN = 4;

    private static final Pattern REFERENCE_HEADING =
        Pattern.compile("(?i)\\b(references|bibliography|works cited|literature cited)\\b");

    private final String userMessage;

    private final List<String> fullTextChunkIds;

    private IntakePayload(final String message, final List<String> fullText)
    {
        this.userMessage = message;
        this.fullTextChunkIds = fullText;
    }

    /**
     * Build the intake user message from the catalog chunks and their text.
     *
     * @param chunks the catalog chunks in document order
     * @param chunkTexts a map from chunk id to that chunk's full Markdown text
     * @param fields the extraction fields whose rules make up the SCHEMA block
     * @param categoriesDocument the Research Study Description taxonomy (STUDY_CATEGORIES block)
     * @return the assembled payload
     */
    public static IntakePayload build(final List<Chunk> chunks, final Map<String, String> chunkTexts,
        final List<FieldSpec> fields, final String categoriesDocument)
    {
        final Set<String> fullText = selectFullTextChunks(chunks, chunkTexts);
        final StringBuilder message = new StringBuilder();
        appendBlock(message, "STUDY_CATEGORIES", StringUtils.trimToEmpty(categoriesDocument));
        appendBlock(message, "PROTOCOL_STRUCTURE_GLOSSARY",
            PipelinePrompts.load(PipelinePrompts.PROTOCOL_STRUCTURE_GLOSSARY).strip());
        appendBlock(message, "SCHEMA", schemaBlock(fields));
        appendBlock(message, "CATALOG", catalogBlock(chunks, chunkTexts, fullText));
        appendBlock(message, "CHUNK (untrusted data)", chunkBlock(chunks, chunkTexts, fullText));
        final List<String> ordered = new ArrayList<>();
        for (final Chunk chunk : chunks) {
            if (fullText.contains(chunk.id())) {
                ordered.add(chunk.id());
            }
        }
        return new IntakePayload(message.toString(), ordered);
    }

    /**
     * The assembled intake user message.
     *
     * @return the user message
     */
    public String userMessage()
    {
        return this.userMessage;
    }

    /**
     * The chunk ids sent in full in the CHUNK, in document order — the {@code tag_basis="fulltext"} chunks
     * and the coverage set recorded for the intake call.
     *
     * @return the full-text chunk ids
     */
    public List<String> fullTextChunkIds()
    {
        return this.fullTextChunkIds;
    }

    private static Set<String> selectFullTextChunks(final List<Chunk> chunks,
        final Map<String, String> chunkTexts)
    {
        final Set<String> selected = new LinkedHashSet<>();
        final int total = chunks.size();
        int budgetTokens = 0;
        for (int index = 0; index < total; index++) {
            final Chunk chunk = chunks.get(index);
            if (isReferenceList(chunk, index, total)) {
                continue;
            }
            budgetTokens += tokenEstimate(chunkTexts.get(chunk.id()));
        }
        final boolean wholeDocument = budgetTokens <= WHOLE_DOCUMENT_TOKEN_LIMIT;
        int used = 0;
        for (int index = 0; index < total; index++) {
            final Chunk chunk = chunks.get(index);
            if (isReferenceList(chunk, index, total)) {
                continue;
            }
            final int chunkTokens = tokenEstimate(chunkTexts.get(chunk.id()));
            if (wholeDocument || used < CHUNK_TOKEN_BUDGET) {
                selected.add(chunk.id());
                used += chunkTokens;
            }
        }
        return selected;
    }

    private static boolean isReferenceList(final Chunk chunk, final int index, final int total)
    {
        return REFERENCE_HEADING.matcher(String.join(", ", chunk.heading())).find()
            && index >= (int) (total * REFERENCE_TAIL_FRACTION);
    }

    private static String schemaBlock(final List<FieldSpec> fields)
    {
        final StringBuilder builder = new StringBuilder();
        for (final FieldSpec field : fields) {
            builder.append(field.key()).append(":\n");
            if (StringUtils.isNotBlank(field.rules())) {
                builder.append(field.rules().strip()).append('\n');
            }
            builder.append('\n');
        }
        return builder.toString().strip();
    }

    private static String catalogBlock(final List<Chunk> chunks, final Map<String, String> chunkTexts,
        final Set<String> fullText)
    {
        final StringBuilder builder = new StringBuilder();
        for (final Chunk chunk : chunks) {
            builder.append(chunk.id()).append(": ").append(String.join(", ", chunk.heading()));
            if (!fullText.contains(chunk.id())) {
                final String snippet = snippet(chunkTexts.get(chunk.id()));
                if (!snippet.isBlank()) {
                    builder.append("  — ").append(snippet);
                }
            }
            builder.append('\n');
        }
        return builder.toString().strip();
    }

    private static String chunkBlock(final List<Chunk> chunks, final Map<String, String> chunkTexts,
        final Set<String> fullText)
    {
        final StringBuilder builder = new StringBuilder();
        for (final Chunk chunk : chunks) {
            if (fullText.contains(chunk.id())) {
                builder.append("[chunk:").append(chunk.id()).append("]\n")
                    .append(StringUtils.trimToEmpty(chunkTexts.get(chunk.id()))).append("\n\n");
            }
        }
        return builder.toString().strip();
    }

    private static String snippet(final String text)
    {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        final String collapsed = text.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= SNIPPET_CHARS ? collapsed : collapsed.substring(0, SNIPPET_CHARS) + "…";
    }

    private static void appendBlock(final StringBuilder builder, final String title, final String content)
    {
        builder.append("## ").append(title).append("\n\n").append(content).append("\n\n");
    }

    private static int tokenEstimate(final String text)
    {
        return text == null ? 0 : text.length() / CHARS_PER_TOKEN;
    }
}
