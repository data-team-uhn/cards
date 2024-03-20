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

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.auth.token.TokenManager;
import io.uhndata.cards.emailnotifications.Email;
import io.uhndata.cards.emailnotifications.EmailTemplate;
import io.uhndata.cards.emailnotifications.EmailUtils;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.patients.api.PatientAccessConfiguration;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
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

    protected final ThreadResourceResolverProvider resolverProvider;

    private final EventAdmin eventAdmin;

    private final TokenManager tokenManager;

    private final MailService mailService;

    private final FormUtils formUtils;

    private final PatientAccessConfiguration patientAccessConfiguration;

    AbstractEmailNotification(final ResourceResolverFactory resolverFactory,
        final ThreadResourceResolverProvider resolverProvider,
        final TokenManager tokenManager, final MailService mailService, final FormUtils formUtils,
        final PatientAccessConfiguration patientAccessConfiguration, final EventAdmin eventAdmin)
    {
        this.resolverFactory = resolverFactory;
        this.resolverProvider = resolverProvider;
        this.tokenManager = tokenManager;
        this.mailService = mailService;
        this.formUtils = formUtils;
        this.patientAccessConfiguration = patientAccessConfiguration;
        this.eventAdmin = eventAdmin;
    }

    /*
     * Sends notification emails based on templates for appointments in the past or future.
     * @param differenceInDays the difference in days between now and the day of the appointment. Positive values if the
     * appointment is in the future, negative values if the appointment is in the past.
     * @param emailTextTemplateName the name of the plain text email template associated with the Visit's clinic, if
     * clinicId is null, otherwise the absolute JCR path to the template.
     * @param emailHtmlTemplateName the name of the HTML email template associated with the Visit's clinic, if clinicId
     * is null, otherwise the absolute JCR path to the template.
     * @param emailSubject The subject line of the notification email
     * @param clinicId only send notifications for appointments with a clinic specified by this ID.
     * @return the number of notification emails that have been sent
     */
    public long sendNotification(final int differenceInDays, final EmailTemplate template, final String clinicId)
    {
        final Calendar dateToQuery = Calendar.getInstance();
        dateToQuery.add(Calendar.DATE, differenceInDays);
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "EmailNotifications");
        long emailsSent = 0;
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(parameters)) {
            this.resolverProvider.push(resolver);
            mustPopResolver = true;
            final Session session = resolver.adaptTo(Session.class);
            NodeIterator appointmentResults = AppointmentUtils.getAppointmentsForDay(session,
                dateToQuery, clinicId);
            while (appointmentResults.hasNext()) {
                Node appointmentDate = appointmentResults.nextNode();
                Node appointmentForm = this.formUtils.getForm(appointmentDate);
                // This will have to be changed later,
                // since we will have different visit information forms for the same subject
                Node visitSubject = this.formUtils.getSubject(appointmentForm, "/SubjectTypes/Patient/Visit");
                if (appointmentForm == null || AppointmentUtils.isVisitSurveySubmitted(this.formUtils, visitSubject)) {
                    continue;
                }

                // Get the Patient Subject associated with this appointment Form
                Node patientSubject = this.formUtils.getSubject(appointmentForm, "/SubjectTypes/Patient");

                try {
                    Email email = renderTemplate(template, appointmentDate, appointmentForm, visitSubject,
                        patientSubject, session);
                    if (email == null) {
                        continue;
                    }
                    EmailUtils.sendHtmlEmail(email, this.mailService);
                    Event event = new Event(getNotificationType(), Map.of(
                        "visit", visitSubject.getPath(),
                        "patient", patientSubject.getPath()));
                    this.eventAdmin.postEvent(event);
                    emailsSent += 1;
                } catch (MessagingException e) {
                    LOGGER.warn("Failed to send Initial Notification Email");
                }
            }
        } catch (LoginException | RepositoryException e) {
            LOGGER.warn("Failed to results.next().getPath()");
        } finally {
            if (mustPopResolver) {
                this.resolverProvider.pop();
            }
        }
        return emailsSent;
    }

    protected abstract String getNotificationType();

    private Email renderTemplate(final EmailTemplate template, final Node appointmentDate, final Node appointmentForm,
        final Node visitSubject, final Node patientSubject, final Session session) throws RepositoryException
    {
        String patientEmailAddress =
            AppointmentUtils.getPatientConsentedEmail(this.formUtils, patientSubject, visitSubject);
        if (StringUtils.isBlank(patientEmailAddress)) {
            return null;
        }

        String patientFullName = AppointmentUtils.getPatientFullName(this.formUtils, patientSubject);
        Calendar visitDate = (Calendar) this.formUtils.getValue(appointmentDate);
        Calendar tokenExpiryDate = (Calendar) visitDate.clone();
        final int tokenLifetime =
            this.patientAccessConfiguration.getDaysRelativeToEventWhileSurveyIsValid(appointmentForm);
        tokenExpiryDate.add(Calendar.DATE, tokenLifetime);
        atMidnight(tokenExpiryDate);
        final String token = this.tokenManager.create(
            "patient",
            tokenExpiryDate,
            Collections.singletonMap(
                "cards:sessionSubject",
                visitSubject.getPath()))
            .getToken();
        // Send the Notification Email
        Map<String, String> valuesMap = new HashMap<>();
        valuesMap.put("surveysLink", "https://" + CARDS_HOST_AND_PORT + CLINIC_SLING_PATH + "?auth_token=" + token);
        final String unsubscribeLink =
            "https://" + CARDS_HOST_AND_PORT + "/Survey.unsubscribe.html?auth_token=" + token;
        valuesMap.put("unsubscribeLink", unsubscribeLink);
        final DateFormat sdf = DateFormat.getDateInstance(DateFormat.LONG);
        sdf.setTimeZone(tokenExpiryDate.getTimeZone());
        valuesMap.put("expirationDate", sdf.format(tokenExpiryDate.getTime()));
        return template.getEmailBuilderForSubject(visitSubject, valuesMap, this.formUtils)
            .withRecipient(patientEmailAddress, patientFullName)
            .withExtraHeader("List-Unsubscribe", "<" + unsubscribeLink + ">")
            .build();
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
