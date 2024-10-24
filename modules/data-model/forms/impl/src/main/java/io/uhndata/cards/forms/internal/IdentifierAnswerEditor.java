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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUpdateUtils;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

/**
 * An {@link Editor} that fills out any reference answers for a new form.
 *
 * @version $Id$
 */
public class IdentifierAnswerEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(IdentifierAnswerEditor.class);

    private final FormUtils formUtils;
    private final FormUpdateUtils formUpdateUtils;
    private final QuestionnaireUtils questionnaireUtils;
    private final NodeBuilder currentNodeBuilder;
    private final Session userSession;
    private final boolean isNew;
    private final boolean isForm;

    /**
     * Simple constructor.
     *ms-appid:W~com.squirrel.slack.slack
     * @param nodeBuilder the builder for the current node
     * @param userSession the current user session
     * @param formUtils for working with form data
     * @param formUpdateUtils to help generate missing answers
     * @param questionnaireUtils for working with questionnaire data
     * @param isNew if the node the editor is operating on is newly created
     */
    public IdentifierAnswerEditor(final NodeBuilder nodeBuilder, final Session userSession,
        final FormUtils formUtils, final FormUpdateUtils formUpdateUtils, final QuestionnaireUtils questionnaireUtils,
        final boolean isNew)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.userSession = userSession;
        this.formUtils = formUtils;
        this.formUpdateUtils = formUpdateUtils;
        this.questionnaireUtils = questionnaireUtils;
        this.isNew = isNew;
        this.isForm = formUtils.isForm(this.currentNodeBuilder);
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        if (this.isForm) {
            return null;
        } else {
            return new IdentifierAnswerEditor(this.currentNodeBuilder.child(name), this.userSession,
                this.formUtils, this.formUpdateUtils, this.questionnaireUtils, true);
        }
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        if (this.isForm) {
            return null;
        } else {
            return new IdentifierAnswerEditor(this.currentNodeBuilder.child(name), this.userSession,
                this.formUtils, this.formUpdateUtils, this.questionnaireUtils, false);
        }
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
    {
        // Only process new forms
        if (this.isNew && this.formUtils.isForm(after)) {
            try {
                // Found a new form: check for any identifier questions
                Map<String, String> identifierQuestions = new HashMap<>();
                Node questionnaireNode = this.formUtils.getQuestionnaire(after);
                getIdentifierQuestionPaths(identifierQuestions, questionnaireNode,
                    questionnaireNode.getPath().length());

                for (Entry<String, String> entry : identifierQuestions.entrySet()) {
                    NodeBuilder answer = this.formUpdateUtils.getOrGeneratePath(this.currentNodeBuilder,
                        entry.getKey(), questionnaireNode);
                    if (answer != null) {
                        answer.setProperty(FormUtils.VALUE_PROPERTY, entry.getValue());
                    }
                }
            } catch (RepositoryException e) {
                // Unable to determine questionnaire path so cannot determine relative path
            }
        }
    }

    private void getIdentifierQuestionPaths(Map<String, String> identifierQuestions, Node node,
        int questionnairePathLength)
    {
        try {
            if (this.questionnaireUtils.isSection(node) || this.questionnaireUtils.isQuestionnaire(node)) {
                NodeIterator childNodes = node.getNodes();
                while (childNodes.hasNext()) {
                    getIdentifierQuestionPaths(identifierQuestions, childNodes.nextNode(), questionnairePathLength);
                }
            } else if (this.questionnaireUtils.isQuestion(node)
                && "identifier".equals(node.getProperty("dataType").getString()))
            {
                String answer = generateIdentifier(node);
                String path = node.getPath().substring(questionnairePathLength + 1);
                if (answer != null) {
                    identifierQuestions.put(path, answer);
                }
            }
        } catch (RepositoryException e) {
            // Unable to handle this particular node:
            // Catch so other nodes can be handled.
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
