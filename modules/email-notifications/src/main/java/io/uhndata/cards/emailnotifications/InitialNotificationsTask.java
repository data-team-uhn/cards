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
         * all appointments on January 4th 2022.
         *
         * Suppose that the nightly ImportTask runs every night at 00:00:00.
         * Therefore, when it runs at 2022-01-01T00:00:00 with a daysToQuery
         * parameter of 3, it will import appointments for 2022-01-01,
         * 2022-01-02, and 2022-01-03.
         *
         * Now suppose that this nightly InitialNotificationsTask runs every night
         * at 00:30:00. When it runs at 2022-01-01T00:30:00 it will obtain
         * all the appointments happening on 2022-01-04 and immediately send
         * out the notification emails requesting completion of the
         * pre-appointment surveys. If an appointment is scheduled for
         * 2022-01-04T00:00:00 (although highly unlikely at this hour)
         * the email notification will thus be sent out 71.5 hours before
         * the appointment. If an appointment is scheduled for
         * 2022-01-04T23:59:59 (although highly unlikely at this hour) the
         * email notification will thus be sent out 95.5 hours before the
         * appointment.
         *
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
                String patientFullName = AppointmentUtils.getPatientFullName(resolver, patientSubject);
                Calendar tokenExpiryDate = AppointmentUtils.parseDate(appointmentResult.getValueMap().get("value", ""));
                tokenExpiryDate.add(Calendar.HOUR, 2);
                String surveysLink = "http://localhost:8080/Proms.html/Cardio?auth_token="
                    + this.tokenManager.create(
                        "patient",
                        tokenExpiryDate,
                        Collections.singletonMap(
                            "cards:sessionSubject",
                            visitSubject.getPath()
                        )
                    ).getToken();
                // Send the Initial Notification Email
                String emailBody = "You have an appointment in 3 days from now.";
                emailBody += " Please complete your surveys beforehand at";
                emailBody += " ";
                emailBody += surveysLink;
                try {
                    EmailUtils.sendNotificationEmail(this.mailService, patientEmailAddress, patientFullName, emailBody);
                } catch (MessagingException e) {
                    LOGGER.warn("Failed to send Initial Notification Email");
                }
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to results.next().getPath()");
        }
    }
}
