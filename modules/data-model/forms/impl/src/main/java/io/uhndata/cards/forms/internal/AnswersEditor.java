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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

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
 * @version $Id$
 */
public abstract class AnswersEditor extends DefaultEditor
{
    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    protected final NodeBuilder currentNodeBuilder;

    protected final ResourceResolverFactory rrf;

    /** The current user session. */
    protected final Session currentSession;

    /**
     * A session that has access to all the questionnaire questions and can access restricted questions. This session
     * should not be used for accessing any user data.
     */
    protected Session serviceSession;

    protected final QuestionnaireUtils questionnaireUtils;

    protected final FormUtils formUtils;

    protected boolean isFormNode;

    protected boolean shouldRunOnLeave;

    protected AbstractAnswerChangeTracker answerChangeTracker;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param currentSession the current user session
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     */
    public AnswersEditor(final NodeBuilder nodeBuilder, final Session currentSession, final ResourceResolverFactory rrf,
        final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.answerChangeTracker = getAnswerChangeTracker();
        this.shouldRunOnLeave = false;
        this.currentSession = currentSession;
        this.rrf = rrf;
        this.isFormNode = this.formUtils.isForm(nodeBuilder);
    }

    protected abstract Logger getLogger();

    protected abstract String getServiceName();

    protected abstract AbstractAnswerChangeTracker getAnswerChangeTracker();

    protected abstract AnswersEditor getNewEditor(String name);

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

        try (ResourceResolver serviceResolver =
            this.rrf.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, getServiceName()))) {
            if (serviceResolver != null) {
                this.serviceSession = serviceResolver.adaptTo(Session.class);
                handleLeave(after);
            }
        } catch (LoginException e) {
            // Should not happen
        } finally {
            this.serviceSession = null;
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
                    currentTree = new QuestionTree(currentNode, true, this.formUtils);
                }
            } else if (this.questionnaireUtils.isQuestionnaire(currentNode)
                || this.questionnaireUtils.isSection(currentNode)) {
                // Recursively check if any children have a matching question
                QuestionTree newTree = new QuestionTree(currentNode, false, this.formUtils);
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
        protected final FormUtils formUtils;

        private Map<String, QuestionTree> children;

        private Node node;

        private boolean isQuestion;

        QuestionTree(final Node node, final boolean isQuestion, final FormUtils formUtils)
        {
            this.isQuestion = isQuestion;
            this.node = node;
            this.children = isQuestion ? null : new HashMap<>();
            this.formUtils = formUtils;
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

        public Map<Node, NodeBuilder> getQuestionAndAnswers(NodeBuilder currentNode)
        {
            if (this.isQuestion) {
                return Collections.singletonMap(this.node, currentNode);
            } else {
                Map<Node, NodeBuilder> result = new HashMap<>();
                Map<String, List<NodeBuilder>> childNodesByReference = getChildNodesByReference(currentNode);

                this.children.values().forEach(childTree -> {
                    try {
                        String referenceKey = childTree.getNode().getIdentifier();
                        if (childNodesByReference.containsKey(referenceKey)) {
                            List<NodeBuilder> matchingChildren = childNodesByReference.get(referenceKey);
                            for (NodeBuilder childNode : matchingChildren) {
                                result.putAll(childTree.getQuestionAndAnswers(childNode));
                            }
                        }
                    } catch (RepositoryException e) {
                        // Question identifier could not be found so could not search for answers
                    }
                });
                return result;
            }
        }

        private Map<String, List<NodeBuilder>> getChildNodesByReference(final NodeBuilder nodeBuilder)
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

        @Override
        public String toString()
        {
            return "{question:" + this.isQuestion
                + (this.children == null ? "" : " children: " + this.children.toString())
                + (this.node == null ? "" : " node: " + this.node.toString()) + " }";
        }
    }

    protected Type<?> getAnswerType(final Node questionNode)
    {
        Type<?> result = Type.STRING;
        try {
            final String dataTypeString = questionNode.getProperty("dataType").getString();
            switch (dataTypeString) {
                case "long":
                    result = Type.LONG;
                    break;
                case "double":
                    result = Type.DOUBLE;
                    break;
                case "decimal":
                    result = Type.DECIMAL;
                    break;
                case "boolean":
                    // Long, not boolean
                    result = Type.LONG;
                    break;
                case "date":
                    result = (questionNode.hasProperty("dateFormat") && "yyyy".equals(
                        questionNode.getProperty("dateFormat").getString().toLowerCase()))
                            ? Type.LONG
                            : Type.DATE;
                    break;
                default:
                    result = Type.STRING;
            }
        } catch (RepositoryException e) {
            getLogger().warn("Error typing value for question. " + e.getMessage());
            // It's OK to assume String by default
        }
        return result;
    }
}
