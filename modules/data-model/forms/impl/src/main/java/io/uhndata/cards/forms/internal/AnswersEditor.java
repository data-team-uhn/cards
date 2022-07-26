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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

/**
 *
 *
 * @version $Id$
 */
public abstract class AnswersEditor extends DefaultEditor
{
    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    protected final NodeBuilder currentNodeBuilder;

    protected final ResourceResolverFactory rrf;

    /** The current user session. **/
    protected Session currentSession;

    protected final QuestionnaireUtils questionnaireUtils;

    protected final FormUtils formUtils;

    protected boolean isFormNode;

    protected boolean shouldRunOnLeave;

    protected AbstractAnswerChangeTracker answerChangeTracker;

    private final String serviceName;

    /**
     * A session that has access to all the questionnaire questions and can access restricted questions.
     * This session should not be used for accessing any user data.
    */
    private Session serviceSession;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param serviceName the name of the service resource resolver that should be used to handle any changes
     */
    public AnswersEditor(final NodeBuilder nodeBuilder, final ResourceResolverFactory rrf,
        final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils, final String serviceName)
    {
        this.serviceName = serviceName;
        this.currentNodeBuilder = nodeBuilder;
        this.rrf = rrf;
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.answerChangeTracker = getAnswerChangeTracker();
        this.isFormNode = this.formUtils.isForm(this.currentNodeBuilder);
        this.shouldRunOnLeave = false;
    }

