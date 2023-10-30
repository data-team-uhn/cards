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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@Component(immediate = true)
public class NightlyClarityImport
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(NightlyClarityImport.class);

    private static final String SCHEDULER_JOB_PREFIX = "ScheduledClarityImport-";

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    /** A list of all available data processors. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.UPDATE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<ClarityDataProcessor> processors = new ArrayList<>();

    @Reference(policyOption = ReferencePolicyOption.GREEDY, bind = "configAdded", unbind = "configRemoved")
    private volatile List<ClarityImportConfig> configs;

    /** The scheduler for rescheduling jobs. */
    @Reference
    private Scheduler scheduler;

    public void configAdded(final ClarityImportConfig newConfig)
    {
        if (this.scheduler == null) {
            // This method can be called both before the component is activated, while the scheduler dependency may
            // still be unsatisfied, and while the component is active and the configuration is changed.
            // If the scheduler is not bound, it's OK to do nothing now, the method will be called during activation.
            return;
        }
        final ClarityImportConfigDefinition config = newConfig.getConfig();
        LOGGER.debug("Added clarity import configuration {}", config.name());

        final String schedule = config.importSchedule();
        if (StringUtils.isBlank(schedule)) {
            LOGGER.info("ClarityImport {} skipped because a cron schedule was not specified.", config.name());
            return;
        }

        final ScheduleOptions options = this.scheduler.EXPR(schedule);
        options.name(SCHEDULER_JOB_PREFIX + config.name());
        options.canRunConcurrently(true);

        final Runnable job =
            new ClarityImportTask(config, config.dayToImport(), this.resolverFactory, this.rrp, this.processors);
        try {
            this.scheduler.schedule(job, options);
            LOGGER.debug("Activated scheduled clarity import configuration {}", config.name());
        } catch (final Exception e) {
            LOGGER.error("Scheduled clarity import {} failed to schedule: {}", config.name(), e.getMessage(), e);
        }
    }

    public void configRemoved(final ClarityImportConfig removedConfig)
    {
        LOGGER.debug("Removed clarity import configuration {}", removedConfig.getConfig().name());
        this.scheduler.unschedule(SCHEDULER_JOB_PREFIX + removedConfig.getConfig().name());
    }

    @Activate
    protected void activate()
    {
        this.configs.forEach(this::configAdded);
    }
}
