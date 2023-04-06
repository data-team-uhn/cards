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

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.PropertyDefinition;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.PropertyStates;
import org.apache.jackrabbit.oak.spi.nodetype.NodeTypeConstants;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.util.ISO8601;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

/**
 * @version $Id$
 */
public class FormGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FormGenerator.class);

    private final QuestionnaireUtils questionnaireUtils;

    private final FormUtils formUtils;

    private final Session session;

    private final String userID;

    /**
     * Simple constructor.
     *
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param session the current JCR session
     */
    public FormGenerator(final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils,
        final Session session)
    {
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.session = session;
        this.userID = session.getUserID();
    }

    public NodeBuilder createMissingNodes(final Node questionnaireNode, final NodeBuilder formNode)
    {
        // If the current node has a primary type, it has already been created
        final boolean nodeNeedsInitialization = !formNode.hasProperty("jcr:primaryType");

        if (this.questionnaireUtils.isSection(questionnaireNode)) {
            if (nodeNeedsInitialization) {
                initializeSection(questionnaireNode, formNode);
            }
            createMissingChildren(questionnaireNode, formNode);
        } else if (this.questionnaireUtils.isQuestionnaire(questionnaireNode)) {
            createMissingChildren(questionnaireNode, formNode);
        } else if (nodeNeedsInitialization) {
            initializeAnswer(questionnaireNode, formNode);
        }

        return formNode;
    }

    private void createMissingChildren(final Node questionnaireNode, final NodeBuilder formNode)
    {
        final Map<String, NodeBuilder> childFormNodes = new HashMap<>();
        for (final String childNodeName : formNode.getChildNodeNames()) {
            final NodeBuilder childNode = formNode.getChildNode(childNodeName);
            if (this.formUtils.isAnswerSection(childNode)) {
                childFormNodes.put(this.formUtils.getSectionIdentifier(childNode), childNode);
            } else if (this.formUtils.isAnswer(childNode)) {
                childFormNodes.put(this.formUtils.getQuestionIdentifier(childNode), childNode);
            }
        }

        try {
            for (final NodeIterator i = questionnaireNode.getNodes(); i.hasNext();) {
                final Node questionnaireChild = i.nextNode();

                if (!this.questionnaireUtils.isSection(questionnaireChild)
                    && !this.questionnaireUtils.isQuestion(questionnaireChild)
                    || this.questionnaireUtils.isConditionalSection(questionnaireChild)) {
                    continue;
                }

                NodeBuilder childNode;
                if (childFormNodes.containsKey(questionnaireChild.getIdentifier())) {
                    childNode = childFormNodes.get(questionnaireChild.getIdentifier());
                } else {
                    childNode = formNode.setChildNode(UUID.randomUUID().toString());
                }

                createMissingNodes(questionnaireChild, childNode);
                // TODO: Handle recurrent sections properly
                // if (childQuestionNode.hasProperty("recurrent")
                // && childQuestionNode.getProperty("recurrent").getBoolean()
                // && childQuestionNode.hasProperty("initialNumberOfInstances")) {
                // expectedNumberOfInstances = (int) childQuestionNode.getProperty("initialNumberOfInstances")
                //     .getLong();

            }
        } catch (final RepositoryException e) {
            // Could not iterate through children
        }
    }

    private void initializeSection(final Node sectionNode, final NodeBuilder answerSectionNode)
    {
        try {
            // Section must be created before primary type
            answerSectionNode.setProperty(FormUtils.SECTION_PROPERTY, sectionNode.getIdentifier(),
                Type.REFERENCE);
            answerSectionNode.setProperty("jcr:primaryType", FormUtils.ANSWER_SECTION_NODETYPE, Type.NAME);
            autoCreateProperties(answerSectionNode);
        } catch (final RepositoryException e) {
            // Could not retrieve section UUID
        }
    }

    private void initializeAnswer(final Node questionNode, final NodeBuilder answerNode)
    {
        try {
            answerNode.setProperty(FormUtils.QUESTION_PROPERTY, questionNode.getIdentifier(),
                Type.REFERENCE);
            answerNode.setProperty("jcr:primaryType", getAnswerNodeType(questionNode), Type.NAME);
            autoCreateProperties(answerNode);
        } catch (final RepositoryException e) {
            // Could not retrieve answer type or question UUID
        }
    }

    private void autoCreateProperties(final NodeBuilder node)
    {
        try {
            final PropertyDefinition[] definitions = this.session.getWorkspace().getNodeTypeManager()
                .getNodeType(node.getProperty("jcr:primaryType").getValue(Type.STRING))
                .getPropertyDefinitions();
            if (definitions == null || definitions.length == 0) {
                return;
            }
            for (PropertyDefinition definition : definitions) {
                if (node.hasProperty(definition.getName()) || !definition.isAutoCreated()) {
                    continue;
                }
                final PropertyState property = autoCreateProperty(definition, node);
                if (property != null) {
                    node.setProperty(property);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to autocreate properties: {}", e.getMessage());
        }
    }

    @SuppressWarnings("checkstyle:ReturnCount")
    private PropertyState autoCreateProperty(final PropertyDefinition definition, final NodeBuilder node)
        throws RepositoryException
    {
        final String name = definition.getName();
        switch (name) {
            case JcrConstants.JCR_UUID:
                return PropertyStates.createProperty(name, UUID.randomUUID().toString(), Type.STRING);
            case JcrConstants.JCR_CREATED:
            case JcrConstants.JCR_LASTMODIFIED:
                return PropertyStates.createProperty(name, ISO8601.format(Calendar.getInstance()), Type.DATE);
            case NodeTypeConstants.JCR_CREATEDBY:
            case NodeTypeConstants.JCR_LASTMODIFIEDBY:
                return PropertyStates.createProperty(name, StringUtils.defaultString(this.userID), Type.STRING);
            default:
                // No default handling, manually create based on the definition
        }

        Value[] values = definition.getDefaultValues();
        if (definition.isMultiple()) {
            return PropertyStates.createProperty(name, Arrays.asList(values));
        } else if (values != null && values.length > 0) {
            return PropertyStates.createProperty(name, values[0]);
        }
        return null;
    }

    private String getAnswerNodeType(final Node questionNode) throws RepositoryException
    {
        final String dataTypeString = questionNode.getProperty("dataType").getString();
        final String capitalizedType = StringUtils.capitalize(dataTypeString);
        return "cards:" + capitalizedType + "Answer";
    }
}
