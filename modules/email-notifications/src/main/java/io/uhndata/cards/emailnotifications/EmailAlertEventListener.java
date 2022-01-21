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
package io.uhndata.cards.emailnotifications;

import java.util.Iterator;
import java.util.Map;

import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.messaging.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private String alertEmail;

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
        this.alertEmail = listenerParams.get("alertEmail");
    }

    @Override
    public void onEvent(EventIterator events)
    {
        try {
            while (events.hasNext()) {
                Event thisEvent = events.nextEvent();
                // Check that the property modified was the "value" property
                if (!thisEvent.getPath().endsWith("/value")) {
                    continue;
                }

                String modifiedPropertyNodePath = thisEvent.getPath();
                modifiedPropertyNodePath = modifiedPropertyNodePath.substring(
                    0, modifiedPropertyNodePath.length() - "/value".length());

                /* Check that the modified node has a question "property" that
                 * points to the "submissionComplete" question.
                 */
                Resource modifiedPropertyNode = this.resolver.getResource(modifiedPropertyNodePath);
                String modifiedPropertyNodeQuestion = modifiedPropertyNode.getValueMap().get("question", "");
                if (!this.submittedFlagUUID.equals(modifiedPropertyNodeQuestion)) {
                    continue;
                }

                // Check that the "submissionComplete" value is true
                long modifiedPropertyNodeValue = modifiedPropertyNode.getValueMap().get("value", 0);
                if (modifiedPropertyNodeValue != 1) {
                    continue;
                }

                // Get the cards:Form node that this modified property descends from
                Resource modifiedFormNode = AppointmentUtils.getFormForAnswer(this.resolver, modifiedPropertyNode);
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
                        EmailUtils.sendNotificationEmail(this.mailService, this.alertEmail, this.alertEmail, emailBody);
                    } catch (MessagingException e) {
                        LOGGER.warn("Failed to send Alert Email");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error happened in EmailAlertEventListener");
        }
    }
}
