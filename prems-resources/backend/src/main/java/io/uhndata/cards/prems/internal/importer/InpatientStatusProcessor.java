/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.uhndata.cards.prems.internal.importer;

import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.AbstractClarityDataProcessor;
import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Clarity import processor that puts on hold or discards any active visits for a given MRN.
 *
 * @version $Id$
 */
@Component
@Designate(ocd = InpatientStatusProcessor.InpatientStatusProcessorConfigDefinition.class)
public class InpatientStatusProcessor extends AbstractClarityDataProcessor implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(InpatientStatusProcessor.class);

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @ObjectClassDefinition(name = "Clarity import processor - Inpatient Status",
        description = "Configuration for the Clarity importer processor that puts on hold any active visits for a "
        + "given MRN")
    public @interface InpatientStatusProcessorConfigDefinition
    {
        @AttributeDefinition(name = "Enabled")
        boolean enabled() default false;

        @AttributeDefinition(name = "Supported import types", description = "Leave empty to support all imports")
        String[] supportedTypes();

        @AttributeDefinition(name = "Priority", description = "Clarity Data Processor priority."
            + " Processors are run in ascending priority order")
        int priority() default 35;
    }

    @Activate
    public InpatientStatusProcessor(InpatientStatusProcessorConfigDefinition configuration)
    {
        super(configuration.enabled(), configuration.supportedTypes(), configuration.priority());
    }

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        final String mrn = input.get("/SubjectTypes/Patient");

        if (mrn == null || mrn.length() == 0) {
            LOGGER.warn("Unable to process row due to no mrn");
            return null;
        } else {
            // Get all patients with that MRN
            ResourceResolver resolver = this.rrp.getThreadResourceResolver();
            String subjectMatchQuery = String.format(
                "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='%s' option (index tag property)",
                mrn);
            resolver.refresh();
            final Iterator<Resource> subjectResourceIter = resolver.findResources(subjectMatchQuery, "JCR-SQL2");

            // Should only be 0 or 1 patient with that MRN. Process it if found.
            if (subjectResourceIter.hasNext()) {
                try {
                    processSubject(resolver, subjectResourceIter.next().adaptTo(Node.class));
                } catch (RepositoryException e) {
                    LOGGER.error("Unexpected error processing inpatient status for {}:", mrn, e);
                }
            }
        }

        // All processing for this row is complete: Discard
        return null;
    }

    private void processSubject(ResourceResolver resolver, Node subject)
        throws RepositoryException
    {
        // Iterate through the subjects visits
        final NodeIterator visitResourceIter = subject.getNodes();

        while (visitResourceIter.hasNext()) {
            Node visit = visitResourceIter.nextNode();

            if (SubjectUtils.SUBJECT_NODETYPE.equals(visit.getPrimaryNodeType().getName())) {
                processVisit(resolver, visit);
            }
        }
    }

    private void processVisit(ResourceResolver resolver, Node visit)
        throws RepositoryException
    {
        try {
            // Get the visit information and survey events form for this visit
            Node visitInformationForm = null;
            Node surveyEventsForm = null;
            final PropertyIterator forms = visit.getReferences("subject");
            while (forms.hasNext()) {
                Node form = forms.nextProperty().getParent();
                String questionnairePath = this.formUtils.getQuestionnaire(form).getPath();
                if ("/Questionnaires/Visit information".equals(questionnairePath)) {
                    visitInformationForm = form;
                } else if ("/Questionnaires/Survey events".equals(questionnairePath)) {
                    if (surveyEventsForm == null) {
                        surveyEventsForm = form;
                    } else if (form.getProperty("jcr:created").getDate().after(
                        surveyEventsForm.getProperty("jcr:created"))
                    ) {
                        surveyEventsForm = form;
                    }
                }
            }

            if (visitInformationForm == null || surveyEventsForm == null) {
                LOGGER.warn("Could not process inpatient status for visit {} due to missing form",
                    visit.getPath());
                return;
            }

            processVisitDetails(resolver.adaptTo(Session.class), visit, visitInformationForm, surveyEventsForm);
        } catch (RepositoryException e) {
            // Visit cannot be processed:s
            LOGGER.warn("Cannot process visit {}", visit.getPath(), e);
        }
    }

    private void processVisitDetails(Session session, Node visit, Node visitInformationForm, Node surveyEventsForm)
        throws RepositoryException
    {
        // Check if visit is recent enough for the survey to be valid
        if (isSurveyValid(session, visitInformationForm)) {
            // If initial email sent, put on-hold
            if (this.formUtils.getValue(this.formUtils.getAnswer(surveyEventsForm,
                session.getNode("/Questionnaires/Survey events/invitation_sent"))) != null)
            {
                setVisitOnHold(session, visitInformationForm);
            } else {
                // No emails have been sent for this visit: delete it so that the email cooldown is reset
                tryDeleteVisit(session, visit);
            }
        }
    }

    private boolean isSurveyValid(Session session, Node visitInformationForm)
        throws RepositoryException
    {
        try {
            // Get the clinic's configured valid period
            String clinicMappingPath = (String) this.formUtils.getValue(this.formUtils.getAnswer(visitInformationForm,
                session.getNode("/Questionnaires/Visit information/clinic")));
            Integer validDays = null;
            if (clinicMappingPath != null && clinicMappingPath.length() > 0) {
                Node clinicMapping = session.getNode(clinicMappingPath);
                if (clinicMapping.hasProperty("daysRelativeToEventWhileSurveyIsValid")) {
                    validDays = (int) clinicMapping.getProperty("daysRelativeToEventWhileSurveyIsValid").getLong();
                }
            }
            // No configured valid period for that clinic: get the default
            if (validDays == null) {
                Node patientAccessNode = session.getNode("/Survey/PatientAccess");
                if (patientAccessNode.hasProperty("daysRelativeToEventWhileSurveyIsValid")) {
                    validDays = (int) patientAccessNode.getProperty("daysRelativeToEventWhileSurveyIsValid").getLong();
                }
            }
            if (validDays == null) {
                LOGGER.error("Unable to determine if survey is valid: No default range or range for clinic {}",
                    clinicMappingPath);
                return false;
            }

            Calendar visitDate = (Calendar) this.formUtils.getValue(this.formUtils.getAnswer(visitInformationForm,
                session.getNode("/Questionnaires/Visit information/time")));
            if (visitDate != null) {
                visitDate.add(Calendar.DATE, validDays);
                return Calendar.getInstance().compareTo(visitDate) <= 0;
            }
        } catch (RepositoryException e) {
            // Should not happen
            LOGGER.error("Error determining if survey {} is valid", visitInformationForm.getPath(), e);
        }
        return true;
    }

    private void setVisitOnHold(Session session, Node visitInformationForm)
        throws RepositoryException
    {
        try {
            Node statusQuestion = session.getNode("/Questionnaires/Visit information/status");
            Node statusAnswer = this.formUtils.getAnswer(visitInformationForm, statusQuestion);
            String status = (String) this.formUtils.getValue(statusAnswer);
            if ("cancelled".equals(status) || "entered-in-error".equals(status)) {
                // Do nothing - already a status that does not recieve emails
                return;
            }

            // Checkout visit information form
            VersionManager versionManager = session.getWorkspace().getVersionManager();
            boolean mustCheckout = !versionManager.isCheckedOut(visitInformationForm.getPath());
            if (mustCheckout) {
                versionManager.checkout(visitInformationForm.getPath());
            }
            if (statusAnswer == null) {
                statusAnswer = visitInformationForm.addNode(UUID.randomUUID().toString(), "cards:TextAnswer");
                statusAnswer.setProperty("question", statusQuestion);
            }
            statusAnswer.setProperty(FormUtils.VALUE_PROPERTY, "on-hold");
            session.save();
            if (mustCheckout) {
                versionManager.checkin(visitInformationForm.getPath());
            }
        } catch (RepositoryException e) {
            LOGGER.error("Error putting visit information form {} on hold", visitInformationForm.getPath(), e);
        }
    }

    /**
     * Delete a visit if there are not already submitted forms.
     * If there are already submitted forms:
     * - Delete the unsubmitted patient forms
     * - Change the visit information form to on-hold
     * - Keep any survey event forms with responses received, delete others
     */
    private void tryDeleteVisit(Session session, Node visit)
        throws RepositoryException
    {
        try {
            // Checkout the parent patient if required
            final Node patient = visit.getParent();
            boolean skippedForm = false;
            Node visitInformationForm = null;
            // Remove any forms for this visit
            for (final PropertyIterator forms = visit.getReferences("subject"); forms.hasNext();) {
                final Node form = forms.nextProperty().getParent();
                final String questionnairePath = this.formUtils.getQuestionnaire(form).getPath();
                if (this.formUtils.getStatusFlags(form).contains("SUBMITTED")) {
                    // If the form is submitted, skip it
                    skippedForm = true;
                } else if (questionnairePath.endsWith("Survey events")
                    && this.formUtils.getValue(this.formUtils.getAnswer(form,
                        session.getNode("/Questionnaires/Survey events/responses_received"))) != null
                ) {
                    // If the form is a survey events form with responses recieved, skip it
                    skippedForm = true;
                } else if (questionnairePath.endsWith("Visit information")) {
                    // Handle visit information forms later
                    visitInformationForm = form;
                } else {
                    form.remove();
                }
            }
            handleDeleteVisit(session, visit, patient, skippedForm, visitInformationForm);
        } catch (RepositoryException e) {
            LOGGER.error("Error deleting visit {}", visit.getPath(), e);
        }
    }

    private void handleDeleteVisit(final Session session, final Node visit, final Node patient,
        final boolean skippedForm, final Node visitInformationForm)
        throws RepositoryException
    {
        if (skippedForm) {
            if (visitInformationForm != null) {
                setVisitOnHold(session, visitInformationForm);
            }
        } else {
            if (visitInformationForm != null) {
                visitInformationForm.remove();
            }
            // Remove the visit
            boolean mustCheckout = false;
            VersionManager versionManager = session.getWorkspace().getVersionManager();
            if (!patient.isCheckedOut()) {
                mustCheckout = true;
                versionManager.checkout(patient.getPath());
            }
            visit.remove();
            session.save();
            if (mustCheckout) {
                versionManager.checkin(patient.getPath());
            }
        }
    }
}
