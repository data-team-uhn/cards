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
package io.uhndata.cards.patients.internal;

import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.version.VersionManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;
import io.uhndata.cards.utils.ThreadResourceResolverProvider;

/**
 * Change listener looking for new or modified forms related to a Visit subject. Initially, when a new Visit Information
 * form is created, it also creates any forms in the specified survey set that need to be created, based on the survey
 * set's specified frequency. Then, when all the forms required for a visit are completed, it also marks in the Visit
 * Information that the patient has completed the surveys.
 *
 * @version $Id$
 */
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/Forms",
    ResourceChangeListener.CHANGES + "=ADDED",
    ResourceChangeListener.CHANGES + "=CHANGED"
})
public class PremsSurveyListener implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(VisitChangeListener.class);

    /** Provides access to resources. */
    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @Reference
    private SubjectTypeUtils subjectTypeUtils;

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        changes.forEach(this::handleEvent);
    }

    /**
     * For every form change check if it is missing a clinic.
     * If so, calculate the appropriate clinic based on survey scheduling and apply.
     *
     * @param event a change that happened in the repository
     */
    private void handleEvent(final ResourceChange event)
    {
        // Acquire a service session with the right privileges for accessing visits and their forms
        try (ResourceResolver localResolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "VisitFormsPreparation"))) {
            // Get the information needed from the triggering form
            final Session session = localResolver.adaptTo(Session.class);
            if (!session.nodeExists(event.getPath())) {
                return;
            }
            final String path = event.getPath();
            final Node form = session.getNode(path);
            if (!this.formUtils.isForm(form)) {
                return;
            }
            this.rrp.push(localResolver);
            final Node subject = this.formUtils.getSubject(form);
            final String subjectType = this.subjectTypeUtils.getLabel(this.subjectUtils.getType(subject));
            final Node questionnaire = this.formUtils.getQuestionnaire(form);

            if (isVisitInformation(questionnaire)) {
                // Create any forms that need to be created for this new visit
                handleVisitInformationForm(form, subject, questionnaire, session);
            }
            this.rrp.pop();
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Creates any forms that need to be completed per the visit's questionnaire set.
     *
     * @param form the Visit Information form that triggered the current event
     * @param visit the Visit that is the subject for the triggering form
     * @param questionnaire the Visit Information questionnaire
     * @param session a service session providing access to the repository
     * @throws RepositoryException if any required data could not be checked
     */
    private void handleVisitInformationForm(final Node form, final Node visit, final Node questionnaire,
        final Session session) throws RepositoryException
    {
        final VisitInformation visitInformation = new VisitInformation(questionnaire, form);

        // Only continue with the surveys update if we have the requirements
        if (!visitInformation.hasRequiredInformation()) {
            return;
        } else {
            // Record that this visit has forms
            changeVisitInformation(form, "has_surveys", true, session);
        }
    }

    /**
     * Record a boolean value to the visit information form for the specified question/answer.
     * Will not update the visit information form if the question already has the desired answer.
     *
     * @param visitInformationForm the Visit Information form to save the value to, if needed
     * @param questionPath the relative path from the visit information questionnaire to the question to be answered
     * @param value the value that answer should be set to
     * @param session a service session providing access to the repository
     */
    private void changeVisitInformation(final Node visitInformationForm, final String questionPath,
        final boolean value, final Session session)
    {
        try {
            final Long newValue = value ? 1L : 0L;
            final Node questionnaire = this.formUtils.getQuestionnaire(visitInformationForm);
            final Node question = this.questionnaireUtils.getQuestion(questionnaire, questionPath);
            if (question == null) {
                LOGGER.warn("Could update visit information form as requested question could not be found: "
                    + questionPath);
                return;
            }

            Node answer =
                this.formUtils.getAnswer(visitInformationForm, question);
            // Check if the value is already the correct one
            if (answer != null
                && newValue.equals(this.formUtils.getValue(answer))) {
                // Form is already set to the right value, nothing else to do
                return;
            }

            // We must set the new value; this involves checking out the form, updating the value, saving, and finally
            // checking in the form.
            // Since this is in reaction to another thread saving and checking in the form, it is possible to enter a
            // race condition where the checkin from the other thread happens between our checkout and our save, so we
            // have to try this operation a few times.
            final VersionManager versionManager = session.getWorkspace().getVersionManager();
            final String formPath = visitInformationForm.getPath();

            for (int i = 0; i < 3; ++i) {
                try {
                    // Checkout
                    versionManager.checkout(formPath);

                    if (answer == null) {
                        // No answer node yet, create one
                        answer =
                            visitInformationForm.addNode(UUID.randomUUID().toString(), "cards:BooleanAnswer");
                        answer.setProperty(FormUtils.QUESTION_PROPERTY, question);
                    }
                    // Set the new value
                    answer.setProperty("value", newValue);
                    session.save();

                    // Checkin
                    versionManager.checkin(formPath);
                    // All done, exit the try-loop
                    return;
                } catch (final RepositoryException e) {
                    LOGGER.info("Failed to checkin form {}, trying again", formPath);
                }
            }
            LOGGER.error("Failed to checkin form {} after multiple attempts", formPath);
        } catch (final RepositoryException e) {
            LOGGER.error("Failed to obtain version manager or questionnaire data: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if a questionnaire is the {@code Visit Information} questionnaire.
     *
     * @param questionnaire the questionnaire to check
     * @return {@code true} if the questionnaire is indeed the {@code Visit Information}
     */
    private static boolean isVisitInformation(final Node questionnaire)
    {
        return isSpecificQuestionnaire(questionnaire, "/Questionnaires/Visit information");
    }

    /**
     * Check if a questionnaire is the specified questionnaire.
     *
     * @param questionnaire the questionnaire to check
     * @param questionnairePath the path to the desired questionnaire
     * @return {@code true} if the questionnaire is indeed the questionnaire specified by path
     */
    private static boolean isSpecificQuestionnaire(final Node questionnaire, final String questionnairePath)
    {
        try {
            return questionnaire != null && questionnairePath.equals(questionnaire.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if form is of questionnaire type {}: {}", questionnairePath, e.getMessage(), e);
            return false;
        }
    }

    private final class VisitInformation
    {
        private final Calendar visitDate;

        private final Node questionnaire;

        private final Node visitDateQuestion;

        private final Node clinicQuestion;

        private final String questionnaireSet;

        private final String clinicPath;

        VisitInformation(final Node questionnaire, final Node form) throws RepositoryException
        {
            Session session = questionnaire.getSession();
            this.questionnaire = questionnaire;
            this.visitDateQuestion = questionnaire.getNode("time");
            this.visitDate = (Calendar) VisitChangeListener.this.formUtils
                .getValue(VisitChangeListener.this.formUtils.getAnswer(form, this.visitDateQuestion));
            this.clinicQuestion =
                VisitChangeListener.this.questionnaireUtils.getQuestion(questionnaire, "clinic");
            final String clinicName = (String) VisitChangeListener.this.formUtils
                .getValue(VisitChangeListener.this.formUtils.getAnswer(form, this.clinicQuestion));
            final Node clinicNode = StringUtils.isNotBlank(clinicName) && session.nodeExists(clinicName)
                ? session.getNode(clinicName) : null;
            if (clinicNode != null) {
                this.questionnaireSet = session.nodeExists("/Survey/" + clinicNode.getProperty("survey").getString())
                    ? clinicNode.getProperty("survey").getString() : null;
                this.clinicPath = clinicNode.getPath();
            } else {
                this.questionnaireSet = null;
                this.clinicPath = "";
            }
        }

        public boolean hasRequiredInformation()
        {
            return this.visitDate != null && StringUtils.isNotBlank(this.questionnaireSet);
        }

        public Calendar getVisitDate()
        {
            return this.visitDate;
        }

        public Node getQuestionnaire()
        {
            return this.questionnaire;
        }

        public Node getVisitDateQuestion()
        {
            return this.visitDateQuestion;
        }

        public String getQuestionnaireSet()
        {
            return this.questionnaireSet;
        }

        public Node getClinicQuestion()
        {
            return this.clinicQuestion;
        }

        public String getClinicPath()
        {
            return this.clinicPath;
        }
    }
}
