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

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;

import io.uhndata.cards.auth.token.TokenManager;
import io.uhndata.cards.emailnotifications.EmailTemplate;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.metrics.Metrics;
import io.uhndata.cards.patients.api.PatientAccessConfiguration;
import io.uhndata.cards.utils.ThreadResourceResolverProvider;

public class GeneralNotificationsTask extends AbstractEmailNotification implements Runnable
{
    private String taskName;

    private String clinicId;

    private EmailTemplate emailTemplate;

    private int daysToVisit;

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
        final ThreadResourceResolverProvider resolverProvider,
        final TokenManager tokenManager, final MailService mailService,
        final FormUtils formUtils,
        final PatientAccessConfiguration patientAccessConfiguration, final String taskName,
        final String clinicId, final EmailTemplate emailTemplate, final int daysToVisit)
    {
        super(resolverFactory, resolverProvider, tokenManager, mailService, formUtils, patientAccessConfiguration);
        this.taskName = taskName;
        this.clinicId = clinicId;
        this.emailTemplate = emailTemplate;
        this.daysToVisit = daysToVisit;
    }

    @Override
    public void run()
    {
        long emailsSent = sendNotification(this.daysToVisit, this.emailTemplate, this.clinicId);
        Metrics.increment(this.resolverFactory, this.taskName, emailsSent);
    }
}
