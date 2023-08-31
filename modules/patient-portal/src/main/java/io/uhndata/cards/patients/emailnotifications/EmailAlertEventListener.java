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

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.emailnotifications.EmailTemplate;
import io.uhndata.cards.emailnotifications.EmailUtils;
import io.uhndata.cards.forms.api.FormUtils;
import jakarta.mail.MessagingException;

public final class EmailAlertEventListener implements EventListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailAlertEventListener.class);

    private final ResourceResolverFactory rrf;

    private FormUtils formUtils;

    private MailService mailService;

    private String alertName;

    private String submittedFlagUUID;

    private String linkingSubjectType;

    private String alertingQuestionPath;

    private String triggerOperator;

    private String triggerOperand;

    private String alertDescription;

    private String clinicEmailProperty;

    private String emailFromAddress;

    private EmailTemplate template;

    public EmailAlertEventListener(ResourceResolverFactory rrf, FormUtils formUtils,
        MailService mailService, Map<String, String> listenerParams)
    {
        this.rrf = rrf;
        this.formUtils = formUtils;
        this.mailService = mailService;
        this.alertName = listenerParams.get("alertName");
        this.submittedFlagUUID = listenerParams.get("submittedFlagUUID");
        this.linkingSubjectType = listenerParams.get("linkingSubjectType");
        this.alertingQuestionPath = listenerParams.get("alertingQuestionPath");
        this.triggerOperator = listenerParams.get("triggerOperator");
        this.triggerOperand = listenerParams.get("triggerOperand");
        this.alertDescription = listenerParams.get("alertDescription");
        this.clinicEmailProperty = listenerParams.get("clinicEmailProperty");
        this.emailFromAddress = listenerParams.get("emailFromAddress");

        this.template = EmailTemplate.builder().withSubject("DATAPRO Alert: " + this.alertName)
            .withSender(this.emailFromAddress, this.alertName).build();
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

    private boolean isSubmissionEvent(final Node modifiedValueNode)
    {
        return this.formUtils.isAnswer(modifiedValueNode)
            && this.submittedFlagUUID.equals(this.formUtils.getQuestionIdentifier(modifiedValueNode))
            && ((Long) this.formUtils.getValue(modifiedValueNode)) == 1L;
    }

    @Override
    public void onEvent(EventIterator events)
    {
        try (ResourceResolver resolver =
            this.rrf.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "EmailNotifications"))) {
            final Session session = resolver.adaptTo(Session.class);
            while (events.hasNext()) {
                Event thisEvent = events.nextEvent();
                String modifiedValueNodePath = getModifedValueNodePath(thisEvent);
                if (modifiedValueNodePath == null) {
                    continue;
                }

                Node modifiedValueNode = session.getNode(modifiedValueNodePath);

                // Check that this event is a "surveys submission" event
                if (!isSubmissionEvent(modifiedValueNode)) {
                    continue;
                }

                // Get the cards:Form node that this modified value property descends from
                Node modifiedFormNode = this.formUtils.getForm(modifiedValueNode);
                /*
                 * Get the subject of given type (eg. /SubjectTypes/Patient, /SubjectTypes/Patient/Visit) that this Form
                 * is related to.
                 */
                Node formRelatedSubject = this.formUtils.getSubject(modifiedFormNode, this.linkingSubjectType);
                if (formRelatedSubject == null) {
                    continue;
                }

                /*
                 * This subject is of type Visit, therefore get the value of /Questionnaires/Visit information/surveys
                 * associated with this visit.
                 */
                String clinicAlertEmail = AppointmentUtils.getValidClinicEmail(this.formUtils,
                    formRelatedSubject, this.clinicEmailProperty);
                if (clinicAlertEmail == null) {
                    continue;
                }

                /*
                 * Find all the answer nodes that match the trigger filter criteria and are linked with the submit
                 * button whose setting caused this event to occur.
                 */
                Collection<Node> answers = this.formUtils.findAllSubjectRelatedAnswers(formRelatedSubject,
                    session.getNode(this.alertingQuestionPath),
                    EnumSet.of(FormUtils.SearchType.SUBJECT_FORMS, FormUtils.SearchType.DESCENDANTS_FORMS));

                for (Node answer : answers) {
                    if (!answerMatchesExpression(answer)) {
                        continue;
                    }
                    Node triggeringForm = this.formUtils.getForm(answer);
                    Node triggeringSubject = this.formUtils.getSubject(triggeringForm, "/SubjectTypes/Patient");
                    String triggeringPatientFullName = AppointmentUtils.getPatientFullName(
                        this.formUtils, triggeringSubject);

                    // Generate the email alert for this event
                    String emailBody = "Alert " + this.alertName + " has been triggered for patient "
                        + triggeringPatientFullName + ". "
                        + this.alertDescription
                        + "\nYour attention is required.";

                    // Send this email
                    try {
                        EmailUtils.sendTextEmail(this.template.getEmailBuilder().withBody(null, emailBody)
                            .withRecipient(clinicAlertEmail, clinicAlertEmail).build(),
                            this.mailService);
                    } catch (MessagingException e) {
                        LOGGER.warn("Failed to send Alert Email: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error happened in EmailAlertEventListener: {}", e.getMessage());
        }
    }

    @SuppressWarnings({ "checkstyle:CyclomaticComplexity", "checkstyle:ReturnCount" })
    private boolean answerMatchesExpression(final Node answer)
    {
        try {
            Object value = this.formUtils.getValue(answer);
            if (value == null) {
                return "is empty".equals(this.triggerOperator);
            }
            switch (this.triggerOperator) {
                case "=":
                    return this.triggerOperand.equals(String.valueOf(value));
                case ">":
                    return ((Number) value).doubleValue() > Double.parseDouble(this.triggerOperand);
                case ">=":
                    return ((Number) value).doubleValue() >= Double.parseDouble(this.triggerOperand);
                case "<":
                    return ((Number) value).doubleValue() < Double.parseDouble(this.triggerOperand);
                case "<=":
                    return ((Number) value).doubleValue() <= Double.parseDouble(this.triggerOperand);
                case "is not empty":
                    return value != null && !String.valueOf(value).isEmpty();
                case "is empty":
                    return value == null || String.valueOf(value).isEmpty();
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
