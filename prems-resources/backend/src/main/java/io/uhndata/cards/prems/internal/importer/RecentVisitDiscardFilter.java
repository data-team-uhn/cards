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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Clarity import processor that discards visits for patients who have recently been sent surveys.
 *
 * @version $Id$
 */
@Component
public class RecentVisitDiscardFilter implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RecentVisitDiscardFilter.class);

    // TODO: Retrieve from configuration
    private final String subjectIDColumn = "PAT_MRN";
    private final int dayLimit = -183;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    // TODO: Cleanup
    @SuppressWarnings("checkstyle:NestedIfDepth")
    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        LOGGER.error("Running on visit {}", input.getOrDefault("ID", "Unknown"));
        final String mrn = input.get(this.subjectIDColumn);

        if (mrn == null || mrn.length() == 0) {
            return null;
        } else {
            ResourceResolver resolver = this.rrp.getThreadResourceResolver();
            String subjectMatchQuery = String.format(
                "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='%s' option (index tag property)",
                mrn);
            resolver.refresh();
            final Iterator<Resource> subjectResourceIter = resolver.findResources(subjectMatchQuery, "JCR-SQL2");
            if (subjectResourceIter.hasNext()) {
                LOGGER.error("Found patient");
                final Node subjectNode = subjectResourceIter.next().adaptTo(Node.class);

                try {
                    for (final NodeIterator visits = subjectNode.getNodes(); visits.hasNext();) {
                        // TODO: Verify child is a visit
                        Node visit = visits.nextNode();
                        LOGGER.error("Found visit");
                        for (final PropertyIterator forms = visit.getReferences("subject"); forms.hasNext();) {
                            final Node form = forms.nextProperty().getParent();
                            final Node questionnaire = this.formUtils.getQuestionnaire(form);
                            if (isSurveyEventsForm(questionnaire)) {
                                LOGGER.error("Found survey event form");
                                Node invitationSentQuestion =
                                    this.questionnaireUtils.getQuestion(questionnaire, "invitation_sent");

                                final Calendar invitationSent =
                                    (Calendar) this.formUtils.getValue(
                                    this.formUtils.getAnswer(form, invitationSentQuestion));

                                // If no value is set, survey is pending but has not yet been sent.
                                // If that is the case or an invitation was sent recently, discard this visit.
                                if (invitationSent == null || isRecent(invitationSent)) {
                                    LOGGER.warn("Discarded visit {} due to recent invitation sent",
                                        input.getOrDefault("ID", "Unknown"));
                                    return null;
                                }
                            }
                        }
                    }
                } catch (RepositoryException e) {
                    // Failed to get forms for this subject: Could not check if any recent invitations have been sent.
                    // Default to discarding this visit
                    LOGGER.error("Discarded visit {}: Could not check for recent invitations",
                        input.getOrDefault("ID", "Unknown"));
                    return null;
                }
            }
        }
        return input;
    }

    @Override
    public int getPriority()
    {
        return 10;
    }

    private boolean isRecent(Calendar date)
    {
        Calendar compareTo = Calendar.getInstance();
        compareTo.add(Calendar.DATE, this.dayLimit);

        // Return true if the requested date is after the configured recent date.
        return date.after(compareTo);
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
}
