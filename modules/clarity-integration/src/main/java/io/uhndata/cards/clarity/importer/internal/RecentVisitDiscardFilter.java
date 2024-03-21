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

package io.uhndata.cards.clarity.importer.internal;

import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

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
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Clarity import processor that discards visits for patients who have recently been sent surveys.
 *
 * @version $Id$
 */
@Component
@Designate(ocd = RecentVisitDiscardFilter.Config.class, factory = true)
public class RecentVisitDiscardFilter extends AbstractClarityDataProcessor implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RecentVisitDiscardFilter.class);

    private final int minimumFrequency;

    private final Set<String> clinics;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @Reference
    private SubjectTypeUtils subjectTypeUtils;

    @ObjectClassDefinition(name = "Clarity import filter - Recent Visit Discarder",
        description = "Configuration for the Clarity importer filter that discards visits for patients who have had "
            + "surveys sent to them recently.")
    public @interface Config
    {
        @AttributeDefinition(name = "Enabled")
        boolean enable() default false;

        @AttributeDefinition(name = "Supported import types", description = "Leave empty to support all imports")
        String[] supportedTypes();

        @AttributeDefinition(name = "Minimum Frequency",
            description = "Minimum period in days between sending surveys to a patient")
        int minimum_visit_frequency();

        @AttributeDefinition(name = "Clinics to consider",
            description = "List paths to the clinics to consider as a recent visit."
                + " If empty, all other visits will be considered regardless of their clinic.")
        String[] clinics();
    }

    @Activate
    public RecentVisitDiscardFilter(final Config configuration)
    {
        super(configuration.enable(), configuration.supportedTypes(), 10);
        this.minimumFrequency = configuration.minimum_visit_frequency();
        this.clinics = configuration.clinics() != null ? Set.of(configuration.clinics()) : Collections.emptySet();
    }

    @Override
    public Map<String, String> processEntry(final Map<String, String> input)
    {
        if (this.minimumFrequency <= 0) {
            return input;
        }
        final String subjectId = input.get("/SubjectTypes/Patient");
        final String id = input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown");

        if (subjectId == null || subjectId.length() == 0) {
            LOGGER.warn("Discarded visit {} due to no subject identifier", id);
            return null;
        } else {
            // Get all patients with that identifier
            ResourceResolver resolver = this.rrp.getThreadResourceResolver();
            String subjectMatchQuery = String.format(
                "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='%s' option (index tag property)",
                subjectId);
            resolver.refresh();
            final Iterator<Resource> subjectResourceIter = resolver.findResources(subjectMatchQuery, "JCR-SQL2");
            // Should only be 0 or 1 patient with that identifier. Process it if found.
            if (subjectResourceIter.hasNext() && subjectHasRecentSurveyEvent(subjectResourceIter.next(), id)) {
                return null;
            }
        }
        return input;
    }

    private boolean subjectHasRecentSurveyEvent(Resource subjectResource, String id)
    {
        final Node subjectNode = subjectResource.adaptTo(Node.class);
        try {
            // Iterate through all of the patients visits
            for (final NodeIterator visits = subjectNode.getNodes(); visits.hasNext();) {
                Node visit = visits.nextNode();
                if (isVisitSubject(visit) && !isSameVisit(visit, id)) {
                    // Iterate through all forms for that visit
                    for (final PropertyIterator forms = visit.getReferences("subject"); forms.hasNext();) {
                        final Node form = forms.nextProperty().getParent();
                        if (formIsRecentSurveyEvent(form)) {
                            // Discard the clarity row
                            LOGGER.warn("Discarded visit {} due to recent survey event", id);
                            return true;
                        }
                    }
                }
            }
        } catch (RepositoryException e) {
            // Failed to get forms for this subject: Could not check if any recent invitations have been sent.
            // Default to discarding this visit
            LOGGER.error("Discarded visit {}: Could not check for recent invitations", id);
            return true;
        }

        // No recent event was found
        return false;
    }

    private boolean isVisitSubject(final Node subject)
    {
        final String subjectType = this.subjectTypeUtils.getLabel(this.subjectUtils.getType(subject));
        return "Visit".equals(subjectType);
    }

    private boolean isSameVisit(final Node visit, final String id)
    {
        return StringUtils.equals(this.subjectUtils.getLabel(visit), id);
    }

    private boolean isSurveyEventsForm(final Node questionnaire)
    {
        try {
            return questionnaire != null && "/Questionnaires/Survey events".equals(questionnaire.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if form is Survey events form: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean formIsRecentSurveyEvent(Node form) throws RepositoryException
    {
        final Node questionnaire = this.formUtils.getQuestionnaire(form);
        if (isSurveyEventsForm(questionnaire)) {

            if (!this.clinics.isEmpty()) {
                Node clinicQuestion =
                    this.questionnaireUtils.getQuestion(questionnaire.getNode("../Visit information"),
                        "clinic");
                Collection<Node> clinic = this.formUtils.findAllFormRelatedAnswers(form, clinicQuestion,
                    EnumSet.of(FormUtils.SearchType.SUBJECT_FORMS));
                if (!clinic.isEmpty() && !clinic.stream().anyMatch(c -> {
                    try {
                        return c.hasProperty("value") && this.clinics.contains(c.getProperty("value").getString());
                    } catch (RepositoryException e) {
                        return false;
                    }
                })) {
                    return false;
                }
            }
            Node invitationSentQuestion =
                this.questionnaireUtils.getQuestion(questionnaire, "invitation_sent");

            final Calendar invitationSent =
                (Calendar) this.formUtils.getValue(
                    this.formUtils.getAnswer(form, invitationSentQuestion));

            // If no value is set, survey is pending but has not yet been sent.
            // If that is the case or an invitation was sent recently, discard this visit.
            if (invitationSent == null || isRecent(invitationSent)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRecent(Calendar date)
    {
        Calendar compareTo = Calendar.getInstance();
        compareTo.add(Calendar.DATE, -this.minimumFrequency);

        // Return true if the requested date is after the configured recent date.
        return date.after(compareTo);
    }
}
