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

public class ReminderNotificationsTask extends AbstractPromsNotification implements Runnable
{
    private static final String PATIENT_NOTIFICATION_SUBJECT =
        "Reminder: You have 24 hours left to complete your pre-appointment questions";

    ReminderNotificationsTask(final ResourceResolverFactory resolverFactory,
        final TokenManager tokenManager, final MailService mailService)
    {
        super(resolverFactory, tokenManager, mailService);
    }

    @Override
    public void run()
    {
        long emailsSent = sendNotification(1, "24h.txt", "24h.html", PATIENT_NOTIFICATION_SUBJECT);
        Metrics.increment(this.resolverFactory, "ReminderEmailsSent", emailsSent);
    }
}
