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

package io.uhndata.cards.patients.internal;

import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;
import javax.jcr.query.Query;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.patients.api.DataRetentionConfiguration;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Periodically clears last_name, first_name, email from any Patient information form because we no longer need it.
 *
 * @version $Id$
 * @since 0.9.16
 */
public class PatientInformationCleanupTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(PatientInformationCleanupTask.class);
    private static final String UUID_KEY = "jcr:uuid";

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    /** For sharing the resource resolver with other services. */
    private final ThreadResourceResolverProvider rrp;

    private final FormUtils formUtils;

    private final DataRetentionConfiguration dataRetentionConfiguration;

    /**
     * @param resolverFactory a valid ResourceResolverFactory providing access to resources
     * @param rrp ThreadResourceResolverProvider sharing the resource resolver with other services
     * @param formUtils for working with form data
     */
    PatientInformationCleanupTask(final ResourceResolverFactory resolverFactory,
        final ThreadResourceResolverProvider rrp, final FormUtils formUtils,
        final DataRetentionConfiguration dataRetentionConfiguration)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.formUtils = formUtils;
        this.dataRetentionConfiguration = dataRetentionConfiguration;
    }

    @Override
    public void run()
    {
        if (!this.dataRetentionConfiguration.deleteUnneededPatientDetails()) {
            return;
        }
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "VisitFormsPreparation"))) {
            this.rrp.push(resolver);
            mustPopResolver = true;
            final String patientInformationQuestionnaire =
                (String) resolver.getResource("/Questionnaires/Patient information").getValueMap().get(UUID_KEY);
            final Iterator<Resource> resources = findPatientInformationForms(patientInformationQuestionnaire, resolver);
            resources.forEachRemaining(form -> {
                try {
                    Node formNode = form.adaptTo(Node.class);
                    if (!canDeleteInformation(formNode, resolver, patientInformationQuestionnaire)) {
                        return;
                    }

                    if (deleteFormAnswers(formNode)) {
                        formNode.getSession().getWorkspace().getVersionManager().checkin(formNode.getPath());
                    }
                } catch (final RepositoryException e) {
                    LOGGER.warn("Failed to delete patient information {}: {}", form.getPath(), e.getMessage());
                }
            });
            resolver.commit();
        } catch (final LoginException e) {
            LOGGER.warn("Invalid setup, service rights not set up for the patient information cleanup task");
        } catch (final PersistenceException e) {
            LOGGER.warn("Failed to delete patient information: {}", e.getMessage());
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private Iterator<Resource> findPatientInformationForms(final String patientInformationQuestionnaire,
        final ResourceResolver resolver)
    {
        // Gather the needed UUIDs to place in the query
        final String firstName = (String) resolver.getResource("/Questionnaires/Patient information/first_name")
            .getValueMap().get(UUID_KEY);
        final String lastName = (String) resolver.getResource("/Questionnaires/Patient information/last_name")
            .getValueMap().get(UUID_KEY);
        final String email =
            (String) resolver.getResource("/Questionnaires/Patient information/email").getValueMap().get(UUID_KEY);

        // Query:
        return resolver.findResources(String.format(
            // select all of the Patient information forms with some of private information filled
            "select distinct patientInfoForm.*"
                + "  from [cards:Form] as patientInforForm"
                + "    inner join [cards:TextAnswer] as firstName on isdescendantnode(firstName, patientInfoForm)"
                + "    inner join [cards:TextAnswer] as lastName on isdescendantnode(lastName, patientInfoForm)"
                + "    inner join [cards:TextAnswer] as email on isdescendantnode(email, patientInfoForm)"
                + " where"
                // for which at least one of first_name, last_name, email answers is filled in
                + " (firstName.value IS NOT NULL or lastName.value IS NOT NULL or email.value IS NOT NULL)"
                // link to the correct Patient Information questionnaire
                + "  and patientInformation.questionnaire = '%1$s'"
                // link to the first_name question
                + "  and firstName.question = '%2$s'"
                // link to the last_name question
                + "  and firstName.question = '%3$s'"
                // link to the email question
                + "  and email.question = '%4$s'"
                // use the fast index
                + " option (index tag cards)",
            patientInformationQuestionnaire, firstName, lastName, email),
            Query.JCR_SQL2);
    }

    private boolean canDeleteInformation(final Node form, final ResourceResolver resolver, final String patientQ)
        throws ValueFormatException, PathNotFoundException, RepositoryException
    {
        final String surveyEventsQuestionnaire =
            (String) resolver.getResource("/Questionnaires/Survey events").getValueMap().get(UUID_KEY);
        final String dischargedDate = (String) resolver
            .getResource("/Questionnaires/Survey events/discharged_date").getValueMap().get(UUID_KEY);
        final Node visitSubject = this.formUtils.getSubject(form, "/SubjectTypes/Patient/Visit");
        final String visitSubjectUuid = visitSubject.getProperty(UUID_KEY).getString();

        // run query to get all associated Survey events forms sorted by discharged_date property
        Iterator<Resource> resources = resolver.findResources(String.format(
            // select the Survey events forms
            "select distinct surveyEvents.*"
                + "  from [cards:Form] as surveyEvents"
                + "    inner join [cards:Answer] as dischargedDate on isdescendantnode(dischargedDate, surveyEvents)"
                + " where"
                // for which the answer to discharged_date exists
                + "  dischargedDate.value IS NOT NULL"
                // link to the correct Survey events questionnaire
                + "  and surveyEvents.questionnaire = '%1$s'"
                // link to the discharged_date question
                + "  and dischargedDate.question = '%2$s'"
                // link to the patient visit subject
                + "  and surveyEvents.subject = '%3$s'"
                + " order by dischargedDate.value desc",
                surveyEventsQuestionnaire, dischargedDate, visitSubjectUuid),
            Query.JCR_SQL2);

        // If there are no visit information forms, delete the survey event since there's no visit to notify about
        if (!resources.hasNext()) {
            return true;
        }
        // Get the last Survey events form (i.e. for which the answer to discharged_date is the latest)
        final Node survey = resources.next().adaptTo(Node.class);
        if (survey == null) {
            return true;
        }

        // see if form has at least one of responses_received or reminder2_sent answers filled in with a date value
        final String surveyUuid = survey.getProperty(UUID_KEY).getString();
        final String responsesReceived = (String) resolver
            .getResource("/Questionnaires/Survey events/responses_received").getValueMap().get(UUID_KEY);
        final String reminderSent = (String) resolver
            .getResource("/Questionnaires/Survey events/reminder2_sent").getValueMap().get(UUID_KEY);

        // run query to see if this Survey events form has at least one of responses_received or reminder2_sent answers
        Iterator<Resource> resource = resolver.findResources(String.format(
            // select the Survey events forms
            "select distinct surveyEvents.*"
                + " from [cards:Form] as surveyEvents "
                + "inner join [cards:Answer] as responsesReceived on isdescendantnode(responsesReceived, surveyEvents)"
                + " inner join [cards:Answer] as reminderSent on isdescendantnode(reminderSent, surveyEvents)"
                + " where"
                // link to the given survey events form
                + " surveyEvents.'jcr:uuid' = '%1$s'"
                // for which at least one of responses_received or reminder2_sent answers filled in with a date value
                + " and (responsesReceived.value IS NOT NULL or reminderSent.value IS NOT NULL)"
                // link to the correct Survey events questionnaire
                + " and surveyEvents.questionnaire = '%2$s'"
                // link to the responses_received question
                + " and responsesReceived.question = '%3$s'"
                // link to the reminder2_sent question
                + " and reminderSent.question = '%4$s'",
                surveyUuid, surveyEventsQuestionnaire, responsesReceived, reminderSent),
            Query.JCR_SQL2);

        if (!resource.hasNext()) {
            return false;
        }

        return true;
    }

    private boolean deleteFormAnswers(final Node form)
        throws RepositoryException
    {
        boolean result = false;
        final NodeIterator children = form.getNodes();
        while (children.hasNext()) {
            final Node child = children.nextNode();
            if (child.isNodeType("cards:Answer")) {
                final String name = child.getProperty("question").getNode().getName();
                if ("first_name".equals(name) || "last_name".equals(name) || "email".equals(name)) {
                    form.getSession().getWorkspace().getVersionManager().checkout(form.getPath());
                    child.getProperty("value").remove();
                    result = true;
                }
            }
        }
        return result;
    }
}
