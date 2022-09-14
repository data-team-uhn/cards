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
package io.uhndata.cards.patients.emailnotifications;

import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.messaging.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.emailnotifications.EmailUtils;
import jakarta.mail.MessagingException;

public final class EmailAlertEventListener implements EventListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailAlertEventListener.class);

    private ResourceResolver resolver;
    private MailService mailService;
    private String alertName;
    private String submittedFlagUUID;
    private String linkingSubjectType;
    private String alertingQuestionUUID;
    private String alertingQuestionDataType;
    private String triggerExpression;
    private String alertDescription;
    private String clinicIdLink;
    private String clinicsJcrPath;
    private String clinicEmailProperty;

    public EmailAlertEventListener(ResourceResolver resolver,
        MailService mailService, Map<String, String> listenerParams)
    {
        this.resolver = resolver;
        this.mailService = mailService;
        this.alertName = listenerParams.get("alertName");
        this.submittedFlagUUID = listenerParams.get("submittedFlagUUID");
        this.linkingSubjectType = listenerParams.get("linkingSubjectType");
        this.alertingQuestionUUID = listenerParams.get("alertingQuestionUUID");
        this.alertingQuestionDataType = listenerParams.get("alertingQuestionDataType");
        this.triggerExpression = listenerParams.get("triggerExpression");
        this.alertDescription = listenerParams.get("alertDescription");
        this.clinicIdLink = listenerParams.get("clinicIdLink");
        this.clinicsJcrPath = listenerParams.get("clinicsJcrPath");
        this.clinicEmailProperty = listenerParams.get("clinicEmailProperty");
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

                // Get the cards:Form node that this modified value property descends from
                Resource modifiedFormNode = AppointmentUtils.getFormForAnswer(this.resolver, modifiedValueNode);
                if (modifiedFormNode == null) {
                    continue;
                }

                /*
                 * Get the subject of given type (eg. /SubjectTypes/Patient, /SubjectTypes/Patient/Visit)
                 * that this Form is related to.
                 */
                Resource formRelatedSubject = AppointmentUtils.getRelatedSubjectOfType(
                    this.resolver, modifiedFormNode, this.linkingSubjectType);
                if (formRelatedSubject == null) {
                    continue;
                }
                String formRelatedSubjectUUID = formRelatedSubject.getValueMap().get("jcr:uuid", "");

                /*
                 * This subject is of type Visit, therefore get the value of
                 * /Questionnaires/Visit information/surveys associated with
                 * this visit.
                 */
                String clinicAlertEmail = AppointmentUtils.getValidClinicEmail(this.resolver,
                    formRelatedSubject, this.clinicIdLink, this.clinicsJcrPath, this.clinicEmailProperty);
                if (clinicAlertEmail == null) {
                    continue;
                }

                /*
                 * Find all the answer nodes that match the trigger filter criteria and are linked with the submit
                 * button whose setting caused this event to occur.
                 */
                Iterator<Resource> results;
                results = this.resolver.findResources(
                    "SELECT a.* FROM [" + this.alertingQuestionDataType + "] as a INNER JOIN [cards:Form] AS f ON"
                    + " isdescendantnode(a, f) WHERE a.'question'='" + this.alertingQuestionUUID + "'"
                    + " AND f.'relatedSubjects'='" + formRelatedSubjectUUID + "'"
                    + " AND a.'value'" + this.triggerExpression,
                    "JCR-SQL2");

                while (results.hasNext()) {
                    Resource triggeringAnswer = results.next();
                    Resource triggeringForm = AppointmentUtils.getFormForAnswer(this.resolver, triggeringAnswer);
                    Resource triggeringSubject = AppointmentUtils.getRelatedSubjectOfType(
                        this.resolver, triggeringForm, "/SubjectTypes/Patient");
                    String triggeringPatientFullName = AppointmentUtils.getPatientFullName(
                        this.resolver, triggeringSubject);

                    // Generate the email alert for this event
                    String emailBody = "Alert " + this.alertName + " has been triggered for patient "
                        + triggeringPatientFullName + ". "
                        + this.alertDescription
                        + "\nYour attention is required.";

                    // Send this email
                    try {
                        EmailUtils.sendNotificationEmail(this.mailService,
                            clinicAlertEmail, clinicAlertEmail, "DATAPRO Alert: " + this.alertName, emailBody);
                    } catch (MessagingException e) {
                        LOGGER.warn("Failed to send Alert Email: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error happened in EmailAlertEventListener: {}", e.getMessage());
        }
    }
}
