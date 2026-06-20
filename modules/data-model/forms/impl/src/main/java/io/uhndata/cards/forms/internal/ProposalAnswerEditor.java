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
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.internal.parse.DocumentParseException;
import io.uhndata.cards.forms.internal.parse.FileParser;
import io.uhndata.cards.forms.internal.parse.FileParserFactory;
import io.uhndata.cards.forms.internal.parse.ParsedMarkdownStore;

/**
 * Parse uploaded files and place extracted text into answer notes.
 *
 * @version $Id$
 */
public class ProposalAnswerEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProposalAnswerEditor.class);

    private static final String PROPOSAL_ANSWER_NODETYPE = "cards:ProposalAnswer";

    private static final String JCR_PRIMARY_TYPE = "jcr:primaryType";

    private static final String NT_FILE = "nt:file";

    private static final long MAX_DOCUMENT_SIZE_BYTES = 50L * 1024L * 1024L;

    private static final FileParserFactory PARSER_FACTORY = new FileParserFactory();

    private final FormUtils formUtils;

    private final NodeBuilder currentNodeBuilder;

    private final String nodeName;

    private boolean hasNewFile;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param formUtils helper for checking form nodes
     */
    public ProposalAnswerEditor(final NodeBuilder nodeBuilder, final FormUtils formUtils)
    {
        this(nodeBuilder, formUtils, null);
    }

    /**
     * Constructor that also records the name of the node the builder represents.
     *
     * @param nodeBuilder the builder for the current node
     * @param formUtils helper for checking form nodes
     * @param name the name of the node the builder represents, used as the parse output folder name
     */
    private ProposalAnswerEditor(final NodeBuilder nodeBuilder, final FormUtils formUtils, final String name)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.formUtils = formUtils;
        this.nodeName = name;
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        if (this.formUtils.isFormsHomepage(after) || this.formUtils.isForm(after)
            || this.formUtils.isAnswerSection(after)
            || PROPOSAL_ANSWER_NODETYPE.equals(after.getName(JCR_PRIMARY_TYPE))) {
            return new ProposalAnswerEditor(this.currentNodeBuilder.child(name), this.formUtils, name);
        }
        if (NT_FILE.equals(after.getName(JCR_PRIMARY_TYPE))) {
            this.hasNewFile = true;
        }
        return null;
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        return childNodeAdded(name, after);
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
    {
        if (this.hasNewFile && PROPOSAL_ANSWER_NODETYPE.equals(after.getName(JCR_PRIMARY_TYPE))) {
            handleAnswer(this.currentNodeBuilder);
        }
    }

    private void handleAnswer(final NodeBuilder nodeBuilder)
    {
        if (!isEligibleProposalAnswer(nodeBuilder)) {
            return;
        }

        final String answerFolder = resolveAnswerFolder(nodeBuilder);
        final List<String> parsedContents = parseProposalFiles(nodeBuilder, answerFolder);
        if (!parsedContents.isEmpty()) {
            nodeBuilder.setProperty("note", String.join("\n\n", parsedContents), Type.STRING);
        }
        ParsedMarkdownStore.writeAggregate(answerFolder);
    }

    private String resolveAnswerFolder(final NodeBuilder nodeBuilder)
    {
        final String uuid = nodeBuilder.getString("jcr:uuid");
        if (StringUtils.isNotBlank(uuid)) {
            return uuid;
        }
        return this.nodeName;
    }

    private boolean isEligibleProposalAnswer(final NodeBuilder nodeBuilder)
    {
        if (!PROPOSAL_ANSWER_NODETYPE.equals(nodeBuilder.getName(JCR_PRIMARY_TYPE))) {
            return false;
        }
        String note = nodeBuilder.getString("note");
        return StringUtils.isBlank(note) || note.startsWith("<!-- source_file:")
            || DocumentParseException.isParseErrorNote(note);
    }

    private List<String> parseProposalFiles(final NodeBuilder answerNode, final String answerFolder)
    {
        final List<String> parsedContents = new ArrayList<>();
        for (String fileName : answerNode.getChildNodeNames()) {
            if (fileName == null || fileName.isBlank()) {
                continue;
            }
            final NodeBuilder fileNode = answerNode.getChildNode(fileName);
            if (!NT_FILE.equals(fileNode.getName(JCR_PRIMARY_TYPE))) {
                continue;
            }
            final String parsedText = parseFileNode(fileNode, fileName, answerFolder);
            if (StringUtils.isNotBlank(parsedText)) {
                parsedContents.add(parsedText.trim());
            }
        }
        return parsedContents;
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
        final NodeBuilder contentNode = fileNode.getChildNode("jcr:content");
        if (!contentNode.exists() || !contentNode.hasProperty("jcr:data")) {
            return null;
        }
        return contentNode.getProperty("jcr:data").getValue(Type.BINARY);
    }
}
