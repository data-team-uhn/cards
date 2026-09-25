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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link MetricRolloverScheduler}.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class MetricRolloverSchedulerTest
{
    private static final String NIGHTLY = "0 0 0 * * ?";

    private static final String NOON = "0 0 12 * * ?";

    @Rule
    public SlingContext context = new SlingContext();

    private final Scheduler scheduler = Mockito.mock(Scheduler.class);

    private final ScheduleOptions options = Mockito.mock(ScheduleOptions.class);

    private ResourceResolverFactory factory;

    private MetricsManagerImpl manager;

    private MetricRolloverScheduler rolloverScheduler;

    @Before
    public void setUp() throws Exception
    {
        this.factory = this.context.getService(ResourceResolverFactory.class);
        this.manager = new MetricsManagerImpl();
        inject(this.manager, "resolverFactory", this.factory);
        this.rolloverScheduler = new MetricRolloverScheduler();
        inject(this.rolloverScheduler, "scheduler", this.scheduler);
        inject(this.rolloverScheduler, "resolverFactory", this.factory);
        Mockito.when(this.scheduler.EXPR(Mockito.anyString())).thenReturn(this.options);
        Mockito.when(this.scheduler.schedule(Mockito.any(), Mockito.any())).thenReturn(true);
        try (ResourceResolver resolver = open()) {
            resolver.create(resolver.getResource("/"), "Metrics",
                Map.of("sling:resourceType", "cards/MetricsHomepage"));
            resolver.commit();
        }
    }

    @Test
    public void schedulesEveryMetricWithAScheduleOnActivation()
    {
        this.manager.createMetric("nightly").withRolloverSchedule(NIGHTLY).create();
        this.manager.createMetric("manual").create();
        this.manager.createMetric("noon").withRolloverSchedule(NOON).create();

        this.rolloverScheduler.activate();

        Mockito.verify(this.scheduler).EXPR(NIGHTLY);
        Mockito.verify(this.scheduler).EXPR(NOON);
        Mockito.verify(this.scheduler, Mockito.times(2)).schedule(Mockito.any(), Mockito.eq(this.options));
        Mockito.verify(this.options).name("cards-metrics-rollover-nightly");
        Mockito.verify(this.options).name("cards-metrics-rollover-noon");
        // The jobs must not run concurrently with themselves, and only once per cluster
        Mockito.verify(this.options, Mockito.times(2)).canRunConcurrently(false);
        Mockito.verify(this.options, Mockito.times(2)).onLeaderOnly(true);
        Mockito.verify(this.scheduler, Mockito.never()).unschedule(Mockito.anyString());
    }

    @Test
    public void blankSchedulesAreIgnored() throws Exception
    {
        try (ResourceResolver resolver = open()) {
            resolver.create(resolver.getResource("/Metrics"), "blank",
                Map.of("jcr:primaryType", "cards:Metric", "label", "Blank", "rolloverSchedule", "   "));
            resolver.commit();
        }

        this.rolloverScheduler.activate();

        Mockito.verify(this.scheduler, Mockito.never()).schedule(Mockito.any(), Mockito.any());
    }

    @Test
    public void nothingToScheduleWithoutTheHomepage() throws Exception
    {
        try (ResourceResolver resolver = open()) {
            resolver.delete(resolver.getResource("/Metrics"));
            resolver.commit();
        }

        this.rolloverScheduler.activate();

        Mockito.verify(this.scheduler, Mockito.never()).schedule(Mockito.any(), Mockito.any());
    }

    @Test
    public void newMetricsAreScheduledOnChangeEvents()
    {
        this.manager.createMetric("nightly").withRolloverSchedule(NIGHTLY).create();
        this.rolloverScheduler.activate();

        this.manager.createMetric("noon").withRolloverSchedule(NOON).create();
        this.rolloverScheduler.onChange(List.of());

        // Only the new metric gets scheduled, the existing job is left alone
        Mockito.verify(this.scheduler, Mockito.times(2)).schedule(Mockito.any(), Mockito.eq(this.options));
        Mockito.verify(this.scheduler).EXPR(NOON);
        Mockito.verify(this.scheduler, Mockito.never()).unschedule(Mockito.anyString());
    }

    @Test
    public void irrelevantChangesDoNotDisturbTheSchedules()
    {
        this.manager.createMetric("nightly").withRolloverSchedule(NIGHTLY).create();
        this.rolloverScheduler.activate();

        this.manager.getMetric("nightly").orElseThrow().increment();
        this.rolloverScheduler.onChange(List.of());

        Mockito.verify(this.scheduler, Mockito.times(1)).schedule(Mockito.any(), Mockito.any());
        Mockito.verify(this.scheduler, Mockito.never()).unschedule(Mockito.anyString());
    }

    @Test
    public void changedSchedulesAreRescheduled()
    {
        this.manager.createMetric("periodic").withRolloverSchedule(NIGHTLY).create();
        this.rolloverScheduler.activate();

        this.manager.createMetric("periodic").withRolloverSchedule(NOON).create();
        this.rolloverScheduler.onChange(List.of());

        Mockito.verify(this.scheduler).unschedule("cards-metrics-rollover-periodic");
        Mockito.verify(this.scheduler).EXPR(NIGHTLY);
        Mockito.verify(this.scheduler).EXPR(NOON);
        Mockito.verify(this.scheduler, Mockito.times(2)).schedule(Mockito.any(), Mockito.any());
    }

    @Test
    public void removedSchedulesAreUnscheduled()
    {
        this.manager.createMetric("periodic").withRolloverSchedule(NIGHTLY).create();
        this.rolloverScheduler.activate();

        // Redefining the metric without a schedule removes the automatic roll-over
        this.manager.createMetric("periodic").create();
        this.rolloverScheduler.onChange(List.of());

        Mockito.verify(this.scheduler).unschedule("cards-metrics-rollover-periodic");
        Mockito.verify(this.scheduler, Mockito.times(1)).schedule(Mockito.any(), Mockito.any());
    }

    @Test
    public void deletedMetricsAreUnscheduled() throws Exception
    {
        this.manager.createMetric("periodic").withRolloverSchedule(NIGHTLY).create();
        this.rolloverScheduler.activate();

        try (ResourceResolver resolver = open()) {
            resolver.delete(resolver.getResource("/Metrics/periodic"));
            resolver.commit();
        }
        this.rolloverScheduler.onChange(List.of());

        Mockito.verify(this.scheduler).unschedule("cards-metrics-rollover-periodic");
        Mockito.verify(this.scheduler, Mockito.times(1)).schedule(Mockito.any(), Mockito.any());
    }

    @Test
    public void deactivationUnschedulesEverything()
    {
        this.manager.createMetric("nightly").withRolloverSchedule(NIGHTLY).create();
        this.manager.createMetric("noon").withRolloverSchedule(NOON).create();
        this.rolloverScheduler.activate();

        this.rolloverScheduler.deactivate();

        Mockito.verify(this.scheduler).unschedule("cards-metrics-rollover-nightly");
        Mockito.verify(this.scheduler).unschedule("cards-metrics-rollover-noon");
    }

    @Test
    public void failedSchedulingIsRetriedOnTheNextChange()
    {
        Mockito.when(this.scheduler.schedule(Mockito.any(), Mockito.any())).thenReturn(false, true);
        this.manager.createMetric("nightly").withRolloverSchedule(NIGHTLY).create();

        this.rolloverScheduler.activate();
        this.rolloverScheduler.onChange(List.of());

        Mockito.verify(this.scheduler, Mockito.times(2)).schedule(Mockito.any(), Mockito.any());
        // Once successfully scheduled, further events don't reschedule it
        this.rolloverScheduler.onChange(List.of());
        Mockito.verify(this.scheduler, Mockito.times(2)).schedule(Mockito.any(), Mockito.any());
    }

    @Test
    public void schedulingFailuresAreNotPropagated()
    {
        Mockito.when(this.scheduler.schedule(Mockito.any(), Mockito.any()))
            .thenThrow(new IllegalArgumentException("Expression cannot be parsed"));
        this.manager.createMetric("broken").withRolloverSchedule("not a cron expression").create();

        this.rolloverScheduler.activate();
    }

    @Test
    public void unreadableRepositoriesAreNotFatal() throws Exception
    {
        final ResourceResolverFactory broken = Mockito.mock(ResourceResolverFactory.class);
        Mockito.when(broken.getServiceResourceResolver(Mockito.anyMap()))
            .thenThrow(new LoginException("no service user"));
        inject(this.rolloverScheduler, "resolverFactory", broken);

        this.rolloverScheduler.activate();
        Mockito.verifyNoInteractions(this.scheduler);
    }

    @Test
    public void theJobRollsTheMetricOver() throws Exception
    {
        this.manager.createMetric("counted").withRolloverSchedule(NIGHTLY).create();
        try (ResourceResolver resolver = open()) {
            resolver.getResource("/Metrics/counted").adaptTo(
                org.apache.sling.api.resource.ModifiableValueMap.class).put("oak:counter", 7L);
            resolver.commit();
        }
        final ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        this.rolloverScheduler.activate();
        Mockito.verify(this.scheduler).schedule(job.capture(), Mockito.any());

        job.getValue().run();

        assertEquals(7L, this.manager.getMetric("counted").orElseThrow().getPreviousValue());
        assertEquals(7L, this.manager.getMetric("counted").orElseThrow().getLastDelta());
        assertTrue(this.manager.getMetric("counted").orElseThrow().getLastRollover() != null);
    }

    @Test
    public void theJobSurvivesADeletedMetric() throws Exception
    {
        this.manager.createMetric("counted").withRolloverSchedule(NIGHTLY).create();
        final ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        this.rolloverScheduler.activate();
        Mockito.verify(this.scheduler).schedule(job.capture(), Mockito.any());

        try (ResourceResolver resolver = open()) {
            resolver.delete(resolver.getResource("/Metrics/counted"));
            resolver.commit();
        }

        job.getValue().run();
    }

    @Test
    public void theJobSurvivesRollOverFailures() throws Exception
    {
        this.manager.createMetric("counted").withRolloverSchedule(NIGHTLY).create();
        final ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        this.rolloverScheduler.activate();
        Mockito.verify(this.scheduler).schedule(job.capture(), Mockito.any());

        // The repository becomes unreachable only after the job has been scheduled
        final ResourceResolverFactory broken = Mockito.mock(ResourceResolverFactory.class);
        Mockito.when(broken.getServiceResourceResolver(Mockito.anyMap()))
            .thenThrow(new LoginException("repository unavailable"));
        inject(this.rolloverScheduler, "resolverFactory", broken);

        job.getValue().run();
    }

    private ResourceResolver open() throws Exception
    {
        return this.factory.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "metrics"));
    }

    private void inject(final Object target, final String fieldName, final Object value) throws Exception
    {
        final Field reference = target.getClass().getDeclaredField(fieldName);
        reference.setAccessible(true);
        reference.set(target, value);
    }
}
