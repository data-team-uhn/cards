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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.patients.api.PatientAccessConfiguration;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Periodically remove visit data forms (except the Visit Information itself) belonging to past visits that haven't been
 * submitted by the patient.
 *
 * @version $Id$
 * @since 0.9.2
 */
public class UnsubmittedFormsCleanupTask implements Runnable
{

    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsubmittedFormsCleanupTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    /** For sharing the resource resolver with other services. */
    private final ThreadResourceResolverProvider rrp;

    /** Grab details on patient authentication for token lifetime purposes. */
    private final PatientAccessConfiguration patientAccessConfiguration;

    /**
     * @param resolverFactory a valid ResourceResolverFactory providing access to resources
     * @param patientAccessConfiguration details on patient authentication for token lifetime purposes
     */
    UnsubmittedFormsCleanupTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final PatientAccessConfiguration patientAccessConfiguration)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.patientAccessConfiguration = patientAccessConfiguration;
    }

    @Override
    public void run()
    {
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "VisitFormsPreparation"))) {
            this.rrp.push(resolver);
            mustPopResolver = true;
            // Gather the needed UUIDs to place in the query
            final String visitInformationQuestionnaire =
                (String) resolver.getResource("/Questionnaires/Visit information").getValueMap().get("jcr:uuid");
            final String time =
                (String) resolver.getResource("/Questionnaires/Visit information/time").getValueMap().get("jcr:uuid");
            final String submitted = (String) resolver
                .getResource("/Questionnaires/Visit information/surveys_submitted").getValueMap().get("jcr:uuid");
            final int patientTokenLifetime = this.patientAccessConfiguration.getAllowedPostVisitCompletionTime();

            // Find all clinics to iterate over
            final Iterator<Resource> results = resolver.findResources(
                "select * from [cards:ClinicMapping] as clinic", Query.JCR_SQL2);
            while (results.hasNext()) {
                Resource resource = results.next();
                final Node clinicNode = resource.adaptTo(Node.class);
                final String clinicPath = clinicNode.getPath();
                // Get clinic token lifetime or default to the global patient token lifetime
                int delay = patientTokenLifetime;
                if (clinicNode.hasProperty("allowedPostVisitCompletionTime")) {
                    delay = (int) clinicNode.getProperty("allowedPostVisitCompletionTime").getLong();
                }

                // Get all data forms for the specific clinic
                final Iterator<Resource> resources = resolver.findResources(String.format(
                    // select the data forms
                    "select distinct dataForm.*"
                        + "  from [cards:Form] as dataForm"
                        // belonging to a visit
                        + "  inner join [cards:Form] as visitInformation on visitInformation.subject = dataForm.subject"
                        + " inner join [cards:DateAnswer] as visitDate on visitDate.form = visitInformation.[jcr:uuid]"
                        + "    inner join [cards:BooleanAnswer] as submitted"
                        + "      on submitted.form = visitInformation.[jcr:uuid]"
                        + " where"
                        // link to the correct Visit Information questionnaire
                        + "  visitInformation.questionnaire = '%1$s'"
                        // link to the exact clinic in Visit Information form
                        + "  and visitInformation.clinic = '%5$s'"
                        // the visit date is in the past
                        + "  and visitDate.question = '%2$s'"
                        + "  and visitDate.value < '%4$s'"
                        // the visit is not submitted
                        + "  and submitted.question = '%3$s'"
                        + "  and (submitted.value <> 1 OR submitted.value IS NULL)"
                        // exclude the Visit Information form itself
                        + "  and dataForm.questionnaire <> '%1$s'"
                        + " option (index tag cards)",
                    visitInformationQuestionnaire, time, submitted,
                    ZonedDateTime.now().minusDays(delay)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")), clinicPath),
                    Query.JCR_SQL2);
                resources.forEachRemaining(form -> {
                    try {
                        resolver.delete(form);
                    } catch (final PersistenceException e) {
                        LOGGER.warn("Failed to delete expired form {}: {}", form.getPath(), e.getMessage());
                    }
                });
                resolver.commit();
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to fetch forms: {}", e.getMessage());
        } catch (final LoginException e) {
            LOGGER.warn("Invalid setup, service rights not set up for the expired forms cleanup task");
        } catch (final PersistenceException e) {
            LOGGER.warn("Failed to delete expired forms: {}", e.getMessage());
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }
}
