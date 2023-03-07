/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.patients.submissioncounter;

import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.metrics.Metrics;

public final class SubmissionEventListener implements EventListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionEventListener.class);

    private static final String SINGLE_QUOTE = "'";

    private final ResourceResolverFactory resolverFactory;

    private final ResourceResolver resolver;

    private final FormUtils formUtils;

    private final String linkingSubjectType;

    private final String submittedFlagPath;

    private final String[] excludedQuestionnairePaths;

    private String submittedFlagUUID;

    private String[] excludedQuestionnaireUUIDs;

    public SubmissionEventListener(FormUtils formUtils, ResourceResolverFactory resolverFactory,
        ResourceResolver resolver, Map<String, Object> listenerParams)
    {
        this.formUtils = formUtils;
        this.resolverFactory = resolverFactory;
        this.resolver = resolver;
        this.submittedFlagPath = ((String) listenerParams.get("submittedFlagPath"));
        this.linkingSubjectType = ((String) listenerParams.get("linkingSubjectType"));
        this.excludedQuestionnairePaths = ((String[]) listenerParams.get("excludedQuestionnairePaths"));
    }

    private String getSubmittedFlagUUID()
    {
        if (this.submittedFlagUUID != null) {
            return this.submittedFlagUUID;
        }

        this.submittedFlagUUID = this.resolver.getResource(this.submittedFlagPath).getValueMap().get("jcr:uuid", "");
        return this.submittedFlagUUID;
    }

    private String[] getExcludedQuestionnaireUUIDs()
    {
        if (this.excludedQuestionnaireUUIDs != null) {
            return this.excludedQuestionnaireUUIDs;
        }

        this.excludedQuestionnaireUUIDs = new String[this.excludedQuestionnairePaths.length];
        for (int i = 0; i < this.excludedQuestionnairePaths.length; i++) {
            this.excludedQuestionnaireUUIDs[i] =
                this.resolver.getResource(this.excludedQuestionnairePaths[i]).getValueMap().get("jcr:uuid", "");
        }
        return this.excludedQuestionnaireUUIDs;
    }

    private long countVisitForms(String visitUUID, String excludeFormUUID)
    {
        long count = 0;

        String sqlQuery = "SELECT * FROM [cards:Form] as f WHERE f.'relatedSubjects'='" + visitUUID + SINGLE_QUOTE;
        sqlQuery += " AND f.'jcr:uuid'<>'" + excludeFormUUID + SINGLE_QUOTE;
        for (String excludeQuestionnaireUUID : this.getExcludedQuestionnaireUUIDs()) {
            sqlQuery += " AND f.'questionnaire'<>'" + excludeQuestionnaireUUID + SINGLE_QUOTE;
        }

        Iterator<Resource> results;
        results = this.resolver.findResources(sqlQuery, "JCR-SQL2");
        while (results.hasNext()) {
            count += 1;
            results.next();
        }
        return count;
    }

    private String getModifedValueNodePath(Event thisEvent) throws RepositoryException
    {
        if (!thisEvent.getPath().endsWith("/value")) {
            return null;
        }

        String modifiedValueNodePath = thisEvent.getPath();
        modifiedValueNodePath = modifiedValueNodePath.substring(
            0, modifiedValueNodePath.length() - "/value".length());

        return modifiedValueNodePath;
    }

    private boolean isSubmissionEvent(Node modifiedValueNode)
    {
        return this.formUtils.isAnswer(modifiedValueNode)
            && this.getSubmittedFlagUUID().equals(this.formUtils.getQuestionIdentifier(modifiedValueNode))
            && ((Long) this.formUtils.getValue(modifiedValueNode)) == 1L;
    }

    @Override
    public void onEvent(EventIterator events)
    {
        try {
            while (events.hasNext()) {
                Event thisEvent = events.nextEvent();
                String modifiedValueNodePath = getModifedValueNodePath(thisEvent);
                if (modifiedValueNodePath == null) {
                    continue;
                }

                Node modifiedValueNode = this.resolver.getResource(modifiedValueNodePath).adaptTo(Node.class);

                // Check that this event is a "surveys submission" event
                if (!isSubmissionEvent(modifiedValueNode)) {
                    continue;
                }

                // Increment the performance counter
                Metrics.increment(this.resolverFactory, "AppointmentSurveysSubmitted", 1);

                // Get the cards:Form node that this modified value property descends from
                Node modifiedFormNode = this.formUtils.getForm(modifiedValueNode);
                if (modifiedFormNode == null) {
                    continue;
                }
                String modifiedFormNodeUUID = modifiedFormNode.getIdentifier();

                Node formRelatedSubject = this.formUtils.getSubject(modifiedFormNode, this.linkingSubjectType);
                if (formRelatedSubject == null) {
                    continue;
                }
                String formRelatedSubjectUUID = formRelatedSubject.getIdentifier();

                // Get the count of submitted Forms for this appointment
                long formsForAppointmentCount = countVisitForms(formRelatedSubjectUUID, modifiedFormNodeUUID);

                // Increment the performance counter
                Metrics.increment(this.resolverFactory,
                    "TotalSurveysSubmitted", formsForAppointmentCount);
            }
        } catch (Exception e) {
            LOGGER.warn("Error happened in SubmissionEventListener: {}", e.getMessage());
        }
    }
}
