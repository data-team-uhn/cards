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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.version.VersionManager;

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
import io.uhndata.cards.patients.api.VisitInformation;
import io.uhndata.cards.patients.api.VisitInformationAdapter;
import io.uhndata.cards.qsets.api.QuestionnaireRef;
import io.uhndata.cards.qsets.api.QuestionnaireSet;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Change listener looking for new or modified forms related to a Visit subject. Initially, when a new Visit Information
 * form is created, it also creates any forms in the specified questionnaire set that need to be created, based on the
 * questionnaire set's specified frequency. When all the forms required for a visit are completed marks in the Visit
 * Information form that the patient has completed the required forms.
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

    private static final String CLINIC_PATH = "/Questionnaires/Visit information/clinic";

    private static final String[] EMPTY_ARRAY_OF_STRINGS = {};

    private static final String STATUS_FLAGS = "statusFlags";

    /** Provides access to resources. */
    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private VisitInformationAdapter visitAdapter;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        changes.forEach(this::handleEvent);
    }

    /**
     * For every form change, check if the changed form completes a visit or requires additional forms to be created.
     * If the changed form is a Visit information form, check if new forms need to be created for said visit.
     * If the changed form is for a visit, check if it completes the questionnaire set for said visit and flag if so.
     *
     * @param event a change that happened in the repository
     */
    private void handleEvent(final ResourceChange event)
    {
        // Acquire a service session with the right privileges for accessing visits and their forms
        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "VisitFormsPreparation"))) {
            this.rrp.push(localResolver);
            mustPopResolver = true;
            // Get the information needed from the triggering form
            final Session session = localResolver.adaptTo(Session.class);
            if (!session.nodeExists(event.getPath())) {
                return;
            }
            final String path = event.getPath();
            Node node = session.getNode(path);

            if (node.isNodeType("cards:ResourceAnswer")
                && CLINIC_PATH.equals(this.formUtils.getQuestion(node).getPath())) {
                node = this.formUtils.getForm(node);
            }
            if (!this.formUtils.isForm(node)) {
                return;
            }

            handleFormChange(node);
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void handleFormChange(final Node node) throws RepositoryException
    {
        final Node questionnaire = this.formUtils.getQuestionnaire(node);
        final Node subject = this.formUtils.getSubject(node);

        if (isVisitInformation(questionnaire)) {
            // Create any forms that need to be created for this visit
            handleVisitInformationForm(node, subject);
        } else if ("/SubjectTypes/Patient/Visit".equals(this.subjectUtils.getType(subject).getPath())) {
            // Not a new visit information form, but it is a form for a visit.
            // Check if all forms for the current visit are complete.
            handleVisitDataForm(subject);
        }
    }

    /**
     * Creates any forms that need to be completed per the visit's questionnaire set.
     *
     * @param visitForm the Visit Information form that triggered the current event
     * @param visitSubject the Visit that is the subject for the triggering form
     * @param visitQuestionnaire the Visit Information questionnaire
     * @param session a service session providing access to the repository
     * @throws RepositoryException if any required data could not be checked
     */
    private void handleVisitInformationForm(final Node visitForm, final Node visitSubject)
        throws RepositoryException
    {
        final VisitInformation visitInformation = this.visitAdapter.toVisitInformation(visitForm);

        // Only continue with the surveys update if we have the requirements
        if (!visitInformation.hasRequiredInformation()) {
            return;
        }

        final QuestionnaireSet existingForms = visitInformation.getExistingForms();
        final QuestionnaireSet missingForms = visitInformation.getMissingForms();
        final int existingFormsCount = existingForms.questionnairesCount();
        final int missingFormsCount = missingForms.questionnairesCount();

        if (missingFormsCount == 0) {
            // Ideally, has_surveys would already be set to true so this should not be needed.
            // However, if the visit information form is edited via the browser editor and saved twice,
            // `has_surveys` can be incorrectly deleted and may need to be set back to true.
            changeVisitInformation(visitForm, "has_surveys", existingFormsCount > 0, true);
            // No valid questionnaire set: skip
            return;
        }

        // Visit needs additional forms. Record that and create the required forms
        boolean checkinNeeded = changeVisitInformation(visitForm, "has_surveys", true, false);
        checkinNeeded |= changeVisitInformation(visitForm, "surveys_complete", false, false);
        checkinNeeded |= changeVisitInformation(visitForm, "surveys_submitted", false, false);
        checkinNeeded |= updateVisitInformationFlags(visitForm);
        if (checkinNeeded) {
            checkin(visitForm);
        }
        final List<String> createdForms = createForms(visitSubject, missingForms);
        commitForms(createdForms, visitForm.getSession());
    }

    /**
     * Update the visit's "The patient completed the pre-appointment surveys" answer if all forms have been completed.
     *
     * @param visitSubject the visit subject which should have all forms checked for completion
     * @param session a service session providing access to the repository
     * @throws RepositoryException if any required data could not be retrieved
     */
    private void handleVisitDataForm(final Node visitSubject) throws RepositoryException
    {
        final VisitInformation visitInformation = this.visitAdapter.toVisitInformation(visitSubject);
        if (visitInformation == null || visitInformation.isComplete()) {
            // Visit Information form does not exist or is already marked complete: exit
            return;
        }
        final QuestionnaireSet template = visitInformation.getTemplateForms();

        // Get the visit information form for this subject and iterate through all other forms.
        // If any other forms are incomplete, terminate early without updating visit completion.
        for (final PropertyIterator forms = visitSubject.getReferences("subject"); forms.hasNext();) {
            final Node form = forms.nextProperty().getParent();
            final String questionnairePath = this.formUtils.getQuestionnaire(form).getPath();
            if (template.containsQuestionnaire(questionnairePath)
                && isFormIncomplete(form)) {
                // Visit is not complete: stop checking without saving completion status
                return;
            }
        }

        // The actual number of forms does not matter since all the needed forms will have been pre-created.
        // If this code runs then all created forms are completed so all needed forms are complete.
        changeVisitInformation(visitInformation.getVisitInformationForm(), "surveys_complete", true, true);
    }

    /**
     * Remove any questionnaires from the questionnaire set which do not need to be created per their frequency. Iterate
     * through a patient's visits looking for completed forms within the current questionnaire set. If this completed
     * form is recent enough to fall within the current frequency range, remove that questionnaire from the current
     * questionnaire set members.
     *
     * @param visitNode the visit Subject which should be checked for forms
     * @param visitInformation the set of data about the visit that triggered this event
     * @param questionnaireSetInfo the current questionnaireSet. This will be modified by removing key-value pairs
     * @throws RepositoryException if iterating the patient's visits fails
     */

    /**
     * Create a new form for each questionnaire in the questionnaireSet, with the visit as the parent subject.
     *
     * @param visitSubject the visit which should be the new form's subject
     * @param questionnaireSetInfo the set of questionnaires which should be created
     * @param session a service session providing access to the repository
     * @return a list of paths for all created forms
     * @throws RepositoryException if the form data could not be accessed
     */
    private List<String> createForms(final Node visitSubject, QuestionnaireSet questionnaireSet)
        throws RepositoryException
    {
        final List<String> results = new LinkedList<>();

        for (final QuestionnaireRef questionnaire : questionnaireSet.getQuestionnaires()) {
            final String uuid = UUID.randomUUID().toString();
            final Node form = visitSubject.getSession().getNode("/Forms").addNode(uuid, FormUtils.FORM_NODETYPE);
            form.setProperty(FormUtils.QUESTIONNAIRE_PROPERTY, questionnaire.getQuestionnaire());
            form.setProperty(FormUtils.SUBJECT_PROPERTY, visitSubject);
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
            if (this.formUtils.isForm(form) && form.hasProperty(STATUS_FLAGS)) {
                final Property statusProp = form.getProperty(STATUS_FLAGS);
                if (statusProp.isMultiple()) {
                    final Value[] statuses = form.getProperty(STATUS_FLAGS).getValues();
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

    private boolean updateVisitInformationFlags(final Node visitInformationForm)
    {
        try {
            if (visitInformationForm.hasProperty(STATUS_FLAGS)) {
                Set<String> flags = new TreeSet<>();
                boolean needsUpdate = false;
                for (Value v : visitInformationForm.getProperty(STATUS_FLAGS).getValues()) {
                    String flag = v.getString();
                    if ("SUBMITTED".equals(flag)) {
                        needsUpdate = true;
                    } else {
                        flags.add(flag);
                    }
                }
                if (needsUpdate) {
                    boolean checkedOut = checkoutIfNeeded(visitInformationForm);
                    visitInformationForm.setProperty(STATUS_FLAGS, flags.toArray(EMPTY_ARRAY_OF_STRINGS));
                    checkedOut |= save(visitInformationForm);
                    return checkedOut;
                }
            }
        } catch (final RepositoryException e) {
            LOGGER.error("Failed to obtain form data: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * Record a boolean value to the visit information form for the specified question/answer.
     * Will not update the visit information form if the question already has the desired answer.
     * Value is saved as a long rather than boolean per standard procedure.
     *
     * @param visitInformationForm the Visit Information form to save the value to, if needed
     * @param questionPath the relative path from the visit information questionnaire to the question to be answered
     * @param value the value that answer should be set to
     * @param session a service session providing access to the repository
     * @return true if the form was checked out
     */
    private boolean changeVisitInformation(final Node visitInformationForm, final String questionPath,
        final boolean value, final boolean checkin)
    {
        boolean checkedOut = false;
        try {
            final Long newValue = value ? 1L : 0L;
            final Node questionnaire = this.formUtils.getQuestionnaire(visitInformationForm);
            final Node question = this.questionnaireUtils.getQuestion(questionnaire, questionPath);
            if (question == null) {
                LOGGER.warn("Could not update visit information form as requested question could not be found: "
                    + questionPath);
                return false;
            }

            Node answer = this.formUtils.getAnswer(visitInformationForm, question);
            // Check if the value is already the correct one
            if (answer != null && newValue.equals(this.formUtils.getValue(answer))) {
                // Form is already set to the right value, nothing else to do
                return false;
            }

            // Checkout
            checkedOut = checkoutIfNeeded(visitInformationForm);

            if (answer == null) {
                // No answer node yet, create one
                answer = visitInformationForm.addNode(UUID.randomUUID().toString(), "cards:BooleanAnswer");
                answer.setProperty(FormUtils.QUESTION_PROPERTY, question);
            }

            // Set the new value
            answer.setProperty("value", newValue);
            checkedOut |= save(visitInformationForm);

            // All done, exit the try-loop
            return checkedOut;
        } catch (final RepositoryException e) {
            LOGGER.error("Failed to obtain form data: {}", e.getMessage(), e);
        } finally {
            if (checkin && checkedOut) {
                checkin(visitInformationForm);
            }
        }
        return false;
    }

    private boolean checkoutIfNeeded(final Node form) throws RepositoryException
    {
        form.getSession().refresh(true);
        if (!form.isCheckedOut()) {
            form.getSession().getWorkspace().getVersionManager().checkout(form.getPath());
            return true;
        }
        return false;
    }

    private void checkin(final Node form)
    {
        try {
            form.getSession().getWorkspace().getVersionManager().checkin(form.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check in the form: {}", e.getMessage(), e);
        }
    }

    private boolean save(final Node form) throws RepositoryException
    {
        try {
            form.getSession().save();
            return false;
        } catch (RepositoryException e) {
            boolean checkedOut = checkoutIfNeeded(form);
            form.getSession().save();
            return checkedOut;
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
        try {
            return questionnaire != null && "/Questionnaires/Visit information".equals(questionnaire.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if form is Visit Information Form: {}", e.getMessage(), e);
            return false;
        }
    }
}
