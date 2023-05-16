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

package io.uhndata.cards.scheduledcsvexport;

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
public class ScheduledExport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledExport.class);

    private static final String SCHEDULER_JOB_PREFIX = "ScheduledExport-CSV-";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private Scheduler scheduler;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, bind = "configAdded", unbind = "configRemoved")
    private volatile List<ExportConfig> configs;

    public void configAdded(final ExportConfig newConfig)
    {
        if (this.scheduler == null) {
            // This method can be called both before the component is activated, while the scheduler dependency may
            // still be unsatisfied, and while the component is active and the configuration is changed.
            // If the scheduler is not bound, it's OK to do nothing now, the method will be called during activation.
            return;
        }
        final ExportConfigDefinition configDef = newConfig.getConfig();
        if (configDef == null) {
            LOGGER.error("Unknown configuration.");
            return;
        }

        LOGGER.debug("Added csv export configuration {}", configDef.name());
        final String schedule = configDef.export_schedule();
        final ScheduleOptions options = this.scheduler.EXPR(schedule);
        options.name(SCHEDULER_JOB_PREFIX + configDef.name());
        options.canRunConcurrently(true);

        final Runnable csvExportJob;
        csvExportJob =
            new ExportTask(this.resolverFactory, this.rrp, configDef);
        try {
            this.scheduler.schedule(csvExportJob, options);
        } catch (final Exception e) {
            LOGGER.error("ScheduledExport Failed to schedule: {}", e.getMessage(), e);
        }
    }

    public void configRemoved(final ExportConfig removedConfig)
    {
        LOGGER.debug("Removed CSV exporter config {}", removedConfig.getConfig().name());
        this.scheduler.unschedule(SCHEDULER_JOB_PREFIX + removedConfig.getConfig().name());
    }

    @Activate
    protected void activate(ComponentContext componentContext) throws Exception
    {
        this.configs.forEach(this::configAdded);
    }
}
