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
import javax.jcr.NodeIterator;
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
 * Periodically remove patient's answer values with a frequency specified by @{code PatientAccess.draftLifetime}
 * that haven't been submitted by the patient.
 *
 * @version $Id$
 * @since 0.9.6
 */
public class DraftsAnswersCleanupTask implements Runnable
{

    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DraftsAnswersCleanupTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    /** For sharing the resource resolver with other services. */
    private final ThreadResourceResolverProvider rrp;

    /** Grab details on the number of days draft responses from patients are kept. */
    private final PatientAccessConfiguration patientAccessConfiguration;

    /**
     * @param resolverFactory a valid ResourceResolverFactory providing access to resources
     * @param rrp sharing the resource resolver with other services
     * @param patientAccessConfiguration details on the number of days draft responses from patients are kept
     */
    DraftsAnswersCleanupTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
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
            final int draftLifetime = this.patientAccessConfiguration.getDraftLifetime();
            // If draftLifetime is -1, do nothing
            // (-1 means never delete; any cleanup that is necessary will be done by UnsubmittedFormsCleanupTask)
            if (draftLifetime == -1) {
                return;
            }

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
                    + "    inner join [cards:BooleanAnswer] as submitted"
                    + "      on submitted.form = visitInformation.[jcr:uuid]"
                    + " where"
                    // link to the correct Visit Information questionnaire
                    + "  visitInformation.questionnaire = '%1$s'"
                    // the data form was last modified by the patient before the allowed timeframe
                    + "  and dataForm.[jcr:lastModified] < '%2$s'"
                    + "  and"
                    + " (dataForm.[jcr:lastModifiedBy] = 'patient' or dataForm.[jcr:lastModifiedBy] = 'guest-patient')"
                    // the visit is not submitted
                    + "  and submitted.question = '%3$s'"
                    + "  and (submitted.value <> 1 OR submitted.value IS NULL)"
                    // exclude the Visit Information form itself
                    + "  and dataForm.questionnaire <> '%1$s'"
                    // use the fast index for the query
                    + " OPTION (index tag cards)",
                visitInformationQuestionnaire, ZonedDateTime.now().minusDays(draftLifetime)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")),
                submitted),
                Query.JCR_SQL2);
            resources.forEachRemaining(form -> {
                try {
                    final Node formNode = form.adaptTo(Node.class);
                    if (deleteFormAnswers(formNode, formNode)) {
                        resolver.commit();
                        formNode.getSession().getWorkspace().getVersionManager().checkin(formNode.getPath());
                    }
                } catch (RepositoryException | PersistenceException e) {
                    LOGGER.warn("Failed to delete patient's answer values from unsubmitted drafts from the form {}: {}",
                        form.getPath(), e.getMessage());
                }
            });
            resolver.commit();
        } catch (LoginException e) {
            LOGGER.warn("Invalid setup, service rights not set up for drafts answer cleanup task");
        } catch (PersistenceException e) {
            LOGGER.warn("Failed to delete patient's answer values from unsubmitted drafts: {}", e.getMessage());
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private boolean deleteFormAnswers(final Node form, final Node node)
        throws RepositoryException
    {
        final NodeIterator children = node.getNodes();
        boolean result = false;
        while (children.hasNext()) {
            final Node child = children.nextNode();
            if (child.isNodeType("cards:AnswerSection")) {
                result |= deleteFormAnswers(form, child);
            } else if (child.isNodeType("cards:Answer") && child.hasProperty("value")) {
                // Only the answers added by the patient should be deleted.
                // (entry mode: reference or autocreated)
                final Node questionNode = child.getProperty("question").getNode();
                if (questionNode != null && questionNode.hasProperty("entryMode")) {
                    final String entrymode = questionNode.getProperty("entryMode").getString();
                    if (!"reference".equals(entrymode) && !"autocreated".equals(entrymode)) {
                        form.getSession().getWorkspace().getVersionManager().checkout(form.getPath());
                        child.getProperty("value").remove();
                        result = true;
                    }
                }
            }
        }
        return result;
    }
}
