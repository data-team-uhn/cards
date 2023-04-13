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
package io.uhndata.cards.heracles.internal;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

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
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * An {@link Editor} that fills out any reference answers for a new form.
 *
 * @version $Id$
 */
public class PauseResumeFormEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PauseResumeFormEditor.class);

    private final NodeBuilder currentNodeBuilder;

    private final ResourceResolverFactory rrf;

    private final ThreadResourceResolverProvider rrp;

    private final QuestionnaireUtils questionnaireUtils;

    private final FormUtils formUtils;

    private final SubjectUtils subjectUtils;

    private boolean isFormNode;

    private boolean isNew;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param rrp the thread resource resolver provider to store resource resolvers to
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param subjectUtils for working with subject data
     * @param isNew if this node is a newly created node or a changed node
     */
    public PauseResumeFormEditor(final NodeBuilder nodeBuilder, final ResourceResolverFactory rrf,
        final ThreadResourceResolverProvider rrp, final QuestionnaireUtils questionnaireUtils,
        final FormUtils formUtils, SubjectUtils subjectUtils, boolean isNew)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.rrf = rrf;
        this.rrp = rrp;
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.subjectUtils = subjectUtils;
        this.isFormNode = this.formUtils.isForm(this.currentNodeBuilder);
        this.isNew = isNew;
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        if (this.isFormNode) {
            // No need to descend further down, we already know that this is a form that has changes
            return null;
        } else {
            return new PauseResumeFormEditor(this.currentNodeBuilder.getChildNode(name), this.rrf,
                this.rrp, this.questionnaireUtils, this.formUtils, this.subjectUtils, true);
        }
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        if (this.isFormNode) {
            // No need to descend further down, we already know that this is a form that has changes
            return null;
        } else {
            return new PauseResumeFormEditor(this.currentNodeBuilder.getChildNode(name), this.rrf,
                this.rrp, this.questionnaireUtils, this.formUtils, this.subjectUtils, false);
        }
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
    {
        // Only process new forms
        if (!this.isFormNode || !this.isNew) {
            return;
        }

        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.rrf
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "PauseResumeEditor"))) {
            this.rrp.push(localResolver);
            mustPopResolver = true;

            if (!isPauseResumeForm(after)) {
                return;
            }

            this.processForm(after);
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void processForm(NodeState after)
    {
        Calendar newDate = getFormDate(after);

        // Count the number of pause resume forms and record the most recent one
        final Node subject = this.formUtils.getSubject(after);
        Node latestForm = null;
        try {
            Calendar latestFormDate = null;
            for (final PropertyIterator forms = subject.getReferences("subject"); forms.hasNext();) {
                final Node referencedForm = forms.nextProperty().getParent();
                if (isPauseResumeForm(referencedForm)) {
                    Calendar referencedDate = referencedForm.getProperty("jcr:created").getDate();
                    if (!referencedDate.equals(newDate)
                        && (latestFormDate == null || referencedDate.after(latestFormDate))) {
                        latestFormDate = referencedDate;
                        latestForm = referencedForm;
                    }
                }
            }

            this.saveFormStatus(after, latestForm);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private Calendar getFormDate(NodeState after)
    {
        final String newDateString = after.getProperty("jcr:created").getValue(Type.DATE);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        final Calendar newDate = Calendar.getInstance();
        try {
            newDate.setTime(dateFormat.parse(newDateString));
        } catch (ParseException e) {
            LOGGER.error("Failed to parse date");
        }
        return newDate;
    }

    private void saveFormStatus(NodeState after, Node latestForm)
    {
        final Node questionnaire = this.formUtils.getQuestionnaire(after);
        final Node idQuestion = this.questionnaireUtils.getQuestion(questionnaire, "pause_resume_index");
        final Node statusQuestion = this.questionnaireUtils.getQuestion(questionnaire, "enrollment_status");
        String status = "";
        if (latestForm != null) {
            status = String.valueOf(this.formUtils.getValue(this.formUtils.getAnswer(latestForm, statusQuestion)));
        }
        if ("paused".equals(status)) {
            // New form must be a resume form
            String id = String.valueOf(this.formUtils.getValue(this.formUtils.getAnswer(latestForm, idQuestion)));
            this.createOrEditAnswer(questionnaire, "pause_resume_index", id);
            this.createOrEditAnswer(questionnaire, "enrollment_status", "resumed");
        } else {
            // Default, New form must be a pause form
            this.createOrEditAnswer(questionnaire, "pause_resume_index", null);
            this.createOrEditAnswer(questionnaire, "enrollment_status", "paused");
        }
    }

    private void createOrEditAnswer(final Node questionnaire, final String questionPath,
        final String value)
    {
        try {
            String questionUUID = this.questionnaireUtils.getQuestion(questionnaire, questionPath)
                .getProperty("jcr:uuid").getString();
            for (String answerName : this.currentNodeBuilder.getChildNodeNames()) {
                NodeBuilder answer = this.currentNodeBuilder.getChildNode(answerName);
                PropertyState question = answer.getNodeState().getProperty("question");
                if (question != null && questionUUID != null && questionUUID.equals(question.getValue(Type.STRING)))
                {
                    this.editAnswer(answer, answerName, value);
                    return;
                }
            }
            this.createAnswer(questionUUID, value);
        } catch (RepositoryException e) {
            LOGGER.error("Could not create question " + questionPath, e);
        }
    }

    private void editAnswer(final NodeBuilder node, final String nodeName, final String value)
    {
        node.setProperty("value", value == null ? nodeName : value);
    }

    private void createAnswer(final String questionUUID, final String value)
    {
        final String uuid = UUID.randomUUID().toString();
        NodeBuilder node = this.currentNodeBuilder.setChildNode(uuid);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        node.setProperty("jcr:created", dateFormat.format(new Date()), Type.DATE);
        node.setProperty("jcr:createdBy", this.rrp.getThreadResourceResolver().getUserID(), Type.NAME);
        node.setProperty(FormUtils.QUESTION_PROPERTY, questionUUID, Type.REFERENCE);
        node.setProperty("jcr:primaryType", "cards:TextAnswer", Type.NAME);
        node.setProperty("sling:resourceSuperType", FormUtils.ANSWER_RESOURCE, Type.STRING);
        node.setProperty("sling:resourceType", "cards/TextAnswer", Type.STRING);
        node.setProperty("statusFlags", Collections.emptyList(), Type.STRINGS);
        // If no value is specified, set the value to be an ID
        node.setProperty("value", value == null ? uuid : value, Type.STRING);
    }

    /**
     * Check if a form is a {@code Pause-Resume} form.
     *
     * @param form the form to check
     * @return {@code true} if the form is indeed a {@code Pause-Resume} form
     */
    private boolean isPauseResumeForm(final Node form)
    {
        final Node questionnaire = this.formUtils.getQuestionnaire(form);
        if (questionnaire == null) {
            return false;
        }
        try {
            return "/Questionnaires/Pause-Resume Status".equals(questionnaire.getPath());
        } catch (RepositoryException e) {
            return false;
        }
    }

    /**
     * Check if a form is a {@code Pause-Resume} form.
     *
     * @param form the form to check
     * @return {@code true} if the form is indeed a {@code Pause-Resume} form
     */
    private boolean isPauseResumeForm(final NodeState form)
    {
        final Node questionnaire = this.formUtils.getQuestionnaire(form);
        try {
            return "/Questionnaires/Pause-Resume Status".equals(questionnaire.getPath());
        } catch (RepositoryException e) {
            return false;
        }
    }
}
