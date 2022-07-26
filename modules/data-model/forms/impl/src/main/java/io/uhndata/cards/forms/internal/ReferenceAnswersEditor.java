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

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
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
public class ReferenceAnswersEditor extends AnswersEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputedAnswersEditor.class);

    private final SubjectUtils subjectUtils;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param subjectUtils for working with subject data
     */
    public ReferenceAnswersEditor(final NodeBuilder nodeBuilder, final ResourceResolverFactory rrf,
        final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils, SubjectUtils subjectUtils)
    {
        super(nodeBuilder, rrf, questionnaireUtils, formUtils, "referenceAnswers");
        this.subjectUtils = subjectUtils;
    }

    @Override
    protected Logger getLogger()
    {
        return this.LOGGER;
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
            this.rrf, this.questionnaireUtils, this.formUtils, this.subjectUtils);
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
        if (this.isFormNode && "questionnaire".equals(after.getName()))
        {
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
                final String referencedQuestionnaire;
                final String referencedQuestion;
                try {
                    referencedQuestionnaire = question.getProperty("questionnaire").getString();
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

                Object result = getAnswer(form, referencedQuestionnaire, referencedQuestion);

                if (result == null) {
                    answer.removeProperty(FormUtils.VALUE_PROPERTY);
                } else {
                    // Type erasure makes the actual type irrelevant, there's only one real implementation method
                    // The implementation can extract the right type from the type object
                    @SuppressWarnings("unchecked")
                    Type<Object> untypedResultType = (Type<Object>) resultType;
                    answer.setProperty(FormUtils.VALUE_PROPERTY, result, untypedResultType);
                }

            });
        }
    }

    @SuppressWarnings("checkstyle:NestedIfDepth")
    private Object getAnswer(NodeState form, String questionnaireName, String questionName)
    {
        Node subject = this.formUtils.getSubject(form);
        try {
            if (this.subjectUtils.isSubject(subject)) {
                for (final PropertyIterator subjectReferences = subject.getReferences();
                    subjectReferences.hasNext();) {
                    Node subjectForm = subjectReferences.nextProperty().getParent();
                    if (this.formUtils.isForm(subjectForm)) {
                        Node subjectQuestionnaire = this.formUtils.getQuestionnaire(subjectForm);
                        if (this.questionnaireUtils.isQuestionnaire(subjectQuestionnaire)
                            && questionnaireName.equals(subjectQuestionnaire.getName())) {
                            Object value = getAnswerFromParentNode(subjectForm, questionName);
                            if (value != null) {
                                return value;
                            }
                        }
                    }
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("Skipping referenced question due to error finding the referenced answer. "
                + e.getMessage());
        }
        return null;
    }

    private Object getAnswerFromParentNode(Node currentNode, String questionName) throws RepositoryException
    {
        if (this.formUtils.isAnswerSection(currentNode) || this.formUtils.isForm(currentNode)) {
            // Found a section: Recursively get all of this section's answers
            for (final NodeIterator childNodes = currentNode.getNodes(); childNodes.hasNext();) {
                Node childNode = childNodes.nextNode();
                Object value = getAnswerFromParentNode(childNode, questionName);
                if (value != null) {
                    return value;
                }
            }
        } else if (this.formUtils.isAnswer(currentNode)) {
            String currentQuestionName
                = this.questionnaireUtils.getQuestionName(this.formUtils.getQuestion(currentNode));
            if (questionName.equals(currentQuestionName)) {
                return this.formUtils.getValue(currentNode);
            }
        }
        return null;
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
}
