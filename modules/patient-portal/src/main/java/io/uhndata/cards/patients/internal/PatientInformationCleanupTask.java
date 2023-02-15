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
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Periodically clears last_name, first_name, email from any Patient information form of patient subjects
 * for whom Survey events form corresponding to the latest visit (i.e. for which the answer to discharged_date
 * is the latest) has at least one of responses_received or reminder2_sent answers filled in with a date value.
 *
 * @version $Id$
 * @since 0.9.6
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

    /**
     * @param resolverFactory a valid ResourceResolverFactory providing access to resources
     * @param rrp ThreadResourceResolverProvider sharing the resource resolver with other services
     * @param formUtils for working with form data
     */
    PatientInformationCleanupTask(final ResourceResolverFactory resolverFactory,
        final ThreadResourceResolverProvider rrp, final FormUtils formUtils)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.formUtils = formUtils;
    }

    @Override
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public void run()
    {
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "VisitFormsPreparation"))) {
            this.rrp.push(resolver);
            mustPopResolver = true;

            // Gather the needed UUIDs to place in the query
            final String patientInformationQuestionnaire =
                (String) resolver.getResource("/Questionnaires/Patient information").getValueMap().get(UUID_KEY);
            final String surveyEventsQuestionnaire =
                (String) resolver.getResource("/Questionnaires/Survey events").getValueMap().get(UUID_KEY);
            final String dischargedDate = (String) resolver
                .getResource("/Questionnaires/Survey events/discharged_date").getValueMap().get(UUID_KEY);

            // Query:
            final Iterator<Resource> resources = resolver.findResources(String.format(
                // select the Patient information forms
                "select distinct patientInformation.*"
                    + "  from [cards:Form] as patientInformation"
                    // with Survey events
                    + "  inner join [cards:Form] as surveyEvents on"
                    + "  isdescendantnode(surveyEvents.subject, patientInformation.subject)"
                    // for which the answer to discharged_date exists
                    + "  inner join [cards:Answer] as dischargedDate on isdescendantnode(dischargedDate, surveyEvents)"
                    + " where"
                    + " dischargedDate.value IS NOT NULL"
                    // link to the correct Survey events questionnaire
                    + "  and surveyEvents.questionnaire = '%1$s'"
                    // link to the correct Patient Information questionnaire
                    + "  and patientInformation.questionnaire = '%2$s'"
                    // link to the discharged_date question
                    + "  and dischargedDate.question = '%3$s'",
                    surveyEventsQuestionnaire, patientInformationQuestionnaire, dischargedDate),
                Query.JCR_SQL2);

            resources.forEachRemaining(form -> {
                try {
                    Node formNode = form.adaptTo(Node.class);
                    if (!canDeleteInformation(formNode, resolver, patientInformationQuestionnaire,
                        surveyEventsQuestionnaire, dischargedDate)) {
                        return;
                    }

                    final NodeIterator children = formNode.getNodes();
                    while (children.hasNext()) {
                        final Node child = children.nextNode();
                        if (child.isNodeType("cards:Answer")) {
                            final String name = child.getProperty("question").getNode().getName();
                            if ("first_name".equals(name) || "last_name".equals(name) || "email".equals(name)) {
                                child.getProperty("value").remove();
                            }
                        }
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

    private boolean canDeleteInformation(final Node form, final ResourceResolver resolver, final String patientQ,
        final String surveyQ, final String dischargedDate)
        throws ValueFormatException, PathNotFoundException, RepositoryException
    {
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
                surveyQ, dischargedDate, visitSubjectUuid),
            Query.JCR_SQL2);

        if (!resources.hasNext()) {
            return false;
        }
        // get the last Survey events form (i.e. for which the answer to discharged_date is the latest)
        final Node survey = resources.next().adaptTo(Node.class);
        if (survey == null) {
            return false;
        }

        // see if form has at least one of responses_received or reminder2_sent answers filled in with a date value
        final String surveyUuid = survey.getProperty(UUID_KEY).getString();
        final String responsesReceived = (String) resolver
            .getResource("/Questionnaires/Survey events/responses_received").getValueMap().get(UUID_KEY);
        final String reminderSent = (String) resolver
            .getResource("/Questionnaires/Survey events/reminder2Sent").getValueMap().get(UUID_KEY);

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
                surveyUuid, surveyQ, responsesReceived, reminderSent),
            Query.JCR_SQL2);

        if (!resource.hasNext()) {
            return false;
        }

        return false;
    }
}
