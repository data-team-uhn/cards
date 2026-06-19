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
package io.uhndata.cards.identifier.internal;

import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

import io.uhndata.cards.forms.api.FormUtils;

/**
 * An {@link Editor} that fills out any identifier answers for a new form.
 *
 * @version $Id$
 * @since 0.9.30
 */
public class IdentifierAnswerEditor extends DefaultEditor
{
    private final FormUtils formUtils;
    private final NodeBuilder currentNodeBuilder;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param formUtils for working with form data
     */
    public IdentifierAnswerEditor(final NodeBuilder nodeBuilder, final FormUtils formUtils)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.formUtils = formUtils;
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        if (this.formUtils.isFormsHomepage(after) || this.formUtils.isForm(after)) {
            return new IdentifierAnswerEditor(this.currentNodeBuilder.child(name), this.formUtils);
        } else {
            return null;
        }
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        return childNodeAdded(name, after);
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
    {
        if (this.formUtils.isForm(after)) {
            // Found a form: search for and handle any incomplete identifier answers
            searchNode(this.currentNodeBuilder);
        }
    }

    private void searchNode(NodeBuilder nodeBuilder)
    {
        for (String name : nodeBuilder.getChildNodeNames()) {
            NodeBuilder child = nodeBuilder.getChildNode(name);
            if (this.formUtils.isAnswerSection(child)) {
                searchNode(child);
            } else if (this.formUtils.isAnswer(child)) {
                handleAnswer(child);
            }
        }
    }

    private void handleAnswer(NodeBuilder nodeBuilder)
    {
        if ("cards:IdentifierAnswer".equals(nodeBuilder.getProperty("jcr:primaryType").getValue(Type.STRING))) {
            String value = (String) this.formUtils.getValue(nodeBuilder);
            if (value == null || value.length() == 0) {
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY,
                    generateIdentifier(this.formUtils.getQuestion(nodeBuilder)));
            }
        }
    }

    private String generateIdentifier(Node question)
    {
        String identifierType = "";
        try {
            if (question.hasProperty("identifierType")) {
                identifierType = question.getProperty("identifierType").getString();
            }
        } catch (RepositoryException e) {
            // Unable to locate determine what type of identifier this should be:
            // Can't generate identifier
            return null;
        }

        switch (identifierType) {
            case "uuid":
                return UUID.randomUUID().toString();
            default:
                return null;
        }
    }
}
