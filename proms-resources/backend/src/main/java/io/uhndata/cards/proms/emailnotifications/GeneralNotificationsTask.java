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

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;

import io.uhndata.cards.auth.token.TokenManager;
import io.uhndata.cards.metrics.Metrics;

public class GeneralNotificationsTask extends AbstractPromsNotification implements Runnable
{
    private String taskName;
    private String clinicId;
    private String emailSubject;
    private String plainTemplatePath;
    private String htmlTemplatePath;
    private int daysBeforeVisit;

    @SuppressWarnings({ "checkstyle:ParameterNumber" })
    GeneralNotificationsTask(final ResourceResolverFactory resolverFactory,
        final TokenManager tokenManager, final MailService mailService, final String taskName,
        final String clinicId, final String emailSubject, final String plainTemplatePath,
        final String htmlTemplatePath, final int daysBeforeVisit)
    {
        super(resolverFactory, tokenManager, mailService);
        this.taskName = taskName;
        this.clinicId = clinicId;
        this.emailSubject = emailSubject;
        this.plainTemplatePath = plainTemplatePath;
        this.htmlTemplatePath = htmlTemplatePath;
        this.daysBeforeVisit = daysBeforeVisit;
    }

    @Override
    public void run()
    {
        long emailsSent = sendNotification(this.daysBeforeVisit, this.plainTemplatePath, this.htmlTemplatePath,
            this.emailSubject, this.clinicId);
        Metrics.increment(this.resolverFactory, this.taskName, emailsSent);
    }
}
