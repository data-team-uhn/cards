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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
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
public class ReferenceAnswersEditor extends AnswersEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceAnswersEditor.class);

    private final SubjectUtils subjectUtils;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param resolver the resource resolver factory which can provide access to JCR sessions
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param subjectUtils for working with subject data
     */
    public ReferenceAnswersEditor(final NodeBuilder nodeBuilder, final ResourceResolver resolver,
        final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils, SubjectUtils subjectUtils)
    {
        super(nodeBuilder, resolver, questionnaireUtils, formUtils);
        this.subjectUtils = subjectUtils;
    }

    @Override
    protected Logger getLogger()
    {
        return LOGGER;
    }

    @Override
    protected ReferenceAnswerChangeTracker getAnswerChangeTracker()
    {
        return new ReferenceAnswerChangeTracker();
    }

    @Override
    protected ReferenceAnswersEditor getNewEditor(String name)
    {
        return new ReferenceAnswersEditor(this.currentNodeBuilder.getChildNode(name),
            this.resolver, this.questionnaireUtils, this.formUtils, this.subjectUtils);
    }

    @Override
    protected ReferenceAnswerNodeTypes getNewAnswerNodeTypes(Node node)
        throws RepositoryException
    {
        return new ReferenceAnswerNodeTypes(node);
    }

    @Override
    protected boolean isQuestionNodeMatchingType(Node node)
    {
        return this.questionnaireUtils.isReferenceQuestion(node);
    }

    @Override
    public void propertyAdded(final PropertyState after)
    {
        if (this.isFormNode && "questionnaire".equals(after.getName())) {
            // Only run on a newly created questionnaire
            this.shouldRunOnLeave = true;
        }
    }

    @Override
    protected void handleLeave(final NodeState form)
    {
        // Get a list of all unanswered reference questions
        final Node questionnaireNode = getQuestionnaire();
        if (questionnaireNode == null) {
            return;
        }
        final QuestionTree referenceQuestionsTree =
            getUnansweredMatchingQuestions(questionnaireNode);

        // There are missing reference questions, let's create them!
        if (referenceQuestionsTree != null) {
            // Create the missing structure, i.e. AnswerSection and Answer nodes
            final Map<QuestionTree, NodeBuilder> answersToFinish =
                createMissingNodes(referenceQuestionsTree, this.currentNodeBuilder);

            // Retrieve all the referenced answers
            answersToFinish.entrySet().stream().forEach(entry -> {
                Node question = entry.getKey().getNode();
                final String referencedQuestion;
                try {
                    referencedQuestion = question.getProperty("question").getString();
                } catch (final RepositoryException e) {
                    LOGGER.warn("Skipping referenced question due to missing property");
                    return;
                }

                NodeBuilder answer = entry.getValue();
                Type<?> resultType = Type.STRING;
                try {
                    ReferenceAnswerNodeTypes types = new ReferenceAnswerNodeTypes(question);
                    resultType = types.getDataType();
                } catch (RepositoryException e) {
                    LOGGER.warn("Error typing value for question. " + e.getMessage());
                }

                Object result = getAnswer(form, referencedQuestion);

                if (result == null) {
                    answer.removeProperty(FormUtils.VALUE_PROPERTY);
                } else {
                    // Type erasure makes the actual type irrelevant, there's only one real implementation method
                    // The implementation can extract the right type from the type object
                    @SuppressWarnings("unchecked")
                    Type<Object> untypedResultType =
                        (Type<Object>) (result instanceof List ? resultType.getArrayType() : resultType);
                    answer.setProperty(FormUtils.VALUE_PROPERTY, result, untypedResultType);
                }

            });
        }
    }

    private Object getAnswer(NodeState form, String questionPath)
    {
        Node subject = this.getSubject(form);
        try {
            Collection<Node> answers =
                this.formUtils.findAllSubjectRelatedAnswers(subject, this.currentSession.getNode(questionPath),
                    EnumSet.allOf(FormUtils.SearchType.class));
            if (!answers.isEmpty()) {
                Object value = this.formUtils.getValue(answers.iterator().next());
                if (value != null) {
                    return serializeValue(value);
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

    private final class ReferenceAnswerChangeTracker extends AbstractAnswerChangeTracker
    {
        ReferenceAnswerChangeTracker()
        {
            super(ReferenceAnswersEditor.this.formUtils);
        }

        @Override
        public boolean isMatchedAnswerNode(NodeState after, String questionId)
        {
            if ("cards:ReferenceAnswer".equals(after.getName("jcr:primaryType"))) {
                return true;
            } else if (questionId != null) {
                Node questionNode = ReferenceAnswersEditor.this.questionnaireUtils.getQuestion(questionId);
                try {
                    if (questionNode != null && questionNode.hasProperty("entryMode")
                        && "refernce".equals(questionNode.getProperty("entryMode").getString())) {
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

    private final class ReferenceAnswerNodeTypes extends AnswerNodeTypes
    {
        ReferenceAnswerNodeTypes(final Node questionNode) throws RepositoryException
        {
            super(questionNode, "cards:ReferenceAnswer", "cards/ReferenceAnswer");
        }
    }

    // Ideally, formUtils would be used for getSubject.
    // However, formUtils uses a ResourceResolverFactory not a ThreadResourceResolverProvider
    // so is not reliable in this use case
    private Node getSubject(final NodeState form)
    {
        String identifier = form.getProperty(FormUtils.SUBJECT_PROPERTY).getValue(Type.STRING);
        try {
            final Node result = this.currentSession.getNodeByIdentifier(identifier);
            return this.isNodeType(result, SubjectUtils.SUBJECT_NODETYPE) ? result : null;
        } catch (RepositoryException e) {
            // SHould not happen
            return null;
        }
    }
    protected boolean isNodeType(final Node node, final String targetNodeType)
    {
        if (node == null) {
            return false;
        }
        try {
            return node.isNodeType(targetNodeType);
        } catch (final RepositoryException e) {
            return false;
        }
    }
}
