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
import java.util.Date;
import java.util.HashMap;
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


public class InitialNotificationsTask implements Runnable
{
    private static final String PATIENT_NOTIFICATION_SUBJECT =
        "Welcome to DATAPRO: Answer your Pre-Appointment Questions";

    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(InitialNotificationsTask.class);

    private static final String CARDS_HOST_AND_PORT =
        StringUtils.defaultIfEmpty(System.getenv("CARDS_HOST_AND_PORT"), "localhost:8080");

    private static final String CLINIC_SLING_PATH = "/Proms.html";

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
    @SuppressWarnings("checkstyle:ExecutableStatementCount")
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
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "EmailNotifications");
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(parameters)) {
            Iterator<Resource> appointmentResults = AppointmentUtils.getAppointmentsForDay(resolver, dateToQuery);
            while (appointmentResults.hasNext()) {
                LOGGER.warn("Processing appointment");
                Resource appointmentResult = appointmentResults.next();
                Resource appointmentForm = AppointmentUtils.getFormForAnswer(resolver, appointmentResult);
                if (appointmentForm == null) {
                    LOGGER.warn("Exit 1");
                    continue;
                }
                // Get the Patient Subject associated with this appointment Form
                Resource patientSubject = AppointmentUtils.getRelatedSubjectOfType(
                    resolver, appointmentForm, "/SubjectTypes/Patient");
                Resource visitSubject = AppointmentUtils.getRelatedSubjectOfType(
                    resolver, appointmentForm, "/SubjectTypes/Patient/Visit");
                String patientEmailAddress = AppointmentUtils.getPatientConsentedEmail(resolver, patientSubject);
                if (patientEmailAddress == null) {
                    LOGGER.warn("Exit 2");
                    continue;
                }
                String emailTextTemplate = AppointmentUtils.getVisitEmailTemplate(resolver, visitSubject, "72h.txt");
                if (emailTextTemplate == null) {
                    LOGGER.warn("Exit 3");
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
                Map<String, String> valuesMap = new HashMap<String, String>();
                valuesMap.put("surveysLink", surveysLink);
                String emailTextBody = EmailUtils.renderEmailTemplate(emailTextTemplate, valuesMap);
                try {
                    EmailUtils.sendNotificationEmail(this.mailService, patientEmailAddress,
                        patientFullName, PATIENT_NOTIFICATION_SUBJECT, emailTextBody);
                } catch (MessagingException e) {
                    LOGGER.warn("Failed to send Initial Notification Email");
                }
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to results.next().getPath()");
        }
    }
}
