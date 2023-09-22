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

package io.uhndata.cards.prems.internal.integratedcare;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Periodically switch visits eligible for Integrated Care surveys from the initial one (ED, IP or EDIP) to the
 * equivalent one that also includes the IC survey.
 *
 * @version $Id$
 * @since 0.9.17
 */
public class IntegratedCareSwitchingTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(IntegratedCareSwitchingTask.class);

    /**
     * The IC-only clinic, used when the patient already submitted all the required Patient Experience forms for the
     * visit.
     */
    private static final String DEFAULT_IC_CLINIC = "/Survey/ClinicMapping/-1792626676";

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    /** For sharing the resource resolver with other services. */
    private final ThreadResourceResolverProvider rrp;

    private final FormUtils formUtils;

    private Node visitInformationQuestionnaire;

    private Node clinicQuestion;

    private Node statusQuestion;

    private Node visitDateQuestion;

    private Node submittedQuestion;

    /**
     * @param resolverFactory a valid ResourceResolverFactory providing access to resources
     * @param rrp sharing the resource resolver with other services
     * @param patientAccessConfiguration details on the number of days draft responses from patients are kept
     */
    IntegratedCareSwitchingTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final FormUtils formUtils)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.formUtils = formUtils;
    }

    @Override
    public void run()
    {
        // Get a new JCR session.
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "VisitFormsPreparation"))) {
            this.rrp.push(resolver);
            mustPopResolver = true;
            final Session session = resolver.adaptTo(Session.class);

            // Gather the needed UUIDs to place in the query
            this.visitInformationQuestionnaire = session.getNode("/Questionnaires/Visit information");
            this.clinicQuestion = session.getNode("/Questionnaires/Visit information/clinic");
            this.statusQuestion = session.getNode("/Questionnaires/Visit information/status");
            this.visitDateQuestion = session.getNode("/Questionnaires/Visit information/time");
            this.submittedQuestion = session.getNode("/Questionnaires/Visit information/surveys_submitted");

            final Map<String, String> clinicsToFix = gatherClinicsToUpdate(session);
            clinicsToFix
                .forEach((clinic, newClinic) -> updateVisits(clinic, newClinic, session));
        } catch (LoginException e) {
            LOGGER.warn("Invalid setup, service rights not set up for drafts answer cleanup task: {}", e.getMessage());
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to update Integrated Care clinics: {}", e.getMessage());
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
            this.visitInformationQuestionnaire = null;
            this.clinicQuestion = null;
            this.statusQuestion = null;
            this.visitDateQuestion = null;
            this.submittedQuestion = null;
        }
    }

    /**
     * Get all the clinics for which an equivalent "+IC" clinic also exists.
     *
     * @param session a valid JCR session
     * @return a map of clinic paths, from a clinic to the equivalent clinic that also includes the IC forms
     * @throws RepositoryException if accessing the repository fails
     */
    private Map<String, String> gatherClinicsToUpdate(final Session session) throws RepositoryException
    {
        final NodeIterator clinics = session.getNode("/Survey/ClinicMapping").getNodes();
        final Map<String, String> nameToId = new HashMap<>();
        while (clinics.hasNext()) {
            final Node clinic = clinics.nextNode();
            if (clinic.isNodeType("cards:ClinicMapping")) {
                nameToId.put(clinic.getProperty("clinicName").getString(), clinic.getPath());
            }
        }
        final Map<String, String> result = new HashMap<>();
        nameToId.forEach((name, id) -> {
            if (nameToId.containsKey(name + "IC")) {
                result.put(id, nameToId.get(name + "IC"));
            }
        });
        return result;
    }

    /**
     * Switch the eligible visits belonging to a clinic to the equivalent +IC clinic.
     *
     * @param clinic the old clinic path
     * @param newClinic the new clinic path
     * @param session a valid JCR session
     */
    private void updateVisits(final String clinic, final String newClinic, final Session session)
    {
        try {
            // Query:
            final String query = String.format(
                // select the Visit Information forms
                "select distinct visitInformation.*"
                    + "  from [cards:Form] as visitInformation"
                    + "    inner join [cards:ResourceAnswer] as clinic on clinic.form = visitInformation.[jcr:uuid]"
                    + "    inner join [cards:TextAnswer] as status on status.form = visitInformation.[jcr:uuid]"
                    + "    inner join [cards:DateAnswer] as visitDate on visitDate.form=visitInformation.[jcr:uuid]"
                    + " where"
                    // the form is a Visit Information form
                    + "  visitInformation.questionnaire = '%1$s'"
                    // the form belongs to the correct clinic
                    + "  and clinic.question = '%2$s' and clinic.value = '%3$s'"
                    // the status is in-progress
                    + "  and status.question = '%4$s' and status.value = 'in-progress'"
                    // the visit happened more than 30 days ago
                    + "  and visitDate.question = '%5$s' and visitDate.value <= '%6$s'"
                    // use the fast index for the query
                    + " OPTION (index tag cards)",
                this.visitInformationQuestionnaire.getIdentifier(),
                this.clinicQuestion.getIdentifier(), clinic,
                this.statusQuestion.getIdentifier(),
                this.visitDateQuestion.getIdentifier(),
                ZonedDateTime.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")));
            final NodeIterator visits = session.getWorkspace().getQueryManager().createQuery(query,
                Query.JCR_SQL2).execute().getNodes();
            while (visits.hasNext()) {
                Node visitForm = visits.nextNode();
                final Node clinicAnswer = this.formUtils.getAnswer(visitForm, this.clinicQuestion);
                final Node statusAnswer = this.formUtils.getAnswer(visitForm, this.statusQuestion);
                final Long submittedAnswer =
                    (Long) this.formUtils.getValue(this.formUtils.getAnswer(visitForm, this.submittedQuestion));
                final boolean checkinNeeded = checkoutIfNeeded(visitForm, session);
                statusAnswer.setProperty("value", "discharged");
                if (Long.valueOf(1L).equals(submittedAnswer)) {
                    clinicAnswer.setProperty("value", DEFAULT_IC_CLINIC);
                } else {
                    clinicAnswer.setProperty("value", newClinic);
                }
                session.save();
                if (checkinNeeded) {
                    checkin(visitForm, session);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("Failed to update clinic: {}", e.getMessage(), e);
        }
    }

    private boolean checkoutIfNeeded(final Node form, final Session session) throws RepositoryException
    {
        session.refresh(true);
        if (!form.isCheckedOut()) {
            session.getWorkspace().getVersionManager().checkout(form.getPath());
            return true;
        }
        return false;
    }

    private void checkin(final Node form, final Session session)
    {
        try {
            session.getWorkspace().getVersionManager().checkin(form.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check in the form: {}", e.getMessage(), e);
        }
    }
}
