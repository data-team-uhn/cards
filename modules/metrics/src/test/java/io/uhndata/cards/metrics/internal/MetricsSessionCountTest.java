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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

import io.uhndata.cards.metrics.api.Metric;

import static org.junit.Assert.assertEquals;

/**
 * Pins down how many repository sessions the listing paths open. Each session is a service login, so a listing that
 * opens one per property read costs a multiple of the metric count on every status poll or dashboard refresh; these
 * assertions are what keeps that from creeping back.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class MetricsSessionCountTest
{
    /** How many metrics the counted listings run over; more than one, so a per-metric cost is visible. */
    private static final int METRICS = 5;

    @Rule
    public SlingContext context = new SlingContext();

    private final AtomicInteger sessions = new AtomicInteger();

    private MetricsManagerImpl manager;

    private ResourceResolverFactory counting;

    private ResourceResolverFactory real;

    @Before
    public void setUp() throws Exception
    {
        this.real = this.context.getService(ResourceResolverFactory.class);
        this.counting = Mockito.mock(ResourceResolverFactory.class, AdditionalAnswers.delegatesTo(this.real));
        Mockito.doAnswer(invocation -> {
            this.sessions.incrementAndGet();
            return this.real.getServiceResourceResolver(invocation.getArgument(0));
        }).when(this.counting).getServiceResourceResolver(Mockito.any());

        this.manager = new MetricsManagerImpl();
        inject(this.manager, "resolverFactory", this.counting);
        try (ResourceResolver resolver =
            this.real.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "metrics"))) {
            resolver.create(resolver.getResource("/"), "Metrics",
                Map.of("sling:resourceType", "cards/MetricsHomepage"));
            resolver.commit();
        }
        for (int i = 0; i < METRICS; ++i) {
            this.manager.createMetric("metric" + i).withCategory("Counted").withDefaultOrder(i).create();
        }
        this.sessions.set(0);
    }

    @Test
    public void listingTheMetricsOpensOneSession()
    {
        assertEquals(METRICS, this.manager.getMetrics().size());
        assertEquals(1, this.sessions.get());
    }

    @Test
    public void readingEveryPropertyOfAListedMetricStaysWithinTheListingSession()
    {
        this.manager.getMetrics().forEach(metric -> {
            metric.getLabel();
            metric.getDescription();
            metric.getCategory();
            metric.getDefaultOrder();
            metric.getAccessLevel();
            metric.getCurrentValue();
            metric.getPreviousValue();
            metric.getCurrentDelta();
            metric.getLastDelta();
            metric.getLastUpdated();
            metric.getLastRollover();
            metric.getRolloverSchedule();
            metric.toJson();
        });

        assertEquals(1, this.sessions.get());
    }

    @Test
    public void theStatusReportOpensOneSession() throws Exception
    {
        final MetricsStatusReporter reporter = new MetricsStatusReporter();
        inject(reporter, "metricsManager", this.manager);

        reporter.report(false);

        assertEquals(1, this.sessions.get());
    }

    @Test
    public void aSingleMetricLookupOpensOneSessionAndThenReadsLive()
    {
        final Metric metric = this.manager.getMetric("metric0").orElseThrow();
        assertEquals(1, this.sessions.get());

        // A handle asked for by name stays live: it re-reads, which is what makes it safe to hold on to
        metric.getLabel();
        metric.getCurrentValue();
        assertEquals(3, this.sessions.get());
    }

    @Test
    public void aListedMetricStillWritesToTheRepository() throws Exception
    {
        final Metric listed = this.manager.getMetrics().get(0);

        listed.increment(3);

        // Nothing consolidates the increment into the counter here, so the request itself is the evidence that the
        // write went to the repository rather than into the properties the handle reads from
        assertEquals(Long.valueOf(3L), properties(listed.getName()).get("oak:increment", Long.class));
    }

    @Test
    public void aListedMetricRollsOverAgainstLiveValuesRatherThanItsOwn() throws Exception
    {
        // Listed while the counter reads 0, so a roll-over that trusted the listed values would set that as the new
        // baseline and lose everything counted in between
        final Metric listed = this.manager.getMetrics().get(0);
        setCounter(listed.getName(), 42);

        listed.rollOver();

        assertEquals(Long.valueOf(42L), properties(listed.getName()).get("previousValue", Long.class));
        assertEquals("the listed handle keeps reporting what it was listed with", 0L,
            listed.getCurrentValue());
    }

    @Test
    public void aScheduledRollOverOpensOneSession() throws Exception
    {
        final MetricRolloverScheduler scheduler = new MetricRolloverScheduler();
        inject(scheduler, "resolverFactory", this.counting);

        scheduler.rollOver("metric0");

        assertEquals(1, this.sessions.get());
    }

    private ValueMap properties(final String metric) throws Exception
    {
        try (ResourceResolver resolver = this.real.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "metrics"))) {
            return new ValueMapDecorator(
                new HashMap<>(resolver.getResource(MetricImpl.METRICS_PATH + "/" + metric).getValueMap()));
        }
    }

    private void setCounter(final String metric, final long value) throws Exception
    {
        try (ResourceResolver resolver = this.real.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "metrics"))) {
            resolver.getResource(MetricImpl.METRICS_PATH + "/" + metric)
                .adaptTo(ModifiableValueMap.class).put("oak:counter", value);
            resolver.commit();
        }
    }

    private void inject(final Object target, final String name, final Object value) throws Exception
    {
        final Field reference = target.getClass().getDeclaredField(name);
        reference.setAccessible(true);
        reference.set(target, value);
    }
}
