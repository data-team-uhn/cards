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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.ExpressionUtils;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

/**
 * An {@link Editor} that calculates any computed answers that were not submitted by the client.
 *
 * @version $Id$
 */
public class ComputedAnswersEditor extends AnswersEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputedAnswersEditor.class);

    private final ExpressionUtils expressionUtils;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param resolver the resource resolver which can provide access to JCR sessions
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param expressionUtils for evaluating the computed questions
     */
    public ComputedAnswersEditor(final NodeBuilder nodeBuilder, final ResourceResolver resolver,
        final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils, final ExpressionUtils expressionUtils)
    {
        super(nodeBuilder, resolver, questionnaireUtils, formUtils);
        this.expressionUtils = expressionUtils;
    }

    @Override
    protected Logger getLogger()
    {
        return LOGGER;
    }

    @Override
    protected ComputedAnswerChangeTracker getAnswerChangeTracker()
    {
        return new ComputedAnswerChangeTracker();
    }

    @Override
    protected ComputedAnswersEditor getNewEditor(String name)
    {
        return new ComputedAnswersEditor(this.currentNodeBuilder.getChildNode(name),
            this.resolver, this.questionnaireUtils, this.formUtils, this.expressionUtils);
    }

    @Override
    protected ComputedAnswerNodeTypes getNewAnswerNodeTypes(Node node)
        throws RepositoryException
    {
        return new ComputedAnswerNodeTypes(node);
    }

    @Override
    protected boolean isQuestionNodeMatchingType(Node node)
    {
        return this.questionnaireUtils.isComputedQuestion(node);
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        if (this.isFormNode) {
            this.shouldRunOnLeave = true;
            // No need to descend further down, we already know that this is a form that has changes
            return this.answerChangeTracker;
        } else {
            return getNewEditor(name);
        }
    }

    @Override
    protected void handleLeave(final NodeState form)
    {
        // Get a list of all current answers for the form for use in computing answers
        final Map<String, Object> answersByQuestionName = getNodeAnswers(form);

        // Get a list of all unanswered computed questions that need to be calculated
        final Node questionnaireNode = getQuestionnaire();
        if (questionnaireNode == null) {
            return;
        }
        final QuestionTree computedQuestionsTree =
            getUnansweredMatchingQuestions(questionnaireNode);

        // There are missing computed questions, let's create them!
        if (computedQuestionsTree != null) {
            // Create the missing structure, i.e. AnswerSection and Answer nodes
            final Map<QuestionTree, NodeBuilder> answersToCompute =
                createMissingNodes(computedQuestionsTree, this.currentNodeBuilder);

            // Try to determine the right order in which answers should be computed, so that the answers that depend on
            // other computed answers are evaluated after all their dependencies have been evaluated
            final Set<String> questionNames = answersToCompute.keySet().stream()
                .map(QuestionTree::getNode)
                .map(this.questionnaireUtils::getQuestionName)
                .collect(Collectors.toSet());
            final Map<String, Set<String>> computedAnswerDependencies =
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
                    computeAnswer(entry, answersByQuestionName);
                });
        }
    }

    private void computeAnswer(final Map.Entry<QuestionTree, NodeBuilder> entry,
        final Map<String, Object> answersByQuestionName)
    {
        final QuestionTree question = entry.getKey();
        final NodeBuilder answer = entry.getValue();
        Type<?> resultType = Type.STRING;
        try {
            ComputedAnswerNodeTypes types = new ComputedAnswerNodeTypes(question.getNode());
            resultType = types.getDataType();
        } catch (RepositoryException e) {
            LOGGER.error("Error typing value for question. " + e.getMessage());
        }
        Object result = this.expressionUtils.evaluate(question.getNode(), answersByQuestionName, resultType);

        if (result == null || (result instanceof String && "null".equals(result))) {
            answer.removeProperty(FormUtils.VALUE_PROPERTY);
        } else {
            // Type erasure makes the actual type irrelevant, there's only one real implementation method
            // The implementation can extract the right type from the type object
            @SuppressWarnings("unchecked")
            Type<Object> untypedResultType = (Type<Object>) resultType;
            answer.setProperty(FormUtils.VALUE_PROPERTY, result, untypedResultType);
        }
        // Update the computed value in the map of existing answers
        String questionName = this.questionnaireUtils.getQuestionName(question.getNode());
        if (answersByQuestionName.containsKey(questionName)) {
            // Question has multiple answers. Ignore this answer, just keep previous.
            // TODO: Implement better recurrent section handling
        } else {
            answersByQuestionName.put(questionName, result);
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

    private void addAnswer(final String answer, final Map<String, Set<String>> dependencies,
        final List<String> orderedAnswersToCompute, final Set<String> processedAnswers)
    {
        if (!processedAnswers.contains(answer)) {
            processedAnswers.add(answer);
            dependencies.get(answer)
                .forEach(dependency -> addAnswer(dependency, dependencies, orderedAnswersToCompute, processedAnswers));
            orderedAnswersToCompute.add(answer);
        }
    }

    private Map<String, Object> getNodeAnswers(final NodeState currentNode)
    {
        final Map<String, Object> currentAnswers = new HashMap<>();
        if (currentNode.exists()) {
            if (this.isAnswerSection(currentNode) || this.formUtils.isForm(currentNode)) {
                // Found a section: Recursively get all of this section's answers
                for (ChildNodeEntry childNode : currentNode.getChildNodeEntries()) {
                    currentAnswers.putAll(getNodeAnswers(childNode.getNodeState()));
                }
            } else if (this.isAnswer(currentNode)) {
                String questionName = this.questionnaireUtils.getQuestionName(this.formUtils.getQuestion(currentNode));
                Object value = this.formUtils.getValue(currentNode);
                if (questionName != null && value != null && !currentAnswers.containsKey(questionName)) {
                    // Found an answer. Store it using the question's name to easily compare with
                    // saved answer nodes to avoid duplicating existing answers
                    // TODO: Implement better recurrent section handling
                    currentAnswers.put(questionName, value);
                }

            }
        }
        return currentAnswers;
    }

    private final class ComputedAnswerChangeTracker extends AbstractAnswerChangeTracker
    {
        ComputedAnswerChangeTracker()
        {
            super(ComputedAnswersEditor.this.formUtils);
        }

        @Override
        public boolean isMatchedAnswerNode(NodeState after, String questionId)
        {
            if ("cards:ComputedAnswer".equals(after.getName("jcr:primaryType"))) {
                return true;
            } else if (questionId != null) {
                Node questionNode = ComputedAnswersEditor.this.questionnaireUtils.getQuestion(questionId);
                try {
                    if (questionNode != null && questionNode.hasProperty("entryMode")
                        && "computed".equals(questionNode.getProperty("entryMode").getString())) {
                        return true;
                    }
                } catch (RepositoryException e) {
                    // Can't access this answer's question. Ignore
                    return false;
                }
            }
            return false;
        }
    }

    private final class ComputedAnswerNodeTypes extends AnswerNodeTypes
    {
        ComputedAnswerNodeTypes(final Node questionNode) throws RepositoryException
        {
            super(questionNode, "cards:ComputedAnswer", "cards/ComputedAnswer");
        }
    }
}
