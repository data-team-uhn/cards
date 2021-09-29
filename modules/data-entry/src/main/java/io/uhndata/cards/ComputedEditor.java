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
package io.uhndata.cards;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.jackrabbit.oak.api.CommitFailedException;
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
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class ComputedEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputedEditor.class);
    private static String startTag = "@{";
    private static String endTag = "}";
    private static String defaultTag = ":-";
    private static String primaryTypeDefinition = "jcr:primaryType";

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;
    private final ResourceResolver currentResourceResolver;
    private final NodeBuilder versionableAncestor;

    private String nodeType = "";
    private boolean formEditorHasChanged;
    private int numberOfCreatedQuestions;

    private class QuestionTree
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

    private class ParsedExpression
    {
        private final List<String> inputNames;
        private final List<Object> inputValues;
        private final String expression;
        private final boolean missingValue;

        ParsedExpression(List<String> inputNames, List<Object> inputValues, String expression, boolean missingValue)
        {
            this.inputNames = inputNames;
            this.inputValues = inputValues;
            this.expression = expression;
            this.missingValue = missingValue;
        }

        public List<String> getInputNames()
        {
            return this.inputNames;
        }

        public List<Object> getInputValues()
        {
            return this.inputValues;
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

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param resourceResolver a ResourceResolver object used to ultimately determine the logged-in user
     * @param versionableAncestor a NodeBuilder for the ancestor object that is of type mix:lastModified
     */
    public ComputedEditor(NodeBuilder nodeBuilder, ResourceResolver resourceResolver,
        NodeBuilder versionableAncestor)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.currentResourceResolver = resourceResolver;
        this.versionableAncestor = versionableAncestor;
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to
    // be invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        if ("cards:Form".equals(this.nodeType)) {
            // Found a modified form. Flag for the current editor to checking computed answers.
            // No need to make any child editors.
            this.formEditorHasChanged = true;
            return null;
        } else {
            return new ComputedEditor(this.currentNodeBuilder.getChildNode(name),
                this.currentResourceResolver,
                this.versionableAncestor);
        }
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return childNodeAdded(name, after);
    }

    @Override
    public void enter(NodeState before, NodeState after)
    {
        // Store the current node's type
        PropertyState primaryType = this.currentNodeBuilder.getProperty(primaryTypeDefinition);
        if (primaryType != null) {
            this.nodeType = primaryType.getValue(Type.NAME);
        }
    }

    @Override
    public void leave(NodeState before, NodeState after)
    {
        final Session thisSession = this.currentResourceResolver.adaptTo(Session.class);
        if ("cards:Form".equals(this.nodeType) && this.formEditorHasChanged) {
            // Get a list of all current answers for the form for use in computing answers
            Map<String, NodeState> answers = getNodeAnswers(after);

            // Get a list of all unanswered computed questions that need to be calculated
            String questionnaireId = this.currentNodeBuilder.getProperty("questionnaire").getValue(Type.REFERENCE);
            try {
                Node questionnaireNode = thisSession.getNodeByIdentifier(questionnaireId);
                QuestionTree computedQuestionTree = getUnansweredComputedQuestions(questionnaireNode, answers);
                Map<String, NodeState> answersByQuestionName = getAnswersByQuestionName(thisSession, answers);

                if (computedQuestionTree != null) {
                    computeUnansweredQuestions(answersByQuestionName, computedQuestionTree,
                        this.currentNodeBuilder);
                }

                if (this.numberOfCreatedQuestions > 0) {
                    LOGGER.info("ComputedEditor created " + this.numberOfCreatedQuestions + " computed answers");
                }
            } catch (RepositoryException e) {
                // Could not find a questionnaire definition for this form: Can't calculate computed questions
            }
        }
    }

    private Map<String, NodeState> getNodeAnswers(NodeState currentNode)
    {
        Map<String, NodeState> currentAnswers = new HashMap<>();
        if (currentNode.exists()) {
            PropertyState primaryType = currentNode.getProperty(primaryTypeDefinition);
            PropertyState superType = currentNode.getProperty("sling:resourceSuperType");
            if (primaryType != null
                && ("cards:AnswerSection".equals(primaryType.getValue(Type.NAME))
                || "cards:Form".equals(primaryType.getValue(Type.NAME)))
            ) {
                // Found a section: Recursively get all of this section's answers
                for (ChildNodeEntry childNode : currentNode.getChildNodeEntries()) {
                    currentAnswers.putAll(getNodeAnswers(childNode.getNodeState()));
                }
            } else if (superType != null && "cards/Answer".equals(superType.getValue(Type.STRING))) {
                // Found an answer. Store it using the question's UUID to easily compare with the questionnaire's
                // nodes to retrieve question names and saved answer nodes to avoid duplicating existing answers
                String currentName = currentNode.getProperty("question").getValue(Type.STRING);
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
                    if (!answers.containsKey(currentNode.getIdentifier())) {
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
        for (Entry<String, NodeState> entry : answers.entrySet()) {
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

    private void computeUnansweredQuestions(Map<String, NodeState> answers, QuestionTree computedQuestionTree,
        NodeBuilder nodeBuilder)
    {
        if (!nodeBuilder.hasProperty(primaryTypeDefinition)) {
            // New node, insert all required properties
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                nodeBuilder.setProperty("jcr:created", dateFormat.format(new Date()), Type.DATE);
                nodeBuilder.setProperty("jcr:createdBy",
                    this.currentResourceResolver.adaptTo(Session.class).getUserID(), Type.NAME);
                if (computedQuestionTree.isQuestion()) {
                    String result = computeQuestion(answers, computedQuestionTree, nodeBuilder);
                    if (result == null) {
                        nodeBuilder.remove();
                        return;
                    } else {
                        this.numberOfCreatedQuestions++;
                    }

                    String questionReference = computedQuestionTree.getNode().getIdentifier();
                    nodeBuilder.setProperty("question", questionReference, Type.REFERENCE);
                    nodeBuilder.setProperty(primaryTypeDefinition, "cards:ComputedAnswer", Type.NAME);
                    nodeBuilder.setProperty("sling:resourceSuperType", "cards/Answer", Type.STRING);
                    nodeBuilder.setProperty("sling:resourceType", "cards/ComputedAnswer", Type.STRING);
                    nodeBuilder.setProperty("value", result, Type.STRING);
                    answers.put(computedQuestionTree.getNode().getName(), nodeBuilder.getNodeState());
                } else {

                    // Section must be created before primary type
                    nodeBuilder.setProperty("section", computedQuestionTree.getNode().getIdentifier(),
                        Type.REFERENCE);
                    nodeBuilder.setProperty(primaryTypeDefinition, "cards:AnswerSection", Type.NAME);
                    nodeBuilder.setProperty("sling:resourceSuperType", "cards/Resource", Type.STRING);
                    nodeBuilder.setProperty("sling:resourceType", "cards/AnswerSection", Type.STRING);
                }

                nodeBuilder.setProperty("statusFlags", "", Type.STRING);
            } catch (RepositoryException e) {
                LOGGER.error("Error creating " + (computedQuestionTree.isQuestion() ? "question. " : "section. ")
                    + e.getMessage());
            }
        }

        if (!computedQuestionTree.isQuestion()) {
            Map<String, List<NodeBuilder>> childNodesByReference = getChildNodesByReference(nodeBuilder);
            createChildrenNodes(computedQuestionTree, childNodesByReference, answers, nodeBuilder);
        }
    }

    private Map<String, List<NodeBuilder>> getChildNodesByReference(NodeBuilder nodeBuilder)
    {
        Map<String, List<NodeBuilder>> result = new HashMap<>();
        for (String childNodeName : nodeBuilder.getChildNodeNames()) {
            NodeBuilder childNode = nodeBuilder.getChildNode(childNodeName);
            String childIdentifier;
            if (childNode.hasProperty("section")) {
                childIdentifier = childNode.getProperty("section").getValue(Type.STRING);
            } else if (childNode.hasProperty("question")) {
                childIdentifier = childNode.getProperty("question").getValue(Type.STRING);
            } else {
                continue;
            }

            List<NodeBuilder> childNodes = result.containsKey(childIdentifier)
                ? result.get(childIdentifier)
                : new ArrayList<NodeBuilder>();
            childNodes.add(childNode);
            result.put(childIdentifier, childNodes);
        }
        return result;
    }

    private void createChildrenNodes(QuestionTree computedQuestionTree,
        Map<String, List<NodeBuilder>> childNodesByReference,
        Map<String, NodeState> answers,
        NodeBuilder nodeBuilder)
    {
        for (Entry<String, QuestionTree> childQuestion : computedQuestionTree.getChildren().entrySet()) {
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
                        computeUnansweredQuestions(answers, childTree, childNode);
                    }
                }
                while (numberOfInstances < expectedNumberOfInstances) {
                    NodeBuilder childNodeBuilder = nodeBuilder.setChildNode(UUID.randomUUID().toString());
                    computeUnansweredQuestions(answers, childTree, childNodeBuilder);
                    numberOfInstances++;
                }
            } catch (RepositoryException e) {
                // Node has no accessible identifier, skip
            }
        }
    }

    private String computeQuestion(Map<String, NodeState> answers, QuestionTree computedQuestionTree,
        NodeBuilder nodeBuilder)
    {
        try {
            String expression = computedQuestionTree.getNode().getProperty("expression").getString();
            ParsedExpression parsedExpression = parseExpressionInputs(answers, expression);
            if (parsedExpression.hasMissingValue()) {
                return null;
            }
            expression = "var compute = function(" + String.join(", ", parsedExpression.getInputNames())
                + ") {" + parsedExpression.getExpression() + "}";

            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            engine.eval(expression);
            Invocable invocable = (Invocable) engine;
            Object result = invocable.invokeFunction("compute",
                parsedExpression.getInputValues().toArray(new Object[0]));
            return result == null ? null : String.valueOf(result);
        } catch (ScriptException e) {
            LOGGER.warn("JS failed " + e.getMessage());
        } catch (PathNotFoundException | ValueFormatException e) {
            LOGGER.warn("JS failed " + e.getMessage());
        } catch (RepositoryException e) {
            LOGGER.warn("JS failed " + e.getMessage());
        } catch (NoSuchMethodException e) {
            //Do nothing
        }
        return null;
    }

    private ParsedExpression parseExpressionInputs(Map<String, NodeState> answers, String expression)
    {
        String expr = expression;
        List<String> inputNames = new ArrayList<>();
        List<Object> inputValues = new ArrayList<>();
        int start = expr.indexOf(startTag);
        int end = expr.indexOf(endTag, start);
        boolean missingValue = false;

        while (start > -1 && end > -1) {
            int optionStart = expr.indexOf(defaultTag, start);
            boolean hasOption = optionStart > -1 && optionStart < end;

            String inputName;
            String defaultValue = null;

            if (hasOption) {
                inputName = expr.substring(start + startTag.length(), optionStart);
                defaultValue = expr.substring(optionStart + defaultTag.length(), end);
            } else {
                inputName = expr.substring(start + startTag.length(), end);
            }

            if (!inputNames.contains(inputName)) {
                inputNames.add(inputName);
                Object value = answers.get(inputName) != null
                    ? getAnswerNodeStateValue(answers.get(inputName))
                    : defaultValue;
                if (value == null) {
                    missingValue = true;
                }
                inputValues.add(value);
            }

            // Remove the start and end tags as well as the default option if provided, leaving just
            // the Javascript variable name
            expr = expr.substring(0, start) + expr.substring(start + startTag.length(), hasOption
            ? optionStart : end) + expr.substring(end + endTag.length());

            start = expr.indexOf(startTag, (hasOption ? optionStart : end) - startTag.length());
            end = expr.indexOf(endTag, start);
        }
        return new ParsedExpression(inputNames, inputValues, expr, missingValue);
    }

    private Object getAnswerNodeStateValue(NodeState answerNodeState)
    {
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
}
