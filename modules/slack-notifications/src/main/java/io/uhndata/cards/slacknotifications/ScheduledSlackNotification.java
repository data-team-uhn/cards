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

package io.uhndata.cards.slacknotifications;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.slacknotifications.spi.SlackNotificationProducer;

@Component(immediate = true)
@Designate(ocd = Configuration.class, factory = true)
public class ScheduledSlackNotification
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledSlackNotification.class);

    private static final String SCHEDULER_JOB_PREFIX = "ScheduledSlackNotification-";

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.UPDATE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<SlackNotificationProducer> notifications = new ArrayList<>();

    @Reference
    private Scheduler scheduler;

    @Activate
    protected void activate(Configuration config) throws Exception
    {
        LOGGER.info("ScheduledSlackNotifications activating");
        if (StringUtils.isBlank(resolveFromEnvironment(config.endpoint()))) {
            // Scheduling a job that can only ever fail would report the same failure every single night
            LOGGER.warn("The {} notification has no endpoint configured, it will not be scheduled", config.name());
            return;
        }
        final String nightlyNotificationsSchedule = getSchedule(config.schedule());

        ScheduleOptions slackNotificationsOptions = this.scheduler.EXPR(nightlyNotificationsSchedule);
        slackNotificationsOptions.name(SCHEDULER_JOB_PREFIX + config.name());
        slackNotificationsOptions.canRunConcurrently(true);

        final Runnable slackNotificationsJob =
            new SlackNotificationsTask(config, this.notifications);

        try {
            this.scheduler.schedule(slackNotificationsJob, slackNotificationsOptions);
            LOGGER.info("Scheduled SlackNotificationsTask");
        } catch (Exception e) {
            LOGGER.error("SlackNotificationsTask Failed to schedule: {}", e.getMessage(), e);
        }
    }

    @Deactivate
    public void configRemoved(final Configuration removedConfig)
    {
        LOGGER.debug("Removed slack notification configuration {}", removedConfig.name());
        this.scheduler.unschedule(SCHEDULER_JOB_PREFIX + removedConfig.name());
    }

    private String getSchedule(final String config)
    {
        return StringUtils.defaultIfEmpty(resolveFromEnvironment(config), "0 0 0 * * ? *");
    }

    /*
     * Reads a configuration value that names an environment variable rather than holding the value itself, which is
     * how a secret such as a webhook address is kept out of the configuration files.
     *
     * @param config the configured value
     * @return the value itself, or what the named environment variable holds
     */
    private String resolveFromEnvironment(final String config)
    {
        if (config != null && config.startsWith("%ENV%")) {
            return System.getenv(config.substring("%ENV%".length()));
        }
        return config;
    }
}
