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

package io.uhndata.cards.export;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
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

import io.uhndata.cards.export.spi.DataFormatter;
import io.uhndata.cards.export.spi.DataRetriever;
import io.uhndata.cards.export.spi.DataStore;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@Component(immediate = true)
public class PeriodicExportManager
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicExportManager.class);

    private static final String SCHEDULER_JOB_PREFIX = "PeriodicExport-";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private Scheduler scheduler;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, bind = "configAdded", unbind = "configRemoved")
    private volatile List<ExportConfig> configs;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, bind = "retrieverAdded", unbind = "retrieverRemoved")
    private volatile List<DataRetriever> retrievers;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, bind = "formatterAdded", unbind = "formatterRemoved")
    private volatile List<DataFormatter> formatters;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, bind = "storeAdded", unbind = "storeRemoved")
    private volatile List<DataStore> stores;

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
        } else if (StringUtils.equals("none", configDef.exportSchedule())) {
            LOGGER.debug("Skipping non-periodic export {}.", configDef.name());
            return;
        }

        final DataPipeline pipeline = buildPipeline(configDef);
        if (pipeline == null) {
            return;
        }

        LOGGER.debug("Added periodic export configuration {}", configDef.name());
        final String schedule = StringUtils.defaultIfEmpty(configDef.exportSchedule(),
            StringUtils.defaultIfEmpty(System.getenv("NIGHTLY_EXPORT_SCHEDULE"),
                ExportConfigDefinition.NIGHTLY_EXPORT_SCHEDULE));

        final ScheduleOptions options = this.scheduler.EXPR(schedule);
        options.name(SCHEDULER_JOB_PREFIX + configDef.name());
        options.canRunConcurrently(true);

        final Runnable exportJob = new ExportTask(this.resolverFactory, this.rrp, configDef, pipeline, "scheduled");

        try {
            this.scheduler.schedule(exportJob, options);
            LOGGER.debug("Successfully scheduled periodic export {}", configDef.name());
        } catch (final Exception e) {
            LOGGER.error("Periodic export failed to schedule: {}", e.getMessage(), e);
        }
    }

    private DataPipeline buildPipeline(ExportConfigDefinition config)
    {
        LOGGER.error("Scheduling! s={}, r={}, f={}, s={}", this.scheduler, this.retrievers, this.formatters,
            this.stores);
        final DataRetriever retriever =
            this.retrievers.stream().filter(r -> StringUtils.equals(config.retriever(), r.getName())).findFirst()
                .orElse(null);
        if (retriever == null) {
            LOGGER.warn("Unknown data retriever configured for {}: {}. Maybe it's not loaded yet, will retry.",
                config.name(), config.retriever());
            return null;
        }
        final DataFormatter formatter =
            this.formatters.stream().filter(f -> StringUtils.equals(config.formatter(), f.getName())).findFirst()
                .orElse(null);
        if (formatter == null) {
            LOGGER.warn("Unknown data formatter configured for {}: {}. Maybe it's not loaded yet, will retry.",
                config.name(), config.formatter());
            return null;
        }
        final DataStore store =
            this.stores.stream().filter(s -> StringUtils.equals(config.storage(), s.getName())).findFirst()
                .orElse(null);
        if (store == null) {
            LOGGER.warn("Unknown storage configured for {}: {}. Maybe it's not loaded yet, will retry.",
                config.name(), config.storage());
            return null;
        }
        return new DataPipeline(retriever, formatter, store);
    }

    public void configRemoved(final ExportConfig removedConfig)
    {
        LOGGER.debug("Removed exporter config {}", removedConfig.getConfig().name());
        try {
            this.scheduler.unschedule(SCHEDULER_JOB_PREFIX + removedConfig.getConfig().name());
        } catch (Exception e) {
            LOGGER.error("Failed to unschedule: {}, {}, {}, {}", this.scheduler, removedConfig,
                removedConfig != null ? removedConfig.getConfig() : "null",
                (removedConfig != null && removedConfig.getConfig() != null) ? removedConfig.getConfig().name()
                    : "null");
        }
    }

    public void retrieverAdded(final DataRetriever retriever)
    {
        final String name = retriever.getName();
        this.configs.stream().filter(c -> StringUtils.equals(name, c.getConfig().retriever())).forEach(c -> {
            configRemoved(c);
            configAdded(c);
        });
    }

    public void retrieverRemoved(final DataRetriever retriever)
    {
        final String name = retriever.getName();
        this.configs.stream().filter(c -> StringUtils.equals(name, c.getConfig().retriever()))
            .forEach(this::configAdded);
    }

    public void formatterAdded(final DataFormatter formatter)
    {
        final String name = formatter.getName();
        this.configs.stream().filter(c -> StringUtils.equals(name, c.getConfig().formatter())).forEach(c -> {
            configRemoved(c);
            configAdded(c);
        });
    }

    public void formatterRemoved(final DataFormatter formatter)
    {
        final String name = formatter.getName();
        this.configs.stream().filter(c -> StringUtils.equals(name, c.getConfig().formatter()))
            .forEach(this::configAdded);
    }

    public void storeAdded(final DataStore store)
    {
        final String name = store.getName();
        this.configs.stream().filter(c -> StringUtils.equals(name, c.getConfig().storage())).forEach(c -> {
            configRemoved(c);
            configAdded(c);
        });
    }

    public void storeRemoved(final DataStore store)
    {
        final String name = store.getName();
        this.configs.stream().filter(c -> StringUtils.equals(name, c.getConfig().storage()))
            .forEach(this::configAdded);
    }

    @Activate
    protected void activate(ComponentContext componentContext) throws Exception
    {
        LOGGER.info("ScheduledExport activating");
        this.configs.forEach(this::configAdded);
    }
}
