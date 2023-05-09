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

package io.uhndata.cards.torch.internal;

import java.util.List;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@Component(immediate = true)
public class NightlyImport
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(NightlyImport.class);

    private static final String SCHEDULER_JOB_PREFIX = "NightlyTorchImport-";

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    /** The scheduler for rescheduling jobs. */
    @Reference
    private Scheduler scheduler;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, bind = "configAdded", unbind = "configRemoved")
    private volatile List<ImportConfig> configs;

    public void configAdded(final ImportConfig newConfig)
    {
        if (this.scheduler == null) {
            // This method can be called both before the component is activated, while the scheduler dependency may
            // still be unsatisfied, and while the component is active and the configuration is changed.
            // If the scheduler is not bound, it's OK to do nothing now, the method will be called during activation.
            return;
        }

        LOGGER.debug("Added torch importer configuration {}", newConfig.getConfig().name());
        final String nightlyImportSchedule = newConfig.getConfig().nightly_import_schedule();
        final ScheduleOptions options = this.scheduler.EXPR(nightlyImportSchedule);
        options.name(SCHEDULER_JOB_PREFIX + newConfig.getConfig().name());
        options.canRunConcurrently(true);

        final Runnable importJob;
        importJob =
            new ImportTask(this.resolverFactory, this.rrp, newConfig.getConfig().auth_url(),
                newConfig.getConfig().endpoint_url(),
                newConfig.getConfig().days_to_query(),
                newConfig.getConfig().vault_token(),
                newConfig.getConfig().clinic_names(),
                newConfig.getConfig().provider_names(),
                newConfig.getConfig().allowed_roles(),
                newConfig.getConfig().vault_role(),
                newConfig.getConfig().dates_to_query());
        try {
            if (importJob != null) {
                this.scheduler.schedule(importJob, options);
            }
        } catch (final Exception e) {
            LOGGER.error("NightlyTorchImport Failed to schedule: {}", e.getMessage(), e);
        }
    }

    public void configRemoved(final ImportConfig removedConfig)
    {
        LOGGER.debug("Removed torch importer config {}", removedConfig.getConfig().name());
        this.scheduler.unschedule(SCHEDULER_JOB_PREFIX + removedConfig.getConfig().name());
    }

    @Activate
    protected void activate(final ComponentContext componentContext) throws Exception
    {
        // Re-activate all the configurations; if some have been already activated, that's OK, the scheduler will simply
        // update each job.
        this.configs.forEach(this::configAdded);
    }
}
