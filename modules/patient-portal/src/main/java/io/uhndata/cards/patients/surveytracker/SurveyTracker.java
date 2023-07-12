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
package io.uhndata.cards.patients.surveytracker;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.patients.api.PatientAccessConfiguration;
import io.uhndata.cards.patients.emailnotifications.AppointmentUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Change listener monitoring changes to Visit Information forms, and to appointment notification emails.
 *
 * @version $Id$
 */
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE,
    service = { ResourceChangeListener.class, EventHandler.class }, property = {
        ResourceChangeListener.PATHS + "=/Forms",
        ResourceChangeListener.CHANGES + "=ADDED",
        ResourceChangeListener.CHANGES + "=CHANGED",
        EventConstants.EVENT_TOPIC + "=Notification/Patient/Appointment/*",
    })
@Designate(ocd = SurveyTracker.Config.class)
public class SurveyTracker implements ResourceChangeListener, EventHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SurveyTracker.class);

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

    @Reference
    private PatientAccessConfiguration accessConfiguration;

    private final boolean trackSubmissions;

    private final boolean trackEmails;

    @ObjectClassDefinition(name = "Patient portal - Survey tracker")
    public @interface Config
    {
        @AttributeDefinition(name = "Track survey submission")
        boolean trackSubmissions() default true;

        @AttributeDefinition(name = "Track invitation emails")
        boolean trackEmails() default true;
    }

    @Activate
    public SurveyTracker(Config config)
    {
        this.trackSubmissions = config.trackSubmissions();
        this.trackEmails = config.trackEmails();
    }

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        if (!this.trackSubmissions) {
            return;
        }
        changes.forEach(this::handleResourceEvent);
    }

    @Override
    public void handleEvent(Event event)
    {
        if (!this.trackEmails) {
            return;
        }
        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "SurveyTracker"))) {
            this.rrp.push(localResolver);
            mustPopResolver = true;
            final Session session = localResolver.adaptTo(Session.class);

            final Node visitSubject = session.getNode((String) event.getProperty("visit"));
            final Node surveyStatusQuestionnaire = session.getNode("/Questionnaires/Survey events");
            final Node surveyStatusForm =
                ensureSurveyStatusFormExists(surveyStatusQuestionnaire, visitSubject, session);
            final String questionName =
                StringUtils.toRootLowerCase(StringUtils.substringAfterLast(event.getTopic(), "/")) + "_sent";
            if (!surveyStatusQuestionnaire.hasNode(questionName)) {
                return;
            }
            final Node question = surveyStatusQuestionnaire.getNode(questionName);
            final Node answer = this.formUtils.getAnswer(surveyStatusForm, question);
            if (answer != null) {
                answer.setProperty("value", Calendar.getInstance());
                session.save();
            }
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage());
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    /**
     * For every form change, check if the changed form completes a visit or requires additional forms to be created. If
     * the changed form is a Visit information form, check if new forms need to be created for said visit. If the
     * changed form is for a visit, check if it completes the questionnaire set for said visit and flag if so.
     *
     * @param event a change that happened in the repository
     */
    private void handleResourceEvent(final ResourceChange event)
    {
        // Acquire a service session with the right privileges for accessing visits and their forms
        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "SurveyTracker"))) {
            this.rrp.push(localResolver);
            mustPopResolver = true;
            // Get the information needed from the triggering form
            final Session session = localResolver.adaptTo(Session.class);
            final String path = event.getPath();
            if (!session.nodeExists(path)) {
                return;
            }
            final Node node = session.getNode(path);
            final Node form = this.formUtils.getForm(node);
            if (isAnswerForHasSurveys(node) && hasSurveys(node)) {
                // Also update the expiration date, since this cannot be copied from the visit
                ensureSurveyStatusFormExists(session.getNode("/Questionnaires/Survey events"),
                    this.formUtils.getSubject(form), session);
                updateSurveyExpirationDate(form, this.formUtils.getAnswer(form,
                    session.getNode("/Questionnaires/Visit information/time")), session);
            } else if (isAnswerForSurveysSubmitted(node) && isSubmitted(node)) {
                updateSurveySubmittedDate(node, session);
            } else if (isAnswerForVisitTime(node)) {
                updateSurveyExpirationDate(form, node, session);
            }
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage());
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void updateSurveySubmittedDate(final Node submittedAnswer, final Session session) throws RepositoryException
    {
        if (!session.nodeExists("/Questionnaires/Survey events/responses_received")) {
            return;
        }
        final Node surveyStatusQuestionnaire = session.getNode("/Questionnaires/Survey events");
        final Node surveyStatusForm = ensureSurveyStatusFormExists(surveyStatusQuestionnaire,
            this.formUtils.getSubject(this.formUtils.getForm(submittedAnswer)), session);
        final Node submittedDateAnswer = this.formUtils.getAnswer(surveyStatusForm,
            session.getNode("/Questionnaires/Survey events/responses_received"));
        if (submittedDateAnswer != null && this.formUtils.getValue(submittedDateAnswer) == null) {
            submittedDateAnswer.setProperty("value", Calendar.getInstance());
            session.save();
        }
    }

    private void updateSurveyExpirationDate(final Node form, final Node dischargedAnswer, final Session session)
        throws RepositoryException
    {
        if (!session.nodeExists("/Questionnaires/Survey events/survey_expiry")) {
            return;
        }
        Calendar eventDate = (Calendar) this.formUtils.getValue(dischargedAnswer);
        if (eventDate != null) {
            final Node surveyStatusQuestionnaire = session.getNode("/Questionnaires/Survey events");
            final Node surveyStatusForm = findSurveyStatusForm(surveyStatusQuestionnaire,
                this.formUtils.getSubject(this.formUtils.getForm(dischargedAnswer)), session);
            final Node expirationDateAnswer = this.formUtils.getAnswer(surveyStatusForm,
                session.getNode("/Questionnaires/Survey events/survey_expiry"));
            if (expirationDateAnswer != null) {
                Calendar expirationDate = (Calendar) eventDate.clone();
                final int postVisitCompletionTime = this.accessConfiguration.getAllowedPostVisitCompletionTime();
                Node visitSubject = this.formUtils.getSubject(form, "/SubjectTypes/Patient/Visit");
                final int tokenLifetime = AppointmentUtils.getTokenLifetime(
                    this.formUtils,
                    visitSubject,
                    "/Questionnaires/Visit information/clinic",
                    postVisitCompletionTime);
                expirationDate.add(Calendar.DATE, tokenLifetime + 1);
                expirationDate.add(Calendar.DATE, 1);
                expirationDate.set(Calendar.HOUR_OF_DAY, 0);
                expirationDate.set(Calendar.MINUTE, 0);
                expirationDate.set(Calendar.SECOND, 0);
                expirationDate.set(Calendar.MILLISECOND, 0);
                expirationDateAnswer.setProperty("value", expirationDate);
                session.save();
            }
        }
    }

    private boolean hasSurveys(final Node hasSurveysAnswer) throws RepositoryException
    {
        return isTrue(hasSurveysAnswer);
    }

    private boolean isSubmitted(final Node submittedAnswer) throws RepositoryException
    {
        return isTrue(submittedAnswer);
    }

    private boolean isTrue(final Node answer) throws RepositoryException
    {
        final Long value = (Long) this.formUtils.getValue(answer);
        return value != null && value == 1;
    }

    private Node ensureSurveyStatusFormExists(final Node surveyStatusQuestionnaire, final Node visitSubject,
        final Session session) throws RepositoryException
    {
        // First look for an existing form
        Node surveyStatusForm = findSurveyStatusForm(surveyStatusQuestionnaire, visitSubject, session);
        if (surveyStatusForm == null) {
            // Not found, create a new form
            surveyStatusForm = createSurveyStatusForm(surveyStatusQuestionnaire, visitSubject, session);
        }
        return surveyStatusForm;
    }

    private Node findSurveyStatusForm(final Node surveyStatusQuestionnaire, final Node visitSubject,
        final Session session) throws RepositoryException
    {
        final String query = String.format(
            "SELECT surveyStatusForm.*"
                + "  FROM [cards:Form] as surveyStatusForm"
                + " WHERE"
                + "  surveyStatusForm.questionnaire = '%1$s'"
                + "  AND surveyStatusForm.subject = '%2$s'"
                + "OPTION (index tag property)",
            surveyStatusQuestionnaire.getIdentifier(), visitSubject.getIdentifier());
        final NodeIterator queryResult =
            session.getWorkspace().getQueryManager().createQuery(query, "JCR-SQL2").execute().getNodes();
        if (queryResult.hasNext()) {
            return queryResult.nextNode();
        }
        return null;
    }

    private Node createSurveyStatusForm(final Node surveyStatusQuestionnaire, final Node visitSubject,
        final Session session) throws RepositoryException
    {
        final Node result = session.getNode("/Forms").addNode(UUID.randomUUID().toString(), FormUtils.FORM_NODETYPE);
        result.setProperty(FormUtils.QUESTIONNAIRE_PROPERTY, surveyStatusQuestionnaire);
        result.setProperty(FormUtils.SUBJECT_PROPERTY, visitSubject);
        // Saving and refreshing will autocreate properties and answer nodes
        session.save();
        return result;
    }

    /**
     * Check if an answer is for the "visit has surveys" question.
     *
     * @param answer the answer node to check
     * @return {@code true} if the answer is indeed for the target question
     */
    private boolean isAnswerForHasSurveys(final Node answer)
    {
        return isAnswerForQuestion(answer, "has_surveys");
    }

    /**
     * Check if an answer is for the "survey has been submitted" question.
     *
     * @param answer the answer node to check
     * @return {@code true} if the answer is indeed for the target question
     */
    private boolean isAnswerForSurveysSubmitted(final Node answer)
    {
        return isAnswerForQuestion(answer, "surveys_submitted");
    }

    /**
     * Check if an answer is for the "visit time" question.
     *
     * @param answer the answer node to check
     * @return {@code true} if the answer is indeed for the target question
     */
    private boolean isAnswerForVisitTime(final Node answer)
    {
        return isAnswerForQuestion(answer, "time");
    }

    private boolean isAnswerForQuestion(final Node answer, final String questionName)
    {
        try {
            final Node question = this.formUtils.getQuestion(answer);
            return question != null && ("/Questionnaires/Visit information/" + questionName).equals(question.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if answer is for question {}: {}", questionName, e.getMessage(), e);
            return false;
        }
    }
}
