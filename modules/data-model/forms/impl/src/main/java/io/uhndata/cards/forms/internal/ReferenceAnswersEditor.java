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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * An {@link Editor} that fills out any reference answers for a new form.
 *
 * @version $Id$
 */
public class ReferenceAnswersEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceAnswersEditor.class);

    private final NodeBuilder currentNodeBuilder;

    /** The current user session. */
    private final Session currentSession;
    private Session serviceSession;

    private final ResourceResolverFactory rrf;

    private final SubjectUtils subjectUtils;
    private final QuestionnaireUtils questionnaireUtils;
    private final FormUtils formUtils;

    private final boolean isFormNode;

    private final String serviceName = "referenceAnswers";

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param currentSession the current user session
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param subjectUtils for working with subject data
     */
    public ReferenceAnswersEditor(final NodeBuilder nodeBuilder, final Session currentSession,
        final ResourceResolverFactory rrf, final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils,
        final SubjectUtils subjectUtils)
    {
        this.subjectUtils = subjectUtils;
        this.currentNodeBuilder = nodeBuilder;
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.currentSession = currentSession;
        this.rrf = rrf;
        this.isFormNode = this.formUtils.isForm(nodeBuilder);
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        if (this.isFormNode) {
            // No need to descend further down, we already know that this is a form that has changes
            return null;
        } else {
            return new ReferenceAnswersEditor(this.currentNodeBuilder.getChildNode(name), this.currentSession,
                this.rrf, this.questionnaireUtils, this.formUtils, this.subjectUtils);
        }
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        return childNodeAdded(name, after);
    }

    protected boolean isReferenceQuestion(Node node)
    {
        return this.questionnaireUtils.isReferenceQuestion(node);
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
    {
        try (ResourceResolver serviceResolver =
            this.rrf.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, this.serviceName))) {
            if (serviceResolver != null) {
                this.serviceSession = serviceResolver.adaptTo(Session.class);

                if (!this.isFormNode) {
                    // Only process forms
                    return;
                }

                // Get a list of all reference questions
                final Node questionnaireNode = this.formUtils.getQuestionnaire(this.currentNodeBuilder);
                if (questionnaireNode == null) {
                    return;
                }
                // A map of reference questions with the question path as the key
                final Map<String, Node> referenceQuestions = new HashMap<>();
                getChildReferenceQuestions(questionnaireNode, referenceQuestions);

                // If the questionnaire has no reference questions, nothing to do
                if (referenceQuestions.size() == 0) {
                    return;
                }

                // A set of all the reference answers that do not currently reference a source answer
                final Set<NodeBuilder> unlinkedReferenceAnswers = new HashSet<>();
                // A map of all the answer sections in this form, keyed by section id
                final Map<String, NodeBuilder> answerSections = new HashMap<>();
                // A map of all the forms referenced by one of this form's reference answers
                // Key is the questionnaire id, value is the form node
                final Set<String> sourceForms = new HashSet<>();

                // Iterate through all answers looking for unlinked reference answers, referenced forms and
                // clearing reference questions that have been answered
                iterateAnswers(this.currentNodeBuilder, unlinkedReferenceAnswers, sourceForms,
                    referenceQuestions, answerSections);

                createMissingReferenceAnswers(referenceQuestions, unlinkedReferenceAnswers, answerSections);

                // There are missing reference questions, let's create them!
                if (unlinkedReferenceAnswers.size() > 0) {
                    unlinkedReferenceAnswers.stream().forEach(answer -> {
                        processUnlinkedAnswer(answer, sourceForms);
                    });
                }
            }
        } catch (LoginException e) {
            // Should not happen
        } finally {
            this.serviceSession = null;
        }
    }

    // Returns a map of reference questions as entries keyed by their path
    @SuppressWarnings("unchecked")
    private void getChildReferenceQuestions(Node node, Map<String, Node> referenceQuestions)
    {
        try {
            if (this.questionnaireUtils.isReferenceQuestion(node)) {
                referenceQuestions.put(node.getPath(), node);
            } else if (this.questionnaireUtils.isSection(node)
                || this.questionnaireUtils.isQuestionnaire(node))
            {
                node.getNodes()
                    .forEachRemaining(childNode -> getChildReferenceQuestions((Node) childNode, referenceQuestions));
            }
        } catch (RepositoryException e) {
            // Could not process node - do nothing
        }
    }

    // Traverse through all answers on this form.
    // Record all:
    // - Reference answers that do not link to a source answer
    // - Forms that are linked to by a reference answer
    // - answerSections
    // Remove any reference questions from the set of questions that already have a reference answer
    private void iterateAnswers(final NodeBuilder node, Set<NodeBuilder> unlinkedReferenceAnswers,
        Set<String> sourceForms, Map<String, Node> referenceQuestions, Map<String, NodeBuilder> answerSections)
    {
        if (this.formUtils.isAnswer(node)
            && this.questionnaireUtils.isReferenceQuestion(this.formUtils.getQuestion(node)))
        {
            try {
                // Found a reference answer
                String questionPath = this.formUtils.getQuestion(node).getPath();
                // Remove the answers' question from the map of questions that will need to be processed later
                referenceQuestions.remove(questionPath);

                if (!node.hasProperty("copiedFrom")) {
                    // Reference answer is not linked to a source yet
                    unlinkedReferenceAnswers.add(node);
                } else {
                    // Reference answer is linked to a source: record the form
                    Node sourceAnswer = this.serviceSession.getNode(
                        node.getProperty("copiedFrom").getValue(Type.REFERENCE));
                    Node form = this.formUtils.getForm(sourceAnswer);
                    String questionnaireId = this.formUtils.getQuestionnaireIdentifier(form);
                    sourceForms.add(questionnaireId);
                }
            } catch (RepositoryException e) {
                // Unable to process answer - do nothing
            }
        } else if (this.formUtils.isAnswerSection(node)) {
            // Record this section and traverse
            answerSections.put(this.formUtils.getSectionIdentifier(node), node);
            node.getChildNodeNames().forEach(name -> iterateAnswers(node.getChildNode(name),
                unlinkedReferenceAnswers, sourceForms, referenceQuestions, answerSections));
        } else if (this.formUtils.isForm(node)) {
            // Traverse the base form
            node.getChildNodeNames().forEach(name -> iterateAnswers(node.getChildNode(name),
                unlinkedReferenceAnswers, sourceForms, referenceQuestions, answerSections));
        }
    }

    // If the parent section or form exists, create any missing reference answers and record it's existance
    private void createMissingReferenceAnswers(Map<String, Node> referenceQuestions,
        Set<NodeBuilder> unlinkedReferenceAnswers, Map<String, NodeBuilder> answerSections)
    {
        referenceQuestions.values().forEach(question -> createMissingReferenceAnswer(question,
            unlinkedReferenceAnswers, answerSections));
    }

    // If the parent section or form exists, create the specified reference answer and record it's existance
    private void createMissingReferenceAnswer(Node question, Set<NodeBuilder> unlinkedReferenceAnswers,
        Map<String, NodeBuilder> answerSections)
    {
        try {
            Node parent = question.getParent();
            if (this.questionnaireUtils.isQuestionnaire(parent)) {
                // Reference question is not in a section: can always be created
                generateAnswer(this.currentNodeBuilder, question, unlinkedReferenceAnswers);
            } else if (this.questionnaireUtils.isSection(parent)
                && answerSections.containsKey(parent.getIdentifier())) {
                generateAnswer(answerSections.get(parent.getIdentifier()), question, unlinkedReferenceAnswers);
            }
        } catch (RepositoryException e) {
            // Should not happen
        }
    }

    // Create an answer for the specified question as a child of the provided parent.
    // Does not generate an answer value but does generate all other required properties.
    // Record the new answer.
    private void generateAnswer(NodeBuilder parentBuilder, Node question, Set<NodeBuilder> unlinkedReferenceAnswers)
        throws RepositoryException
    {
        // Reference question should be in a section that exists: generate an anser for it
        NodeBuilder answer = parentBuilder.setChildNode(UUID.randomUUID().toString());
        // Set up answer properties
        answer.setProperty(FormUtils.QUESTION_PROPERTY, question.getIdentifier(), Type.REFERENCE);
        String type = question.getProperty("dataType").getString();
        String primaryType = "cards:" + StringUtils.capitalize(type) + "Answer";
        String resourceType = "cards/" + StringUtils.capitalize(type) + "Answer";
        answer.setProperty("jcr:primaryType", primaryType, Type.NAME);
        answer.setProperty("question", question.getIdentifier(), Type.REFERENCE);
        answer.setProperty("sling:resourceType", resourceType, Type.STRING);

        answer.setProperty("jcr:created", ISO8601.format(Calendar.getInstance()), Type.DATE);
        answer.setProperty("jcr:createdBy", StringUtils.defaultString(this.serviceSession.getUserID()), Type.STRING);
        answer.setProperty("sling:resourceSuperType", "cards/Answer", Type.STRING);

        unlinkedReferenceAnswers.add(answer);
    }

    // Attempt to find a source answer for the provided reference answer node.
    // Prioritize answers from forms that are in the provided set of forms
    private void processUnlinkedAnswer(NodeBuilder answer, Set<String> sourceForms)
    {
        try {
            Node question = this.serviceSession.getNodeByIdentifier(answer.getProperty("question")
                .getValue(Type.STRING));
            final String referencedQuestion;
            try {
                referencedQuestion = question.getProperty("question").getString();
            } catch (final RepositoryException e) {
                LOGGER.warn("Skipping referenced question due to missing property");
                return;
            }
            Type<?> resultType = this.questionnaireUtils.getAnswerType(question);
            ReferenceAnswer result = getAnswer(referencedQuestion, sourceForms);

            setAnswer(answer, result, resultType, question);
        } catch (RepositoryException e) {
            // Could not set answer
        }
    }

    private void setAnswer(NodeBuilder answer, ReferenceAnswer result, Type<?> resultType, Node question)
    {
        if (result == null) {
            answer.removeProperty(FormUtils.VALUE_PROPERTY);
            setInvalidSourceStatusFlag(answer);
        } else {
            try {
                if (ReferenceConditionUtils.referenceHasCondition(question)
                    && !ReferenceConditionUtils.isReferenceConditionSatisfied(this.formUtils, question,
                        this.serviceSession.getNode(result.getPath()))
                ) {
                    ReferenceConditionUtils.setToFallback(answer, question);
                    answer.setProperty("copiedFrom", result.getPath());
                    setInvalidSourceStatusFlag(answer);
                } else {
                    // Type erasure makes the actual type irrelevant, there's only one real
                    // implementation method. he implementation can extract the right type from the
                    // type object
                    @SuppressWarnings("unchecked")
                    Type<Object> untypedResultType =
                        (Type<Object>) (result.getValue() instanceof List
                            ? resultType.getArrayType()
                            : resultType);
                    answer.setProperty(FormUtils.VALUE_PROPERTY, result.getValue(), untypedResultType);
                    answer.setProperty("copiedFrom", result.getPath());
                }
            } catch (RepositoryException e) {
                LOGGER.error("Could not set reference question", e);
            }
        }
    }

    private void setInvalidSourceStatusFlag(NodeBuilder answer)
    {
        if (answer.hasProperty(FormUtils.STATUS_FLAGS)) {
            Iterable<String> statusFlags = answer.getProperty(FormUtils.STATUS_FLAGS).getValue(Type.STRINGS);
            final boolean[] containsInvalidSourceFlag = {false};
            statusFlags.forEach(s -> {
                if (ReferenceConditionUtils.INVALID_SOURCE_FLAG.equals(s)) {
                    containsInvalidSourceFlag[0] = true;
                }
            });

            if (!containsInvalidSourceFlag[0]) {
                ArrayList<String> newFlags = new ArrayList<>();
                statusFlags.forEach(s -> newFlags.add(s));
                newFlags.add(ReferenceConditionUtils.INVALID_SOURCE_FLAG);
                answer.setProperty(FormUtils.STATUS_FLAGS, newFlags, Type.STRINGS);
            }
        } else {
            answer.setProperty(FormUtils.STATUS_FLAGS,
                new String[]{ReferenceConditionUtils.INVALID_SOURCE_FLAG});
        }
    }

    // Get any answer for the specified question in any form on the current subject.
    // Prioritize answers in the provided set of forms if multiple exist.
    private ReferenceAnswer getAnswer(String questionPath, Set<String> sourceForms)
    {

        try {
            Node subject = this.formUtils.getSubject(this.currentNodeBuilder);
            Collection<Node> answers =
                this.formUtils.findAllSubjectRelatedAnswers(subject, this.serviceSession.getNode(questionPath),
                    EnumSet.allOf(FormUtils.SearchType.class));
            if (!answers.isEmpty()) {
                Node answer;
                // Prioritize answers in forms that are already referenced
                Optional<Node> priorityAnswer = answers.stream().filter(a ->
                    sourceForms.contains(this.formUtils.getQuestionnaireIdentifier(this.formUtils.getForm(a))))
                    .findAny();
                if (priorityAnswer.isPresent()) {
                    answer = priorityAnswer.get();
                } else {
                    answer = answers.iterator().next();
                }
                Object value = this.formUtils.getValue(answer);
                if (value != null) {
                    return new ReferenceAnswer(serializeValue(value), answer.getPath());
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("Skipping referenced question due to error finding the referenced answer. "
                + e.getMessage());
        }
        return null;
    }

    private Object serializeValue(final Object rawValue)
    {
        if (rawValue instanceof Calendar) {
            final Calendar value = (Calendar) rawValue;
            // Use the ISO 8601 date+time format
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            sdf.setTimeZone(value.getTimeZone());
            return sdf.format(value.getTime());
        } else if (rawValue instanceof Object[]) {
            return Arrays.asList((Object[]) rawValue);
        }
        return rawValue;
    }

    private static final class ReferenceAnswer
    {
        private final Object value;
        private final String path;

        ReferenceAnswer(Object value, String path)
        {
            this.value = value;
            this.path = path;
        }

        public Object getValue()
        {
            return this.value;
        }

        public String getPath()
        {
            return this.path;
        }
    }
}
