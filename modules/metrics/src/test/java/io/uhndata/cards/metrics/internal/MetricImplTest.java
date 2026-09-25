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

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import jakarta.json.JsonObject;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import io.uhndata.cards.metrics.api.Metric;
import io.uhndata.cards.metrics.api.MetricsException;
import io.uhndata.cards.utils.DateUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link MetricImpl}.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class MetricImplTest
{
    private static final String NAME = "counted";

    private static final String PATH = "/Metrics/" + NAME;

    @Rule
    public SlingContext context = new SlingContext();

    private ResourceResolverFactory factory;

    private Metric metric;

    @Before
    public void setUp() throws Exception
    {
        this.factory = this.context.getService(ResourceResolverFactory.class);
        this.metric = new MetricImpl(this.factory, NAME);
        try (ResourceResolver resolver = open()) {
            resolver.create(resolver.getResource("/"), "Metrics",
                Map.of("sling:resourceType", "cards/MetricsHomepage"));
            resolver.commit();
        }
    }

    @Test
    public void readsTheMetadata() throws Exception
    {
        final Calendar updated = calendar(2026, Calendar.JULY, 22);
        final Calendar reset = calendar(2026, Calendar.JULY, 20);
        final Map<String, Object> properties = new HashMap<>();
        properties.put("jcr:primaryType", "cards:Metric");
        properties.put("label", "Counted things");
        properties.put("description", "How many things were counted");
        properties.put("category", "Tests");
        properties.put("cards:defaultOrder", 20L);
        properties.put("accessLevel", "admin");
        properties.put("oak:counter", 5L);
        properties.put("previousValue", 2L);
        properties.put("lastDelta", 4L);
        properties.put("rolloverSchedule", "0 0 0 * * ?");
        properties.put("lastUpdated", updated);
        properties.put("lastRollover", reset);
        createMetricNode(properties);

        assertEquals(NAME, this.metric.getName());
        assertEquals("Counted things", this.metric.getLabel());
        assertEquals("How many things were counted", this.metric.getDescription());
        assertEquals("Tests", this.metric.getCategory());
        assertEquals(20L, this.metric.getDefaultOrder());
        assertEquals(Metric.AccessLevel.ADMIN, this.metric.getAccessLevel());
        assertEquals(5L, this.metric.getCurrentValue());
        assertEquals(2L, this.metric.getPreviousValue());
        assertEquals(3L, this.metric.getCurrentDelta());
        assertEquals(4L, this.metric.getLastDelta());
        assertEquals("0 0 0 * * ?", this.metric.getRolloverSchedule());
        assertEquals(updated.toInstant(), this.metric.getLastUpdated().toInstant());
        assertEquals(reset.toInstant(), this.metric.getLastRollover().toInstant());
    }

    @Test
    public void missingMetadataGetsSaneDefaults() throws Exception
    {
        createMetricNode(Map.of("jcr:primaryType", "cards:Metric"));

        assertEquals(NAME, this.metric.getLabel());
        assertNull(this.metric.getDescription());
        assertNull(this.metric.getCategory());
        assertEquals(0L, this.metric.getDefaultOrder());
        assertEquals(Metric.AccessLevel.PUBLIC, this.metric.getAccessLevel());
        assertEquals(0L, this.metric.getCurrentValue());
        assertEquals(0L, this.metric.getPreviousValue());
        assertEquals(0L, this.metric.getCurrentDelta());
        assertEquals(0L, this.metric.getLastDelta());
        assertNull(this.metric.getRolloverSchedule());
        assertNull(this.metric.getLastUpdated());
        assertNull(this.metric.getLastRollover());
    }

    @Test
    public void incrementRequestsAnAtomicIncrement() throws Exception
    {
        createMetricNode(Map.of("jcr:primaryType", "cards:Metric"));

        this.metric.increment();
        // In this test environment nothing consolidates the increment into the counter,
        // so the requested increment remains visible as a simple property
        assertEquals(Long.valueOf(1L), properties().get("oak:increment", Long.class));
        assertTrue(properties().containsKey("lastUpdated"));

        this.metric.increment(5);
        assertEquals(Long.valueOf(5L), properties().get("oak:increment", Long.class));

        // Negative adjustments are allowed
        this.metric.increment(-2);
        assertEquals(Long.valueOf(-2L), properties().get("oak:increment", Long.class));
    }

    @Test
    public void incrementingByZeroDoesNothing() throws Exception
    {
        createMetricNode(Map.of("jcr:primaryType", "cards:Metric"));

        this.metric.increment(0);

        assertNull(properties().get("oak:increment", Long.class));
        assertFalse(properties().containsKey("lastUpdated"));
    }

    @Test
    public void rollingOverClosesThePeriod() throws Exception
    {
        createMetricNode(Map.of("jcr:primaryType", "cards:Metric", "oak:counter", 7L, "previousValue", 2L));

        this.metric.rollOver();

        // The delta of the closed period is frozen, and the current value becomes the new baseline
        assertEquals(Long.valueOf(5L), properties().get("lastDelta", Long.class));
        assertEquals(Long.valueOf(7L), properties().get("previousValue", Long.class));
        assertTrue(properties().containsKey("lastRollover"));
        assertEquals(7L, this.metric.getCurrentValue());
        assertEquals(0L, this.metric.getCurrentDelta());
        assertEquals(5L, this.metric.getLastDelta());
    }

    @Test
    public void rollingOverAFreshMetricKeepsAZeroBaseline() throws Exception
    {
        createMetricNode(Map.of("jcr:primaryType", "cards:Metric"));

        this.metric.rollOver();

        assertEquals(Long.valueOf(0L), properties().get("previousValue", Long.class));
        assertEquals(Long.valueOf(0L), properties().get("lastDelta", Long.class));
        assertTrue(properties().containsKey("lastRollover"));
    }

    @Test
    public void serializesToJson() throws Exception
    {
        final Calendar updated = calendar(2026, Calendar.JULY, 22);
        final Calendar reset = calendar(2026, Calendar.JULY, 20);
        final Map<String, Object> properties = new HashMap<>();
        properties.put("jcr:primaryType", "cards:Metric");
        properties.put("label", "Counted things");
        properties.put("description", "How many things were counted");
        properties.put("category", "Tests");
        properties.put("cards:defaultOrder", 20L);
        properties.put("accessLevel", "admin");
        properties.put("oak:counter", 5L);
        properties.put("previousValue", 2L);
        properties.put("lastDelta", 4L);
        properties.put("rolloverSchedule", "0 0 0 * * ?");
        properties.put("lastUpdated", updated);
        properties.put("lastRollover", reset);
        createMetricNode(properties);

        final JsonObject json = this.metric.toJson();

        assertEquals(NAME, json.getString("name"));
        assertEquals("Counted things", json.getString("label"));
        assertEquals("How many things were counted", json.getString("description"));
        assertEquals("Tests", json.getString("category"));
        assertEquals(20, json.getJsonNumber("defaultOrder").longValue());
        assertEquals("admin", json.getString("accessLevel"));
        assertEquals(5, json.getJsonNumber("currentValue").longValue());
        assertEquals(2, json.getJsonNumber("previousValue").longValue());
        assertEquals(3, json.getJsonNumber("currentDelta").longValue());
        assertEquals(4, json.getJsonNumber("lastDelta").longValue());
        assertEquals("0 0 0 * * ?", json.getString("rolloverSchedule"));
        assertEquals(DateUtils.toString(updated), json.getString("lastUpdated"));
        assertEquals(DateUtils.toString(reset), json.getString("lastRollover"));
    }

    @Test
    public void serializesABareMetricToJson() throws Exception
    {
        createMetricNode(Map.of("jcr:primaryType", "cards:Metric"));

        final JsonObject json = this.metric.toJson();

        assertEquals(NAME, json.getString("name"));
        assertEquals(NAME, json.getString("label"));
        assertEquals("", json.getString("description"));
        assertEquals("", json.getString("category"));
        assertEquals(0, json.getJsonNumber("defaultOrder").longValue());
        assertEquals("public", json.getString("accessLevel"));
        assertEquals(0, json.getJsonNumber("currentValue").longValue());
        assertEquals(0, json.getJsonNumber("previousValue").longValue());
        assertEquals(0, json.getJsonNumber("currentDelta").longValue());
        assertEquals(0, json.getJsonNumber("lastDelta").longValue());
        assertFalse(json.containsKey("lastUpdated"));
        assertFalse(json.containsKey("lastRollover"));
        assertFalse(json.containsKey("rolloverSchedule"));
    }

    @Test
    public void failsWhenTheMetricNodeIsGone()
    {
        // The /Metrics homepage exists, but the metric node itself was never created
        assertThrows(MetricsException.class, () -> this.metric.getCurrentValue());
        assertThrows(MetricsException.class, () -> this.metric.rollOver());
    }

    @Test
    public void failsWhenTheNodeIsNotAMetric() throws Exception
    {
        createMetricNode(Map.of("jcr:primaryType", "nt:unstructured"));
        assertThrows(MetricsException.class, () -> this.metric.getCurrentValue());
    }

    @Test
    public void incrementFailuresAreNotPropagated() throws Exception
    {
        final ResourceResolverFactory broken = Mockito.mock(ResourceResolverFactory.class);
        Mockito.when(broken.getServiceResourceResolver(Mockito.anyMap()))
            .thenThrow(new LoginException("no service user"));
        final Metric unreachable = new MetricImpl(broken, NAME);

        unreachable.increment();
        // But explicit roll-overs and reads do propagate the failure
        assertThrows(MetricsException.class, unreachable::rollOver);
        assertThrows(MetricsException.class, unreachable::getCurrentValue);
    }

    @Test
    public void unexpectedIncrementFailuresAreNotPropagatedEither() throws Exception
    {
        final ResourceResolverFactory broken = Mockito.mock(ResourceResolverFactory.class);
        Mockito.when(broken.getServiceResourceResolver(Mockito.anyMap()))
            .thenThrow(new IllegalStateException("resolver factory stopped"));

        new MetricImpl(broken, NAME).increment(2);

        Mockito.verify(broken).getServiceResourceResolver(Mockito.anyMap());
    }

    @Test
    public void aBlankCategoryIsNoCategory() throws Exception
    {
        createMetricNode(Map.of("jcr:primaryType", "cards:Metric", "category", "   "));

        assertNull(this.metric.getCategory());
        assertEquals("", this.metric.toJson().getString("category"));
    }

    @Test
    public void conflictingUpdatesAreRetried() throws Exception
    {
        final ResourceResolverFactory mockedFactory = Mockito.mock(ResourceResolverFactory.class);
        final ResourceResolver conflicting = mockedResolver();
        final ResourceResolver working = mockedResolver();
        Mockito.doThrow(new PersistenceException("conflict")).when(conflicting).commit();
        Mockito.when(mockedFactory.getServiceResourceResolver(Mockito.anyMap()))
            .thenReturn(conflicting, working);

        new MetricImpl(mockedFactory, NAME).rollOver();

        Mockito.verify(conflicting).commit();
        Mockito.verify(working).commit();
    }

    @Test
    public void updatesGiveUpAfterTooManyConflicts() throws Exception
    {
        final ResourceResolverFactory mockedFactory = Mockito.mock(ResourceResolverFactory.class);
        final ResourceResolver conflicting = mockedResolver();
        Mockito.doThrow(new PersistenceException("conflict")).when(conflicting).commit();
        Mockito.when(mockedFactory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(conflicting);

        final Metric doomed = new MetricImpl(mockedFactory, NAME);
        assertThrows(MetricsException.class, doomed::rollOver);
        // Three attempts were made before giving up
        Mockito.verify(conflicting, Mockito.times(3)).commit();
    }

    @Test
    public void failsWhenTheMetricIsNotModifiable() throws Exception
    {
        final ResourceResolverFactory mockedFactory = Mockito.mock(ResourceResolverFactory.class);
        final ResourceResolver resolver = mockedResolver();
        final Resource resource = resolver.getResource(PATH);
        Mockito.when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(null);
        Mockito.when(mockedFactory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resolver);

        final Metric readOnly = new MetricImpl(mockedFactory, NAME);
        assertThrows(MetricsException.class, readOnly::rollOver);
    }

    private ResourceResolver open() throws Exception
    {
        return this.factory.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "metrics"));
    }

    private void createMetricNode(final Map<String, Object> properties) throws Exception
    {
        try (ResourceResolver resolver = open()) {
            resolver.create(resolver.getResource("/Metrics"), NAME, properties);
            resolver.commit();
        }
    }

    private ValueMap properties() throws Exception
    {
        try (ResourceResolver resolver = open()) {
            return resolver.getResource(PATH).getValueMap();
        }
    }

    private Calendar calendar(final int year, final int month, final int day)
    {
        final Calendar date = Calendar.getInstance();
        date.clear();
        date.set(year, month, day);
        return date;
    }

    private ResourceResolver mockedResolver()
    {
        final ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        final Resource resource = Mockito.mock(Resource.class);
        final ModifiableValueMap properties = Mockito.mock(ModifiableValueMap.class);
        Mockito.when(resolver.getResource(PATH)).thenReturn(resource);
        Mockito.when(resource.getValueMap())
            .thenReturn(new ValueMapDecorator(Map.of("jcr:primaryType", "cards:Metric")));
        Mockito.when(resource.adaptTo(ModifiableValueMap.class)).thenReturn(properties);
        // A mocked map returns null instead of the requested default value
        Mockito.when(properties.get("oak:counter", 0L)).thenReturn(0L);
        Mockito.when(properties.get("previousValue", 0L)).thenReturn(0L);
        return resolver;
    }
}
