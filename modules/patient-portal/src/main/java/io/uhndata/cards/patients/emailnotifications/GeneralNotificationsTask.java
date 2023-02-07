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

import java.io.IOException;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.auth.token.TokenManager;
import io.uhndata.cards.emailnotifications.EmailTemplate;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.metrics.Metrics;
import io.uhndata.cards.patients.api.PatientAccessConfiguration;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

public class GeneralNotificationsTask extends AbstractEmailNotification implements Runnable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralNotificationsTask.class);

    private final String taskName;

    private final String notificationType;

    private final String clinicId;

    private final String emailTemplatePath;

    private EmailTemplate emailTemplate;

    private final int daysToVisit;

    /*
     * Task that sends email notifications as per the configuration parameters.
     * @param resolverFactory a ResourceResolverFactory object that can be used for accessing the JCR
     * @param tokenManager a TokenManager object that can be used for generating user authentication tokens, to provide
     * authorization links in emails
     * @param mailService a MailService object that can be used for sending emails
     * @param formUtils form utilities service that can be used to interact with form nodes
     * @param taskName the name associated with the performance metrics gathered from this task
     * @param clinicId the clinic ID that identifies the clinic for which notifications should be sent about (or null
     * for all clinics)
     * @param emailTemplate the template for the email notifications
     * @param daysToVisit the difference in days between now and the day of the appointment. Positive values if the
     * appointment is in the future, negative values if the appointment is in the past.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public GeneralNotificationsTask(final ResourceResolverFactory resolverFactory,
        final ThreadResourceResolverProvider resolverProvider, final EventAdmin eventAdmin,
        final TokenManager tokenManager, final MailService mailService,
        final FormUtils formUtils, final PatientAccessConfiguration patientAccessConfiguration, final String taskName,
        final String notificationType, final String clinicId, final String emailTemplatePath, final int daysToVisit)
    {
        super(resolverFactory, resolverProvider, tokenManager, mailService, formUtils, patientAccessConfiguration,
            eventAdmin);
        this.taskName = taskName;
        this.notificationType = notificationType;
        this.clinicId = clinicId;
        this.emailTemplatePath = emailTemplatePath;
        this.daysToVisit = daysToVisit;
    }

    @Override
    public void run()
    {
        if (this.emailTemplate == null) {
            this.emailTemplate = buildTemplate(this.emailTemplatePath);
        }
        long emailsSent = sendNotification(this.daysToVisit, this.emailTemplate, this.clinicId);
        Metrics.increment(this.resolverFactory, this.taskName, emailsSent);
    }

    @Override
    protected String getNotificationType()
    {
        return "Notification/Patient/Appointment/" + this.notificationType;
    }

    private EmailTemplate buildTemplate(final String configurationNodePath)
    {
        try (ResourceResolver rr = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "EmailNotifications"))) {
            final Session session = rr.adaptTo(Session.class);
            // Sometimes the notification is activated before the template node is imported
            // If that is the case, wait a little bit and try again
            for (int i = 0; i < 10; ++i) {
                if (session.nodeExists(configurationNodePath)) {
                    break;
                } else {
                    try {
                        Thread.sleep(10_000);
                        session.refresh(false);
                    } catch (InterruptedException e) {
                        // We're not expecting any interruptions
                    }
                }
            }
            if (!session.nodeExists(configurationNodePath)) {
                throw new IllegalStateException(
                    "Configured email template " + configurationNodePath + " was not found");
            }
            final Node configuration = session.getNode(configurationNodePath);
            return EmailTemplate.builder(configuration, rr).build();
        } catch (LoginException e) {
            LOGGER.warn("Missing rights configuration for Email Notifications");
        } catch (RepositoryException | IOException e) {
            LOGGER.warn("Failed to read the email configuration: {}", e.getMessage(), e);
            throw new IllegalStateException();
        }
        return null;
    }
}
