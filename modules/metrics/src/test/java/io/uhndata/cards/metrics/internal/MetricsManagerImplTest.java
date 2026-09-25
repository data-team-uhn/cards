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

import javax.jcr.Node;
import javax.jcr.nodetype.NodeType;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.uhndata.cards.metrics.api.Metric;
import io.uhndata.cards.metrics.api.MetricsException;
import io.uhndata.cards.metrics.api.MetricsManager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link MetricsManagerImpl}.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class MetricsManagerImplTest
{
    @Rule
    public SlingContext context = new SlingContext();

    private MetricsManagerImpl manager;

    private ResourceResolverFactory factory;

    @Before
    public void setUp() throws Exception
    {
        this.factory = this.context.getService(ResourceResolverFactory.class);
        this.manager = new MetricsManagerImpl();
        inject(this.manager, this.factory);
        try (ResourceResolver resolver = open()) {
            resolver.create(resolver.getResource("/"), "Metrics",
                Map.of("sling:resourceType", "cards/MetricsHomepage"));
            resolver.commit();
        }
    }

    @Test
    public void createsMetricWithDefaults() throws Exception
    {
        final Metric metric = this.manager.createMetric("simple").create();

        assertEquals("simple", metric.getName());
        final ValueMap properties = properties("simple");
        assertEquals("cards:Metric", properties.get("jcr:primaryType", String.class));
        assertArrayEquals(new String[] { "mix:atomicCounter" }, properties.get("jcr:mixinTypes", String[].class));
        // The protected sling:resourceType property is not set by the code, it is autocreated by the node type
        assertNull(properties.get("sling:resourceType", String.class));
        assertEquals("simple", properties.get("label", String.class));
        assertEquals(Long.valueOf(0L), properties.get("cards:defaultOrder", Long.class));
        assertEquals("public", properties.get("accessLevel", String.class));
        assertEquals(Long.valueOf(0L), properties.get("previousValue", Long.class));
        assertNull(properties.get("description", String.class));
        assertNull(properties.get("category", String.class));
        assertNull(properties.get("rolloverSchedule", String.class));
    }

    @Test
    public void createsMetricWithFullMetadata() throws Exception
    {
        this.manager.createMetric("full")
            .withLabel("Full metric")
            .withDescription("Counts many things")
            .withCategory("Tests")
            .withDefaultOrder(20)
            .withAccessLevel(Metric.AccessLevel.ADMIN)
            .withRolloverSchedule("0 0 0 * * ?")
            .create();

        final ValueMap properties = properties("full");
        assertEquals("Full metric", properties.get("label", String.class));
        assertEquals("Counts many things", properties.get("description", String.class));
        assertEquals("Tests", properties.get("category", String.class));
        assertEquals(Long.valueOf(20L), properties.get("cards:defaultOrder", Long.class));
        assertEquals("admin", properties.get("accessLevel", String.class));
        assertEquals("0 0 0 * * ?", properties.get("rolloverSchedule", String.class));
    }

    @Test
    public void recreatingUpdatesMetadataAndKeepsTheCounter() throws Exception
    {
        this.manager.createMetric("counted")
            .withLabel("Old label")
            .withDescription("Old description")
            .withCategory("Old category")
            .withRolloverSchedule("0 0 0 * * ?")
            .create();
        // Simulate some activity on the counter
        try (ResourceResolver resolver = open()) {
            final ModifiableValueMap properties =
                resolver.getResource("/Metrics/counted").adaptTo(ModifiableValueMap.class);
            properties.put("oak:counter", 42L);
            properties.put("previousValue", 30L);
            resolver.commit();
        }

        this.manager.createMetric("counted")
            .withLabel("New label")
            .withAccessLevel(Metric.AccessLevel.ADMIN)
            .create();

        final ValueMap properties = properties("counted");
        assertEquals("New label", properties.get("label", String.class));
        assertEquals("admin", properties.get("accessLevel", String.class));
        // Metadata not repeated on the new definition is removed
        assertNull(properties.get("description", String.class));
        assertNull(properties.get("category", String.class));
        assertNull(properties.get("rolloverSchedule", String.class));
        // The counter state is left untouched
        assertEquals(Long.valueOf(42L), properties.get("oak:counter", Long.class));
        assertEquals(Long.valueOf(30L), properties.get("previousValue", Long.class));

        // And metadata provided again on a later definition is set back
        this.manager.createMetric("counted")
            .withDescription("Newer description")
            .withCategory("Newer category")
            .withRolloverSchedule("0 0 12 * * ?")
            .create();
        final ValueMap updated = properties("counted");
        assertEquals("Newer description", updated.get("description", String.class));
        assertEquals("Newer category", updated.get("category", String.class));
        assertEquals("0 0 12 * * ?", updated.get("rolloverSchedule", String.class));
    }

    @Test
    public void recreatingWithoutLabelRestoresTheDefaultLabel() throws Exception
    {
        this.manager.createMetric("plain").withLabel("Fancy").create();
        this.manager.createMetric("plain").create();
        assertEquals("plain", properties("plain").get("label", String.class));
    }

    @Test
    public void rejectsNamesThatCannotBeNodeNames()
    {
        for (final String name : new String[] { null, "", " ", ".", "..", "../escape", "a/b", "ns:name", "[0]",
            "a|b", "a*", " padded", "padded " }) {
            assertThrows(String.valueOf(name), IllegalArgumentException.class, () -> this.manager.createMetric(name));
            assertTrue(String.valueOf(name), this.manager.getMetric(name).isEmpty());
        }
    }

    @Test
    public void acceptsTheNamesConfigurationsUse()
    {
        for (final String name : new String[] { "UHN-IP-InitialInvitationsTask", "ImportedAppointments", "has spaces",
            "-leadingDash", "with.dot" }) {
            this.manager.createMetric(name).create();
            assertTrue(name, this.manager.getMetric(name).isPresent());
        }
    }

    @Test
    public void refusesToOverwriteNonMetricNodes() throws Exception
    {
        try (ResourceResolver resolver = open()) {
            resolver.create(resolver.getResource("/Metrics"), "policy",
                Map.of("jcr:primaryType", "nt:unstructured"));
            resolver.commit();
        }
        final MetricsManager.MetricBuilder builder = this.manager.createMetric("policy");
        assertThrows(MetricsException.class, builder::create);
    }

    @Test
    public void createFailsWithoutTheHomepage() throws Exception
    {
        deleteHomepage();
        final MetricsManager.MetricBuilder builder = this.manager.createMetric("orphan");
        assertThrows(MetricsException.class, builder::create);
    }

    @Test
    public void getMetricFindsExistingMetrics()
    {
        this.manager.createMetric("known").create();
        assertTrue(this.manager.getMetric("known").isPresent());
        assertEquals("known", this.manager.getMetric("known").get().getName());
    }

    @Test
    public void getMetricIsEmptyForUnknownOrInvalidNames() throws Exception
    {
        assertFalse(this.manager.getMetric("unknown").isPresent());
        assertFalse(this.manager.getMetric(null).isPresent());
        assertFalse(this.manager.getMetric("../Metrics").isPresent());
        // A child of /Metrics which is not an actual metric is not returned either
        try (ResourceResolver resolver = open()) {
            resolver.create(resolver.getResource("/Metrics"), "other",
                Map.of("jcr:primaryType", "nt:unstructured"));
            resolver.commit();
        }
        assertFalse(this.manager.getMetric("other").isPresent());
    }

    @Test
    public void getMetricsFallsBackToTheNameWhenNothingElseDiffers() throws Exception
    {
        this.manager.createMetric("charlie").create();
        this.manager.createMetric("alpha").create();
        this.manager.createMetric("bravo").create();
        // Non-metric children are not listed
        try (ResourceResolver resolver = open()) {
            resolver.create(resolver.getResource("/Metrics"), "other",
                Map.of("jcr:primaryType", "nt:unstructured"));
            resolver.commit();
        }

        final List<Metric> metrics = this.manager.getMetrics();
        assertEquals(List.of("alpha", "bravo", "charlie"), metrics.stream().map(Metric::getName).toList());
    }

    @Test
    public void getMetricsGroupsByCategoryAndListsUncategorizedLast() throws Exception
    {
        this.manager.createMetric("loose").create();
        this.manager.createMetric("submitted").withCategory("Submissions").create();
        this.manager.createMetric("failed").withCategory("Problems").create();
        this.manager.createMetric("approved").withCategory("Submissions").create();

        assertEquals(List.of("failed", "approved", "submitted", "loose"), names());
    }

    @Test
    public void getMetricsOrdersByDefaultOrderWithinACategory() throws Exception
    {
        this.manager.createMetric("third").withCategory("Submissions").withDefaultOrder(30).create();
        this.manager.createMetric("first").withCategory("Submissions").withDefaultOrder(10).create();
        this.manager.createMetric("second").withCategory("Submissions").withDefaultOrder(20).create();
        // A negative order is allowed, and puts the metric ahead of the ones that declare none
        this.manager.createMetric("zeroth").withCategory("Submissions").withDefaultOrder(-10).create();
        this.manager.createMetric("unordered").withCategory("Submissions").create();

        assertEquals(List.of("zeroth", "unordered", "first", "second", "third"), names());
    }

    @Test
    public void getMetricsTreatsABlankCategoryAsUncategorized() throws Exception
    {
        this.manager.createMetric("blank").withCategory("   ").create();
        this.manager.createMetric("categorized").withCategory("Submissions").create();

        assertEquals(List.of("categorized", "blank"), names());
    }

    @Test
    public void getMetricsOrdersCategoriesAheadOfTheDefaultOrder() throws Exception
    {
        // The category wins over the order: a low order in a later category still comes second
        this.manager.createMetric("early").withCategory("Alpha").withDefaultOrder(90).create();
        this.manager.createMetric("late").withCategory("Beta").withDefaultOrder(10).create();

        assertEquals(List.of("early", "late"), names());
    }

    @Test
    public void getMetricsFailsWithoutTheHomepage() throws Exception
    {
        deleteHomepage();
        assertThrows(MetricsException.class, () -> this.manager.getMetrics());
    }

    @Test
    public void failsWhenTheServiceUserIsNotSetUp() throws Exception
    {
        final ResourceResolverFactory broken = Mockito.mock(ResourceResolverFactory.class);
        Mockito.when(broken.getServiceResourceResolver(Mockito.anyMap()))
            .thenThrow(new LoginException("no service user"));
        inject(this.manager, broken);

        assertThrows(MetricsException.class, () -> this.manager.getMetrics());
        assertThrows(MetricsException.class, () -> this.manager.getMetric("any"));
        final MetricsManager.MetricBuilder builder = this.manager.createMetric("any");
        assertThrows(MetricsException.class, builder::create);
    }

    @Test
    public void createWrapsCommitFailures() throws Exception
    {
        final ResourceResolver resolver = mockedResolverWithHomepage();
        Mockito.doThrow(new PersistenceException("conflict")).when(resolver).commit();

        final MetricsManager.MetricBuilder builder = this.manager.createMetric("doomed");
        final MetricsException exception = assertThrows(MetricsException.class, builder::create);
        assertTrue(exception.getMessage().contains("doomed"));
        // A conflicting change is worth retrying, a few times
        Mockito.verify(resolver, Mockito.times(3)).commit();
    }

    @Test
    public void createWrapsRefusedNodes() throws Exception
    {
        final ResourceResolver resolver = mockedResolverWithHomepage();
        Mockito.when(resolver.create(Mockito.any(), Mockito.anyString(), Mockito.anyMap()))
            .thenThrow(new PersistenceException("refused"));

        final MetricsManager.MetricBuilder builder = this.manager.createMetric("refused");
        final MetricsException exception = assertThrows(MetricsException.class, builder::create);
        assertTrue(exception.getMessage().contains("refused"));
        // Not a conflict, so there is no point in trying again
        Mockito.verify(resolver).create(Mockito.any(), Mockito.anyString(), Mockito.anyMap());
    }

    @Test
    public void createRetriesAfterAConflictingChange() throws Exception
    {
        final ResourceResolver resolver = mockedResolverWithHomepage();
        Mockito.doThrow(new PersistenceException("conflict")).doNothing().when(resolver).commit();

        final Metric metric = this.manager.createMetric("contended").create();

        assertEquals("contended", metric.getName());
        Mockito.verify(resolver, Mockito.times(2)).commit();
    }

    @Test
    public void getMetricConvertsAnOldMetricItTouches() throws Exception
    {
        final ResourceResolver resolver = mockedResolverWithHomepage();
        final Node node = oldMetric("old");
        final Resource old = Mockito.mock(Resource.class);
        Mockito.when(old.adaptTo(Node.class)).thenReturn(node);
        final Resource converted = Mockito.mock(Resource.class);
        Mockito.when(converted.getValueMap())
            .thenReturn(new ValueMapDecorator(Map.of("jcr:primaryType", "cards:Metric")));
        Mockito.when(resolver.getResource("/Metrics/old")).thenReturn(old, converted);

        assertTrue(this.manager.getMetric("old").isPresent());
        Mockito.verify(node).setPrimaryType("cards:Metric");
        Mockito.verify(resolver).commit();
    }

    @Test
    public void incrementsADefinedMetricByName() throws Exception
    {
        this.manager.createMetric("counted").create();

        this.manager.increment("counted", 5);

        assertEquals(Long.valueOf(5L), properties("counted").get("oak:increment", Long.class));
    }

    @Test
    public void incrementByNameCountsNothingForUnknownNamesOrNoAmount() throws Exception
    {
        this.manager.createMetric("counted").create();

        this.manager.increment("missing", 3);
        this.manager.increment(null, 3);
        this.manager.increment("../escape", 3);
        this.manager.increment("counted", 0);

        assertNull(properties("counted").get("oak:increment", Long.class));
        try (ResourceResolver resolver = open()) {
            assertNull(resolver.getResource("/Metrics/missing"));
        }
    }

    @Test
    public void incrementByNameNeverThrows() throws Exception
    {
        final ResourceResolverFactory broken = Mockito.mock(ResourceResolverFactory.class);
        Mockito.when(broken.getServiceResourceResolver(Mockito.anyMap()))
            .thenThrow(new LoginException("no service user"));
        inject(this.manager, broken);

        this.manager.increment("any", 1);

        Mockito.verify(broken).getServiceResourceResolver(Mockito.anyMap());
    }

    @Test
    public void numberedLabelsSetTheLabelAndTheOrder() throws Exception
    {
        this.manager.createMetric("numbered").withNumberedLabel("{007} Initial Emails Sent").create();
        this.manager.createMetric("plain").withDefaultOrder(5).withNumberedLabel("Plain label").create();
        this.manager.createMetric("unlabeled").withNumberedLabel(null).create();

        assertEquals("Initial Emails Sent", properties("numbered").get("label", String.class));
        assertEquals(Long.valueOf(7L), properties("numbered").get("cards:defaultOrder", Long.class));
        assertEquals("Plain label", properties("plain").get("label", String.class));
        assertEquals(Long.valueOf(5L), properties("plain").get("cards:defaultOrder", Long.class));
        assertEquals("unlabeled", properties("unlabeled").get("label", String.class));
    }

    @Test
    public void activationOnlyStartsTheConversion() throws Exception
    {
        final Scheduler scheduler = Mockito.mock(Scheduler.class);
        final ScheduleOptions options = Mockito.mock(ScheduleOptions.class);
        Mockito.when(scheduler.NOW()).thenReturn(options);
        injectScheduler(scheduler);

        this.manager.activate();

        final ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(scheduler).schedule(job.capture(), Mockito.eq(options));
        Mockito.verify(options).name("cards-metrics-conversion");
        Mockito.verify(options).canRunConcurrently(false);
        // The job itself finds nothing to convert without a JCR repository, and fails on nothing either
        job.getValue().run();
    }

    @Test
    public void activationNeverFails() throws Exception
    {
        final Scheduler scheduler = Mockito.mock(Scheduler.class);
        Mockito.when(scheduler.NOW()).thenThrow(new IllegalStateException("scheduler stopped"));
        injectScheduler(scheduler);

        this.manager.activate();

        Mockito.verify(scheduler).NOW();
    }

    @Test
    public void createFailsOnUnmodifiableMetrics() throws Exception
    {
        final ResourceResolver resolver = mockedResolverWithHomepage();
        final Resource existing = Mockito.mock(Resource.class);
        Mockito.when(existing.getValueMap())
            .thenReturn(new ValueMapDecorator(Map.of("jcr:primaryType", "cards:Metric")));
        Mockito.when(existing.adaptTo(ModifiableValueMap.class)).thenReturn(null);
        Mockito.when(resolver.getResource("/Metrics").getChild("stuck")).thenReturn(existing);

        final MetricsManager.MetricBuilder builder = this.manager.createMetric("stuck");
        assertThrows(MetricsException.class, builder::create);
    }

    private ResourceResolver open() throws Exception
    {
        return this.factory.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "metrics"));
    }

    private List<String> names()
    {
        return this.manager.getMetrics().stream().map(Metric::getName).toList();
    }

    private ValueMap properties(final String name) throws Exception
    {
        try (ResourceResolver resolver = open()) {
            return resolver.getResource("/Metrics/" + name).getValueMap();
        }
    }

    private void deleteHomepage() throws Exception
    {
        try (ResourceResolver resolver = open()) {
            resolver.delete(resolver.getResource("/Metrics"));
            resolver.commit();
        }
    }

    private ResourceResolver mockedResolverWithHomepage() throws Exception
    {
        final ResourceResolverFactory mockedFactory = Mockito.mock(ResourceResolverFactory.class);
        final ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        final Resource homepage = Mockito.mock(Resource.class);
        Mockito.when(mockedFactory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resolver);
        Mockito.when(resolver.getResource("/Metrics")).thenReturn(homepage);
        inject(this.manager, mockedFactory);
        return resolver;
    }

    private void inject(final MetricsManagerImpl target, final ResourceResolverFactory value) throws Exception
    {
        final Field reference = MetricsManagerImpl.class.getDeclaredField("resolverFactory");
        reference.setAccessible(true);
        reference.set(target, value);
    }

    private void injectScheduler(final Scheduler scheduler) throws Exception
    {
        final Field reference = MetricsManagerImpl.class.getDeclaredField("scheduler");
        reference.setAccessible(true);
        reference.set(this.manager, scheduler);
    }

    /** A metric stored the way older versions did, reduced to what tells it apart. */
    private static Node oldMetric(final String name) throws Exception
    {
        final NodeType folder = Mockito.mock(NodeType.class);
        Mockito.when(folder.getName()).thenReturn("sling:Folder");
        final NodeType atomicCounter = Mockito.mock(NodeType.class);
        Mockito.when(atomicCounter.getName()).thenReturn("mix:atomicCounter");
        final Node counter = Mockito.mock(Node.class);
        Mockito.when(counter.getMixinNodeTypes()).thenReturn(new NodeType[] { atomicCounter });
        final Node node = Mockito.mock(Node.class);
        Mockito.when(node.getName()).thenReturn(name);
        Mockito.when(node.getPrimaryNodeType()).thenReturn(folder);
        Mockito.when(node.getMixinNodeTypes()).thenReturn(new NodeType[0]);
        Mockito.when(node.hasNode("total")).thenReturn(true);
        Mockito.when(node.getNode("total")).thenReturn(counter);
        return node;
    }
}
