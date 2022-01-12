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

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;

/**
 * Change listener looking for new or modified Visit Information forms. Creates any forms in the specified survey set
 * that needs to be created, based on the seurvey set's specified frequency.
 *
 * @version $Id$
 */
@Component(
    service = EventHandler.class,
    property = {
        EventConstants.EVENT_TOPIC + "=org/apache/sling/api/resource/Resource/ADDED",
        EventConstants.EVENT_TOPIC + "=org/apache/sling/api/resource/Resource/CHANGED",
        EventConstants.EVENT_FILTER + "=(&(resourceType=cards/Form)(path=/Forms/*))"
    }
)
public class VisitChangeListener implements EventHandler
{
    private static final int FREQUENCY_MARGIN_DAYS = 2;

    private static final int VISIT_CREATION_MARGIN_DAYS = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger(VisitChangeListener.class);

    private Session session;

    private ResourceResolver resolver;

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Override
    public void handleEvent(final Event event)
    {
        try (ResourceResolver localResolver = this.resolverFactory.getServiceResourceResolver(null)) {
            this.resolver = localResolver;
            this.session = this.resolver.adaptTo(Session.class);

            final String path = event.getProperty("path").toString();

            final Node eventFormNode = this.session.getNode(path);
            final Node subjectNode = eventFormNode.getProperty("subject").getNode();
            final String subjectType = subjectNode.getProperty("type").getNode().getProperty("label").getString();

            // Get the information needed from the triggering form
            final Node questionnaireNode = this.formUtils.getQuestionnaire(eventFormNode);
            if (VisitInformation.isVisitInformationForm(questionnaireNode)) {
                // Create any forms that need to be created for this new visit
                handleVisitInformationForm(eventFormNode, subjectNode, questionnaireNode);
            } else if ("Visit".equals(subjectType)) {
                // Not a new visit information form, but it is a form for a visit.
                // Check if all forms for the current visit are complete.
                handleVisitDataForm(subjectNode);
            }
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (RepositoryException | PersistenceException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Creates any forms that need to be completed per the visit's questionnaire set.
     *
     * @param eventFormNode the Visit Information form that triggered the current event
     * @param visitNode the Visit that is the subject for the triggering form
     * @param questionnaireNode the Visit Information questionnaire
     * @throws RepositoryException if any required data could not be checked
     * @throws PersistenceException if created forms could not be commited
     */
    private void handleVisitInformationForm(final Node eventFormNode, final Node visitNode,
        final Node questionnaireNode)
        throws RepositoryException, PersistenceException
    {
        final VisitInformation visitInformation = new VisitInformation(questionnaireNode, eventFormNode);

        if (visitInformation.missingQuestionSetInfo()) {
            return;
        }

        final Map<String, QuestionnaireFrequency> questionnaireSetInfo =
            getQuestionnaireSetInformation(visitInformation);

        if (questionnaireSetInfo == null) {
            // No valid questionnaireset: Skip
            return;
        }
        final int baseQuestionnaireSetSize = questionnaireSetInfo.size();

        pruneQuestionnaireSetByVisit(visitNode, visitInformation, questionnaireSetInfo);

        final int prunedQuestionnaireSetSize = questionnaireSetInfo.size();
        if (prunedQuestionnaireSetSize < 1) {
            // Visit already has all needed forms: end early
            return;
        }

        pruneQuestionnaireSet(visitNode, visitInformation, questionnaireSetInfo);

        if (questionnaireSetInfo.size() < 1) {
            if (prunedQuestionnaireSetSize == baseQuestionnaireSetSize) {
                // Visit does not need any forms due to other visits meeting frequency requirements:
                // mark form as complete.
                changeVisitComplete(eventFormNode, true);
            }
            return;
        } else {
            changeVisitComplete(eventFormNode, false);
            final List<String> createdForms = createForms(visitNode, questionnaireSetInfo);
            commitForms(createdForms);
        }
    }

    /**
     * Update the visit's Visit Information form if all forms have been completed.
     *
     * @param visitNode the visit subject which should have all forms checked for completion
     * @throws RepositoryException if any required data could not be retrieved
     */
    private void handleVisitDataForm(final Node visitNode) throws RepositoryException
    {
        Node visitInformationForm = null;
        String surveysCompleteIdentifier = null;
        int numberOfForms = 0;

        for (final PropertyIterator forms = visitNode.getReferences(); forms.hasNext();) {
            final Node form = forms.nextProperty().getParent();

            final Node questionnaireNode = this.formUtils.getQuestionnaire(form);
            if (VisitInformation.isVisitInformationForm(questionnaireNode)) {
                visitInformationForm = form;
                if (questionnaireNode.hasNode("surveys_complete")) {
                    surveysCompleteIdentifier = questionnaireNode.getNode("surveys_complete").getIdentifier();
                    final Property surveysCompleted =
                        getFormAnswerValue(visitInformationForm, surveysCompleteIdentifier);
                    if (surveysCompleted != null && surveysCompleted.getLong() == 1L) {
                        // Form is already complete: exit
                        return;
                    }
                }
            } else {
                numberOfForms++;
            }

            if (isFormIncomplete(form)) {
                // Visit is not complete: stop checking
                return;
            }
        }

        if (numberOfForms > 0 && visitInformationForm != null) {
            changeVisitComplete(visitInformationForm, true);
        }
    }

    /**
     * Get a date answer for the requested form and question.
     *
     * @param formNode the form which should have it's answers checked
     * @param questionIdentifier the question for which we want the answer
     * @return the requested date or null
     */
    private static Calendar getFormDate(final Node formNode, final String questionIdentifier)
    {
        try {
            final Property prop = getFormAnswerValue(formNode, questionIdentifier);
            return prop == null ? null : prop.getDate();
        } catch (final RepositoryException e) {
            LOGGER.warn("Requested answer could not be parsed to a date: {}", questionIdentifier, e);
            return null;
        }
    }

    /**
     * Get a string answer for the requested form and question.
     *
     * @param formNode the form which should have it's answers checked
     * @param questionIdentifier the question for which we want the answer
     * @return the requested string or null
     */
    private static String getFormString(final Node formNode, final String questionIdentifier)
    {
        try {
            final Property prop = getFormAnswerValue(formNode, questionIdentifier);
            return prop == null ? null : prop.getString();
        } catch (final RepositoryException e) {
            LOGGER.warn("Requested answer could not be parsed to a string: {}", questionIdentifier, e);
            return null;
        }
    }

    /**
     * Get an answer Value for the requested form and question.
     *
     * @param formNode the form which should have it's answers checked
     * @param questionIdentifier the question for which we want the answer
     * @return the requested value or null
     */
    private static Property getFormAnswerValue(final Node formNode, final String questionIdentifier)
    {
        try {
            final Node answer = getFormAnswer(formNode, questionIdentifier);
            if (answer != null && answer.hasProperty("value")) {
                return answer.getProperty("value");
            } else {
                // Question wasn't answered
                return null;
            }
        } catch (final RepositoryException e) {
            LOGGER.warn("Requested answer Value could not be found: {}", questionIdentifier, e);
            return null;
        }
    }

    /**
     * Get an answer Node for the requested form and question.
     *
     * @param formNode the form which should have it's answers checked
     * @param questionIdentifier the question for which we want the answer
     * @return the requested Node or null
     */
    private static Node getFormAnswer(final Node formNode, final String questionIdentifier)
    {
        try {
            for (final NodeIterator answers = formNode.getNodes(); answers.hasNext();) {
                final Node answer = answers.nextNode();
                if (answer.hasProperty("question")
                    && questionIdentifier.equals(answer.getProperty("question").getString())) {
                    return answer;
                }
            }
        } catch (final RepositoryException e) {
            LOGGER.warn("Requested answer could not be found: {}", questionIdentifier, e);
            return null;
        }
        return null;
    }

    private static boolean isWithinDateRange(final Calendar testedDate, final Calendar baseDate, final int days)
    {
        final Calendar start = addDays(baseDate, -Math.abs(days));
        final Calendar end = addDays(baseDate, Math.abs(days));
        return testedDate.after(start) && testedDate.before(end);
    }

    /**
     * Get the result of adding days to a Calendar. Does not mutate the provided date.
     *
     * @param date the date that is being added to.
     * @param days number of days being added
     * @return the resulting calenar
     */
    private static Calendar addDays(final Calendar date, final int days)
    {
        final Calendar result = (Calendar) date.clone();
        result.add(Calendar.DATE, (days));
        return result;
    }

    /**
     * Get the questionnare set defined by the Visit Information form.
     *
     * @param visitInformation the Visit Information form data for which the questionnaire set should be retrieved.
     * @return a map of all questionnaires with frequency in the questionnaire set, keyed by questionnaire uuid
     */
    private Map<String, QuestionnaireFrequency> getQuestionnaireSetInformation(final VisitInformation visitInformation)
    {
        final Map<String, QuestionnaireFrequency> result = new HashMap<>();
        try {
            for (final NodeIterator questionnaireSet = session.getNode("/Proms/"
                + visitInformation.getQuestionnaireSet()).getNodes(); questionnaireSet.hasNext();) {
                final Node questionnaireRef = questionnaireSet.nextNode();
                final Node questionnaire = questionnaireRef.getProperty("questionnaire").getNode();
                final String uuid = questionnaire.getIdentifier();
                int frequency = 0;
                if (questionnaireRef.hasProperty("frequency")) {
                    frequency = (int) questionnaireRef.getProperty("frequency").getLong();
                }
                result.put(uuid, new QuestionnaireFrequency(frequency, questionnaire));
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
     * frequency requirements. If that is the case, remove that form's questionnaire from the questionnaire set.
     *
     * @param visitNode the visit Subject which should be checked for forms
     * @param visitInformation the set of data about the visit that triggered this event
     * @param questionnaireSetInfo the current questionnaireSet. This will be modified by removing key-value pairs
     * @throws RepositoryException if iterating the patient's visits fails
     */
    private void pruneQuestionnaireSet(final Node visitNode,
        final VisitInformation visitInformation,
        final Map<String, QuestionnaireFrequency> questionnaireSetInfo)
        throws RepositoryException
    {
        final Node patientNode = visitNode.getParent();
        final String triggeringVisit = visitNode.getName();
        for (final NodeIterator visits = patientNode.getNodes(); visits.hasNext();) {
            final Node visit = visits.nextNode();
            // Check if visit is not the triggering visit, since the triggering visit has already been processed.
            if (!triggeringVisit.equals(visit.getName())) {
                pruneQuestionnaireSetByVisit(visit, visitInformation, questionnaireSetInfo);
            }
        }
    }

    /**
     * Remove any questionnaires from the questionnaire set which do not need to be created per their frequency.
     * Iterates through all of the provided visit's forms and checks if they meet the frequency requirements for the
     * provided visitInformation. If they do, remove that form's questionnaire from the questionnaire set.
     *
     * @param visitNode the visit Subject which should be checked for forms
     * @param visitInformation the set of data about the visit that triggered this event
     * @param questionnaireSetInfo the current questionnaireSet. This will be modified by removing key-value pairs
     * @throws RepositoryException if iterating the patient's visits fails
     */
    private void pruneQuestionnaireSetByVisit(final Node visit,
        final VisitInformation visitInformation,
        final Map<String, QuestionnaireFrequency> questionnaireSetInfo)
        throws RepositoryException
    {
        // Create a list of all complete forms that exist for this visit.
        // Record the visit's date as well, if there is a Visit Information form with a date.
        final Map<String, Boolean> questionnaires = new HashMap<>();
        Calendar visitDate = null;
        for (final PropertyIterator forms = visit.getReferences(); forms.hasNext();) {
            final Node form = forms.nextProperty().getParent();
            if (this.formUtils.isForm(form)) {
                final String questionnaireIdentifier = this.formUtils.getQuestionnaireIdentifier(form);
                if (questionnaireIdentifier.equals(visitInformation.getQuestionnaireIdentifier())) {
                    visitDate = getFormDate(form, visitInformation.getTimeQuestionIdentifier());
                } else {
                    questionnaires.put(questionnaireIdentifier, !isFormIncomplete(form));
                }
            }
        }

        final Calendar triggeringDate = visitInformation.getEventDate();
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
     * @param visitNode the visit which should be the new form's subject
     * @param questionnaireSetInfo the set of questionnaires which should be created
     * @return a list of paths for all created forms
     * @throws PersistenceException if a form could not be created
     * @throws RepositoryException if the Forms node could not be resolved
     */
    private List<String> createForms(final Node visitNode,
        final Map<String, QuestionnaireFrequency> questionnaireSetInfo)
        throws PersistenceException, RepositoryException
    {
        final List<String> results = new LinkedList<>();

        for (final String questionnaireIdentifier : questionnaireSetInfo.keySet()) {
            final Map<String, Object> formProperties = new HashMap<>();

            formProperties.put("jcr:primaryType", "cards:Form");
            formProperties.put("questionnaire", questionnaireSetInfo.get(questionnaireIdentifier).getNode());
            formProperties.put("subject", visitNode);

            final String uuid = UUID.randomUUID().toString();
            this.resolver.create(this.resolver.resolve("/Forms/"), uuid, formProperties);
            results.add("/Forms/" + uuid);
        }

        return results;
    }

    /**
     * Commit all changes in the current resource resolver and check in all listed forms.
     *
     * @param createdForms the list of paths that should be checked in
     */
    private void commitForms(final List<String> createdForms)
    {
        if (createdForms.size() > 0) {
            // Commit new forms and check them in
            try {
                this.resolver.commit();
            } catch (PersistenceException e) {
                LOGGER.error("Failed to commit forms: {}", e.getMessage(), e);
            }

            try {
                final VersionManager versionManager = this.session.getWorkspace().getVersionManager();
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
     * Check if the provided form has the INCOMPLETE status flag.
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
     * @param form the Visit Information form to save the completion status to, if relevant.
     * @param complete the value that completion status should be set to; true for complete, false for incomplete.
     */
    private void changeVisitComplete(final Node form, final Boolean complete)
    {
        try {
            final Long newValue = complete ? 1L : 0L;
            final Node questionnaireNode = this.formUtils.getQuestionnaire(form);
            final String surveysCompleteIdentifier;
            if (questionnaireNode.hasNode("surveys_complete")) {
                surveysCompleteIdentifier = questionnaireNode.getNode("surveys_complete").getIdentifier();
                final Property surveysCompleted = getFormAnswerValue(form, surveysCompleteIdentifier);
                if (surveysCompleted != null && surveysCompleted.getLong() == newValue) {
                    // Form is already set to the right value
                    return;
                }
            } else {
                LOGGER.warn("Could not save visit completion status as surveys_complete could not be found");
                return;
            }

            final VersionManager versionManager = this.session.getWorkspace().getVersionManager();
            final String formPath = form.getPath();
            try {
                versionManager.checkout(formPath);
            } catch (final RepositoryException e) {
                LOGGER.error("Failed to checkout form: {}", e.getMessage(), e);
            }

            final Node answer = getFormAnswer(form, surveysCompleteIdentifier);
            answer.setProperty("value", newValue);
            this.session.save();

            try {
                versionManager.checkin(formPath);
            } catch (final RepositoryException e) {
                LOGGER.error("Failed to checkin form: {}", e.getMessage(), e);
            }
        } catch (final RepositoryException e) {
            LOGGER.error("Failed to obtain version manager or questionnaire data: {}", e.getMessage(), e);
        }
    }

    private static final class VisitInformation
    {
        private final Calendar eventDate;

        private final String questionnaireIdentifier;

        private final String timeQuestionIdentifier;

        private final String questionnaireSet;

        VisitInformation(final Node questionnaireNode, final Node formNode) throws RepositoryException
        {
            this.questionnaireIdentifier = questionnaireNode.getIdentifier();
            this.timeQuestionIdentifier = questionnaireNode.getNode("time").getIdentifier();
            this.eventDate = getFormDate(formNode, this.timeQuestionIdentifier);
            final String questionSetIdentifier = questionnaireNode.getNode("surveys").getIdentifier();
            this.questionnaireSet = getFormString(formNode, questionSetIdentifier);
        }

        public static Boolean isVisitInformationForm(final Node node)
        {
            try {
                return node != null
                    && node.hasProperty("title")
                    && "Visit information".equals(node.getProperty("title").getString());
            } catch (final RepositoryException e) {
                LOGGER.warn("Failed check for visit information form: {}", e.getMessage(), e);
                return false;
            }
        }

        public Boolean missingQuestionSetInfo()
        {
            return this.eventDate == null
                || this.questionnaireSet == null
                || "".equals(this.questionnaireSet);
        }

        public Calendar getEventDate()
        {
            return this.eventDate;
        }

        public String getQuestionnaireIdentifier()
        {
            return this.questionnaireIdentifier;
        }

        public String getTimeQuestionIdentifier()
        {
            return this.timeQuestionIdentifier;
        }

        public String getQuestionnaireSet()
        {
            return this.questionnaireSet;
        }
    }

    private static final class QuestionnaireFrequency
    {
        private final Integer frequency;

        private final Node node;

        QuestionnaireFrequency(final int frequency, final Node node)
        {
            this.frequency = frequency;
            this.node = node;

        }

        public int getFrequency()
        {
            return this.frequency;
        }

        public Node getNode()
        {
            return this.node;
        }
    }
}
