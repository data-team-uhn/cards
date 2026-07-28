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
package io.uhndata.cards.forms.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.internal.parse.DoclingChatChunker;
import io.uhndata.cards.forms.internal.parse.SimpleDocumentParser;
import io.uhndata.cards.forms.internal.parse.DocumentParseException;
import io.uhndata.cards.forms.internal.parse.FileParser;
import io.uhndata.cards.forms.internal.parse.FileParserFactory;
import io.uhndata.cards.forms.internal.parse.ParsedMarkdownStore;
import io.uhndata.cards.llm.LLMConfigurationService;

/**
 * Parse uploaded files and store any parse errors in the answer note.
 * <p>
 * Files are parsed only when their content actually changes: on each commit the editor compares the answer's
 * before and after state, parses just the files that were added or whose binary content differs (or whose
 * parsed output is missing), and only then re-runs the chunker. A re-save that does not touch the
 * files is a no-op. When files or whole answers are removed, the corresponding parse output is deleted.
 * </p>
 *
 * @version $Id$
 */
public class ProposalAnswerEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProposalAnswerEditor.class);

    private static final String PROPOSAL_ANSWER_NODETYPE = "cards:ProposalAnswer";

    private static final String JCR_PRIMARY_TYPE = "jcr:primaryType";

    private static final String JCR_UUID = "jcr:uuid";

    private static final String JCR_CONTENT = "jcr:content";

    private static final String JCR_DATA = "jcr:data";

    private static final String NOTE_PROPERTY = "note";

    private static final String NT_FILE = "nt:file";

    private static final long MAX_DOCUMENT_SIZE_BYTES = 50L * 1024L * 1024L;

    private static final FileParserFactory PARSER_FACTORY = new FileParserFactory();

    private final FormUtils formUtils;

    private final LLMConfigurationService llmConfigurationService;

    private final NodeBuilder currentNodeBuilder;

    private final String nodeName;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param formUtils helper for checking form nodes
     * @param llmConfigurationService resolves the active LLM model settings, the source of the small-document
     *            chunking threshold passed to the chunker
     */
    public ProposalAnswerEditor(final NodeBuilder nodeBuilder, final FormUtils formUtils,
        final LLMConfigurationService llmConfigurationService)
    {
        this(nodeBuilder, formUtils, llmConfigurationService, null);
    }

    /**
     * Constructor that also records the name of the node the builder represents.
     *
     * @param nodeBuilder the builder for the current node
     * @param formUtils helper for checking form nodes
     * @param llmConfigurationService resolves the active LLM model settings
     * @param name the name of the node the builder represents, used as the parse output folder name
     */
    private ProposalAnswerEditor(final NodeBuilder nodeBuilder, final FormUtils formUtils,
        final LLMConfigurationService llmConfigurationService, final String name)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.formUtils = formUtils;
        this.llmConfigurationService = llmConfigurationService;
        this.nodeName = name;
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        return descend(name, after);
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        return descend(name, after);
    }

    @Override
    public Editor childNodeDeleted(final String name, final NodeState before)
    {
        cleanupDeleted(name, before);
        return null;
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
    {
        if (after != null && PROPOSAL_ANSWER_NODETYPE.equals(after.getName(JCR_PRIMARY_TYPE))) {
            reconcile(before, after);
        }
    }

    /**
     * Return a child editor for the container types and proposal answers, so the commit is followed down to the
     * answer nodes; other nodes (including the {@code nt:file} children themselves) are not descended into.
     *
     * @param name the child node name
     * @param after the child node's state after the commit
     * @return a child editor, or {@code null} when the child is not relevant
     */
    private Editor descend(final String name, final NodeState after)
    {
        if (this.formUtils.isFormsHomepage(after) || this.formUtils.isForm(after)
            || this.formUtils.isAnswerSection(after)
            || PROPOSAL_ANSWER_NODETYPE.equals(after.getName(JCR_PRIMARY_TYPE))) {
            return new ProposalAnswerEditor(this.currentNodeBuilder.getChildNode(name), this.formUtils,
                this.llmConfigurationService, name);
        }
        return null;
    }

    /**
     * Bring the parse output of a proposal answer in line with its current files: parse new or changed files,
     * drop the output of removed files, and renew the chunk trees only when something changed.
     *
     * @param before the answer's state before the commit (may be non-existent for a newly added answer)
     * @param after the answer's state after the commit
     */
    private void reconcile(final NodeState before, final NodeState after)
    {
        final String answerFolder = resolveAnswerFolder(this.currentNodeBuilder);
        final List<String> removed = removedFiles(before, after);
        final List<String> toParse = filesNeedingParse(before, after, answerFolder);
        if (removed.isEmpty() && toParse.isEmpty()) {
            return;
        }
        if (!hasAnyFile(after)) {
            this.currentNodeBuilder.removeProperty(NOTE_PROPERTY);
            ParsedMarkdownStore.deleteFolder(answerFolder);
            return;
        }
        for (final String fileName : removed) {
            ParsedMarkdownStore.deleteParsedFile(answerFolder, fileName);
        }
        // Chunking happens inside the parse call now, but the threshold comes from the LLM configuration, which
        // only this editor can resolve. Hand it to the parsers for the duration of this (same-thread) parse.
        SimpleDocumentParser.setChunkingThreshold(wholeDocumentTokenLimit());
        try {
            applyParseResults(answerFolder, parseFiles(toParse, answerFolder));
        } finally {
            SimpleDocumentParser.clearChunkingThreshold();
        }
    }

    private void applyParseResults(final String answerFolder, final List<String> parseErrors)
    {
        this.currentNodeBuilder.removeProperty(NOTE_PROPERTY);
        if (!parseErrors.isEmpty()) {
            this.currentNodeBuilder.setProperty(NOTE_PROPERTY, String.join("\n\n", parseErrors), Type.STRING);
        }
        if (parseErrors.isEmpty()) {
            // The chunk tree already arrived with the Markdown from the daemon's /parse call and was written
            // by SimpleDocumentParser, so there is no separate chunking request to make. All that is left is
            // to advance the generation, which is what downstream summarization uses to notice that a newer
            // parse has superseded its work.
            DoclingChatChunker.markChunksWritten(ParsedMarkdownStore.resolveAnswerDir(answerFolder));
        } else {
            DoclingChatChunker.invalidateChunking(ParsedMarkdownStore.resolveAnswerDir(answerFolder));
            ParsedMarkdownStore.clearChunks(answerFolder);
        }
    }

    /**
     * The active LLM model's {@code wholeDocumentTokenLimit}, the single source of the small-document chunking
     * threshold. When the configuration cannot be read, {@code 0} is returned so the chunker applies its own
     * built-in default rather than this editor inventing a second value.
     *
     * @return the configured limit in estimated tokens, or {@code 0} when unavailable
     */
    private long wholeDocumentTokenLimit()
    {
        try {
            return this.llmConfigurationService.getActiveSettings().getWholeDocumentTokenLimit();
        } catch (final IOException e) {
            LOGGER.warn("Could not read the active LLM settings for the chunking threshold: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Delete the parse output of any proposal answer in a removed subtree, whether the removed node is the
     * answer itself or a container (form, answer section) that held answers.
     *
     * @param name the removed node's name
     * @param before the removed node's state before the commit
     */
    private void cleanupDeleted(final String name, final NodeState before)
    {
        if (before == null) {
            return;
        }
        if (PROPOSAL_ANSWER_NODETYPE.equals(before.getName(JCR_PRIMARY_TYPE))) {
            ParsedMarkdownStore.deleteFolder(folderForDeleted(name, before));
            return;
        }
        if (this.formUtils.isFormsHomepage(before) || this.formUtils.isForm(before)
            || this.formUtils.isAnswerSection(before)) {
            for (final ChildNodeEntry entry : before.getChildNodeEntries()) {
                cleanupDeleted(entry.getName(), entry.getNodeState());
            }
        }
    }

    private String resolveAnswerFolder(final NodeBuilder nodeBuilder)
    {
        final String uuid = nodeBuilder.getString(JCR_UUID);
        if (StringUtils.isNotBlank(uuid)) {
            return uuid;
        }
        return this.nodeName;
    }

    private static String folderForDeleted(final String name, final NodeState before)
    {
        final String uuid = before.getString(JCR_UUID);
        return StringUtils.isNotBlank(uuid) ? uuid : name;
    }

    private List<String> parseFiles(final List<String> fileNames, final String answerFolder)
    {
        final List<String> parseErrors = new ArrayList<>();
        for (final String fileName : fileNames) {
            final NodeBuilder fileNode = this.currentNodeBuilder.getChildNode(fileName);
            final String result = parseFileNode(fileNode, fileName, answerFolder);
            if (DocumentParseException.isParseErrorNote(result)) {
                parseErrors.add(result.trim());
            }
        }
        return parseErrors;
    }

    private String parseFileNode(final NodeBuilder fileNode, final String fileName, final String answerFolder)
    {
        final FileParser parser = PARSER_FACTORY.getParser(fileName);
        if (parser == null) {
            LOGGER.error("Unsupported file format, skipping: '{}'", fileName);
            return null;
        }
        final Blob dataBlob = getFileDataBlob(fileNode);
        if (dataBlob == null) {
            return null;
        }
        try {
            return parseBlob(dataBlob, parser, fileName, answerFolder);
        } catch (DocumentParseException e) {
            LOGGER.warn("Failed to parse file '{}': {}", fileName, e.getMessage());
            return DocumentParseException.toNote(fileName, e.getMessage());
        }
    }

    private String parseBlob(final Blob dataBlob, final FileParser parser, final String fileName,
        final String answerFolder)
    {
        final long startTimestamp = System.currentTimeMillis();
        LOGGER.info("<>File parsing started for '{}'", fileName);
        String result = null;
        try {
            final long blobLength = dataBlob.length();
            if (blobLength > MAX_DOCUMENT_SIZE_BYTES) {
                LOGGER.warn("Skipping parse of '{}': size {} bytes exceeds limit", fileName, blobLength);
                return null;
            }
            try (InputStream stream = dataBlob.getNewStream()) {
                final byte[] content = stream.readNBytes((int) blobLength);
                result = parser.parse(new ByteArrayInputStream(content), fileName, answerFolder);
                return result;
            }
        } catch (DocumentParseException e) {
            throw e;
        } catch (IOException e) {
            throw new DocumentParseException("Failed to read document stream", e);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse file '{}': {}", fileName, e.getMessage());
            return null;
        } finally {
            final long endTimestamp = System.currentTimeMillis();
            final int resultLength = result == null ? 0 : result.length();
            LOGGER.info("<>File parsing finished for '{}' at {} (total {} ms, result {} chars)",
                fileName, endTimestamp, endTimestamp - startTimestamp, resultLength);
        }
    }

    private Blob getFileDataBlob(final NodeBuilder fileNode)
    {
        final NodeBuilder contentNode = fileNode.getChildNode(JCR_CONTENT);
        if (!contentNode.exists() || !contentNode.hasProperty(JCR_DATA)) {
            return null;
        }
        return contentNode.getProperty(JCR_DATA).getValue(Type.BINARY);
    }

    private List<String> filesNeedingParse(final NodeState before, final NodeState after,
        final String answerFolder)
    {
        final List<String> toParse = new ArrayList<>();
        for (final String fileName : fileNames(after)) {
            if (fileNeedsParse(before, after, fileName, answerFolder)) {
                toParse.add(fileName);
            }
        }
        return toParse;
    }

    private static boolean fileNeedsParse(final NodeState before, final NodeState after, final String fileName,
        final String answerFolder)
    {
        if (!isFileChild(before, fileName)) {
            return true;
        }
        if (!sameContent(before.getChildNode(fileName), after.getChildNode(fileName))) {
            return true;
        }
        return !ParsedMarkdownStore.hasParsedFile(answerFolder, fileName);
    }

    private static List<String> removedFiles(final NodeState before, final NodeState after)
    {
        final List<String> removed = new ArrayList<>();
        for (final String fileName : fileNames(before)) {
            if (!isFileChild(after, fileName)) {
                removed.add(fileName);
            }
        }
        return removed;
    }

    private static List<String> fileNames(final NodeState state)
    {
        final List<String> names = new ArrayList<>();
        if (state == null || !state.exists()) {
            return names;
        }
        for (final ChildNodeEntry entry : state.getChildNodeEntries()) {
            if (NT_FILE.equals(entry.getNodeState().getName(JCR_PRIMARY_TYPE))) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static boolean hasAnyFile(final NodeState state)
    {
        return !fileNames(state).isEmpty();
    }

    private static boolean isFileChild(final NodeState state, final String fileName)
    {
        return state != null && state.exists() && state.hasChildNode(fileName)
            && NT_FILE.equals(state.getChildNode(fileName).getName(JCR_PRIMARY_TYPE));
    }

    /**
     * Compare the binary content of two file nodes, ignoring volatile metadata such as the last-modified
     * timestamp, so that re-saving a form without re-uploading a file is not treated as a change.
     *
     * @param beforeFile the file node before the commit
     * @param afterFile the file node after the commit
     * @return {@code true} when both files hold the same binary content
     */
    private static boolean sameContent(final NodeState beforeFile, final NodeState afterFile)
    {
        final Blob beforeBlob = dataBlob(beforeFile);
        final Blob afterBlob = dataBlob(afterFile);
        if (beforeBlob == null || afterBlob == null) {
            return beforeBlob == afterBlob;
        }
        if (beforeBlob.length() != afterBlob.length()) {
            return false;
        }
        final String beforeId = beforeBlob.getContentIdentity();
        final String afterId = afterBlob.getContentIdentity();
        if (beforeId != null && afterId != null) {
            return beforeId.equals(afterId);
        }
        return true;
    }

    private static Blob dataBlob(final NodeState fileState)
    {
        if (fileState == null || !fileState.hasChildNode(JCR_CONTENT)) {
            return null;
        }
        final PropertyState data = fileState.getChildNode(JCR_CONTENT).getProperty(JCR_DATA);
        return data == null ? null : data.getValue(Type.BINARY);
    }
}
