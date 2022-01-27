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


public class InitialNotificationsTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(InitialNotificationsTask.class);

    private static final String CARDS_HOST_AND_PORT = System.getenv("CARDS_HOST_AND_PORT");
    private static final String CLINIC_SLING_PATH = System.getenv("CLINIC_SLING_PATH");

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    private final TokenManager tokenManager;

    private final MailService mailService;

    InitialNotificationsTask(final ResourceResolverFactory resolverFactory,
        final TokenManager tokenManager, final MailService mailService)
    {
        this.resolverFactory = resolverFactory;
        this.tokenManager = tokenManager;
        this.mailService = mailService;
    }

    @Override
    public void run()
    {
        LOGGER.warn("Executing InitialNotificationsTask");
        /*
         * Query the `Visit information` Forms for appointments happening
         * on day NOW+3. For example, if today is January 1st 2022, find
         * all appointments on January 4th 2022. This task should run as
         * close as possible to the start of the day, but after any
         * upstream data is fetched (eg. from Data Lake), so that
         * patients will have the maximum amount of time to complete
         * their surveys.
         */
        final Date today = new Date();
        final Calendar dateToQuery = Calendar.getInstance();
        dateToQuery.setTime(today);
        dateToQuery.add(Calendar.DATE, 3);
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
                Resource visitSubject = AppointmentUtils.getRelatedSubjectOfType(
                    resolver, appointmentForm, "/SubjectTypes/Patient/Visit");
                String patientEmailAddress = AppointmentUtils.getPatientConsentedEmail(resolver, patientSubject);
                if (patientEmailAddress == null) {
                    continue;
                }
                String emailTextTemplate = AppointmentUtils.getVisitEmailTemplate(resolver, visitSubject, "72h.txt");
                if (emailTextTemplate == null) {
                    continue;
                }
                String patientFullName = AppointmentUtils.getPatientFullName(resolver, patientSubject);
                Calendar tokenExpiryDate = AppointmentUtils.parseDate(appointmentResult.getValueMap().get("value", ""));
                tokenExpiryDate.add(Calendar.HOUR, 2);
                String surveysLink = "https://" + CARDS_HOST_AND_PORT + CLINIC_SLING_PATH + "?auth_token="
                    + this.tokenManager.create(
                        "patient",
                        tokenExpiryDate,
                        Collections.singletonMap(
                            "cards:sessionSubject",
                            visitSubject.getPath()
                        )
                    ).getToken();
                // Send the Initial Notification Email
                String emailTextBody = EmailUtils.renderEmailTemplate(emailTextTemplate, surveysLink);
                try {
                    EmailUtils.sendNotificationEmail(this.mailService, patientEmailAddress,
                        patientFullName, emailTextBody);
                } catch (MessagingException e) {
                    LOGGER.warn("Failed to send Initial Notification Email");
                }
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to results.next().getPath()");
        }
    }
}
