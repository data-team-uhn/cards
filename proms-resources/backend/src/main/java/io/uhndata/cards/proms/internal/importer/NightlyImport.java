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
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
@Designate(ocd = NightlyImportConfig.class)
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

    @Activate
    protected void activate(NightlyImportConfig config, ComponentContext componentContext) throws Exception
    {
        LOGGER.info("NightlyTorchImport activating");
        final String nightlyImportSchedule = config.nightly_import_schedule();
        ScheduleOptions options = this.scheduler.EXPR(nightlyImportSchedule);
        options.name("NightlyTorchImport");
        options.canRunConcurrently(true);

        final Runnable importJob;
        importJob = new ImportTask(this.resolverFactory, config.auth_url(), config.endpoint_url(),
            config.days_to_query(), config.vault_token(), config.clinic_name(), config.provider_name());

        try {
            if (importJob != null) {
                this.scheduler.schedule(importJob, options);
            }
        } catch (Exception e) {
            LOGGER.error("NightlyTorchImport Failed to schedule: {}", e.getMessage(), e);
        }
    }
}
