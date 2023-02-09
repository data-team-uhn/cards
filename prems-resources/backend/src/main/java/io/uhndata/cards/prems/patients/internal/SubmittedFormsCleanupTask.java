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

package io.uhndata.cards.prems.patients.internal;

import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.query.Query;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Periodically delete submitted survey responses older than max age.
 *
 * @version $Id$
 * @since 0.9.6
 */
public class SubmittedFormsCleanupTask implements Runnable
{

    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SubmittedFormsCleanupTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    /** For sharing the resource resolver with other services. */
    private final ThreadResourceResolverProvider rrp;

    private final int maxAgeDays;

    /**
     * @param resolverFactory a valid ResourceResolverFactory providing access to resources
     * @param patientAccessConfiguration details on patient authentication for token lifetime purposes
     * @param maxAgeDays OSGi config defines days of how long submissions can be kept in the database
     */
    SubmittedFormsCleanupTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final int maxAgeDays)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.maxAgeDays = maxAgeDays;
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
            final String submitted = (String) resolver
                .getResource("/Questionnaires/Visit information/surveys_submitted").getValueMap().get("jcr:uuid");

            // Query:
            final Iterator<Resource> resources = resolver.findResources(String.format(
                // select the data forms
                "select distinct dataForm.*"
                    + "  from [cards:Form] as dataForm"
                    // belonging to a visit
                    + "  inner join [cards:Form] as visitInformation on visitInformation.subject = dataForm.subject"
                    + "    inner join [cards:Answer] as submitted on isdescendantnode(submitted, visitInformation)"
                    + " where"
                    // link to the correct Visit Information questionnaire
                    + "  visitInformation.questionnaire = '%1$s'"
                    // the data form was last modified by the patient before the allowed timeframe
                    + "  and dataForm.[jcr:lastModified] < '%2$s'"
                    // the visit is submitted
                    + "  and submitted.question = '%3$s'"
                    + "  and submitted.value = 1"
                    // exclude the Visit Information form itself
                    + "  and dataForm.questionnaire <> '%1$s'",
                visitInformationQuestionnaire, ZonedDateTime.now().minusDays(this.maxAgeDays), submitted),
                Query.JCR_SQL2);
            resources.forEachRemaining(form -> {
                try {
                    resolver.delete(form);
                } catch (final PersistenceException e) {
                    LOGGER.warn("Failed to delete expired survey form {}: {}", form.getPath(), e.getMessage());
                }
            });
            resolver.commit();
        } catch (final LoginException e) {
            LOGGER.warn("Invalid setup, service rights not set up for the expired survey forms cleanup task");
        } catch (final PersistenceException e) {
            LOGGER.warn("Failed to delete expired survey responses forms: {}", e.getMessage());
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }
}
