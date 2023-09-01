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

package io.uhndata.cards.internal;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Schedule the check in of the forms that haven't been modified in more than 30 minutes.
 *
 * @version $Id$
 * @since 0.9.16
 */
@Component(immediate = true)
public class StaleFormsCheckinScheduler
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(StaleFormsCheckinScheduler.class);

    private static final String SCHEDULER_JOB_NAME = "StaleFormsCheckin";

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    /** For sharing the resource resolver with other services. */
    @Reference
    private ThreadResourceResolverProvider rrp;

    /** The scheduler for rescheduling jobs. */
    @Reference
    private Scheduler scheduler;

    @Activate
    protected void activate(final ComponentContext componentContext) throws Exception
    {
        try {
            // Every 5 minutes
            final ScheduleOptions options = this.scheduler.EXPR("*/5 * * * *");
            options.name(SCHEDULER_JOB_NAME);
            options.canRunConcurrently(false);

            final Runnable cleanupJob = new StaleFormsCheckinTask(this.resolverFactory, this.rrp);
            this.scheduler.schedule(cleanupJob, options);
        } catch (final Exception e) {
            LOGGER.error("StaleFormsCheckinScheduler failed to schedule: {}", e.getMessage(), e);
        }
    }
}
