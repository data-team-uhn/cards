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

package io.uhndata.cards.proms.internal.importer;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class NightlyImport
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(NightlyImport.class);

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    /** The scheduler for rescheduling jobs. */
    @Reference
    private Scheduler scheduler;

    @Reference
    private ImportConfig config;

    @Activate
    protected void activate(ComponentContext componentContext) throws Exception
    {
        LOGGER.info("NightlyTorchImport activating");
        final String nightlyImportSchedule = this.config.getConfig().nightly_import_schedule();
        ScheduleOptions options = this.scheduler.EXPR(nightlyImportSchedule);
        options.name("NightlyTorchImport");
        options.canRunConcurrently(true);

        final Runnable importJob;
        importJob =
            new ImportTask(this.resolverFactory, this.config.getConfig().auth_url(),
                this.config.getConfig().endpoint_url(),
                this.config.getConfig().days_to_query(), this.config.getConfig().vault_token(),
                this.config.getConfig().clinic_name(),
                this.config.getConfig().provider_name());

        try {
            if (importJob != null) {
                this.scheduler.schedule(importJob, options);
            }
        } catch (Exception e) {
            LOGGER.error("NightlyTorchImport Failed to schedule: {}", e.getMessage(), e);
        }
    }
}
