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
package io.uhndata.cards.forms.internal.summary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.internal.parse.DoclingChatChunker;
import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMClientFactory;

/**
 * Fills in the empty {@code summary} fields of a proposal answer's chunk catalog by sending each chunk's
 * Markdown to the active LLM. The chunker writes a single {@code Chunks/} folder (MVP: one proposal file
 * per answer) holding {@code Chunk-*.md} files and a {@code catalog.json} whose entries start with blank
 * summaries. This service walks the catalog after the extraction phase has finished and saved, and fills
 * each empty summary from the chunk's own text; already summarized entries are left untouched, so the walk
 * is idempotent and safely resumable. A failure on any one chunk leaves that summary empty and is logged; it
 * never aborts the rest of the answer.
 * <p>
 * All LLM traffic goes through the shared {@link LLMClient}, so it is logged and configured centrally like the
 * rest of the platform's LLM use. Summarization is scheduled explicitly after extraction saves (see
 * {@link #scheduleAfterExtraction(Path)}), never from chunking completion, so the two workloads do not race on
 * the shared catalog or the LLM.
 * </p>
 *
 * @version $Id$
 */
@Component(service = CatalogSummarizationService.class, immediate = true)
public class CatalogSummarizationService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSummarizationService.class);

    /** Name of the answer subfolder holding the chunk files, catalog and outline. */
    private static final String CHUNKS_DIRNAME = "Chunks";

    /** The catalog file name inside the chunks folder. */
    private static final String CATALOG_NAME = "catalog.json";

    /** Blank line separating an instruction from its content in a prompt. */
    private static final String PARAGRAPH_BREAK = "\n\n";

    /** Shared role prompt establishing the summarizer's contract. */
    private static final String SYSTEM_PROMPT =
        "You are a precise document summarizer. Describe what the provided material contains, capturing its "
        + "specific items, topics and data. Use ONLY the material given: do not invent information, do not "
        + "answer questions about it, and do not add commentary. Respond with prose only, with no markdown "
        + "headings and no preamble such as 'Here is the summary'. Do not include any thinking or reasoning "
        + "in the reply — output only the summary itself.";

    /** Instruction for summarizing one chunk from its Markdown text (~300-600 tokens). */
    private static final String CHUNK_INSTRUCTION =
        "Summarize the following chunk of a study document in approximately 300-600 tokens, capturing the "
        + "specific items, topics and data it covers.";

    /**
     * Generation sentinel for {@link #summarize(Path)} when no chunk-generation guard is needed (manual or test
     * invocation).
     */
    private static final long NO_GENERATION_GUARD = -1L;

    /** Background pool for post-extraction summarization so the extract servlet can return promptly. */
    private static final ExecutorService SUMMARIZATION_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        final Thread thread = new Thread(runnable, "catalog-summarization");
        thread.setDaemon(true);
        return thread;
    });

    @Reference
    private LLMClientFactory llmClientFactory;

    /**
     * Schedule catalog summarization for an answer folder after the extraction phase has finished and saved.
     * Returns immediately; the walk runs asynchronously. No-ops when {@code answerDir} is {@code null} or has no
     * chunk catalog. Already-summarized entries are left untouched, so it is safe to call on skip paths as well.
     *
     * @param answerDir the absolute parse output folder of the answer
     */
    public void scheduleAfterExtraction(final Path answerDir)
    {
        if (answerDir == null) {
            return;
        }
        final Path catalogFile = answerDir.resolve(CHUNKS_DIRNAME).resolve(CATALOG_NAME);
        if (!Files.isRegularFile(catalogFile)) {
            LOGGER.debug("No chunk catalog under {}; skipping post-extraction summarization", answerDir);
            return;
        }
        final long generation = DoclingChatChunker.currentGeneration(answerDir);
        LOGGER.info("Scheduling catalog summarization after extraction for {} (generation {})",
            answerDir, generation);
        SUMMARIZATION_EXECUTOR.submit(() -> this.summarizeQuietly(answerDir, generation));
    }

    /**
     * Fill every empty summary in the chunk catalogs under an answer folder. Already summarized entries are
     * left untouched, so this is idempotent and safely resumable. A failure on any one chunk leaves that
     * summary empty and is logged; it never aborts the rest of the answer.
     *
     * @param answerDir the absolute parse output folder of the answer
     * @throws IOException if the active LLM client cannot be resolved, or the answer folder cannot be listed
     */
    public void summarize(final Path answerDir) throws IOException
    {
        this.summarize(answerDir, NO_GENERATION_GUARD);
    }

    /**
     * Fill every empty summary in the chunk catalogs under an answer folder, aborting when the chunk
     * generation is no longer current. See {@link #summarize(Path)} for traversal semantics.
     *
     * @param answerDir the absolute parse output folder of the answer
     * @param generation the chunk generation that triggered this run; values {@code >= 0} enable staleness checks
     * @throws IOException if the active LLM client cannot be resolved, or the answer folder cannot be listed
     */
    public void summarize(final Path answerDir, final long generation) throws IOException
    {
        if (answerDir == null) {
            return;
        }
        if (this.isStale(answerDir, generation)) {
            LOGGER.warn("Skipping stale summarization for {}", answerDir);
            return;
        }
        final List<Path> catalogFiles = findChunkCatalogs(answerDir);
        if (catalogFiles.isEmpty()) {
            LOGGER.warn("No chunk catalogs to summarize under {}", answerDir);
            return;
        }
        final LLMClient client = this.llmClientFactory.getActiveClient();
        LOGGER.info("START summarizing {} chunk catalog(s) under {} (generation {})",
            catalogFiles.size(), answerDir, generation);
        int done = 0;
        for (final Path catalogFile : catalogFiles) {
            if (this.isStale(answerDir, generation)) {
                LOGGER.warn("STOP: summarization for {} superseded by a newer generation", answerDir);
                return;
            }
            try {
                this.summarizeCatalog(client, answerDir, catalogFile, generation);
                done++;
            } catch (final IOException e) {
                LOGGER.warn("Could not summarize catalog {} under {}: {}", catalogFile, answerDir,
                    e.getMessage(), e);
            }
        }
        LOGGER.info("DONE summarizing chunk catalogs under {}: {}/{} catalog(s) processed",
            answerDir, done, catalogFiles.size());
    }

    /**
     * Summarize every unsummarized chunk in the catalog, then write the catalog back.
     */
    private void summarizeCatalog(final LLMClient client, final Path answerDir, final Path catalogFile,
        final long generation) throws IOException
    {
        final Path chunksDir = catalogFile.getParent();
        final SummaryCatalog catalog = SummaryCatalog.read(catalogFile);
        final List<String> chunkIds = catalog.ids();
        LOGGER.info("Catalog {}: summarizing {} chunk(s) {}",
            chunksDir.getFileName(), chunkIds.size(), chunkIds);
        for (final String chunkId : chunkIds) {
            if (this.isStale(answerDir, generation)) {
                return;
            }
            this.summarizeChunk(client, answerDir, chunksDir, chunkId, catalog, generation);
        }
        if (this.isStale(answerDir, generation)) {
            return;
        }
        catalog.write();
    }

    /**
     * Summarize one chunk from the Markdown file named by its catalog entry, unless it is already summarized,
     * marked as appendix/backmatter, or its text cannot be read.
     */
    private void summarizeChunk(final LLMClient client, final Path answerDir, final Path chunksDir,
        final String chunkId, final SummaryCatalog catalog, final long generation)
    {
        final String label = "chunk " + chunkId;
        if (catalog.isAppendix(chunkId)) {
            LOGGER.debug("{}: appendix/backmatter, skipping summarization", label);
            return;
        }
        if (catalog.isSummarized(chunkId)) {
            LOGGER.debug("{}: already summarized, skipping", label);
            return;
        }
        final String fileName = catalog.fileOf(chunkId);
        if (fileName.isBlank()) {
            LOGGER.warn("{} in {} has no file reference; leaving its summary empty", label, chunksDir);
            return;
        }
        if (this.isStale(answerDir, generation)) {
            return;
        }
        final String text = readChunkText(chunksDir.resolve(fileName));
        if (text.isBlank()) {
            LOGGER.warn("{} in {} has no readable text; leaving its summary empty", label, chunksDir);
            return;
        }
        final String summary = this.callLlm(client, label, CHUNK_INSTRUCTION + PARAGRAPH_BREAK + text);
        if (summary != null) {
            catalog.setSummary(chunkId, summary);
        }
    }

    /**
     * Send one summarization request, returning the trimmed reply, or {@code null} when the request fails or
     * the model returns nothing usable. Failures are logged and swallowed so one bad summary does not abort the
     * surrounding traversal.
     *
     * @param client the active LLM client
     * @param label a short human-readable label identifying what is being summarized, for the logs
     * @param userMessage the prompt to send
     * @return the trimmed reply, or {@code null} when nothing usable came back
     */
    private String callLlm(final LLMClient client, final String label, final String userMessage)
    {
        LOGGER.info("LLM summary REQUEST for {} ({} chars of input)", label, userMessage.length());
        try {
            final String reply = client.chat(SYSTEM_PROMPT, userMessage);
            if (reply == null || reply.isBlank()) {
                LOGGER.warn("LLM summary for {} came back empty", label);
                return null;
            }
            final String trimmed = reply.trim();
            LOGGER.info("LLM summary RECEIVED for {} ({} chars)", label, trimmed.length());
            return trimmed;
        } catch (final IOException e) {
            LOGGER.warn("LLM summary request FAILED for {}: {}", label, e.getMessage(), e);
            return null;
        }
    }

    private void summarizeQuietly(final Path answerDir, final long generation)
    {
        try {
            this.summarize(answerDir, generation);
        } catch (final IOException e) {
            LOGGER.warn("Could not summarize chunk catalogs under {}: {}", answerDir, e.getMessage());
        }
    }

    private boolean isStale(final Path answerDir, final long generation)
    {
        return generation >= 0 && !DoclingChatChunker.isSummarizationCurrent(answerDir, generation);
    }

    /**
     * Locate the answer's {@code Chunks/catalog.json}, when the chunker has produced one. Returned as a
     * (0- or 1-element) list so the caller's traversal logic stays unchanged from when several per-source
     * catalogs could exist (post-MVP multi-file).
     */
    private static List<Path> findChunkCatalogs(final Path answerDir)
    {
        final Path catalogFile = answerDir.resolve(CHUNKS_DIRNAME).resolve(CATALOG_NAME);
        return Files.isRegularFile(catalogFile) ? List.of(catalogFile) : List.of();
    }

    private static String readChunkText(final Path chunkFile)
    {
        if (!Files.isRegularFile(chunkFile)) {
            return "";
        }
        try {
            return Files.readString(chunkFile, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            LOGGER.warn("Could not read chunk file {}: {}", chunkFile, e.getMessage());
            return "";
        }
    }
}
