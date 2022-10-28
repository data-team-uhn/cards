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
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
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

        // Only continue with the surveys update if we have the requirements
        if (!visitInformation.hasRequiredInformation()) {
            return;
        }

        final QuestionnaireSetInfo questionnaireSetInfo =
            getQuestionnaireSetInformation(visitInformation, session);

        if (questionnaireSetInfo == null) {
            // No valid questionnaire set: skip
            return;
        }

        final int baseQuestionnaireSetSize = questionnaireSetInfo.membersSize();
        // Remove the questionnaires already created for the current visit
        pruneQuestionnaireSetByVisit(visit, visitInformation, questionnaireSetInfo);
        final int prunedQuestionnaireSetSize = questionnaireSetInfo.membersSize();

        if (prunedQuestionnaireSetSize < 1) {
            // Visit already has all forms in the questionnaire set: end early

            // Ideally, has_surveys would already be set to true so this should not be needed.
            // However, if the visit information form is edited via the browser editor and saved twice,
            // `has_surveys` can be incorrectly deleted and may need to be set back to true.
            if (baseQuestionnaireSetSize > 0) {
                changeVisitInformation(form, "has_surveys", true, session);
            }
            return;
        }

        // Prune the quesionnaires to be created based on frequency.
        // If all the frequencies are 0, skip this step.
        if (!questionnaireSetInfo.getMembers().values().stream().allMatch(member -> 0 == member.getFrequency())) {
            pruneQuestionnaireSet(visit, visitInformation, questionnaireSetInfo);
        }

        if (questionnaireSetInfo.membersSize() < 1) {
            // No questionnaires were created as all missing questionnaires from the questionnaire set
            // have their frequencies met by other visits.

            // This visit can either have:
            // 1. No questionnaires
            // 2. A subset of the questionnaires from the questionnaire set which were previously created
            if (prunedQuestionnaireSetSize == baseQuestionnaireSetSize) {
                // If case 1, record that this visit has no forms
                changeVisitInformation(form, "has_surveys", false, session);
            } else {
                // If case 2, record that this visit has forms

                // Ideally, has_surveys would already be set to true so this should not be needed.
                // However, if the visit information form is edited via the browser editor and saved twice,
                // `has_surveys` can be incorrectly deleted and may need to be set back to true.
                changeVisitInformation(form, "has_surveys", true, session);
            }
            return;
        } else {
            // Record that this visit has forms
            changeVisitInformation(form, "has_surveys", true, session);

            final List<String> createdForms = createForms(visit, questionnaireSetInfo.getMembers(), session);
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
            changeVisitInformation(visitInformationForm, "surveys_complete", true, session);
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
     * @return a QuestionnaireSetInfo containing all questionnaires and conflicts for this questionnaire set
     */
    private QuestionnaireSetInfo getQuestionnaireSetInformation(final VisitInformation visitInformation,
        final Session session)
    {
        QuestionnaireSetInfo info = new QuestionnaireSetInfo();
        try {
            Node questionnaireSet = session.getNode("/Survey/" + visitInformation.getQuestionnaireSet());
            for (final NodeIterator childNodes = questionnaireSet.getNodes(); childNodes.hasNext();) {
                final Node questionnaireRef = childNodes.nextNode();
                if (questionnaireRef.isNodeType("cards:QuestionnaireRef")
                    && questionnaireRef.hasProperty("questionnaire")) {
                    final Node questionnaire = questionnaireRef.getProperty("questionnaire").getNode();
                    final String uuid = questionnaire.getIdentifier();
                    int frequency = 0;
                    if (questionnaireRef.hasProperty("frequency")) {
                        frequency = (int) questionnaireRef.getProperty("frequency").getLong();
                    }
                    info.putMember(uuid, new QuestionnaireRef(questionnaire, frequency));
                } else if (questionnaireRef.isNodeType("cards:QuestionnaireConflict")
                    && questionnaireRef.hasProperty("questionnaire")) {
                    final String uuid = questionnaireRef.getProperty("questionnaire").getString();
                    final int frequency = (int) questionnaireRef.getProperty("frequency").getLong();
                    info.putConflict(uuid, frequency);
                }
            }
            for (final PropertyIterator childProperties = questionnaireSet.getProperties(); childProperties.hasNext();)
            {
                final Property property = childProperties.nextProperty();
                if ("frequencyIgnoreClinic".equals(property.getName())) {
                    info.setShouldIgnoreClinic(property.getBoolean());
                }
            }
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed to retrieve questionnaire frequency: {}", e.getMessage(), e);
            return null;
        }
        return info;
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
        final QuestionnaireSetInfo questionnaireSetInfo) throws RepositoryException
    {
        final Node patientNode = visit.getParent();
        for (final NodeIterator visits = patientNode.getNodes(); visits.hasNext();) {
            final Node otherVisit = visits.nextNode();
            // Check if visit is not the triggering visit, since the triggering visit has already been processed.
            if (!visit.isSame(otherVisit) && this.subjectUtils.isSubject(otherVisit)
                && "Visit".equals(this.subjectTypeUtils.getLabel(this.subjectUtils.getType(otherVisit)))) {
                pruneQuestionnaireSetByVisit(otherVisit, visitInformation, questionnaireSetInfo);
            }
            if (questionnaireSetInfo.membersSize() == 0) {
                return;
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
        final QuestionnaireSetInfo questionnaireSetInfo) throws RepositoryException
    {
        String visitClinic = null;

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
                    visitClinic = (String) this.formUtils
                        .getValue(this.formUtils.getAnswer(form, visitInformation.getClinicQuestion()));
                } else {
                    questionnaires.merge(questionnaire.getIdentifier(), !isFormIncomplete(form), Boolean::logicalOr);
                }
            }
        }


        // Ignore forms for different clinics, or forms without a clinic
        if (questionnaireSetInfo.shouldIgnoreClinic()
            || (!StringUtils.isBlank(visitClinic) && visitInformation.getClinicPath().equals(visitClinic))) {
            removeQuestionnairesFromVisit(visitInformation, visitDate, questionnaires, questionnaireSetInfo);
        } else {
            return;
        }
    }

    /**
     * Remove any questionnaires from the questionnaire set which do not need to be created per their frequency. Uses a
     * provided list of questionnaires and the date that list occured on to compare with the questionnaire set
     *
     * @param visitInformation the set of data about the visit that triggered this event
     * @param visitDate the data of the visit being checked
     * @param questionnaires the list of questionnaires for the visit being checked, along with their completion status
     * @param questionnaireSetInfo the current questionnaireSet. This will be modified by removing key-value pairs
     * @throws RepositoryException if iterating the patient's visits fails
     */
    private void removeQuestionnairesFromVisit(final VisitInformation visitInformation, final Calendar visitDate,
        final Map<String, Boolean> questionnaires, final QuestionnaireSetInfo questionnaireSetInfo)
    {
        final Calendar triggeringDate = visitInformation.getVisitDate();
        final boolean isWithinVisitMargin = isWithinDateRange(visitDate, triggeringDate, VISIT_CREATION_MARGIN_DAYS);

        // Check if any of this visit's forms meet a frequency requirement for the current questionnaire set.
        // If they do, remove those forms' questionnaires from the set.
        for (final String questionnaireIdentifier : questionnaires.keySet()) {
            Integer frequencyPeriod = null;
            if (questionnaireSetInfo.containsConflict(questionnaireIdentifier)) {
                frequencyPeriod = questionnaireSetInfo.getConflict(questionnaireIdentifier);
            } else if (questionnaireSetInfo.containsMember(questionnaireIdentifier)) {
                frequencyPeriod = questionnaireSetInfo.getMember(questionnaireIdentifier).getFrequency();
            }

            if (frequencyPeriod != null) {
                frequencyPeriod = frequencyPeriod * 7 - FREQUENCY_MARGIN_DAYS;
                final boolean complete = questionnaires.get(questionnaireIdentifier);

                if (isWithinDateRange(visitDate, triggeringDate, frequencyPeriod)) {
                    if (questionnaireSetInfo.containsConflict(questionnaireIdentifier)) {
                        questionnaireSetInfo.clearMembers();
                    } else if (complete || isWithinVisitMargin) {
                        questionnaireSetInfo.removeMember(questionnaireIdentifier);
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
    private List<String> createForms(final Node visit, final Map<String, QuestionnaireRef> questionnaireRefs,
        final Session session) throws RepositoryException
    {
        final List<String> results = new LinkedList<>();

        for (final String questionnaireIdentifier : questionnaireRefs.keySet()) {
            final String uuid = UUID.randomUUID().toString();
            final Node form = session.getNode("/Forms").addNode(uuid, FormUtils.FORM_NODETYPE);
            final Node questionnaire = questionnaireRefs.get(questionnaireIdentifier).getQuestionnaire();
            form.setProperty(FormUtils.QUESTIONNAIRE_PROPERTY, questionnaire);
            form.setProperty(FormUtils.SUBJECT_PROPERTY, visit);
            results.add(form.getPath());
        }

        return results;
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
                LOGGER.warn("Could not update visit information form as requested question could not be found: "
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
            // TODO: Test if !! is needed to convert to boolean
            return !!(this.visitDate != null && StringUtils.isNotBlank(this.questionnaireSet));
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

    private static final class QuestionnaireSetInfo
    {
        private final Map<String, Integer> conflicts;
        private final Map<String, QuestionnaireRef> members;
        private boolean ignoreClinic;

        QuestionnaireSetInfo()
        {
            this.conflicts = new HashMap<>();
            this.ignoreClinic = false;
            this.members = new HashMap<>();
        }

        public void putConflict(String uuid, int frequency)
        {
            this.conflicts.put(uuid, frequency);
        }

        public boolean containsConflict(String uuid)
        {
            return this.conflicts.containsKey(uuid);
        }

        public int getConflict(String uuid)
        {
            return this.conflicts.get(uuid);
        }

        public void putMember(String uuid, QuestionnaireRef member)
        {
            this.members.put(uuid, member);
        }

        public Map<String, QuestionnaireRef> getMembers()
        {
            return this.members;
        }

        public QuestionnaireRef getMember(String uuid)
        {
            return this.members.get(uuid);
        }

        public int membersSize()
        {
            return this.members.size();
        }

        public boolean containsMember(String uuid)
        {
            return this.members.containsKey(uuid);
        }

        public void removeMember(String uuid)
        {
            this.members.remove(uuid);
        }

        public void clearMembers()
        {
            this.members.clear();
        }

        public boolean shouldIgnoreClinic()
        {
            return this.ignoreClinic;
        }

        public void setShouldIgnoreClinic(boolean ignoreClinic)
        {
            this.ignoreClinic = ignoreClinic;
        }
    }

    private static final class QuestionnaireRef
    {
        private final Node questionnaire;

        private final Integer frequency;

        QuestionnaireRef(final Node questionnaire, final int frequency)
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
