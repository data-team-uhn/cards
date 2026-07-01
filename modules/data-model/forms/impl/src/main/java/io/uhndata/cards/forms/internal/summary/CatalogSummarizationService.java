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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.internal.parse.DoclingChatChunker;
import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMClientFactory;

/**
 * Fills in the empty {@code summary} fields of a proposal answer's {@code ChatChunks} catalog tree, bottom-up,
 * by sending the chunked Markdown to the active LLM. The chat chunker (see {@link DoclingChatChunker}) writes
 * the tree with every summary blank and with sections as the deepest level: each {@code Section*.md} file
 * directly under a {@code File-*} folder holds one section unit. This service walks the tree after chunking
 * completes and summarizes from the leaves up: each section is summarized from its {@code .md} text, and each
 * file from the summaries of its sections. A file is only summarized once all of its sections are, so the
 * file summary is built from real section summaries rather than blanks.
 * <p>
 * All LLM traffic goes through the shared {@link LLMClient}, so it is logged and configured centrally like the
 * rest of the platform's LLM use. The service registers itself as the summarization hook on
 * {@link DoclingChatChunker} while active, which is what couples chunking completion to summarization without
 * the chunker depending on this bundle's services directly.
 * </p>
 *
 * @version $Id$
 */
@Component(service = CatalogSummarizationService.class, immediate = true)
public class CatalogSummarizationService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSummarizationService.class);

    /** The {@code ChatChunks} subfolder of an answer's parse folder, holding the catalog tree. */
    private static final String CHAT_CHUNKS_DIRNAME = "ChatChunks";

    /** The per-folder catalog file name, identical at every level of the tree. */
    private static final String CATALOG_NAME = "catalog.json";

    /** Blank line separating an instruction from its content, and successive child summaries, in a prompt. */
    private static final String PARAGRAPH_BREAK = "\n\n";

    /** Shared role prompt establishing the summarizer's contract for every level. */
    private static final String SYSTEM_PROMPT =
        "You are a precise document summarizer. Describe what the provided material contains, capturing its "
        + "specific items, topics and data. Use ONLY the material given: do not invent information, do not "
        + "answer questions about it, and do not add commentary. Respond with prose only, with no markdown "
        + "headings and no preamble such as 'Here is the summary'.";

    /** Instruction for summarizing one leaf section from its Markdown text (~300-600 tokens). */
    private static final String SECTION_INSTRUCTION =
        "Summarize the following section of a study document in approximately 300-600 tokens, capturing the "
        + "specific items, topics and data it covers.";

    /** Instruction for summarizing one file from the summaries of its sections (~500-800 tokens). */
    private static final String FILE_INSTRUCTION =
        "Summarize what this document contains, in approximately 500-800 tokens, using the numbered section "
        + "summaries below. Give a high-level map of the items and topics it covers.";

    /**
     * Generation sentinel for {@link #summarize(Path)} when no chunk-generation guard is needed (manual or test
     * invocation).
     */
    private static final long NO_GENERATION_GUARD = -1L;

    @Reference
    private LLMClientFactory llmClientFactory;

    /**
     * Register this service as the chunker's summarization hook so completed chunking triggers summarization.
     */
    @Activate
    public void activate()
    {
        DoclingChatChunker.setSummarizationHook(this::summarizeQuietly);
        LOGGER.info("Catalog summarization service activated and registered as the chunking completion hook");
    }

    /**
     * Clear the chunker's summarization hook when this service stops.
     */
    @Deactivate
    public void deactivate()
    {
        DoclingChatChunker.setSummarizationHook(null);
        LOGGER.info("Catalog summarization service deactivated and cleared the chunking completion hook");
    }

    /**
     * Fill every empty summary in the {@code ChatChunks} tree under an answer folder, bottom-up. Already
     * summarized entries are left untouched, so this is idempotent and safely resumable. A failure on any one
     * section leaves that summary empty and is logged; it never aborts the rest of the answer.
     *
     * @param answerDir the absolute parse output folder of the answer
     * @throws IOException if the active LLM client cannot be resolved, or the root catalog cannot be read
     */
    public void summarize(final Path answerDir) throws IOException
    {
        this.summarize(answerDir, NO_GENERATION_GUARD);
    }

    /**
     * Fill every empty summary in the {@code ChatChunks} tree under an answer folder, bottom-up, aborting when
     * the chunk generation is no longer current. See {@link #summarize(Path)} for traversal semantics.
     *
     * @param answerDir the absolute parse output folder of the answer
     * @param generation the chunk generation that triggered this run; values {@code >= 0} enable staleness checks
     * @throws IOException if the active LLM client cannot be resolved, or the root catalog cannot be read
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
        final Path chatDir = answerDir.resolve(CHAT_CHUNKS_DIRNAME);
        final Path rootCatalogFile = chatDir.resolve(CATALOG_NAME);
        if (!Files.isRegularFile(rootCatalogFile)) {
            LOGGER.warn("No ChatChunks catalog to summarize under {}; expected root catalog at {}",
                answerDir, rootCatalogFile);
            return;
        }
        final LLMClient client = this.llmClientFactory.getActiveClient();
        final SummaryCatalog rootCatalog = SummaryCatalog.read(rootCatalogFile);
        final List<String> fileIds = rootCatalog.ids();
        LOGGER.info("START summarizing ChatChunks tree under {} (generation {}): {} file(s) {}",
            answerDir, generation, fileIds.size(), fileIds);
        int done = 0;
        for (final String fileEntryId : fileIds) {
            if (this.isStale(answerDir, generation)) {
                LOGGER.warn("STOP: summarization for {} superseded by a newer generation", answerDir);
                return;
            }
            try {
                this.summarizeFile(client, answerDir, chatDir, fileEntryId, rootCatalog, generation);
                rootCatalog.write();
                done++;
            } catch (final IOException e) {
                LOGGER.warn("Could not summarize file {} under {}: {}", fileEntryId, answerDir, e.getMessage(), e);
            }
        }
        LOGGER.info("DONE summarizing ChatChunks tree under {}: {}/{} file(s) processed",
            answerDir, done, fileIds.size());
    }

    private void summarizeFile(final LLMClient client, final Path answerDir, final Path chatDir,
        final String fileEntryId, final SummaryCatalog rootCatalog, final long generation) throws IOException
    {
        final Path fileDir = chatDir.resolve(fileEntryId);
        final Path fileCatalogFile = fileDir.resolve(CATALOG_NAME);
        if (!Files.isRegularFile(fileCatalogFile)) {
            LOGGER.warn("Missing file catalog {} for entry {}", fileCatalogFile, fileEntryId);
            return;
        }
        final SummaryCatalog fileCatalog = SummaryCatalog.read(fileCatalogFile);
        final List<String> sectionIds = fileCatalog.ids();
        LOGGER.info("File {}: summarizing {} section(s) {}", fileEntryId, sectionIds.size(), sectionIds);
        for (final String sectionId : sectionIds) {
            if (this.isStale(answerDir, generation)) {
                return;
            }
            this.summarizeSection(client, answerDir, fileDir, sectionId, fileCatalog, generation);
        }
        if (this.isStale(answerDir, generation)) {
            return;
        }
        fileCatalog.write();
        this.rollUpFile(client, answerDir, generation, rootCatalog, fileEntryId, fileCatalog);
    }

    /**
     * Summarize one leaf section from its {@code <sectionId>.md} text, unless it is already summarized or its
     * text cannot be read.
     */
    private void summarizeSection(final LLMClient client, final Path answerDir, final Path fileDir,
        final String sectionId, final SummaryCatalog fileCatalog, final long generation)
    {
        final String label = "section " + sectionId;
        if (fileCatalog.isSummarized(sectionId)) {
            LOGGER.debug("{}: already summarized, skipping", label);
            return;
        }
        if (this.isStale(answerDir, generation)) {
            return;
        }
        final String text = readSectionText(fileDir.resolve(sectionId + ".md"));
        if (text.isBlank()) {
            LOGGER.warn("{} in {} has no readable text; leaving its summary empty", label, fileDir);
            return;
        }
        if (this.isStale(answerDir, generation)) {
            return;
        }
        final String summary = this.callLlm(client, label, SECTION_INSTRUCTION + PARAGRAPH_BREAK + text);
        if (summary != null) {
            fileCatalog.setSummary(sectionId, summary);
        }
    }

    /**
     * Produce the file-level summary from its section summaries, once every section is summarized. Does nothing
     * when the file is already summarized; warns when sections are still missing summaries.
     */
    private void rollUpFile(final LLMClient client, final Path answerDir, final long generation,
        final SummaryCatalog rootCatalog, final String fileEntryId, final SummaryCatalog fileCatalog)
    {
        if (rootCatalog.isSummarized(fileEntryId)) {
            return;
        }
        if (!fileCatalog.allSummarized()) {
            LOGGER.warn("File {}: skipping file-level summary because not all section(s) are summarized",
                fileEntryId);
            return;
        }
        if (this.isStale(answerDir, generation)) {
            return;
        }
        final String summary = this.summarizeFromChildren(client, "file " + fileEntryId, FILE_INSTRUCTION,
            fileCatalog.orderedSummaries());
        if (summary != null) {
            rootCatalog.setSummary(fileEntryId, summary);
        }
    }

    private String summarizeFromChildren(final LLMClient client, final String label, final String instruction,
        final List<String> childSummaries)
    {
        final StringBuilder builder = new StringBuilder(instruction).append(PARAGRAPH_BREAK);
        int number = 1;
        for (final String childSummary : childSummaries) {
            builder.append(number).append(". ").append(childSummary).append(PARAGRAPH_BREAK);
            number++;
        }
        return this.callLlm(client, label, builder.toString());
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

    private void summarizeQuietly(final Path answerDir, final Long generation)
    {
        try {
            this.summarize(answerDir, generation);
        } catch (final IOException e) {
            LOGGER.warn("Could not summarize ChatChunks tree under {}: {}", answerDir, e.getMessage());
        }
    }

    private boolean isStale(final Path answerDir, final long generation)
    {
        return generation >= 0 && !DoclingChatChunker.isSummarizationCurrent(answerDir, generation);
    }

    private static String readSectionText(final Path sectionFile)
    {
        if (!Files.isRegularFile(sectionFile)) {
            return "";
        }
        try {
            return Files.readString(sectionFile, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            LOGGER.warn("Could not read section file {}: {}", sectionFile, e.getMessage());
            return "";
        }
    }
}
