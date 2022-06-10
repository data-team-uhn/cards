/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.proms.emailnotifications;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.auth.token.TokenManager;
import io.uhndata.cards.metrics.Metrics;

@Designate(ocd = UpcomingNotificationsFactory.Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public final class UpcomingNotificationsFactory
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(UpcomingNotificationsFactory.class);

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

    @ObjectClassDefinition(name = "Upcoming appointment notification",
        description = "Send emails for upcoming appointments")
    public static @interface Config
    {
        @AttributeDefinition(name = "Name", description = "Name")
        String name();

        @AttributeDefinition(name = "ClinicID",
            description = "Clinic Identifier as recorded under /apps/cards/proms/clinics/")
        String clinicId();

        @AttributeDefinition(name = "Email Subject Line", description = "Email Subject Line")
        String emailSubject();

        @AttributeDefinition(name = "Plaintext Email Template JCR Path",
            description = "Plaintext Email Template JCR Path")
        String plainTextEmailTemplatePath();

        @AttributeDefinition(name = "HTML Email Template JCR Path", description = "HTML Email Template JCR Path")
        String htmlEmailTemplatePath();

        @AttributeDefinition(name = "Days before upcoming visit", description = "Days before upcoming visit")
        int daysBeforeUpcomingVisit();
    }

    @Activate
    private void activate(final Config config)
    {
        LOGGER.info("Activating upcoming email notifications: {}", config.name());
        final String nightlyNotificationsSchedule =
            StringUtils.defaultIfEmpty(System.getenv("NIGHTLY_NOTIFICATIONS_SCHEDULE"), "0 0 6 * * ? *");

        // Create the performance metrics measurement node
        Metrics.createStatistic(this.resolverFactory, config.name());

        ScheduleOptions notificationsOptions = this.scheduler.EXPR(nightlyNotificationsSchedule);
        notificationsOptions.name("NightlyNotifications-" + config.name());
        notificationsOptions.canRunConcurrently(true);

        // Instantiate the Runnable
        final Runnable notificationsJob = new GeneralNotificationsTask(this.resolverFactory, this.tokenManager,
            this.mailService, config.name(), config.clinicId(), config.emailSubject(),
            config.plainTextEmailTemplatePath(), config.htmlEmailTemplatePath(),
            config.daysBeforeUpcomingVisit());

        try {
            this.scheduler.schedule(notificationsJob, notificationsOptions);
            LOGGER.info("Scheduled Upcoming Notifications Task: {}", config.name());
        } catch (Exception e) {
            LOGGER.error("Failed to schedule Upcoming Notifications Task: {}. {}", config.name(), e.getMessage(), e);
        }
    }

    @Deactivate
    private void deactivate(final Config config)
    {
        LOGGER.info("Deactivating upcoming email notifications: {}", config.name());
        String jobName = "NightlyNotifications-" + config.name();
        if (this.scheduler.unschedule(jobName)) {
            LOGGER.info("Sucessfully unscheduled {}", jobName);
        } else {
            LOGGER.error("Failed to unschedule {}", jobName);
        }
    }
}
