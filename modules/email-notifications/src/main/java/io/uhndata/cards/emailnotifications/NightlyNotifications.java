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

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.auth.token.TokenManager;

@Component(immediate = true)
public class NightlyNotifications
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(NightlyNotifications.class);

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    /** The scheduler for rescheduling jobs. */
    @Reference
    private Scheduler scheduler;

    /** The MailService for sending notification emails to patients. */
    @Reference
    private MailService mailService;

    /** The TokenManager for generating patient-access tokens. */
    @Reference
    private TokenManager tokenManager;

    @Activate
    protected void activate(ComponentContext componentContext) throws Exception
    {
        LOGGER.info("NightlyNotifications activating");
        final String nightlyNotificationsSchedule = System.getenv("NIGHTLY_NOTIFICATIONS_SCHEDULE");
        ScheduleOptions options = this.scheduler.EXPR(nightlyNotificationsSchedule);
        options.name("NightlyNotifications");
        options.canRunConcurrently(true);

        final Runnable initialNotificationsJob = new InitialNotificationsTask(
            this.resolverFactory, this.tokenManager, this.mailService);

        try {
            this.scheduler.schedule(initialNotificationsJob, options);
            LOGGER.info("Scheduled InitialNotificationsTask");
        } catch (Exception e) {
            LOGGER.error("InitialNotificationsTask Failed to schedule: {}", e.getMessage(), e);
        }
    }
}
