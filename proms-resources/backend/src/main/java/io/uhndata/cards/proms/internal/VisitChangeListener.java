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
package io.uhndata.cards.proms.internal;

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

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;
import io.uhndata.cards.dataentry.api.SubjectTypeUtils;
import io.uhndata.cards.dataentry.api.SubjectUtils;
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
public class VisitChangeListener implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(VisitChangeListener.class);

    /**
     * If a questionnaire needs to be completed every X days, start requiring it not only exactly X days, but also a few
     * days earlier. This defines how many days that is.
     */
    private static final int FREQUENCY_MARGIN_DAYS = 2;

    /**
     * If the new visit takes place close (in time) to another visit that requires the same questionnaire, let only the
     * previous visit require said questionnaire. This defines how "close", in number of days, these visits need to be
     * in order to influence one another.
     */
    private static final int VISIT_CREATION_MARGIN_DAYS = 3;

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
     * For every form change, either creates the forms scheduled for a visit, if it's a new Visit Information, or
     * updates the flag that indicates that all the forms have been completed by the patient, if it's a regular survey
     * needed for a visit.
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
            } else if ("Visit".equals(subjectType)) {
                // Not a new visit information form, but it is a form for a visit.
                // Check if all forms for the current visit are complete.
                handleVisitDataForm(subject, session);
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

        if (!visitInformation.hasRequiredInformation()) {
            return;
        }

        final Map<String, QuestionnaireFrequency> questionnaireSetInfo =
            getQuestionnaireSetInformation(visitInformation, session);

        if (questionnaireSetInfo == null) {
            // No valid questionnaire set: skip
            return;
        }

        final int baseQuestionnaireSetSize = questionnaireSetInfo.size();
        // Remove the questionnaires already created for the current visit
        pruneQuestionnaireSetByVisit(visit, visitInformation, questionnaireSetInfo);
        final int prunedQuestionnaireSetSize = questionnaireSetInfo.size();

        if (prunedQuestionnaireSetSize < 1) {
            // Visit already has all needed forms: end early
            return;
        }

        pruneQuestionnaireSet(visit, visitInformation, questionnaireSetInfo);

        if (questionnaireSetInfo.size() < 1) {
            if (prunedQuestionnaireSetSize == baseQuestionnaireSetSize) {
                // Visit does not need any forms due to other visits meeting frequency requirements:
                // mark form as complete.
                changeVisitComplete(form, true, session);
            }
            return;
        } else {
            changeVisitComplete(form, false, session);
            final List<String> createdForms = createForms(visit, questionnaireSetInfo, session);
            commitForms(createdForms, session);
        }
    }

    /**
     * Update the visit's "The patient completed the pre-appointment surveys" answer if all forms have been completed.
     *
     * @param visitSubject the visit subject which should have all forms checked for completion
     * @param session a service session providing access to the repository
     * @throws RepositoryException if any required data could not be retrieved
     */
    private void handleVisitDataForm(final Node visitSubject, final Session session) throws RepositoryException
    {
        Node visitInformationForm = null;
        Node surveysCompleteQuestion = null;

        for (final PropertyIterator forms = visitSubject.getReferences("subject"); forms.hasNext();) {
            final Node form = forms.nextProperty().getParent();
            final Node questionnaire = this.formUtils.getQuestionnaire(form);
            if (isVisitInformation(questionnaire)) {
                visitInformationForm = form;
                if (questionnaire.hasNode("surveys_complete")) {
                    surveysCompleteQuestion =
                        this.questionnaireUtils.getQuestion(questionnaire, "surveys_complete");
                    final Long surveysCompleted =
                        (Long) this.formUtils
                            .getValue(this.formUtils.getAnswer(visitInformationForm, surveysCompleteQuestion));
                    if (surveysCompleted != null && surveysCompleted == 1L) {
                        // Form is already complete: exit
                        return;
                    }
                }
            } else if (isFormIncomplete(form)) {
                // Visit is not complete: stop checking
                return;
            }
        }

        // The actual number of forms does not matter, since all the needed forms have been pre-created, and any still
        // incomplete form will cause the method to return early from the check above
        if (visitInformationForm != null) {
            changeVisitComplete(visitInformationForm, true, session);
        }
    }

    /**
     * Check if one date is within a few days from a base date.
     *
     * @param testedDate the date to check
     * @param baseDate the base date to compare against
     * @param days the maximum number of days before or after the base date to consider as within range
     * @return {@code true} if the tested date is in range, {@code false} otherwise
     */
    private static boolean isWithinDateRange(final Calendar testedDate, final Calendar baseDate, final int days)
    {
        if (testedDate == null || baseDate == null) {
            return false;
        }
        final Calendar start = addDays(baseDate, -Math.abs(days));
        final Calendar end = addDays(baseDate, Math.abs(days));
        return testedDate.after(start) && testedDate.before(end);
    }

    /**
     * Get the result of adding or removing a number of days to a date. Does not mutate the provided date.
     *
     * @param date the date that is being added to
     * @param days number of days being added, may be negative
     * @return the resulting date
     */
    private static Calendar addDays(final Calendar date, final int days)
    {
        final Calendar result = (Calendar) date.clone();
        result.add(Calendar.DATE, days);
        return result;
    }

    /**
     * Get the questionnaire set specified by the Visit Information form, as a list of questionnaires and their desired
     * frequency. Not all of these questionnaires will have to be filled in for the visit, pruning out the ones that
     * don't need to be filled in again, according to their frequency and the date when they were last filled in, will
     * be done later in {@link #pruneQuestionnaireSet(Node, VisitInformation, Map)}.
     *
     * @param visitInformation the Visit Information form data for which the questionnaire set should be retrieved
     * @param session a service session providing access to the repository
     * @return a map of all questionnaires with frequency in the questionnaire set, keyed by questionnaire uuid
     */
    private Map<String, QuestionnaireFrequency> getQuestionnaireSetInformation(final VisitInformation visitInformation,
        final Session session)
    {
        final Map<String, QuestionnaireFrequency> result = new HashMap<>();
        try {
            for (final NodeIterator questionnaireSet = session.getNode("/Proms/"
                + visitInformation.getQuestionnaireSet()).getNodes(); questionnaireSet.hasNext();) {
                final Node questionnaireRef = questionnaireSet.nextNode();
                if (!questionnaireRef.isNodeType("cards:QuestionnaireRef")
                    || !questionnaireRef.hasProperty("questionnaire")) {
                    continue;
                }
                final Node questionnaire = questionnaireRef.getProperty("questionnaire").getNode();
                final String uuid = questionnaire.getIdentifier();
                int frequency = 0;
                if (questionnaireRef.hasProperty("frequency")) {
                    frequency = (int) questionnaireRef.getProperty("frequency").getLong();
                }
                result.put(uuid, new QuestionnaireFrequency(questionnaire, frequency));
            }
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed to retrieve questionnaire frequency: {}", e.getMessage(), e);
            return null;
        }
        return result;
    }

    /**
     * Remove any questionnaires from the questionnaire set which do not need to be created per their frequency.
     * Iterates through all of the patient's visits and checks these visits for completed forms that satisfy that form's
     * frequency requirements. If another visit has filled in a questionnaire recently enough, remove that questionnaire
     * from the set.
     *
     * @param visit the visit Subject which should be checked for forms
     * @param visitInformation the set of data about the visit that triggered this event
     * @param questionnaireSetInfo the current questionnaireSet. This will be modified by removing key-value pairs
     * @throws RepositoryException if iterating the patient's visits fails
     */
    private void pruneQuestionnaireSet(final Node visit, final VisitInformation visitInformation,
        final Map<String, QuestionnaireFrequency> questionnaireSetInfo) throws RepositoryException
    {
        final Node patientNode = visit.getParent();
        for (final NodeIterator visits = patientNode.getNodes(); visits.hasNext();) {
            final Node otherVisit = visits.nextNode();
            // Check if visit is not the triggering visit, since the triggering visit has already been processed.
            if (!visit.isSame(otherVisit) && this.subjectUtils.isSubject(otherVisit)
                && "Visit".equals(this.subjectTypeUtils.getLabel(this.subjectUtils.getType(otherVisit)))) {
                pruneQuestionnaireSetByVisit(otherVisit, visitInformation, questionnaireSetInfo);
            }
        }
    }

    /**
     * Remove any questionnaires from the questionnaire set which do not need to be created per their frequency.
     * Iterates through all of the provided visit's forms and checks if they meet the frequency requirements for the
     * provided visitInformation. If they do, remove that form's questionnaire from the questionnaire set.
     *
     * @param visit the visit Subject which should be checked for forms
     * @param visitInformation the set of data about the visit that triggered this event
     * @param questionnaireSetInfo the current questionnaireSet. This will be modified by removing key-value pairs
     * @throws RepositoryException if iterating the patient's visits fails
     */
    private void pruneQuestionnaireSetByVisit(final Node visit, final VisitInformation visitInformation,
        final Map<String, QuestionnaireFrequency> questionnaireSetInfo) throws RepositoryException
    {
        String visitQuestionnaireSet = null;

        // Create a list of all complete forms that exist for this visit.
        // Record the visit's date as well, if there is a Visit Information form with a date.
        final Map<String, Boolean> questionnaires = new HashMap<>();
        Calendar visitDate = null;
        for (final PropertyIterator forms = visit.getReferences("subject"); forms.hasNext();) {
            final Node form = forms.nextProperty().getParent();
            if (this.formUtils.isForm(form)) {
                final Node questionnaire = this.formUtils.getQuestionnaire(form);
                if (questionnaire.isSame(visitInformation.getQuestionnaire())) {
                    visitDate = (Calendar) this.formUtils
                        .getValue(this.formUtils.getAnswer(form, visitInformation.getVisitDateQuestion()));
                    visitQuestionnaireSet = (String) this.formUtils
                        .getValue(this.formUtils.getAnswer(form, visitInformation.getQuestionnaireSetQuestion()));
                } else {
                    questionnaires.merge(questionnaire.getIdentifier(), !isFormIncomplete(form), Boolean::logicalOr);
                }
            }
        }

        // Ignore forms for different clinics, or forms without a clinic
        if (visitQuestionnaireSet == null || visitInformation.getQuestionnaireSet() != visitQuestionnaireSet) {
            return;
        } else {
            removeQuestionnairesFromVisit(visitInformation, visitDate, questionnaires, questionnaireSetInfo);
        }
    }

    /**
     * Remove any questionnaires from the questionnaire set which do not need to be created per their frequency.
     * Uses a provided list of questionnaires and the date that list occured on to compare with the questionnaire set
     *
     * @param visitInformation the set of data about the visit that triggered this event
     * @param visitDate the data of the visit being checked
     * @param questionnaires the list of questionnaires for the visit being checked, along with their completion status
     * @param questionnaireSetInfo the current questionnaireSet. This will be modified by removing key-value pairs
     * @throws RepositoryException if iterating the patient's visits fails
     */
    private void removeQuestionnairesFromVisit(final VisitInformation visitInformation, final Calendar visitDate,
        final Map<String, Boolean> questionnaires, final Map<String, QuestionnaireFrequency> questionnaireSetInfo)
    {
        final Calendar triggeringDate = visitInformation.getVisitDate();
        final boolean isWithinVisitMargin = isWithinDateRange(visitDate, triggeringDate, VISIT_CREATION_MARGIN_DAYS);

        // Check if any of this visit's forms meet a frequency requirement for the current questionnaire set.
        // If they do, remove those forms' questionnaires from the set.
        for (final String questionnaireIdentifier : questionnaires.keySet()) {
            if (questionnaireSetInfo.containsKey(questionnaireIdentifier)) {
                final int frequencyPeriod = questionnaireSetInfo.get(questionnaireIdentifier).getFrequency()
                    * 7 - FREQUENCY_MARGIN_DAYS;
                final boolean complete = questionnaires.get(questionnaireIdentifier);

                if (isWithinDateRange(visitDate, triggeringDate, frequencyPeriod)) {
                    if (complete || isWithinVisitMargin) {
                        questionnaireSetInfo.remove(questionnaireIdentifier);
                    }
                }
            }
        }
    }

    /**
     * Create a new form for each questionnaire in the questionnaireSet, with the visit as the parent subject.
     *
     * @param visit the visit which should be the new form's subject
     * @param questionnaireSetInfo the set of questionnaires which should be created
     * @param session a service session providing access to the repository
     * @return a list of paths for all created forms
     * @throws RepositoryException if the form data could not be accessed
     */
    private List<String> createForms(final Node visit, final Map<String, QuestionnaireFrequency> questionnaireSetInfo,
        final Session session) throws RepositoryException
    {
        final List<String> results = new LinkedList<>();

        for (final String questionnaireIdentifier : questionnaireSetInfo.keySet()) {
            final String uuid = UUID.randomUUID().toString();
            final Node form = session.getNode("/Forms").addNode(uuid, FormUtils.FORM_NODETYPE);
            final Node questionnaire = questionnaireSetInfo.get(questionnaireIdentifier).getQuestionnaire();
            form.setProperty(FormUtils.QUESTIONNAIRE_PROPERTY, questionnaire);
            form.setProperty(FormUtils.SUBJECT_PROPERTY, visit);
            if ("AUDITC".equals(questionnaire.getName())) {
                final String sex = getPatientSex(visit);
                if (sex != null) {
                    try {
                        final Node answer = form.addNode(UUID.randomUUID().toString(), "cards:TextAnswer");
                        final Node sexQuestion = session.getNode("/Questionnaires/AUDITC/sex");
                        answer.setProperty(FormUtils.QUESTION_PROPERTY, sexQuestion);
                        answer.setProperty(FormUtils.VALUE_PROPERTY, sex);
                    } catch (final RepositoryException e) {
                        LOGGER.error("Failed to set sex: {}", e.getMessage(), e);
                    }
                }
            }
            results.add(form.getPath());
        }

        return results;
    }

    /**
     * Get the sex for a visit's parent patient, as specified by their patient information form.
     *
     * @param visit the visit which shoulkd have it's patient checked
     * @return the patient sex as listed in the patient information form or null
     */
    private String getPatientSex(final Node visit)
    {
        try {
            final Node patient = visit.getParent();
            if (this.subjectUtils.isSubject(patient)) {
                for (final PropertyIterator references = patient.getReferences(); references.hasNext();) {
                    final Node form = references.nextProperty().getParent();
                    final Node questionnaire = this.formUtils.getQuestionnaire(form);
                    if (questionnaire != null && isPatientInformation(questionnaire)) {
                        for (final NodeIterator answers = form.getNodes(); answers.hasNext();) {
                            final Node answer = answers.nextNode();
                            final Node question = this.formUtils.getQuestion(answer);
                            if (question != null && "sex".equals(question.getName())) {
                                return answer.hasProperty("value") ? answer.getProperty("value").getString() : null;
                            }
                        }
                        // Found the patient information form, but it is missing the information we need.
                        // Exit & return default
                        break;
                    }
                }
            }
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed to find sex: {}", e.getMessage(), e);
            // Could not retrieve required information: Return default
        }
        return null;
    }

    /**
     * Commit all changes in the current session and check in all listed forms.
     *
     * @param createdForms the list of paths that should be checked in
     * @param session a service session providing access to the repository
     */
    private void commitForms(final List<String> createdForms, final Session session)
    {
        if (createdForms.size() > 0) {
            // Commit new forms and check them in
            try {
                session.save();
            } catch (final RepositoryException e) {
                LOGGER.error("Failed to commit forms: {}", e.getMessage(), e);
            }

            try {
                final VersionManager versionManager = session.getWorkspace().getVersionManager();
                for (final String formPath : createdForms) {
                    try {
                        versionManager.checkin(formPath);
                    } catch (final RepositoryException e) {
                        LOGGER.error("Failed to checkin form: {}", e.getMessage(), e);
                    }
                }
            } catch (final RepositoryException e) {
                LOGGER.error("Failed to obtain version manager: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Check if the provided form has the {@code INCOMPLETE} status flag.
     *
     * @param form the form which should be checked
     * @return true if the form's statusFlags property contains INCOMPLETE
     */
    private boolean isFormIncomplete(final Node form)
    {
        boolean incomplete = false;
        try {
            if (this.formUtils.isForm(form) && form.hasProperty("statusFlags")) {
                final Property statusProp = form.getProperty("statusFlags");
                if (statusProp.isMultiple()) {
                    final Value[] statuses = form.getProperty("statusFlags").getValues();
                    for (final Value status : statuses) {
                        final String str = status.getString();
                        if ("INCOMPLETE".equals(str)) {
                            incomplete = true;
                        }
                    }
                } else {
                    final String str = statusProp.getString();
                    if ("INCOMPLETE".equals(str)) {
                        incomplete = true;
                    }
                }
            }
            return incomplete;
        } catch (final RepositoryException e) {
            // Can't check if the form is INCOMPLETE, assume it is
            return true;
        }
    }

    /**
     * Record a visit's completion status to it's visit information surveysComplete field.
     *
     * @param visitInformationForm the Visit Information form to save the completion status to, if needed
     * @param complete the value that completion status should be set to; {@code true} for complete, {@code false} for
     *            incomplete
     * @param session a service session providing access to the repository
     */
    private void changeVisitComplete(final Node visitInformationForm, final boolean complete, final Session session)
    {
        try {
            final Long newValue = complete ? 1L : 0L;
            final Node questionnaire = this.formUtils.getQuestionnaire(visitInformationForm);
            final Node surveysCompleteQuestion = this.questionnaireUtils.getQuestion(questionnaire, "surveys_complete");
            if (surveysCompleteQuestion == null) {
                LOGGER.warn("Could not save visit completion status as surveys_complete could not be found");
                return;
            }

            Node surveysCompletedAnswer =
                this.formUtils.getAnswer(visitInformationForm, surveysCompleteQuestion);
            // Check if the value is already the correct one
            if (surveysCompletedAnswer != null
                && newValue.equals(this.formUtils.getValue(surveysCompletedAnswer))) {
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

                    if (surveysCompletedAnswer == null) {
                        // No answer node yet, create one
                        surveysCompletedAnswer =
                            visitInformationForm.addNode(UUID.randomUUID().toString(), "cards:BooleanAnswer");
                        surveysCompletedAnswer.setProperty(FormUtils.QUESTION_PROPERTY, surveysCompleteQuestion);
                    }
                    // Set the new value
                    surveysCompletedAnswer.setProperty("value", newValue);
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
     * Check if a questionnaire is the {@code Patient Information} questionnaire.
     *
     * @param questionnaire the questionnaire to check
     * @return {@code true} if the questionnaire is indeed the {@code Patient Information}
     */
    private static boolean isPatientInformation(final Node questionnaire)
    {
        return isSpecificQuestionnaire(questionnaire, "/Questionnaires/Patient information");
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

        private final Node questionnaireSetQuestion;

        private final String questionnaireSet;

        VisitInformation(final Node questionnaire, final Node form) throws RepositoryException
        {
            this.questionnaire = questionnaire;
            this.visitDateQuestion = questionnaire.getNode("time");
            this.visitDate = (Calendar) VisitChangeListener.this.formUtils
                .getValue(VisitChangeListener.this.formUtils.getAnswer(form, this.visitDateQuestion));
            this.questionnaireSetQuestion =
                VisitChangeListener.this.questionnaireUtils.getQuestion(questionnaire, "surveys");
            final String questionnaireSetName = (String) VisitChangeListener.this.formUtils
                .getValue(VisitChangeListener.this.formUtils.getAnswer(form, this.questionnaireSetQuestion));
            this.questionnaireSet =
                questionnaire.getSession().nodeExists("/Proms/" + questionnaireSetName) ? questionnaireSetName : null;

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

        public Node getQuestionnaireSetQuestion()
        {
            return this.questionnaireSetQuestion;
        }
    }

    private static final class QuestionnaireFrequency
    {
        private final Node questionnaire;

        private final Integer frequency;

        QuestionnaireFrequency(final Node questionnaire, final int frequency)
        {
            this.questionnaire = questionnaire;
            this.frequency = frequency;
        }

        public Node getQuestionnaire()
        {
            return this.questionnaire;
        }

        public int getFrequency()
        {
            return this.frequency;
        }
    }
}
