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

package io.uhndata.cards.emailnotifications;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.auth.token.TokenManager;
import jakarta.mail.MessagingException;


public class ReminderNotificationsTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ReminderNotificationsTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    private final TokenManager tokenManager;

    private final MailService mailService;

    ReminderNotificationsTask(final ResourceResolverFactory resolverFactory,
        final TokenManager tokenManager, final MailService mailService)
    {
        this.resolverFactory = resolverFactory;
        this.tokenManager = tokenManager;
        this.mailService = mailService;
    }

    @SuppressWarnings("checkstyle:ExecutableStatementCount")
    @Override
    public void run()
    {
        LOGGER.warn("Executing ReminderNotificationsTask");

        final Date today = new Date();
        final Calendar dateToQuery = Calendar.getInstance();
        dateToQuery.setTime(today);
        dateToQuery.add(Calendar.DATE, 1);
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Iterator<Resource> appointmentResults = AppointmentUtils.getAppointmentsForDay(resolver, dateToQuery);
            while (appointmentResults.hasNext()) {
                Resource appointmentResult = appointmentResults.next();
                Resource appointmentForm = AppointmentUtils.getFormForAnswer(resolver, appointmentResult);
                if (appointmentForm == null) {
                    continue;
                }
                // Get the Patient Subject associated with this appointment Form
                Resource patientSubject = AppointmentUtils.getRelatedSubjectOfType(
                    resolver, appointmentForm, "/SubjectTypes/Patient");
                LOGGER.warn("Patient {} has a scheduled visit on {}",
                    patientSubject.getPath(), appointmentResult.getValueMap().get("value", ""));
                Resource visitSubject = AppointmentUtils.getRelatedSubjectOfType(
                    resolver, appointmentForm, "/SubjectTypes/Patient/Visit");
                LOGGER.warn("The associated visit subject is {}", visitSubject.getPath());
                String patientEmailAddress = AppointmentUtils.getPatientConsentedEmail(resolver, patientSubject);
                if (patientEmailAddress == null) {
                    continue;
                }
                String patientFullName = AppointmentUtils.getPatientFullName(resolver, patientSubject);
                boolean patientSurveysComplete = AppointmentUtils.getVisitSurveysComplete(resolver, visitSubject);
                LOGGER.warn("For this visit, surveys complete status is: {}", patientSurveysComplete);
                LOGGER.warn("Surveys email to {}", patientEmailAddress);

                // Send the Reminder Notification Email
                String emailBody = "You have an appointment in 1 day from now.";
                // Only send a reminder about incomplete surveys if there are incomplete surveys for the patient
                if (!patientSurveysComplete) {
                    Calendar tokenExpiryDate = AppointmentUtils.parseDate(
                        appointmentResult.getValueMap().get("value", ""));
                    tokenExpiryDate.add(Calendar.HOUR, 2);
                    LOGGER.warn("The following token will expire on: {}", tokenExpiryDate);
                    String surveysLink = "http://localhost:8080/Proms.html/Cardio?auth_token="
                        + this.tokenManager.create(
                            "patient",
                            tokenExpiryDate,
                            Collections.singletonMap(
                                "cards:sessionSubject",
                                visitSubject.getPath()
                            )
                        ).getToken();
                    LOGGER.warn("{}", surveysLink);
                    emailBody += " Please complete your surveys beforehand at";
                    emailBody += " ";
                    emailBody += surveysLink;
                } else {
                    emailBody += " Thank you for completing your surveys.";
                }
                try {
                    EmailUtils.sendNotificationEmail(this.mailService, patientEmailAddress, patientFullName, emailBody);
                } catch (MessagingException e) {
                    LOGGER.warn("Failed to send Reminder Notification Email");
                }
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to results.next().getPath()");
        }
    }
}
