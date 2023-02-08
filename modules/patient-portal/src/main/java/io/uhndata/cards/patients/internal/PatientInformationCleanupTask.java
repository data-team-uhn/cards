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

import io.uhndata.cards.auth.token.impl.CardsTokenImpl;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Periodically clears last_name, first_name, email from any Patient information form of patient subjects
 * for whom the last Visit information form has surveys_submitted set to true or the token associated with the visit
 * has expired.
 *
 * @version $Id$
 * @since 0.9.2
 */
public class PatientInformationCleanupTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(PatientInformationCleanupTask.class);

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
                (String) resolver.getResource("/Questionnaires/Patient information").getValueMap().get("jcr:uuid");
            final String visitInformationQuestionnaire =
                (String) resolver.getResource("/Questionnaires/Visit information").getValueMap().get("jcr:uuid");

            // Query:
            final Iterator<Resource> resources = resolver.findResources(String.format(
                // select the data forms
                "select distinct patientInformation.*"
                    + "  from [cards:Form] as patientInformation"
                    // belonging to a visit
                    + "  inner join [cards:Form] as visitInformation on"
                    + "   isdescendantnode(visitInformation.subject, patientInformation.subject)"
                    + " where"
                    // link to the correct Visit Information questionnaire
                    + "  visitInformation.questionnaire = '%1$s'"
                    // link to the correct Patient Information questionnaire
                    + "  and patientInformation.questionnaire = '%2$s'",
                visitInformationQuestionnaire, patientInformationQuestionnaire),
                Query.JCR_SQL2);

            resources.forEachRemaining(form -> {
                try {
                    Node formNode = form.adaptTo(Node.class);
                    if (!canDeleteInformation(formNode, resolver, visitInformationQuestionnaire,
                        patientInformationQuestionnaire)) {
                        return;
                    }
                    final NodeIterator children = formNode.getNodes();
                    while (children.hasNext()) {
                        final Node child = children.nextNode();
                        if (child.isNodeType("cards:Answer")) {
                            final String name = child.getProperty("question").getNode().getName();
                            if ("first_name".equals(name) || "last_name".equals(name) || "email".equals(name)) {
                                child.remove();
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

    private boolean canDeleteInformation(final Node form, final ResourceResolver resolver, final String visitQ,
        final String patientQ) throws ValueFormatException, PathNotFoundException, RepositoryException
    {
        final String time = (String) resolver
            .getResource("/Questionnaires/Visit information/time").getValueMap().get("jcr:uuid");
        // run query to get all associated Visit information forms sorted by time property
        Iterator<Resource> resources = resolver.findResources(String.format(
            // select the data forms
            "select distinct visitInformation.*"
                + "  from [cards:Form] as visitInformation"
                // belonging to a visit
                + "  inner join [cards:Form] as patientInformation on"
                + "   isdescendantnode(visitInformation.subject, patientInformation.subject)"
                + "    inner join [cards:Answer] as time on isdescendantnode(time, visitInformation)"
                + " where"
                // link to the correct Visit Information questionnaire
                + "  visitInformation.questionnaire = '%1$s'"
                // link to the correct Patient Information questionnaire
                + "  and patientInformation.questionnaire = '%2$s'"
                + "  and patientInformation.[jcr:uuid] = '%3$s'"
                // link to the time question
                + "  and time.question = '%3$s'"
                + "  order by time desc",
                visitQ, patientQ, form.getProperty("jcr:uuid").getString(), time),
            Query.JCR_SQL2);

        if (!resources.hasNext()) {
            return false;
        }
        // get the last visit and see if it's surveys_submitted = true
        final Node visit = resources.next().adaptTo(Node.class);
        if (visit == null) {
            return false;
        }
        if (visit.hasProperty("surveys_submitted") && visit.getProperty("surveys_submitted").getBoolean()) {
            return true;
        }

        final Node visitSubjectPath = this.formUtils.getSubject(visit);
        // run the query to get an expired token associated with patient name and visit
        resources = resolver.findResources(String.format(
            "select * from [cards:Token]"
                + " where"
                + " [" + CardsTokenImpl.TOKEN_ATTRIBUTE_EXPIRY + "] < '%1$s'"
                + " and [cards:sessionSubject] = '%2$s'",
            ZonedDateTime.now(), visitSubjectPath.getPath()),
            Query.JCR_SQL2);
        if (resources.hasNext()) {
            return true;
        }

        return false;
    }
}
