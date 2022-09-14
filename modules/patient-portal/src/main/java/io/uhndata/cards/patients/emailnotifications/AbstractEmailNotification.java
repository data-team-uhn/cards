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

package io.uhndata.cards.patients.emailnotifications;

import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.auth.token.TokenManager;
import io.uhndata.cards.emailnotifications.EmailUtils;
import io.uhndata.cards.patients.api.PatientAccessConfiguration;
import jakarta.mail.MessagingException;

abstract class AbstractEmailNotification
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEmailNotification.class);

    private static final String CARDS_HOST_AND_PORT =
        StringUtils.defaultIfEmpty(System.getenv("CARDS_HOST_AND_PORT"), "localhost:8080");

    private static final String CLINIC_SLING_PATH = "/Survey.html";

    /** Provides access to resources. */
    protected final ResourceResolverFactory resolverFactory;

    private final TokenManager tokenManager;

    private final MailService mailService;

    private final PatientAccessConfiguration patientAccessConfiguration;

    AbstractEmailNotification(final ResourceResolverFactory resolverFactory,
        final TokenManager tokenManager, final MailService mailService,
        final PatientAccessConfiguration patientAccessConfiguration)
    {
        this.resolverFactory = resolverFactory;
        this.tokenManager = tokenManager;
        this.mailService = mailService;
        this.patientAccessConfiguration = patientAccessConfiguration;

    }

    /*
     * Sends notification emails based on clinic-associated templates for appointments in the past or future.
     *
     * @param differenceInDays the difference in days between now and the day of the appointment.
     * Positive values if the appointment is in the future, negative values if the appointment is in the past.
     *
     * @param emailTextTemplateName the name of the plain text email template associated with the Visit's clinic.
     * @param emailHtmlTemplateName the name of the HTML email template associated with the Visit's clinic.
     * @param emailSubject The subject line of the notification email
     * @return the number of notification emails that have been sent
     */
    public long sendNotification(final int differenceInDays, final String emailTextTemplateName,
        final String emailHtmlTemplateName, final String emailSubject)
    {
        return sendNotification(differenceInDays, emailTextTemplateName, emailHtmlTemplateName, emailSubject, null);
    }

    /*
     * Sends notification emails based on templates for appointments in the past or future.
     *
     * @param differenceInDays the difference in days between now and the day of the appointment.
     * Positive values if the appointment is in the future, negative values if the appointment is in the past.
     *
     * @param emailTextTemplateName the name of the plain text email template associated with the Visit's clinic,
     * if clinicId is null, otherwise the absolute JCR path to the template.
     *
     * @param emailHtmlTemplateName the name of the HTML email template associated with the Visit's clinic,
     * if clinicId is null, otherwise the absolute JCR path to the template.
     *
     * @param emailSubject The subject line of the notification email
     * @param clinicId only send notifications for appointments with a clinic specified by this ID.
     * @return the number of notification emails that have been sent
     */
    @SuppressWarnings("checkstyle:ExecutableStatementCount")
    public long sendNotification(final int differenceInDays, final String emailTextTemplateName,
        final String emailHtmlTemplateName, final String emailSubject, final String clinicId)
    {
        final Calendar dateToQuery = Calendar.getInstance();
        dateToQuery.add(Calendar.DATE, differenceInDays);
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "EmailNotifications");
        long emailsSent = 0;
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(parameters)) {
            Iterator<Resource> appointmentResults = AppointmentUtils.getAppointmentsForDay(resolver,
                dateToQuery, clinicId);
            while (appointmentResults.hasNext()) {
                Resource appointmentDate = appointmentResults.next();
                Resource appointmentForm = AppointmentUtils.getFormForAnswer(resolver, appointmentDate);
                if (appointmentForm == null) {
                    continue;
                }
                // Get the Patient Subject associated with this appointment Form
                Resource patientSubject = AppointmentUtils.getRelatedSubjectOfType(
                    resolver, appointmentForm, "/SubjectTypes/Patient");
                Resource visitSubject = AppointmentUtils.getRelatedSubjectOfType(
                    resolver, appointmentForm, "/SubjectTypes/Patient/Visit");
                // Skip the email if there are no incomplete surveys for the patient
                boolean patientSurveysComplete = AppointmentUtils.getVisitSurveysComplete(resolver, visitSubject);
                if (patientSurveysComplete) {
                    continue;
                }
                String patientEmailAddress =
                    AppointmentUtils.getPatientConsentedEmail(resolver, patientSubject, visitSubject);
                if (patientEmailAddress == null) {
                    continue;
                }

                /* If a clinicId is specified, then emailTextTemplateName and emailHtmlTemplateName are JCR
                 * paths to the plaintext and HTML email templates, respectively. Otherwise, if clinicId is null,
                 * then we need to resolve the full JCR path to the email templates based on the clinic specified
                 * in the Visit information Form and the given emailTextTemplateName, and emailHtmlTemplateName values.
                 */
                String emailTextTemplate = null;
                String emailHtmlTemplate = null;
                if (clinicId != null) {
                    emailTextTemplate = AppointmentUtils.getEmailTemplateFromPath(resolver, emailTextTemplateName);
                    emailHtmlTemplate = AppointmentUtils.getEmailTemplateFromPath(resolver, emailHtmlTemplateName);
                } else {
                    emailTextTemplate = AppointmentUtils.getVisitEmailTemplate(resolver,
                        visitSubject, emailTextTemplateName);
                    emailHtmlTemplate = AppointmentUtils.getVisitEmailTemplate(resolver,
                        visitSubject, emailHtmlTemplateName);
                }

                if (emailTextTemplate == null || emailHtmlTemplate == null) {
                    continue;
                }

                String patientFullName = AppointmentUtils.getPatientFullName(resolver, patientSubject);
                Calendar tokenExpiryDate = AppointmentUtils.parseDate(appointmentDate.getValueMap().get("value", ""));
                // Note the tokenExpiry-1, since atMidnight will go to the next day
                tokenExpiryDate.add(
                    Calendar.DATE,
                    this.patientAccessConfiguration.getAllowedPostVisitCompletionTime() - 1
                );
                atMidnight(tokenExpiryDate);
                final String token = this.tokenManager.create(
                    "patient",
                    tokenExpiryDate,
                    Collections.singletonMap(
                        "cards:sessionSubject",
                        visitSubject.getPath()))
                    .getToken();
                // Send the Notification Email
                Map<String, String> valuesMap = Map.of(
                    "surveysLink", "https://" + CARDS_HOST_AND_PORT + CLINIC_SLING_PATH + "?auth_token=" + token,
                    "unsubscribeLink",
                    "https://" + CARDS_HOST_AND_PORT + "/Survey.unsubscribe.html?auth_token=" + token);
                String emailTextBody = EmailUtils.renderEmailTemplate(emailTextTemplate, valuesMap);
                String emailHtmlBody = EmailUtils.renderEmailTemplate(emailHtmlTemplate, valuesMap);
                try {
                    EmailUtils.sendNotificationHtmlEmail(this.mailService, patientEmailAddress,
                        patientFullName, emailSubject, emailTextBody, emailHtmlBody);
                    emailsSent += 1;
                } catch (MessagingException e) {
                    LOGGER.warn("Failed to send Initial Notification Email");
                }
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to results.next().getPath()");
        }
        return emailsSent;
    }

    private void atMidnight(final Calendar c)
    {
        c.add(Calendar.DATE, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