    protected abstract Logger getLogger();
    protected abstract AbstractAnswerChangeTracker getAnswerChangeTracker();
    protected abstract AnswersEditor getNewEditor(String name);
    protected abstract AnswerNodeTypes getNewAnswerNodeTypes(Node node) throws RepositoryException;
    protected abstract boolean isQuestionNodeMatchingType(Node node) throws RepositoryException;

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        if (this.isFormNode) {
            // No need to descend further down, we already know that this is a form that has changes
            return this.answerChangeTracker;
        } else {
            return getNewEditor(name);
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
        if (!this.isFormNode || !this.shouldRunOnLeave) {
            return;
        }

        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, this.serviceName);
        final ResourceResolver sessionResolver = this.rrf.getThreadResourceResolver();
        try (ResourceResolver serviceResolver = this.rrf.getServiceResourceResolver(parameters)) {
            if (sessionResolver != null && serviceResolver != null) {
                this.currentSession = sessionResolver.adaptTo(Session.class);
                this.serviceSession = serviceResolver.adaptTo(Session.class);
                handleLeave(after);
            }
        } catch (LoginException e) {
            // Should not happen
        }
    }

    protected abstract void handleLeave(NodeState after);

    protected Node getQuestionnaire()
    {
        final String questionnaireId = this.currentNodeBuilder.getProperty("questionnaire").getValue(Type.REFERENCE);
        try {
            return this.serviceSession.getNodeByIdentifier(questionnaireId);
        } catch (RepositoryException e) {
            return null;
        }
    }

    // Returns a QuestionTree if any children of this node contains an unanswered matching question, else null
    protected QuestionTree getUnansweredMatchingQuestions(final Node currentNode)
    {
        QuestionTree currentTree = null;

        try {
            if (isQuestionNodeMatchingType(currentNode)) {
                // Ignore questions that do not match the question type this editor is looking for
                // Skip already answered questions
                if (!this.answerChangeTracker.getModifiedAnswers().contains(currentNode.getIdentifier())) {
                    currentTree = new QuestionTree(currentNode, true);
                }
            } else if (this.questionnaireUtils.isQuestionnaire(currentNode)
                || this.questionnaireUtils.isSection(currentNode)) {
                // Recursively check if any children have a matching question
                QuestionTree newTree = new QuestionTree(currentNode, false);
                for (NodeIterator i = currentNode.getNodes(); i.hasNext();) {
                    Node child = i.nextNode();
                    QuestionTree childTree = getUnansweredMatchingQuestions(child);
                    if (childTree != null) {
                        // Child has data that should be stored
                        newTree.getChildren().put(child.getName(), childTree);
                    }
                }

                // If this node has a child with a matching question, return this information
                if (newTree.getChildren().size() > 0) {
                    currentTree = newTree;
                }
            }
        } catch (RepositoryException e) {
            // Unable to retrieve type: skip
            getLogger().warn(e.getMessage());
        }

        return currentTree;
    }

    protected Map<QuestionTree, NodeBuilder> createMissingNodes(
        final QuestionTree questionTree, final NodeBuilder currentNode)
    {
        try {
            if (questionTree.isQuestion()) {
                if (!currentNode.hasProperty("jcr:" + "primaryType")) {
                    AnswerNodeTypes types = getNewAnswerNodeTypes(questionTree.getNode());
                    // New node, insert all required properties
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                    currentNode.setProperty("jcr:created", dateFormat.format(new Date()), Type.DATE);
                    currentNode.setProperty("jcr:createdBy", this.currentSession.getUserID(), Type.NAME);
                    String questionReference = questionTree.getNode().getIdentifier();
                    currentNode.setProperty(FormUtils.QUESTION_PROPERTY, questionReference, Type.REFERENCE);
                    currentNode.setProperty("jcr:primaryType", types.getPrimaryType(), Type.NAME);
                    currentNode.setProperty("sling:resourceSuperType", FormUtils.ANSWER_RESOURCE, Type.STRING);
                    currentNode.setProperty("sling:resourceType", types.getResourceType(), Type.STRING);
                    currentNode.setProperty("statusFlags", Collections.emptyList(), Type.STRINGS);
                }
                return Collections.singletonMap(questionTree, currentNode);
            } else {
                // Section
                if (!currentNode.hasProperty("jcr:primaryType")) {
                    // Section must be created before primary type
                    currentNode.setProperty(FormUtils.SECTION_PROPERTY, questionTree.getNode().getIdentifier(),
                        Type.REFERENCE);
                    currentNode.setProperty("jcr:primaryType", FormUtils.ANSWER_SECTION_NODETYPE, Type.NAME);
                    currentNode.setProperty("sling:resourceSuperType", "cards/Resource", Type.STRING);
                    currentNode.setProperty("sling:resourceType", FormUtils.ANSWER_SECTION_RESOURCE, Type.STRING);
                    currentNode.setProperty("statusFlags", Collections.emptyList(), Type.STRINGS);
                }
                Map<String, List<NodeBuilder>> childNodesByReference = getChildNodesByReference(currentNode);
                return createChildrenNodes(questionTree, childNodesByReference, currentNode);
            }
        } catch (RepositoryException e) {
            getLogger().error("Error creating " + (questionTree.isQuestion() ? "question. " : "section. ")
                + e.getMessage());
            return Collections.emptyMap();
        }
    }

    protected Map<QuestionTree, NodeBuilder> createChildrenNodes(
        final QuestionTree questionTree,
        final Map<String, List<NodeBuilder>> childNodesByReference, final NodeBuilder nodeBuilder)
    {
        Map<QuestionTree, NodeBuilder> result = new HashMap<>();
        for (Map.Entry<String, QuestionTree> childQuestion
            : questionTree.getChildren().entrySet()) {
            QuestionTree childTree = childQuestion.getValue();
            try {
                Node childQuestionNode = childTree.getNode();
                String referenceKey = childQuestionNode.getIdentifier();
                int expectedNumberOfInstances = 1;
                int numberOfInstances = 0;
                if (childQuestionNode.hasProperty("recurrent")
                    && childQuestionNode.getProperty("recurrent").getBoolean()
                    && childQuestionNode.hasProperty("initialNumberOfInstances")) {
                    expectedNumberOfInstances = (int) childQuestionNode.getProperty("initialNumberOfInstances")
                        .getLong();
                }

                if (childNodesByReference.containsKey(referenceKey)) {
                    List<NodeBuilder> matchingChildren = childNodesByReference.get(referenceKey);
                    numberOfInstances += matchingChildren.size();

                    for (NodeBuilder childNode : matchingChildren) {
                        result.putAll(createMissingNodes(childTree, childNode));
                    }
                }
                while (numberOfInstances < expectedNumberOfInstances) {
                    NodeBuilder childNodeBuilder = nodeBuilder.setChildNode(UUID.randomUUID().toString());
                    result.putAll(createMissingNodes(childTree, childNodeBuilder));
                    numberOfInstances++;
                }
            } catch (RepositoryException e) {
                // Node has no accessible identifier, skip
            }
        }
        return result;
    }

    protected Map<String, List<NodeBuilder>> getChildNodesByReference(final NodeBuilder nodeBuilder)
    {
        Map<String, List<NodeBuilder>> result = new HashMap<>();
        for (String childNodeName : nodeBuilder.getChildNodeNames()) {
            NodeBuilder childNode = nodeBuilder.getChildNode(childNodeName);
            String childIdentifier;
            if (this.formUtils.isAnswerSection(childNode)) {
                childIdentifier = this.formUtils.getSectionIdentifier(childNode);
            } else if (this.formUtils.isAnswer(childNode)) {
                childIdentifier = this.formUtils.getQuestionIdentifier(childNode);
            } else {
                continue;
            }

            List<NodeBuilder> childNodes = result.containsKey(childIdentifier)
                ? result.get(childIdentifier)
                : new ArrayList<>();
            childNodes.add(childNode);
            result.put(childIdentifier, childNodes);
        }
        return result;
    }






    protected abstract static class AbstractAnswerChangeTracker extends DefaultEditor
    {
        private final FormUtils formUtils;

        private final Set<String> modifiedAnswers = new HashSet<>();

        private boolean inMatchedAnswer;

        private String currentAnswer;

        public AbstractAnswerChangeTracker(final FormUtils formUtils)
        {
            this.formUtils = formUtils;
        }

        public abstract boolean isMatchedAnswerNode(NodeState after, String questionId);

        @Override
        public void enter(NodeState before, NodeState after)
        {
            String questionId = this.formUtils.getQuestionIdentifier(after);
            if (isMatchedAnswerNode(after, questionId)) {
                this.inMatchedAnswer = true;
                this.currentAnswer = questionId;
            }
        }

        @Override
        public void leave(NodeState before, NodeState after)
        {
            this.inMatchedAnswer = false;
            this.currentAnswer = null;
        }

        @Override
        public void propertyAdded(PropertyState after)
        {
            if (this.inMatchedAnswer) {
                this.modifiedAnswers.add(this.currentAnswer);
            }
        }

        @Override
        public void propertyChanged(PropertyState before, PropertyState after)
        {
            propertyAdded(after);
        }

        @Override
        public void propertyDeleted(PropertyState before)
        {
            propertyAdded(before);
        }

        @Override
        public Editor childNodeAdded(String name, NodeState after)
        {
            return this;
        }

        @Override
        public Editor childNodeChanged(String name, NodeState before, NodeState after)
        {
            return this;
        }

        @Override
        public Editor childNodeDeleted(String name, NodeState before)
        {
            return this;
        }

        public Set<String> getModifiedAnswers()
        {
            return this.modifiedAnswers;
        }
    }

    protected static class QuestionTree
    {
        private Map<String, QuestionTree> children;

        private Node node;

        private boolean isQuestion;

        QuestionTree(final Node node, final boolean isQuestion)
        {
            this.isQuestion = isQuestion;
            this.node = node;
            this.children = isQuestion ? null : new HashMap<>();
        }

        public Map<String, QuestionTree> getChildren()
        {
            return this.children;
        }

        public Node getNode()
        {
            return this.node;
        }

        public boolean isQuestion()
        {
            return this.isQuestion;
        }

        @Override
        public String toString()
        {
            return "{question:" + this.isQuestion
                + (this.children == null ? "" : " children: " + this.children.toString())
                + (this.node == null ? "" : " node: " + this.node.toString()) + " }";
        }
    }

    protected static class AnswerNodeTypes
    {
        private String primaryType;

        private String resourceType;

        private Type<?> dataType;

        @SuppressWarnings("checkstyle:CyclomaticComplexity")
        AnswerNodeTypes(final Node questionNode, String defaultPrimaryType, String defaultResourceType)
            throws RepositoryException
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
                    this.dataType = Type.STRING;
                    break;
                default:
                    this.primaryType = defaultPrimaryType;
                    this.resourceType = defaultResourceType;
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
