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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.Test;
import org.mockito.Mockito;

import io.uhndata.cards.metrics.api.MetricsException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link LegacyMetrics}. Whether the repository really moves the carried count into the counter is
 * something only a real repository can say, so it is checked against one outside the unit tests.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class LegacyMetricsTest
{
    // Node types are mocked once, up front: mocking one while stubbing a method to return it is an error
    private static final NodeType FOLDER = type("sling:Folder");

    private static final NodeType METRIC = type("cards:Metric");

    private static final NodeType HOMEPAGE = type("cards:MetricsHomepage");

    private static final NodeType ATOMIC_COUNTER = type("mix:atomicCounter");

    @Test
    public void convertsAnOldMetricKeepingItsLabelOrderBaselineAndCount() throws Exception
    {
        final Node node = legacy("ImportedAppointments", "{001} Imported Appointments", 40L, 123L);

        assertTrue(LegacyMetrics.ensureCurrent(resource(node)));

        Mockito.verify(node).setPrimaryType("cards:Metric");
        Mockito.verify(node).addMixin("mix:atomicCounter");
        Mockito.verify(node).setProperty("label", "Imported Appointments");
        Mockito.verify(node).setProperty("cards:defaultOrder", 1L);
        Mockito.verify(node).setProperty("previousValue", 40L);
        Mockito.verify(node).setProperty("rolloverSchedule", "0 59 23 * * ?");
        Mockito.verify(node).setProperty("oak:increment", 123L);
        Mockito.verify(node.getNode("name")).remove();
        Mockito.verify(node.getNode("prevTotal")).remove();
        Mockito.verify(node.getNode("total")).remove();
    }

    @Test
    public void anOldMetricWithoutLabelBaselineOrCountIsNamedAfterItself() throws Exception
    {
        final Node node = legacy("S3ExportedForms", null, null, null);

        assertTrue(LegacyMetrics.ensureCurrent(resource(node)));

        Mockito.verify(node).setProperty("label", "S3ExportedForms");
        Mockito.verify(node, Mockito.never()).setProperty(Mockito.eq("cards:defaultOrder"), Mockito.anyLong());
        Mockito.verify(node).setProperty("previousValue", 0L);
        Mockito.verify(node, Mockito.never()).setProperty(Mockito.eq("oak:increment"), Mockito.anyLong());
        Mockito.verify(node.getNode("total")).remove();
    }

    @Test
    public void anUnnumberedLabelKeepsTheDefaultOrder() throws Exception
    {
        final Node node = legacy("custom", "Plain label", 0L, 5L);

        assertTrue(LegacyMetrics.ensureCurrent(resource(node)));

        Mockito.verify(node).setProperty("label", "Plain label");
        Mockito.verify(node, Mockito.never()).setProperty(Mockito.eq("cards:defaultOrder"), Mockito.anyLong());
    }

    @Test
    public void anOldMetricAlreadyCarryingTheMixinDoesNotGetItTwice() throws Exception
    {
        final Node node = legacy("counted", "Counted", 0L, 1L);
        Mockito.when(node.getMixinNodeTypes()).thenReturn(new NodeType[] { ATOMIC_COUNTER });

        assertTrue(LegacyMetrics.ensureCurrent(resource(node)));

        Mockito.verify(node, Mockito.never()).addMixin(Mockito.anyString());
    }

    @Test
    public void givesBackTheCounterToAMetricThatLostIt() throws Exception
    {
        final Node node = metric(false);

        assertTrue(LegacyMetrics.ensureCurrent(resource(node)));

        Mockito.verify(node).addMixin("mix:atomicCounter");
    }

    @Test
    public void leavesACurrentMetricAlone() throws Exception
    {
        final Node node = metric(true);

        assertFalse(LegacyMetrics.ensureCurrent(resource(node)));

        Mockito.verify(node, Mockito.never()).addMixin(Mockito.anyString());
        Mockito.verify(node, Mockito.never()).setPrimaryType(Mockito.anyString());
    }

    @Test
    public void ignoresResourcesThatAreNotStoredInTheRepository()
    {
        assertFalse(LegacyMetrics.ensureCurrent(Mockito.mock(Resource.class)));
    }

    @Test
    public void reportsWhatCannotBeConverted() throws Exception
    {
        final Node node = Mockito.mock(Node.class);
        Mockito.when(node.getPrimaryNodeType()).thenThrow(new RepositoryException("gone"));
        final Resource resource = resource(node);
        Mockito.when(resource.getName()).thenReturn("broken");

        final MetricsException exception =
            assertThrows(MetricsException.class, () -> LegacyMetrics.ensureCurrent(resource));
        assertTrue(exception.getMessage().contains("broken"));
    }

    @Test
    public void onlyAFolderWithACounterIsAnOldMetric() throws Exception
    {
        final Node noCounter = Mockito.mock(Node.class);
        Mockito.when(noCounter.getPrimaryNodeType()).thenReturn(FOLDER);
        assertFalse(LegacyMetrics.isLegacy(noCounter));

        final Node plainTotal = Mockito.mock(Node.class);
        final Node total = Mockito.mock(Node.class);
        Mockito.when(plainTotal.getPrimaryNodeType()).thenReturn(FOLDER);
        Mockito.when(plainTotal.hasNode("total")).thenReturn(true);
        Mockito.when(plainTotal.getNode("total")).thenReturn(total);
        Mockito.when(total.getMixinNodeTypes()).thenReturn(new NodeType[0]);
        assertFalse(LegacyMetrics.isLegacy(plainTotal));

        assertFalse(LegacyMetrics.isLegacy(metric(false)));
    }

    @Test
    public void convertAllTypesTheHomepageAndConvertsEachOldMetric() throws Exception
    {
        final Session session = Mockito.mock(Session.class);
        final Node homepage = homepage(session, FOLDER);
        final Node old = legacy("old", "{002} Old", 0L, 7L);
        final Node current = metric(true);
        children(homepage, old, current);

        LegacyMetrics.convertAll(factory(session));

        Mockito.verify(homepage).setPrimaryType("cards:MetricsHomepage");
        Mockito.verify(old).setPrimaryType("cards:Metric");
        Mockito.verify(current, Mockito.never()).setPrimaryType(Mockito.anyString());
        // Once for the homepage, once for the converted metric
        Mockito.verify(session, Mockito.times(2)).save();
    }

    @Test
    public void convertAllLeavesATypedHomepageAlone() throws Exception
    {
        final Session session = Mockito.mock(Session.class);
        final Node homepage = homepage(session, HOMEPAGE);
        children(homepage, metric(true));

        LegacyMetrics.convertAll(factory(session));

        Mockito.verify(homepage, Mockito.never()).setPrimaryType(Mockito.anyString());
        Mockito.verify(session, Mockito.never()).save();
    }

    @Test
    public void convertAllKeepsGoingAfterAFailedConversion() throws Exception
    {
        final Session session = Mockito.mock(Session.class);
        final Node homepage = homepage(session, HOMEPAGE);
        final Node failing = legacy("failing", "Failing", 0L, 1L);
        Mockito.doThrow(new RepositoryException("conflict")).when(failing).setPrimaryType(Mockito.anyString());
        Mockito.when(failing.getName()).thenThrow(new RepositoryException("unreadable"));
        final Node alsoFailing = legacy("alsoFailing", "Also failing", 0L, 1L);
        Mockito.doThrow(new RepositoryException("conflict")).when(alsoFailing).setPrimaryType(Mockito.anyString());
        final Node next = legacy("next", "Next", 0L, 2L);
        children(homepage, failing, alsoFailing, next);
        // Discarding a failed change may fail too, which doesn't stop the sweep either
        Mockito.doThrow(new RepositoryException("stuck")).doNothing().when(session).refresh(false);

        LegacyMetrics.convertAll(factory(session));

        Mockito.verify(session, Mockito.times(2)).refresh(false);
        Mockito.verify(next).setPrimaryType("cards:Metric");
        Mockito.verify(session).save();
    }

    @Test
    public void convertAllDoesNothingWithoutAHomepage() throws Exception
    {
        final Session session = Mockito.mock(Session.class);

        LegacyMetrics.convertAll(factory(session));

        Mockito.verify(session, Mockito.never()).getNode(Mockito.anyString());
    }

    @Test
    public void convertAllDoesNothingOutsideTheRepository() throws Exception
    {
        final ResourceResolverFactory factory = factory(null);

        LegacyMetrics.convertAll(factory);

        Mockito.verify(factory).getServiceResourceResolver(Mockito.anyMap());
    }

    @Test
    public void convertAllNeverThrows() throws Exception
    {
        final ResourceResolverFactory noServiceUser = Mockito.mock(ResourceResolverFactory.class);
        Mockito.when(noServiceUser.getServiceResourceResolver(Mockito.anyMap()))
            .thenThrow(new LoginException("no service user"));
        LegacyMetrics.convertAll(noServiceUser);

        final Session session = Mockito.mock(Session.class);
        Mockito.when(session.nodeExists("/Metrics")).thenReturn(true);
        Mockito.when(session.getNode("/Metrics")).thenThrow(new RepositoryException("unreadable"));
        LegacyMetrics.convertAll(factory(session));
    }

    private static NodeType type(final String name)
    {
        final NodeType type = Mockito.mock(NodeType.class);
        Mockito.when(type.getName()).thenReturn(name);
        return type;
    }

    private static Resource resource(final Node node)
    {
        final Resource resource = Mockito.mock(Resource.class);
        Mockito.when(resource.adaptTo(Node.class)).thenReturn(node);
        return resource;
    }

    /** A metric stored the way older versions did. */
    private static Node legacy(final String name, final String label, final Long baseline, final Long count)
        throws RepositoryException
    {
        final Node node = Mockito.mock(Node.class);
        Mockito.when(node.getName()).thenReturn(name);
        Mockito.when(node.getPrimaryNodeType()).thenReturn(FOLDER);
        Mockito.when(node.getMixinNodeTypes()).thenReturn(new NodeType[0]);
        final Node counter = child(node, "total");
        Mockito.when(counter.getMixinNodeTypes()).thenReturn(new NodeType[] { ATOMIC_COUNTER });
        if (count != null) {
            final Property counted = Mockito.mock(Property.class);
            Mockito.when(counted.getLong()).thenReturn(count);
            Mockito.when(counter.hasProperty("oak:counter")).thenReturn(true);
            Mockito.when(counter.getProperty("oak:counter")).thenReturn(counted);
        }
        if (label != null) {
            final Property value = Mockito.mock(Property.class);
            Mockito.when(value.getString()).thenReturn(label);
            final Node labelNode = child(node, "name");
            Mockito.when(labelNode.hasProperty("value")).thenReturn(true);
            Mockito.when(labelNode.getProperty("value")).thenReturn(value);
        }
        if (baseline != null) {
            final Property value = Mockito.mock(Property.class);
            Mockito.when(value.getLong()).thenReturn(baseline);
            final Node baselineNode = child(node, "prevTotal");
            Mockito.when(baselineNode.hasProperty("value")).thenReturn(true);
            Mockito.when(baselineNode.getProperty("value")).thenReturn(value);
        }
        return node;
    }

    private static Node child(final Node parent, final String name) throws RepositoryException
    {
        final Node child = Mockito.mock(Node.class);
        Mockito.when(parent.hasNode(name)).thenReturn(true);
        Mockito.when(parent.getNode(name)).thenReturn(child);
        return child;
    }

    /** A metric stored the current way, with or without its counter mixin. */
    private static Node metric(final boolean withCounter) throws RepositoryException
    {
        final Node node = Mockito.mock(Node.class);
        Mockito.when(node.getPrimaryNodeType()).thenReturn(METRIC);
        Mockito.when(node.getMixinNodeTypes())
            .thenReturn(withCounter ? new NodeType[] { ATOMIC_COUNTER } : new NodeType[0]);
        return node;
    }

    private static Node homepage(final Session session, final NodeType type) throws RepositoryException
    {
        final Node homepage = Mockito.mock(Node.class);
        Mockito.when(homepage.getPrimaryNodeType()).thenReturn(type);
        Mockito.when(session.nodeExists("/Metrics")).thenReturn(true);
        Mockito.when(session.getNode("/Metrics")).thenReturn(homepage);
        return homepage;
    }

    private static void children(final Node parent, final Node... children) throws RepositoryException
    {
        final NodeIterator iterator = Mockito.mock(NodeIterator.class);
        final Boolean[] more = new Boolean[children.length];
        for (int i = 0; i < children.length; ++i) {
            more[i] = i + 1 < children.length;
        }
        Mockito.when(iterator.hasNext()).thenReturn(children.length > 0, more);
        if (children.length > 0) {
            final Node[] rest = new Node[children.length - 1];
            System.arraycopy(children, 1, rest, 0, rest.length);
            Mockito.when(iterator.nextNode()).thenReturn(children[0], rest);
        }
        Mockito.when(parent.getNodes()).thenReturn(iterator);
    }

    private static ResourceResolverFactory factory(final Session session) throws LoginException
    {
        final ResourceResolverFactory factory = Mockito.mock(ResourceResolverFactory.class);
        final ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        Mockito.when(factory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resolver);
        Mockito.when(resolver.adaptTo(Session.class)).thenReturn(session);
        return factory;
    }
}
