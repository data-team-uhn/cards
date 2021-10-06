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
package io.uhndata.cards.internal;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
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
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link Editor} that calculates any computed answers that were not submitted by the client.
 *
 * @version $Id$
 */
public class ComputedAnswersEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputedAnswersEditor.class);

    private static final String PRIMARY_TYPE_PROPERTY = "jcr:primaryType";

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    private final ResourceResolver currentResourceResolver;

    private boolean isFormNode;

    private boolean formEditorHasChanged;

    private ComputedAnswerChangeTracker computedAnswerChangeTracker;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param resourceResolver a ResourceResolver object used to ultimately determine the logged-in user
     */
    public ComputedAnswersEditor(NodeBuilder nodeBuilder, ResourceResolver resourceResolver)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.currentResourceResolver = resourceResolver;
        this.computedAnswerChangeTracker = new ComputedAnswerChangeTracker();
    }

    @Override
    public void enter(NodeState before, NodeState after)
    {
        this.isFormNode = NodeUtils.isForm(this.currentNodeBuilder);
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
                this.currentResourceResolver);
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

        final Session thisSession = this.currentResourceResolver.adaptTo(Session.class);
        // Get a list of all current answers for the form for use in computing answers
        Map<String, NodeState> answers = getNodeAnswers(after);

        // Get a list of all unanswered computed questions that need to be calculated
        String questionnaireId = this.currentNodeBuilder.getProperty("questionnaire").getValue(Type.REFERENCE);
        try {
            Node questionnaireNode = thisSession.getNodeByIdentifier(questionnaireId);
            QuestionTree computedQuestionTree = getUnansweredComputedQuestions(questionnaireNode, answers);
            Map<String, NodeState> answersByQuestionName = getAnswersByQuestionName(thisSession, answers);

            if (computedQuestionTree != null) {
                Map<QuestionTree, NodeBuilder> answersToCompute = new HashMap<>();
                createUnansweredQuestions(computedQuestionTree,
                    this.currentNodeBuilder, answersToCompute);
                answersToCompute.forEach((question, answer) -> {
                    String result = computeAnswer(answersByQuestionName, question);
                    if (result == null) {
                        answer.removeProperty("value");
                    } else {
                        answer.setProperty("value", result, Type.STRING);
                    }
                    try {
                        answersByQuestionName.put(question.getNode().getName(), answer.getNodeState());
                    } catch (RepositoryException e) {
                        LOGGER.warn("Failed to access repository: {}", e.getMessage(), e);
                    }
                });
            }
        } catch (RepositoryException e) {
            // Could not find a questionnaire definition for this form: Can't calculate computed questions
        }
    }

    private Map<String, NodeState> getNodeAnswers(NodeState currentNode)
    {
        Map<String, NodeState> currentAnswers = new HashMap<>();
        if (currentNode.exists()) {
            PropertyState primaryType = currentNode.getProperty(PRIMARY_TYPE_PROPERTY);
            PropertyState superType = currentNode.getProperty("sling:resourceSuperType");
            if (primaryType != null
                && ("cards:AnswerSection".equals(primaryType.getValue(Type.NAME))
                    || "cards:Form".equals(primaryType.getValue(Type.NAME)))) {
                // Found a section: Recursively get all of this section's answers
                for (ChildNodeEntry childNode : currentNode.getChildNodeEntries()) {
                    currentAnswers.putAll(getNodeAnswers(childNode.getNodeState()));
                }
            } else if (superType != null && "cards/Answer".equals(superType.getValue(Type.STRING))) {
                // Found an answer. Store it using the question's UUID to easily compare with the questionnaire's
                // nodes to retrieve question names and saved answer nodes to avoid duplicating existing answers
                String currentName = NodeUtils.getQuestion(currentNode);
                currentAnswers.put(currentName, currentNode);
            } else {
                LOGGER.warn("Computed editor encountered unexpected child node of type {}",
                    primaryType == null ? null : primaryType.getValue(Type.STRING));
            }
        }
        return currentAnswers;
    }

    // Returns a QuestionTree if any children of this node contains an unanswered computed question, else null
    private QuestionTree getUnansweredComputedQuestions(Node currentNode, Map<String, NodeState> answers)
    {
        QuestionTree currentTree = null;

        try {
            if ("cards/Question".equals(currentNode.getProperty("sling:resourceType").getString())) {
                if ("computed".equals(currentNode.getProperty("dataType").getString())) {
                    // Skip already answered questions
                    if (!this.computedAnswerChangeTracker.getModifiedAnswers().contains(currentNode.getIdentifier())) {
                        currentTree = new QuestionTree(currentNode, true);
                    }
                }
                // Ignore questions that are not computed questions
            } else if (currentNode.isNodeType("cards:Questionnaire") || currentNode.isNodeType("cards:Section")) {
                // Recursively check if any children have a computed question
                QuestionTree newTree = new QuestionTree(currentNode, false);
                for (NodeIterator i = currentNode.getNodes(); i.hasNext();) {
                    Node child = i.nextNode();
                    QuestionTree childTree = getUnansweredComputedQuestions(child, answers);
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
    private Map<String, NodeState> getAnswersByQuestionName(Session thisSession, Map<String, NodeState> answers)
    {
        Map<String, NodeState> namedAnswers = new HashMap<>();
        for (Map.Entry<String, NodeState> entry : answers.entrySet()) {
            try {
                Node questionNode = thisSession.getNodeByIdentifier(entry.getKey());
                String questionName = questionNode.getName();
                namedAnswers.put(questionName, entry.getValue());
            } catch (RepositoryException e) {
                // Question node not found: Can't recieve question name so ignore this answer
                LOGGER.warn("Could not retrieve question node. " + e.getMessage());
            }
        }
        return namedAnswers;
    }

    private void createUnansweredQuestions(QuestionTree computedQuestionTree,
        NodeBuilder nodeBuilder, Map<QuestionTree, NodeBuilder> answersToCompute)
    {
        try {
            if (computedQuestionTree.isQuestion()) {
                if (!nodeBuilder.hasProperty(PRIMARY_TYPE_PROPERTY)) {
                    // New node, insert all required properties
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                    nodeBuilder.setProperty("jcr:created", dateFormat.format(new Date()), Type.DATE);
                    nodeBuilder.setProperty("jcr:createdBy",
                        this.currentResourceResolver.adaptTo(Session.class).getUserID(), Type.NAME);

                    String questionReference = computedQuestionTree.getNode().getIdentifier();
                    nodeBuilder.setProperty("question", questionReference, Type.REFERENCE);
                    nodeBuilder.setProperty(PRIMARY_TYPE_PROPERTY, "cards:ComputedAnswer", Type.NAME);
                    nodeBuilder.setProperty("sling:resourceSuperType", "cards/Answer", Type.STRING);
                    nodeBuilder.setProperty("sling:resourceType", "cards/ComputedAnswer", Type.STRING);
                    nodeBuilder.setProperty("statusFlags", "", Type.STRING);
                }
                answersToCompute.put(computedQuestionTree, nodeBuilder);
            } else {
                // Section
                if (!nodeBuilder.hasProperty(PRIMARY_TYPE_PROPERTY)) {
                    // Section must be created before primary type
                    nodeBuilder.setProperty("section", computedQuestionTree.getNode().getIdentifier(),
                        Type.REFERENCE);
                    nodeBuilder.setProperty(PRIMARY_TYPE_PROPERTY, "cards:AnswerSection", Type.NAME);
                    nodeBuilder.setProperty("sling:resourceSuperType", "cards/Resource", Type.STRING);
                    nodeBuilder.setProperty("sling:resourceType", "cards/AnswerSection", Type.STRING);
                    nodeBuilder.setProperty("statusFlags", "", Type.STRING);
                }
                Map<String, List<NodeBuilder>> childNodesByReference = getChildNodesByReference(nodeBuilder);
                createChildrenNodes(computedQuestionTree, childNodesByReference, nodeBuilder,
                    answersToCompute);
            }
        } catch (RepositoryException e) {
            LOGGER.error("Error creating " + (computedQuestionTree.isQuestion() ? "question. " : "section. ")
                + e.getMessage());
        }

    }

    private Map<String, List<NodeBuilder>> getChildNodesByReference(NodeBuilder nodeBuilder)
    {
        Map<String, List<NodeBuilder>> result = new HashMap<>();
        for (String childNodeName : nodeBuilder.getChildNodeNames()) {
            NodeBuilder childNode = nodeBuilder.getChildNode(childNodeName);
            String childIdentifier;
            if (childNode.hasProperty("section")) {
                childIdentifier = NodeUtils.getSection(childNode);
            } else if (childNode.hasProperty("question")) {
                childIdentifier = NodeUtils.getQuestion(childNode);
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

    private void createChildrenNodes(QuestionTree computedQuestionTree,
        Map<String, List<NodeBuilder>> childNodesByReference,
        NodeBuilder nodeBuilder,
        Map<QuestionTree, NodeBuilder> answersToCompute)
    {
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
                        createUnansweredQuestions(childTree, childNode, answersToCompute);
                    }
                }
                while (numberOfInstances < expectedNumberOfInstances) {
                    NodeBuilder childNodeBuilder = nodeBuilder.setChildNode(UUID.randomUUID().toString());
                    createUnansweredQuestions(childTree, childNodeBuilder, answersToCompute);
                    numberOfInstances++;
                }
            } catch (RepositoryException e) {
                // Node has no accessible identifier, skip
            }
        }
    }

    private String computeAnswer(Map<String, NodeState> answers, QuestionTree computedQuestionTree)
    {
        return ExpressionEvaluator.evaluate(computedQuestionTree.getNode(), answers);
    }

    private static final class ComputedAnswerChangeTracker extends DefaultEditor
    {
        private final Set<String> modifiedAnswers = new HashSet<>();

        private boolean inComputedAnswer;

        private String currentAnswer;

        @Override
        public void enter(NodeState before, NodeState after)
        {
            if ("cards:ComputedAnswer".equals(after.getName(PRIMARY_TYPE_PROPERTY))) {
                this.inComputedAnswer = true;
                this.currentAnswer = NodeUtils.getQuestion(after);
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

    private static final class QuestionTree
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
                + (this.node == null ? "" : " node: " + this.node.toString()) + "}";
        }
    }

    private static final class ExpressionEvaluator
    {
        private static final String START_MARKER = "@{";

        private static final String END_MARKER = "}";

        private static final String DEFAULT_MARKER = ":-";

        static String evaluate(final Node question, final Map<String, NodeState> answers)
        {
            try {
                String expression = question.getProperty("expression").getString();
                ParsedExpression parsedExpression = parseExpressionInputs(answers, expression);
                if (parsedExpression.hasMissingValue()) {
                    return null;
                }

                ScriptEngineManager manager = new ScriptEngineManager();
                ScriptEngine engine = manager.getEngineByName("JavaScript");

                Bindings env = engine.createBindings();
                parsedExpression.getInputs().forEach((key, value) -> env.put(key, value));
                Object result = engine.eval("(function(){" + parsedExpression.getExpression() + "})()", env);
                return ValueFormatter.formatResult(result);
            } catch (ScriptException e) {
                LOGGER.warn("Evaluating the expression for question {} failed: {}", question,
                    e.getMessage(), e);
            } catch (RepositoryException e) {
                LOGGER.warn("Failed to access computed question expression: {}", e.getMessage(), e);
            }
            return null;
        }

        private static ParsedExpression parseExpressionInputs(Map<String, NodeState> answers, String expression)
        {
            String expr = expression;
            Map<String, Object> inputs = new HashMap<>();
            int start = expr.indexOf(START_MARKER);
            int end = expr.indexOf(END_MARKER, start);
            boolean missingValue = false;

            while (start > -1 && end > -1) {
                int optionStart = expr.indexOf(DEFAULT_MARKER, start);
                boolean hasOption = optionStart > -1 && optionStart < end;

                String inputName;
                String defaultValue = null;

                if (hasOption) {
                    inputName = expr.substring(start + START_MARKER.length(), optionStart);
                    defaultValue = expr.substring(optionStart + DEFAULT_MARKER.length(), end);
                } else {
                    inputName = expr.substring(start + START_MARKER.length(), end);
                }

                if (!inputs.containsKey(inputName)) {
                    Object value = getAnswerNodeStateValue(answers.get(inputName));
                    if (value == null) {
                        value = defaultValue;
                    }
                    if (value == null) {
                        missingValue = true;
                    }
                    inputs.put(inputName, value);
                }

                // Remove the start and end tags as well as the default option if provided, leaving just
                // the Javascript variable name
                expr = expr.substring(0, start) + expr.substring(start + START_MARKER.length(), hasOption
                    ? optionStart : end) + expr.substring(end + END_MARKER.length());

                start = expr.indexOf(START_MARKER, (hasOption ? optionStart : end) - START_MARKER.length());
                end = expr.indexOf(END_MARKER, start);
            }
            return new ParsedExpression(inputs, expr, missingValue);
        }

        private static Object getAnswerNodeStateValue(NodeState answerNodeState)
        {
            if (answerNodeState == null) {
                return null;
            }
            Object result = null;
            PropertyState valuePropertyState = answerNodeState.getProperty("value");
            if (valuePropertyState != null) {
                Type<?> valueType = valuePropertyState.getType();

                if (valuePropertyState.isArray()) {
                    result = new Object[valuePropertyState.count()];
                    for (int i = 0; i < valuePropertyState.count(); i++) {
                        ((Object[]) result)[i] = valuePropertyState.getValue(valueType, i);
                    }
                } else {
                    result = valuePropertyState.getValue(valueType);
                }
            }
            return result;
        }

        private static final class ParsedExpression
        {
            private final Map<String, Object> inputs;

            private final String expression;

            private final boolean missingValue;

            ParsedExpression(Map<String, Object> inputs, String expression, boolean missingValue)
            {
                this.inputs = inputs;
                this.expression = expression;
                this.missingValue = missingValue;
            }

            public Map<String, Object> getInputs()
            {
                return this.inputs;
            }

            public String getExpression()
            {
                return this.expression;
            }

            public boolean hasMissingValue()
            {
                return this.missingValue;
            }
        }

        private static final class ValueFormatter
        {
            static String formatResult(Object rawResult)
            {
                if (rawResult == null) {
                    return null;
                }

                String formattedResult = String.valueOf(rawResult);

                if (rawResult instanceof Double || rawResult instanceof Float) {
                    Number result = (Number) rawResult;
                    if (result.doubleValue() == result.longValue()) {
                        formattedResult = String.valueOf(result.longValue());
                    } else {
                        formattedResult = String.valueOf(result.doubleValue());
                    }
                } else if (rawResult instanceof Date) {
                    formattedResult = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                        .format(((Date) rawResult).toInstant().atZone(ZoneId.systemDefault()));
                } else if (rawResult instanceof Calendar) {
                    Calendar result = (Calendar) rawResult;
                    formattedResult = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                        .format(result.toInstant().atZone(result.getTimeZone().toZoneId()));
                }
                return formattedResult;
            }
        }
    }

    private static final class NodeUtils
    {
        static boolean isForm(NodeBuilder node)
        {
            // Store the current node's type
            PropertyState primaryType = node.getProperty(PRIMARY_TYPE_PROPERTY);
            if (primaryType != null) {
                final String nodeType = primaryType.getValue(Type.NAME);
                if ("cards:Form".equals(nodeType)) {
                    return true;
                }
            }
            return false;
        }

        static String getQuestion(final NodeState node)
        {
            return node.getProperty("question").getValue(Type.REFERENCE);
        }

        static String getQuestion(final NodeBuilder node)
        {
            return node.getProperty("question").getValue(Type.REFERENCE);
        }

        static String getSection(final NodeBuilder node)
        {
            return node.getProperty("section").getValue(Type.REFERENCE);
        }
    }
}
