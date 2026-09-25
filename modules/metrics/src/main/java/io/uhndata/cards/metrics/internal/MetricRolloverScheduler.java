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

package io.uhndata.cards.metrics.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.errortracking.ErrorLogger;
import io.uhndata.cards.metrics.api.Metric;
import io.uhndata.cards.metrics.api.MetricsException;

/**
 * Automatically {@link Metric#rollOver rolls over} every metric that declares a roll-over schedule: one scheduled
 * job per metric, following its Quartz cron expression. The schedules are read when the component starts, and
 * refreshed whenever anything changes under {@code /Metrics}, so newly defined metrics and edited schedules are
 * picked up on the fly. In a cluster the jobs only run on the leader, so each period is closed exactly once.
 *
 * @version $Id$
 * @since 0.9.42
 */
@Component(immediate = true, service = ResourceChangeListener.class, property = {
    ResourceChangeListener.PATHS + "=" + MetricImpl.METRICS_PATH,
    ResourceChangeListener.CHANGES + "=ADDED",
    ResourceChangeListener.CHANGES + "=CHANGED",
    ResourceChangeListener.CHANGES + "=REMOVED"
})
public class MetricRolloverScheduler implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricRolloverScheduler.class);

    private static final String JOB_NAME_PREFIX = "cards-metrics-rollover-";

    @Reference
    private Scheduler scheduler;

    @Reference
    private ResourceResolverFactory resolverFactory;

    /** The currently scheduled jobs: metric name to the cron expression it was scheduled with. */
    private final Map<String, String> scheduled = new HashMap<>();

    @Activate
    void activate()
    {
        synchronizeSchedules();
    }

    @Deactivate
    synchronized void deactivate()
    {
        this.scheduled.keySet().forEach(name -> this.scheduler.unschedule(JOB_NAME_PREFIX + name));
        this.scheduled.clear();
    }

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        synchronizeSchedules();
    }

    /**
     * Bring the scheduled jobs in line with the schedules currently defined on the metrics: unschedule jobs whose
     * metric is gone or has a different schedule now, then schedule the metrics not yet scheduled. Metrics without
     * a schedule are left alone. Cheap when nothing relevant changed, so it can be invoked on every repository
     * change under {@code /Metrics}, including plain increments.
     */
    private synchronized void synchronizeSchedules()
    {
        final Map<String, String> desired;
        try {
            desired = readSchedules();
        } catch (final MetricsException e) {
            LOGGER.warn("Cannot read the metric roll-over schedules: {}", e.getMessage(), e);
            return;
        }
        this.scheduled.entrySet().removeIf(job -> {
            if (!job.getValue().equals(desired.get(job.getKey()))) {
                this.scheduler.unschedule(JOB_NAME_PREFIX + job.getKey());
                return true;
            }
            return false;
        });
        desired.forEach((name, schedule) -> {
            if (!this.scheduled.containsKey(name) && schedule(name, schedule)) {
                this.scheduled.put(name, schedule);
            }
        });
    }

    /**
     * Gather the declared roll-over schedules.
     *
     * @return a map from metric name to its cron expression, only for the metrics declaring one
     * @throws MetricsException if the repository cannot be accessed
     */
    private Map<String, String> readSchedules()
    {
        try (ResourceResolver resolver = MetricImpl.openServiceResolver(this.resolverFactory)) {
            final Resource homepage = resolver.getResource(MetricImpl.METRICS_PATH);
            if (homepage == null) {
                return Map.of();
            }
            return StreamSupport.stream(homepage.getChildren().spliterator(), false)
                .filter(MetricImpl::isMetric)
                .map(metric -> Map.entry(metric.getName(),
                    metric.getValueMap().get(MetricImpl.PN_ROLLOVER_SCHEDULE, "")))
                .filter(entry -> !entry.getValue().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    /**
     * Schedule the periodic roll-over of one metric.
     *
     * @param name the name of the metric
     * @param schedule the Quartz cron expression to follow
     * @return whether the job was successfully scheduled
     */
    private boolean schedule(final String name, final String schedule)
    {
        final ScheduleOptions options = this.scheduler.EXPR(schedule);
        options.name(JOB_NAME_PREFIX + name);
        options.canRunConcurrently(false);
        options.onLeaderOnly(true);
        try {
            if (this.scheduler.schedule((Runnable) () -> rollOver(name), options)) {
                LOGGER.debug("Scheduled the roll-over of metric {} as [{}]", name, schedule);
                return true;
            }
            LOGGER.error("Failed to schedule the roll-over of metric {}, is [{}] a valid cron expression?",
                name, schedule);
            // Recorded as an error too: a metric that never rolls over keeps reporting its first period forever
            ErrorLogger.logError(new MetricsException(
                "The roll-over schedule [" + schedule + "] of metric " + name + " was refused"));
        } catch (final RuntimeException e) {
            LOGGER.error("Failed to schedule the roll-over of metric {}: {}", name, e.getMessage(), e);
            ErrorLogger.logError(e);
        }
        return false;
    }

    /**
     * The scheduled job itself: roll over one metric. Failures are logged, so that the job remains scheduled and
     * gets another chance at the next period.
     *
     * @param name the name of the metric to roll over
     */
    void rollOver(final String name)
    {
        try {
            // Straight to the handle rather than through MetricsManager.getMetric: that would open a session only to
            // confirm the metric is still there, which the roll-over itself reports anyway. A metric deleted between
            // being scheduled and firing is now logged below instead of passing silently.
            new MetricImpl(this.resolverFactory, name).rollOver();
        } catch (final RuntimeException e) {
            LOGGER.warn("Scheduled roll-over of metric {} failed: {}", name, e.getMessage(), e);
            ErrorLogger.logError(e);
        }
    }
}
