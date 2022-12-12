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

package io.uhndata.cards.clarity.importer;

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

@Component(immediate = true)
public class NightlyClarityImport
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(NightlyClarityImport.class);

    private static final String SCHEDULER_JOB_PREFIX = "NightlyClarityImport-";

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    /** The scheduler for rescheduling jobs. */
    @Reference
    private Scheduler scheduler;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, bind = "configAdded", unbind = "configRemoved")
    private volatile List<ClarityImportConfig> configs;

    public void configAdded(final ClarityImportConfig newConfig)
    {
        if (this.scheduler == null) {
            return;
        }

        LOGGER.debug("Added clarity importer configuration {}", newConfig.getConfig().name());
        final String nightlyClarityImportSchedule = newConfig.getConfig().nightly_import_schedule();
        final ScheduleOptions options = this.scheduler.EXPR(nightlyClarityImportSchedule);
        options.name(SCHEDULER_JOB_PREFIX + newConfig.getConfig().name());
        options.canRunConcurrently(true);

        final Runnable importJob;
        importJob = new ClarityImportTask(this.resolverFactory);
        try {
            if (importJob != null) {
                this.scheduler.schedule(importJob, options);
            }
        } catch (final Exception e) {
            LOGGER.error("NightlyTorchImport Failed to schedule: {}", e.getMessage(), e);
        }
    }

    public void configRemoved(final ClarityImportConfig removedConfig)
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
