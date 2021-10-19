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
package io.uhndata.cards.dataentry.internal;

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
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.ExpressionUtils;
import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;
import io.uhndata.cards.dataentry.internal.ComputedAnswersEditor.QuestionTree;

/**
 * An {@link Editor} that calculates any computed answers that were not submitted by the client.
 *
 * @version $Id$
 */
public class ComputedAnswersEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputedAnswersEditor.class);

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    private final ResourceResolverFactory rrf;

    /** The current user session. **/
    private Session currentSession;

    /**
     * A session that has access to all the questionnaire questions and can access restricted questions.
     */
    private Session serviceSession;

    private final QuestionnaireUtils questionnaireUtils;

    private final FormUtils formUtils;

    private final ExpressionUtils expressionUtils;

    private boolean isFormNode;

    private boolean formEditorHasChanged;

    private ComputedAnswerChangeTracker computedAnswerChangeTracker;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param expressionUtils for evaluating the computed questions
     */
    public ComputedAnswersEditor(final NodeBuilder nodeBuilder, final ResourceResolverFactory rrf,
        final QuestionnaireUtils questionnaireUtils, FormUtils formUtils, final ExpressionUtils expressionUtils)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.rrf = rrf;
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.expressionUtils = expressionUtils;
        this.computedAnswerChangeTracker = new ComputedAnswerChangeTracker();
        this.isFormNode = this.formUtils.isForm(this.currentNodeBuilder);
    }

    @Override
    public Editor childNodeAdded(String name, NodeState after)
    {
        if (this.isFormNode) {
            // Found a modified form. Flag for the current editor to checking computed answers.
            // No need to make any child editors.
            this.formEditorHasChanged = true;
            // No need to descend further down, we already know that this is a form that has changes
            return this.computedAnswerChangeTracker;
        } else {
            return new ComputedAnswersEditor(this.currentNodeBuilder.getChildNode(name),
                this.rrf, this.questionnaireUtils, this.formUtils, this.expressionUtils);
        }
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after)
    {
        return childNodeAdded(name, after);
    }

    @Override
    public void leave(NodeState before, NodeState after)
    {
        if (!this.formEditorHasChanged) {
            return;
        }

        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "computedAnswers");
        ResourceResolver sessionResolver = this.rrf.getThreadResourceResolver();
        try (ResourceResolver serviceResolver = this.rrf.getServiceResourceResolver(parameters)) {
            if (sessionResolver != null && serviceResolver != null) {
                this.currentSession = sessionResolver.adaptTo(Session.class);
                this.serviceSession = serviceResolver.adaptTo(Session.class);
                computeMissingAnswers(after);
            }
        } catch (LoginException e) {
            // Should not happen
        }
    }

    private void computeMissingAnswers(NodeState form)
    {
        // Get a list of all current answers for the form for use in computing answers
        Map<Node, NodeState> existingAnswers = getNodeAnswers(form);
        Map<String, Object> answersByQuestionName = getAnswersByQuestionName(existingAnswers);

        // Get a list of all unanswered computed questions that need to be calculated
        Node questionnaireNode = getQuestionnaire();
        if (questionnaireNode == null) {
            return;
        }
        QuestionTree computedQuestionsTree = getUnansweredComputedQuestions(questionnaireNode);

        // There are missing computed questions, let's create them!
        if (computedQuestionsTree != null) {
            // Create the missing structure, i.e. AnswerSection and Answer nodes
            Map<QuestionTree, NodeBuilder> answersToCompute =
                createMissingNodes(computedQuestionsTree, this.currentNodeBuilder);

            // Try to determine the right order in which answers should be computed, so that the answers that depend on
            // other computed answers are evaluated after all their dependencies have been evaluated
            Set<String> questionNames = answersToCompute.keySet().stream()
                .map(QuestionTree::getNode)
                .map(this.questionnaireUtils::getQuestionName)
                .collect(Collectors.toSet());
            Map<String, Set<String>> computedAnswerDependencies =
                answersToCompute.keySet().stream().map(question -> {
                    Set<String> dependencies = this.expressionUtils.getDependencies(question.getNode());
                    dependencies.retainAll(questionNames);
                    return Pair.of(this.questionnaireUtils.getQuestionName(question.getNode()), dependencies);
                }).collect(Collectors.toConcurrentMap(Pair::getKey, Pair::getValue));
            final List<String> orderedAnswersToCompute = sortDependencies(computedAnswerDependencies);

            // We have the right order, compute all the missing answers
            orderedAnswersToCompute.stream()
                // Get the right answer node
                .map(questionName -> answersToCompute.entrySet().stream()
                    .filter(
                        entry -> questionName.equals(this.questionnaireUtils.getQuestionName(entry.getKey().getNode())))
                    .findFirst().get())
                // Evaluate it
                .forEachOrdered(entry -> {
                    QuestionTree question = entry.getKey();
                    NodeBuilder answer = entry.getValue();
                    String result = computeAnswer(answersByQuestionName, question);
                    if (result == null) {
                        answer.removeProperty(FormUtils.VALUE_PROPERTY);
                    } else {
                        answer.setProperty(FormUtils.VALUE_PROPERTY, result, Type.STRING);
                    }
                    // Update the computed value in the map of existing answers
                    answersByQuestionName.put(this.questionnaireUtils.getQuestionName(question.getNode()), result);
                });
        }
    }

    private Node getQuestionnaire()
    {
        String questionnaireId = this.currentNodeBuilder.getProperty("questionnaire").getValue(Type.REFERENCE);
        try {
            return this.serviceSession.getNodeByIdentifier(questionnaireId);
        } catch (RepositoryException e) {
            return null;
        }
    }

    private List<String> sortDependencies(final Map<String, Set<String>> computedAnswerDependencies)
    {
        final List<String> result = new ArrayList<>();
        final Set<String> processedAnswers = new HashSet<>();
        computedAnswerDependencies.keySet()
            .forEach(answer -> addAnswer(answer, computedAnswerDependencies, result, processedAnswers));
        return result;
    }

    private void addAnswer(String answer, Map<String, Set<String>> dependencies, List<String> orderedAnswersToCompute,
        Set<String> processedAnswers)
    {
        if (!processedAnswers.contains(answer)) {
            processedAnswers.add(answer);
            dependencies.get(answer)
                .forEach(dependency -> addAnswer(dependency, dependencies, orderedAnswersToCompute, processedAnswers));
            orderedAnswersToCompute.add(answer);
        }
    }

    private Map<Node, NodeState> getNodeAnswers(NodeState currentNode)
    {
        Map<Node, NodeState> currentAnswers = new HashMap<>();
        if (currentNode.exists()) {
            if (this.formUtils.isAnswerSection(currentNode) || this.formUtils.isForm(currentNode)) {
                // Found a section: Recursively get all of this section's answers
                for (ChildNodeEntry childNode : currentNode.getChildNodeEntries()) {
                    currentAnswers.putAll(getNodeAnswers(childNode.getNodeState()));
                }
            } else if (this.formUtils.isAnswer(currentNode)) {
                // Found an answer. Store it using the question's UUID to easily compare with the questionnaire's
                // nodes to retrieve question names and saved answer nodes to avoid duplicating existing answers
                currentAnswers.put(this.formUtils.getQuestion(currentNode), currentNode);
            }
        }
        return currentAnswers;
    }

    // Returns a QuestionTree if any children of this node contains an unanswered computed question, else null
    private QuestionTree getUnansweredComputedQuestions(Node currentNode)
    {
        QuestionTree currentTree = null;

        try {
            if (this.questionnaireUtils.isQuestion(currentNode)) {
                if ("computed".equals(currentNode.getProperty("dataType").getString())) {
                    // Skip already answered questions
                    if (!this.computedAnswerChangeTracker.getModifiedAnswers().contains(currentNode.getIdentifier())) {
                        currentTree = new QuestionTree(currentNode, true);
                    }
                }
                // Ignore questions that are not computed questions
            } else if (this.questionnaireUtils.isQuestionnaire(currentNode)
                || this.questionnaireUtils.isSection(currentNode)) {
                // Recursively check if any children have a computed question
                QuestionTree newTree = new QuestionTree(currentNode, false);
                for (NodeIterator i = currentNode.getNodes(); i.hasNext();) {
                    Node child = i.nextNode();
                    QuestionTree childTree = getUnansweredComputedQuestions(child);
                    if (childTree != null) {
                        // Child has data that should be stored
                        newTree.getChildren().put(child.getName(), childTree);
                    }
                }

                // If this node has a child with a computed question, return this information
                if (newTree.getChildren().size() > 0) {
                    currentTree = newTree;
                }
            }
        } catch (RepositoryException e) {
            // Unable to retrieve type: skip
            LOGGER.warn(e.getMessage());
        }

        return currentTree;
    }

    // Convert a map of answered keyed by question UUID into a map keyed by question name
    private Map<String, Object> getAnswersByQuestionName(Map<Node, NodeState> answers)
    {
        return answers.entrySet().stream().map(entry -> {
            String questionName = this.questionnaireUtils.getQuestionName(entry.getKey());
            Object value = this.formUtils.getValue(entry.getValue());
            return Pair.of(questionName, value);
        }).filter(pair -> pair.getKey() != null && pair.getValue() != null)
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private Map<QuestionTree, NodeBuilder> createMissingNodes(QuestionTree questionTree,
        NodeBuilder currentNode)
    {
        try {
            if (questionTree.isQuestion()) {
                if (!currentNode.hasProperty("jcr:" + "primaryType")) {
                    // New node, insert all required properties
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                    currentNode.setProperty("jcr:created", dateFormat.format(new Date()), Type.DATE);
                    currentNode.setProperty("jcr:createdBy", this.currentSession.getUserID(), Type.NAME);
                    String questionReference = questionTree.getNode().getIdentifier();
                    currentNode.setProperty(FormUtils.QUESTION_PROPERTY, questionReference, Type.REFERENCE);
                    currentNode.setProperty("jcr:primaryType", "cards:ComputedAnswer", Type.NAME);
                    currentNode.setProperty("sling:resourceSuperType", FormUtils.ANSWER_RESOURCE, Type.STRING);
                    currentNode.setProperty("sling:resourceType", "cards/ComputedAnswer", Type.STRING);
                    currentNode.setProperty("statusFlags", "", Type.STRING);
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
                    currentNode.setProperty("statusFlags", "", Type.STRING);
                }
                Map<String, List<NodeBuilder>> childNodesByReference = getChildNodesByReference(currentNode);
                return createChildrenNodes(questionTree, childNodesByReference, currentNode);
            }
        } catch (RepositoryException e) {
            LOGGER.error("Error creating " + (questionTree.isQuestion() ? "question. " : "section. ")
                + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, List<NodeBuilder>> getChildNodesByReference(NodeBuilder nodeBuilder)
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

    private Map<QuestionTree, NodeBuilder> createChildrenNodes(QuestionTree computedQuestionTree,
        Map<String, List<NodeBuilder>> childNodesByReference,
        NodeBuilder nodeBuilder)
    {
        Map<QuestionTree, NodeBuilder> result = new HashMap<>();
        for (Map.Entry<String, QuestionTree> childQuestion : computedQuestionTree.getChildren().entrySet()) {
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

    private String computeAnswer(Map<String, Object> answers, QuestionTree computedQuestionTree)
    {
        return this.expressionUtils.evaluate(computedQuestionTree.getNode(), answers);
    }

    private final class ComputedAnswerChangeTracker extends DefaultEditor
    {
        private final Set<String> modifiedAnswers = new HashSet<>();

        private boolean inComputedAnswer;

        private String currentAnswer;

        @Override
        public void enter(NodeState before, NodeState after)
        {
            if ("cards:ComputedAnswer".equals(after.getName("jcr:primaryType"))) {
                this.inComputedAnswer = true;
                this.currentAnswer = ComputedAnswersEditor.this.formUtils.getQuestionIdentifier(after);
            }
        }

        @Override
        public void leave(NodeState before, NodeState after)
        {
            this.inComputedAnswer = false;
            this.currentAnswer = null;
        }

        @Override
        public void propertyAdded(PropertyState after)
        {
            if (this.inComputedAnswer) {
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

    static final class QuestionTree
    {
        private Map<String, QuestionTree> children;

        private Node node;

        private boolean isQuestion;

        QuestionTree(Node node, boolean isQuestion)
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
}
