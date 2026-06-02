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
import io.uhndata.cards.forms.internal.parse.DocumentParser;
import io.uhndata.cards.forms.internal.parse.DocumentParserFactory;

/**
 * Parse uploaded proposal files and place extracted text into answer notes.
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

    private static final DocumentParserFactory PARSER_FACTORY = new DocumentParserFactory();

    private final FormUtils formUtils;

    private final NodeBuilder currentNodeBuilder;

    private boolean hasNewFile;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param formUtils helper for checking form nodes
     */
    public ProposalAnswerEditor(final NodeBuilder nodeBuilder, final FormUtils formUtils)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.formUtils = formUtils;
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        if (this.formUtils.isFormsHomepage(after) || this.formUtils.isForm(after)
            || this.formUtils.isAnswerSection(after)
            || PROPOSAL_ANSWER_NODETYPE.equals(after.getName(JCR_PRIMARY_TYPE))) {
            return new ProposalAnswerEditor(this.currentNodeBuilder.child(name), this.formUtils);
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

        final String documentId = StringUtils.defaultIfBlank(nodeBuilder.getString("jcr:uuid"), "unknown-document");
        final List<String> parsedContents = parseProposalFiles(nodeBuilder, documentId);
        if (!parsedContents.isEmpty()) {
            nodeBuilder.setProperty("note", String.join("\n\n", parsedContents), Type.STRING);
        }
    }

    private boolean isEligibleProposalAnswer(final NodeBuilder nodeBuilder)
    {
        if (!PROPOSAL_ANSWER_NODETYPE.equals(nodeBuilder.getName(JCR_PRIMARY_TYPE))) {
            return false;
        }
        String note = nodeBuilder.getString("note");
        return StringUtils.isBlank(note) || note.startsWith("<!-- document_id:");
    }

    private List<String> parseProposalFiles(final NodeBuilder answerNode, final String documentId)
    {
        final List<String> parsedContents = new ArrayList<>();
        for (String fileName : answerNode.getChildNodeNames()) {
            final NodeBuilder fileNode = answerNode.getChildNode(fileName);
            final String parsedText = parseFileNode(fileNode, fileName, documentId);
            if (StringUtils.isNotBlank(parsedText)) {
                parsedContents.add(parsedText.trim());
            }
        }
        return parsedContents;
    }

    private String parseFileNode(final NodeBuilder fileNode, final String fileName, final String documentId)
    {
        if (!NT_FILE.equals(fileNode.getName(JCR_PRIMARY_TYPE))) {
            return null;
        }
        final DocumentParser parser = PARSER_FACTORY.getParser(fileName);
        if (parser == null) {
            return null;
        }
        final Blob dataBlob = getFileDataBlob(fileNode);
        if (dataBlob == null) {
            return null;
        }
        return parseBlob(dataBlob, parser, fileName, documentId);
    }

    private String parseBlob(final Blob dataBlob, final DocumentParser parser,
        final String fileName, final String documentId)
    {
        final long blobLength = dataBlob.length();
        if (blobLength > MAX_DOCUMENT_SIZE_BYTES) {
            LOGGER.warn("Skipping parse of '{}' in document '{}': size {} bytes exceeds limit",
                fileName, documentId, blobLength);
            return null;
        }
        try (InputStream stream = dataBlob.getNewStream()) {
            return parser.parse(stream, documentId, fileName);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse proposal file '{}' in document '{}': {}", fileName, documentId,
                e.getMessage());
            return null;
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
