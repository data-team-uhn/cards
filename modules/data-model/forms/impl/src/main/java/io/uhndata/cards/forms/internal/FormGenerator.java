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

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

/**
 * @version $Id$
 */
public class FormGenerator
{
    protected final QuestionnaireUtils questionnaireUtils;

    protected final FormUtils formUtils;

    protected final String userID;

    /**
     * Simple constructor.
     *
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param userID the user that should be recorded as ccreating any missing nodes
     */
    public FormGenerator(final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils,
        final String userID)
    {
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.userID = userID;
    }

    public NodeBuilder createMissingNodes(final Node questionnaireNode, final NodeBuilder formNode)
    {
        // If the current node has a primarytype, it has already been created
        boolean nodeNeedsInitialization = !formNode.hasProperty("jcr:primaryType");

        if (this.questionnaireUtils.isSection(questionnaireNode)) {
            if (nodeNeedsInitialization) {
                initializeSection(questionnaireNode, formNode);
            }
            createMissingChildren(questionnaireNode, formNode);
        } else if (this.questionnaireUtils.isQuestionnaire(questionnaireNode)) {
            createMissingChildren(questionnaireNode, formNode);
        } else {
            if (nodeNeedsInitialization) {
                initializeAnswer(questionnaireNode, formNode);
            }
        }

        return formNode;
    }

    private void createMissingChildren(Node questionnaireNode, NodeBuilder formNode)
    {
        Map<String, NodeBuilder> childFormNodes = new HashMap<>();
        for (String childNodeName : formNode.getChildNodeNames()) {
            NodeBuilder childNode = formNode.getChildNode(childNodeName);
            if (this.formUtils.isAnswerSection(childNode)) {
                childFormNodes.put(this.formUtils.getSectionIdentifier(childNode), childNode);
            } else if (this.formUtils.isAnswer(childNode)) {
                childFormNodes.put(this.formUtils.getQuestionIdentifier(childNode), childNode);
            }
        }

        try {
            for (NodeIterator i = questionnaireNode.getNodes(); i.hasNext();) {
                Node questionnaireChild = i.nextNode();

                NodeBuilder childNode;
                if (childFormNodes.containsKey(questionnaireNode.getIdentifier())) {
                    childNode = childFormNodes.get(questionnaireNode.getIdentifier());
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
        } catch (RepositoryException e) {
            // Could not iterate through children
        }
    }

    private void initializeSection(Node sectionNode, NodeBuilder answerSectionNode)
    {
        try {
            // Section must be created before primary type
            answerSectionNode.setProperty(FormUtils.SECTION_PROPERTY, sectionNode.getIdentifier(),
                Type.REFERENCE);
            answerSectionNode.setProperty("jcr:primaryType", FormUtils.ANSWER_SECTION_NODETYPE, Type.NAME);
            answerSectionNode.setProperty("sling:resourceSuperType", "cards/Resource", Type.STRING);
            answerSectionNode.setProperty("sling:resourceType", FormUtils.ANSWER_SECTION_RESOURCE, Type.STRING);
            answerSectionNode.setProperty("statusFlags", Collections.emptyList(), Type.STRINGS);
        } catch (RepositoryException e) {
            // Could not retrieve section UUID
        }
    }

    private void initializeAnswer(Node questionNode, NodeBuilder answerNode)
    {
        try {
            AnswerNodeTypes types = new AnswerNodeTypes(questionNode);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            answerNode.setProperty("jcr:created", dateFormat.format(new Date()), Type.DATE);
            answerNode.setProperty("jcr:createdBy", this.userID, Type.NAME);
            answerNode.setProperty(FormUtils.QUESTION_PROPERTY, questionNode.getIdentifier(),
                Type.REFERENCE);
            answerNode.setProperty("jcr:primaryType", types.getPrimaryType(), Type.NAME);
            answerNode.setProperty("sling:resourceSuperType", FormUtils.ANSWER_RESOURCE, Type.STRING);
            answerNode.setProperty("sling:resourceType", types.getResourceType(), Type.STRING);
            answerNode.setProperty("statusFlags", Collections.emptyList(), Type.STRINGS);
        } catch (RepositoryException e) {
            // Could not retrieve answer type or question UUID
        }
    }

    protected static class AnswerNodeTypes
    {
        private String primaryType;

        private String resourceType;

        private Type<?> dataType;

        @SuppressWarnings("checkstyle:CyclomaticComplexity")
        AnswerNodeTypes(final Node questionNode) throws RepositoryException
        {
            final String dataTypeString = questionNode.getProperty("dataType").getString();
            final String capitalizedType = StringUtils.capitalize(dataTypeString);
            this.primaryType = "cards:" + capitalizedType + "Answer";
            this.resourceType = "cards/" + capitalizedType + "Answer";
            switch (dataTypeString) {
                case "long":
                    this.dataType = Type.LONG;
                    break;
                case "double":
                    this.dataType = Type.DOUBLE;
                    break;
                case "decimal":
                    this.dataType = Type.DECIMAL;
                    break;
                case "boolean":
                    // Long, not boolean
                    this.dataType = Type.LONG;
                    break;
                case "date":
                    this.dataType = (questionNode.hasProperty("dateFormat") && "yyyy".equals(
                        questionNode.getProperty("dateFormat").getString().toLowerCase()))
                            ? Type.LONG
                            : Type.DATE;
                    break;
                case "time":
                case "vocabulary":
                case "text":
                default:
                    this.dataType = Type.STRING;
            }
        }

        public String getPrimaryType()
        {
            return this.primaryType;
        }

        public String getResourceType()
        {
            return this.resourceType;
        }

        public Type<?> getDataType()
        {
            return this.dataType;
        }
    }

}
