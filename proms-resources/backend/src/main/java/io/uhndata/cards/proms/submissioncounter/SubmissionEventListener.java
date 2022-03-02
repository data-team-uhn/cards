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
package io.uhndata.cards.proms.submissioncounter;

import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.performancenotifications.PerformanceUtils;
import io.uhndata.cards.proms.emailnotifications.AppointmentUtils;

public final class SubmissionEventListener implements EventListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionEventListener.class);

    private ResourceResolverFactory resolverFactory;
    private ResourceResolver resolver;
    private String submissionCounterName;
    private String submittedFlagUUID;
    private String linkingSubjectType;

    public SubmissionEventListener(ResourceResolverFactory resolverFactory,
        ResourceResolver resolver, Map<String, String> listenerParams)
    {
        this.resolverFactory = resolverFactory;
        this.resolver = resolver;
        this.submissionCounterName = listenerParams.get("submissionCounterName");
        this.submittedFlagUUID = listenerParams.get("submittedFlagUUID");
        this.linkingSubjectType = listenerParams.get("linkingSubjectType");
    }

    private long countVisitForms(String visitUUID, String excludeFormUUID)
    {
        long count = 0;
        Iterator<Resource> results;
        results = this.resolver.findResources(
            "SELECT * FROM [cards:Form] as f WHERE f.'relatedSubjects'='" + visitUUID + "'"
            + " AND f.'jcr:uuid'<>'" + excludeFormUUID + "'",
            "JCR-SQL2"
        );
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

    private boolean isSubmissionEvent(Resource modifiedValueNode, String submittedFlagUUID)
    {
        String modifiedValueNodeQuestion = modifiedValueNode.getValueMap().get("question", "");
        if (!submittedFlagUUID.equals(modifiedValueNodeQuestion)) {
            return false;
        }
        long modifiedValue = modifiedValueNode.getValueMap().get("value", 0);
        if (modifiedValue != 1) {
            return false;
        }
        return true;
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

                Resource modifiedValueNode = this.resolver.getResource(modifiedValueNodePath);

                // Check that this event is a "surveys submission" event
                if (!isSubmissionEvent(modifiedValueNode, this.submittedFlagUUID)) {
                    continue;
                }

                // Increment the performance counter
                PerformanceUtils.updatePerformanceCounter(this.resolverFactory, "AppointmentSurveysSubmitted", 1);

                // Get the cards:Form node that this modified value property descends from
                Resource modifiedFormNode = AppointmentUtils.getFormForAnswer(this.resolver, modifiedValueNode);
                if (modifiedFormNode == null) {
                    continue;
                }
                String modifiedFormNodeUUID = modifiedFormNode.getValueMap().get("jcr:uuid", "");

                Resource formRelatedSubject = AppointmentUtils.getRelatedSubjectOfType(
                    this.resolver, modifiedFormNode, this.linkingSubjectType);
                if (formRelatedSubject == null) {
                    continue;
                }
                String formRelatedSubjectUUID = formRelatedSubject.getValueMap().get("jcr:uuid", "");

                // Get the count of submitted Forms for this appointment
                long formsForAppointmentCount = countVisitForms(formRelatedSubjectUUID, modifiedFormNodeUUID);

                // Increment the performance counter
                PerformanceUtils.updatePerformanceCounter(this.resolverFactory,
                    "TotalSurveysSubmitted", formsForAppointmentCount);
            }
        } catch (Exception e) {
            LOGGER.warn("Error happened in SubmissionEventListener: {}", e.getMessage());
        }
    }
}
