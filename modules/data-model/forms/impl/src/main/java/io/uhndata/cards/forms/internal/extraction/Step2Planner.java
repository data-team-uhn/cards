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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.uhndata.cards.forms.internal.extraction.ProposalCatalog.Chunk;

/**
 * Plans the Stage 1.2 targeted-extraction calls. Targeted batches group pending fields by their shared candidate
 * chunks — the chunks that hint the field (per the {@code extraction_hints} join) and have not yet been
 * examined for it — so each call carries only the chunks relevant to its fields and fields with the same
 * candidate set share one call. The sweep is the fallback: for fields still missing after the targeted pass, it
 * gathers every chunk not yet examined and not confidently excluded (only a content-based tag that does not
 * match the field can exclude it — {@link FieldTagMap#isWildcard}) into one blind batch. Token-bounding of each
 * batch's chunks is left to the caller, which holds the chunk texts.
 *
 * @version $Id$
 */
public final class Step2Planner
{
    private Step2Planner()
    {
        // Utility class, never instantiated.
    }

    /**
     * Group pending fields into targeted batches by their shared candidate chunks.
     *
     * @param chunks the catalog chunks, with their stamped tags and hints
     * @param pendingFields the field keys still needing an answer
     * @param tracker the coverage tracker, consulted to skip already-examined chunks
     * @return the targeted batches, each carrying the fields that share a candidate chunk set
     */
    public static List<Batch> planTargeted(final List<Chunk> chunks, final List<String> pendingFields,
        final LlmCallTracker tracker)
    {
        final Map<String, List<String>> candidatesByField = new LinkedHashMap<>();
        for (final String field : pendingFields) {
            final List<String> candidates = candidateChunks(chunks, field, tracker);
            if (!candidates.isEmpty()) {
                candidatesByField.put(field, candidates);
            }
        }
        return groupByCandidateSet(candidatesByField);
    }

    /**
     * Plan the fallback sweep for fields still missing after the targeted pass.
     *
     * @param chunks the catalog chunks, with their stamped tags and hints
     * @param stillPending the field keys still missing after targeted extraction
     * @param tracker the coverage tracker, consulted to skip already-examined chunks
     * @return a single-batch list carrying all still-pending fields over the sweep chunks, or empty when there
     *         is nothing left to read
     */
    public static List<Batch> planSweep(final List<Chunk> chunks, final List<String> stillPending,
        final LlmCallTracker tracker)
    {
        if (stillPending.isEmpty()) {
            return List.of();
        }
        final Set<String> sweepIds = new LinkedHashSet<>();
        for (final Chunk chunk : chunks) {
            if (isSweepCandidate(chunk, stillPending, tracker)) {
                sweepIds.add(chunk.id());
            }
        }
        return sweepIds.isEmpty() ? List.of() : List.of(new Batch(stillPending, new ArrayList<>(sweepIds)));
    }

    private static boolean isSweepCandidate(final Chunk chunk, final List<String> stillPending,
        final LlmCallTracker tracker)
    {
        final boolean wildcard =
            FieldTagMap.isWildcard(chunk.rubricTags(), chunk.tagBasis(), chunk.uncertain());
        for (final String field : stillPending) {
            final boolean excluded = !wildcard && !chunk.extractionHints().contains(field);
            final boolean examined = tracker.examined(field).contains(chunk.id());
            if (!excluded && !examined) {
                return true;
            }
        }
        return false;
    }

    private static List<String> candidateChunks(final List<Chunk> chunks, final String field,
        final LlmCallTracker tracker)
    {
        final Set<String> examined = new HashSet<>(tracker.examined(field));
        final List<String> candidates = new ArrayList<>();
        for (final Chunk chunk : chunks) {
            if (chunk.extractionHints().contains(field) && !examined.contains(chunk.id())) {
                candidates.add(chunk.id());
            }
        }
        return candidates;
    }

    private static List<Batch> groupByCandidateSet(final Map<String, List<String>> candidatesByField)
    {
        final Map<String, List<String>> fieldsByKey = new LinkedHashMap<>();
        final Map<String, List<String>> chunksByKey = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : candidatesByField.entrySet()) {
            final String key = String.join(",", entry.getValue());
            fieldsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry.getKey());
            chunksByKey.putIfAbsent(key, entry.getValue());
        }
        final List<Batch> batches = new ArrayList<>();
        for (final Map.Entry<String, List<String>> entry : fieldsByKey.entrySet()) {
            batches.add(new Batch(entry.getValue(), chunksByKey.get(entry.getKey())));
        }
        return batches;
    }

    /**
     * One planned extraction call: the fields to ask for and the chunk ids to send.
     *
     * @param fields the field keys this call extracts
     * @param chunkIds the chunk ids whose full text this call sends
     */
    public record Batch(List<String> fields, List<String> chunkIds)
    {
    }
}
