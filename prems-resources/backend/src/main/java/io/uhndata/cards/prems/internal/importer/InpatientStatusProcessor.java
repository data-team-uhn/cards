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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import org.apache.commons.lang3.StringUtils;
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
    public InpatientStatusProcessor(final InpatientStatusProcessorConfigDefinition configuration)
    {
        super(configuration.enabled(), configuration.supportedTypes(), configuration.priority());
    }

    @Override
    public Map<String, String> processEntry(final Map<String, String> input)
    {
        final String mrn = input.get("/SubjectTypes/Patient");

        if (StringUtils.isBlank(mrn)) {
            LOGGER.warn("Unable to process row due to no mrn");
            return null;
        } else {
            // Get all patients with that MRN
            ResourceResolver resolver = this.rrp.getThreadResourceResolver();
            String subjectMatchQuery = String.format(
                "SELECT * FROM [cards:Subject] as subject WHERE subject.identifier='%s' option (index tag property)",
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

    private void processSubject(final ResourceResolver resolver, final Node subject)
        throws RepositoryException
    {
        // Iterate through the subjects visits
        final NodeIterator visitResourceIter = subject.getNodes();

        while (visitResourceIter.hasNext()) {
            Node visit = visitResourceIter.nextNode();

            if (this.subjectUtils.isSubject(visit)) {
                processVisit(resolver, visit);
            }
        }
    }

    private void processVisit(final ResourceResolver resolver, final Node visit)
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
                        surveyEventsForm.getProperty("jcr:created").getDate())) {
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

    private void processVisitDetails(final Session session, final Node visit, final Node visitInformationForm,
        final Node surveyEventsForm)
        throws RepositoryException
    {
        // Check if visit is recent enough for the survey to be valid
        if (isSurveyValid(session, visit, surveyEventsForm)) {
            // If initial email sent, put on-hold
            if (emailAlreadySent(session, surveyEventsForm) || surveyPartiallySubmitted(session, visit)) {
                setVisitOnHold(session, visitInformationForm);
            } else {
                // No emails have been sent for this visit: delete it so that the email cooldown is reset
                deleteVisit(session, visit);
            }
        }
    }

    private boolean isSurveyValid(final Session session, final Node visit, final Node surveyEventsForm)
        throws RepositoryException
    {
        try {
            if (this.formUtils.getValue(this.formUtils.getAnswer(
                surveyEventsForm, session.getNode("/Questionnaires/Survey events/responses_received"))) != null) {
                // Survey already submitted, don't process submitted forms
                return false;
            }

            Calendar expiry = (Calendar) this.formUtils.getValue(this.formUtils.getAnswer(
                surveyEventsForm, session.getNode("/Questionnaires/Survey events/survey_expiry")));
            return (expiry != null && expiry.after(Calendar.getInstance()));
        } catch (RepositoryException e) {
            // Should not happen
            LOGGER.error("Error determining if survey {} is valid", visit.getPath(), e);
        }
        return false;
    }

    private boolean emailAlreadySent(final Session session, final Node surveyEventsForm)
        throws RepositoryException
    {
        return this.formUtils.getValue(this.formUtils.getAnswer(surveyEventsForm,
            session.getNode("/Questionnaires/Survey events/invitation_sent"))) != null;
    }

    private boolean surveyPartiallySubmitted(final Session session, final Node visit) throws RepositoryException
    {
        for (final PropertyIterator forms = visit.getReferences("subject"); forms.hasNext();) {
            final Node form = forms.nextProperty().getParent();
            final String questionnaireName = this.formUtils.getQuestionnaire(form).getName();
            if (this.formUtils.getStatusFlags(form).contains("SUBMITTED")) {
                // If the form is submitted, skip it
                return true;
            } else if ("Survey events".equals(questionnaireName) && this.formUtils.getValue(this.formUtils
                .getAnswer(form, session.getNode("/Questionnaires/Survey events/responses_received"))) != null) {
                // If the form is a survey events form with responses received, skip it
                return true;
            }
        }
        return false;
    }

    private void setVisitOnHold(final Session session, final Node visitInformationForm)
        throws RepositoryException
    {
        try {
            final Node statusQuestion = session.getNode("/Questionnaires/Visit information/status");
            Node statusAnswer = this.formUtils.getAnswer(visitInformationForm, statusQuestion);
            final String existingStatus = (String) this.formUtils.getValue(statusAnswer);
            if (existingStatus != null
                && List.of("cancelled", "entered-in-error", "on-hold").contains(existingStatus)) {
                // Do nothing - already a status that does not receive emails
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
     * Delete a visit with all its forms.
     */
    private void deleteVisit(final Session session, final Node visit)
        throws RepositoryException
    {
        try {
            // Remove any forms for this visit
            for (final PropertyIterator forms = visit.getReferences("subject"); forms.hasNext();) {
                final Node form = forms.nextProperty().getParent();
                form.remove();
            }

            // Remove the Visit subject itself
            // Checkout the parent patient if required
            final Node patient = visit.getParent();
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
        } catch (RepositoryException e) {
            LOGGER.error("Error deleting visit {}", visit.getPath(), e);
        }
    }
}
