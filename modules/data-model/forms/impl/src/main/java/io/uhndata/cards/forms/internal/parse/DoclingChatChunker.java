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
package io.uhndata.cards.forms.internal.parse;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks which generation of an answer's {@code Chunks/} tree is current, so downstream work can notice that a
 * newer parse has superseded it.
 * <p>
 * The tree itself is no longer requested from here. It arrives with the Markdown from the daemon's
 * {@code POST /parse} call and is written by {@link SimpleDocumentParser} — see {@link DoclingParseClient} for
 * why that endpoint replaced the old path-based {@code POST /chunk} request and its local-Python CLI fallback.
 * What remains is the bookkeeping: {@link #markChunksWritten(Path)} after a tree is written,
 * {@link #invalidateChunking(Path)} after a failed parse, and {@link #isSummarizationCurrent(Path, long)} for
 * catalog summarization to abort when it has been overtaken.
 * </p>
 * <p>
 * This is independent of the per-answer field-extraction chunks produced by {@link MarkdownChunker}; both
 * exist, for different purposes. Catalog summarization is deliberately not started here — it runs only after
 * the extraction phase has finished and saved, so the two LLM workloads never race on the shared catalog.
 * </p>
 *
 * @version $Id$
 */
public final class DoclingChatChunker
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingChatChunker.class);

    /** Per-answer generation counters used to discard stale asynchronous chunk results. */
    private static final ConcurrentHashMap<String, AtomicLong> CHUNK_GENERATIONS = new ConcurrentHashMap<>();

    private DoclingChatChunker()
    {
        // Utility class, never instantiated.
    }

    /**
     * Invalidate any in-flight chat chunking for an answer folder, for example after a parse failure.
     * Callers should also clear on-disk chat chunk output via {@link ParsedMarkdownStore#clearChunks(String)}.
     *
     * @param answerDir the absolute parse output folder of the answer
     */
    public static void invalidateChunking(final Path answerDir)
    {
        if (answerDir == null) {
            return;
        }
        nextGeneration(answerDir);
        LOGGER.debug("Invalidated in-flight Docling chat chunking for {}", answerDir);
    }

    /**
     * The current chunk generation for an answer folder. Downstream work that must abort when a newer chunking
     * run supersedes this one (for example catalog summarization after extraction) should capture this value
     * when it starts and poll {@link #isSummarizationCurrent(Path, long)}.
     *
     * @param answerDir the absolute parse output folder of the answer
     * @return the current generation, or {@code 0} when no chunking has been requested for this folder yet
     */
    public static long currentGeneration(final Path answerDir)
    {
        if (answerDir == null) {
            return 0L;
        }
        final AtomicLong current = CHUNK_GENERATIONS.get(folderKey(answerDir));
        return current == null ? 0L : current.get();
    }

    /**
     * Whether a summarization job started for the given chunk generation is still current. Summarization should
     * abort when this returns {@code false}, for example after a re-parse or a newer chunking request.
     *
     * @param answerDir the absolute parse output folder of the answer
     * @param generation the generation captured when scheduling summarization
     * @return {@code true} when no newer chunking has superseded this generation
     */
    public static boolean isSummarizationCurrent(final Path answerDir, final long generation)
    {
        return isCurrentGeneration(folderKey(answerDir), generation);
    }

    /**
     * Request chat chunking for an answer folder, returning immediately. Does nothing when chunking is
     * disabled, the folder is {@code null}, or a single parsed {@code .md} file cannot be resolved under
     * it (MVP: one proposal file per answer). The chunker always uses its own single hardcoded tokenizer
     * model, so no model is passed here. Any failure is logged, never thrown.
     *
     * @param answerDir the absolute parse output folder of the answer, as produced by
     *            {@link ParsedMarkdownStore#resolveAnswerDir(String)}
     * @param minStructureTokens the small-document threshold (the active LLM model's
     *            {@code wholeDocumentTokenLimit}): documents under this many estimated tokens are not chunked
     *            and are recorded as {@code chunked: false} in {@code outline.json}; pass {@code 0} or a
     *            negative value to let the chunker use its own default
     */
    public static long markChunksWritten(final Path answerDir)
    {
        if (answerDir == null) {
            return 0L;
        }
        final long generation = nextGeneration(answerDir);
        LOGGER.debug("Chunk tree generation for {} advanced to {}", answerDir, generation);
        return generation;
    }

    private static long nextGeneration(final Path answerDir)
    {
        return CHUNK_GENERATIONS.computeIfAbsent(folderKey(answerDir), ignored -> new AtomicLong(0L))
            .incrementAndGet();
    }

    private static boolean isCurrentGeneration(final String folder, final long generation)
    {
        final AtomicLong current = CHUNK_GENERATIONS.get(folder);
        return current != null && current.get() == generation;
    }

    private static String folderKey(final Path answerDir)
    {
        return answerDir.toAbsolutePath().normalize().toString();
    }
}
