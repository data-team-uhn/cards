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

package io.uhndata.cards.proms.emailnotifications;

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
import jakarta.mail.MessagingException;

abstract class AbstractPromsNotification
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPromsNotification.class);

    private static final String CARDS_HOST_AND_PORT =
        StringUtils.defaultIfEmpty(System.getenv("CARDS_HOST_AND_PORT"), "localhost:8080");

    private static final String CLINIC_SLING_PATH = "/Proms.html";

    /** Provides access to resources. */
    protected final ResourceResolverFactory resolverFactory;

    private final TokenManager tokenManager;

    private final MailService mailService;

    AbstractPromsNotification(final ResourceResolverFactory resolverFactory,
        final TokenManager tokenManager, final MailService mailService)
    {
        this.resolverFactory = resolverFactory;
        this.tokenManager = tokenManager;
        this.mailService = mailService;
    }

    @SuppressWarnings("checkstyle:ExecutableStatementCount")
    public long sendNotification(final int daysInTheFuture, final String emailTemplateName,
        final String emailSubject)
    {
        final Calendar dateToQuery = Calendar.getInstance();
        dateToQuery.add(Calendar.DATE, daysInTheFuture);
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "EmailNotifications");
        long emailsSent = 0;
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(parameters)) {
            Iterator<Resource> appointmentResults = AppointmentUtils.getAppointmentsForDay(resolver, dateToQuery);
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
                String emailTextTemplate =
                    AppointmentUtils.getVisitEmailTemplate(resolver, visitSubject, emailTemplateName);
                if (emailTextTemplate == null) {
                    continue;
                }
                String patientFullName = AppointmentUtils.getPatientFullName(resolver, patientSubject);
                Calendar tokenExpiryDate = AppointmentUtils.parseDate(appointmentDate.getValueMap().get("value", ""));
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
                    "https://" + CARDS_HOST_AND_PORT + "/Proms.unsubscribe.html?auth_token=" + token);
                String emailTextBody = EmailUtils.renderEmailTemplate(emailTextTemplate, valuesMap);
                try {
                    EmailUtils.sendNotificationEmail(this.mailService, patientEmailAddress,
                        patientFullName, emailSubject, emailTextBody);
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
