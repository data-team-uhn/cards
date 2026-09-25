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

import java.util.Arrays;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.metrics.api.MetricsException;
import io.uhndata.cards.metrics.api.MetricsManager;

/**
 * Converts the metrics of an instance upgraded from a version that stored them as untyped folders - a {@code name}
 * child holding the label, a {@code prevTotal} child holding the baseline, and a {@code total} child holding the
 * counter - into {@code cards:Metric} nodes, keeping their label, order, baseline and count. Repoinit and initial
 * content never retype an existing node, so the conversion has to happen here, and it happens whenever the
 * {@link MetricsManagerImpl manager} touches an old node as well as in one sweep at startup: the components that
 * define and count metrics start before anything that could run a migration first.
 *
 * @version $Id$
 * @since 0.9.42
 */
final class LegacyMetrics
{
    /** The node type of the node holding all the metrics. */
    static final String HOMEPAGE_TYPE = "cards:MetricsHomepage";

    /** The mixin that makes the repository maintain a counter. */
    static final String ATOMIC_COUNTER = "mix:atomicCounter";

    private static final String OLD_LABEL = "name";

    private static final String OLD_BASELINE = "prevTotal";

    private static final String OLD_COUNTER = "total";

    private static final String OLD_VALUE = "value";

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyMetrics.class);

    private LegacyMetrics()
    {
        // Utility class
    }

    /**
     * Bring a metric node up to date, without saving: convert it if it is stored the old way, and give it back the
     * atomic counter mixin if it is a metric that lost it, since without the mixin the counter silently never counts.
     *
     * @param resource a child of {@code /Metrics}
     * @return {@code true} if something was changed, and must be committed
     * @throws MetricsException if the node cannot be converted
     */
    static boolean ensureCurrent(final Resource resource)
    {
        final Node node = resource.adaptTo(Node.class);
        if (node == null) {
            // Not stored in a JCR repository: nothing there can be stored the old way either
            return false;
        }
        try {
            if (isLegacy(node)) {
                convert(node);
                return true;
            }
            if (MetricImpl.NODE_TYPE.equals(node.getPrimaryNodeType().getName()) && !hasMixin(node, ATOMIC_COUNTER)) {
                node.addMixin(ATOMIC_COUNTER);
                return true;
            }
            return false;
        } catch (final RepositoryException e) {
            throw new MetricsException("Failed to convert the metric " + resource.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convert every metric still stored the old way, and give {@code /Metrics} itself its node type. Each metric is
     * saved on its own, so that one that cannot be converted doesn't hold back the others. Never throws: whatever
     * cannot be converted now is converted when next touched, or at the next start.
     *
     * @param resolverFactory used to open a session as the metrics service user
     */
    static void convertAll(final ResourceResolverFactory resolverFactory)
    {
        try (ResourceResolver resolver = MetricImpl.openServiceResolver(resolverFactory)) {
            final Session session = resolver.adaptTo(Session.class);
            if (session == null || !session.nodeExists(MetricImpl.METRICS_PATH)) {
                return;
            }
            final Node homepage = session.getNode(MetricImpl.METRICS_PATH);
            if (!HOMEPAGE_TYPE.equals(homepage.getPrimaryNodeType().getName())) {
                homepage.setPrimaryType(HOMEPAGE_TYPE);
                session.save();
            }
            int converted = 0;
            for (final NodeIterator children = homepage.getNodes(); children.hasNext();) {
                converted += convertAndSave(session, children.nextNode());
            }
            if (converted > 0) {
                LOGGER.info("Converted {} metrics stored in the old layout", converted);
            }
        } catch (final RepositoryException | RuntimeException e) {
            LOGGER.error("Failed to convert the metrics stored in the old layout: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert one metric if it is stored the old way, and save it.
     *
     * @param session the session the node belongs to
     * @param node a child of {@code /Metrics}
     * @return {@code 1} if the metric was converted, {@code 0} otherwise
     */
    private static int convertAndSave(final Session session, final Node node)
    {
        try {
            if (!isLegacy(node)) {
                return 0;
            }
            convert(node);
            session.save();
            return 1;
        } catch (final RepositoryException e) {
            // Most likely converted concurrently by the manager or by another cluster node; either way, an unsaved
            // change left in the session would make every later save fail too
            LOGGER.warn("Could not convert the metric {}: {}", safeName(node), e.getMessage());
            try {
                session.refresh(false);
            } catch (final RepositoryException ex) {
                LOGGER.warn("Could not discard the failed conversion: {}", ex.getMessage());
            }
            return 0;
        }
    }

    /**
     * Check if a node holds a metric stored the old way.
     *
     * @param node a child of {@code /Metrics}
     * @return {@code true} for an old metric
     * @throws RepositoryException if the node cannot be read
     */
    static boolean isLegacy(final Node node) throws RepositoryException
    {
        return !MetricImpl.NODE_TYPE.equals(node.getPrimaryNodeType().getName())
            && node.hasNode(OLD_COUNTER) && hasMixin(node.getNode(OLD_COUNTER), ATOMIC_COUNTER);
    }

    /**
     * Convert an old metric in place, without saving. The count moves into the node's own counter as an increment,
     * since the repository maintains the counter itself and does not allow setting it; and the old children are
     * removed in the same change, so that a second conversion racing this one fails its save instead of adding the
     * count again.
     *
     * @param node an old metric, as checked by {@link #isLegacy}
     * @throws RepositoryException if the node cannot be converted
     */
    private static void convert(final Node node) throws RepositoryException
    {
        final NumberedLabel label = NumberedLabel.parse(readValue(node, OLD_LABEL));
        final long baseline = readLongValue(node, OLD_BASELINE);
        final Node counter = node.getNode(OLD_COUNTER);
        final long count = counter.hasProperty(MetricImpl.PN_COUNTER)
            ? counter.getProperty(MetricImpl.PN_COUNTER).getLong()
            : 0;

        node.setPrimaryType(MetricImpl.NODE_TYPE);
        if (!hasMixin(node, ATOMIC_COUNTER)) {
            node.addMixin(ATOMIC_COUNTER);
        }
        node.setProperty(MetricImpl.PN_LABEL, label.getLabel() == null ? node.getName() : label.getLabel());
        if (label.getOrder() != null) {
            node.setProperty(MetricImpl.PN_DEFAULT_ORDER, label.getOrder());
        }
        node.setProperty(MetricImpl.PN_PREVIOUS_VALUE, baseline);
        node.setProperty(MetricImpl.PN_ROLLOVER_SCHEDULE, MetricsManager.END_OF_DAY);
        if (count != 0) {
            node.setProperty(MetricImpl.PN_INCREMENT, count);
        }
        for (final String child : new String[] { OLD_LABEL, OLD_BASELINE, OLD_COUNTER }) {
            if (node.hasNode(child)) {
                node.getNode(child).remove();
            }
        }
    }

    private static String readValue(final Node node, final String child) throws RepositoryException
    {
        if (node.hasNode(child) && node.getNode(child).hasProperty(OLD_VALUE)) {
            return node.getNode(child).getProperty(OLD_VALUE).getString();
        }
        return null;
    }

    private static long readLongValue(final Node node, final String child) throws RepositoryException
    {
        if (node.hasNode(child) && node.getNode(child).hasProperty(OLD_VALUE)) {
            return node.getNode(child).getProperty(OLD_VALUE).getLong();
        }
        return 0;
    }

    private static boolean hasMixin(final Node node, final String mixin) throws RepositoryException
    {
        // Not isNodeType: that also answers true for a mixin inherited from a supertype, which the repository does
        // not treat as enabling the counter
        return Arrays.stream(node.getMixinNodeTypes()).map(NodeType::getName).anyMatch(mixin::equals);
    }

    private static String safeName(final Node node)
    {
        try {
            return node.getName();
        } catch (final RepositoryException e) {
            return "?";
        }
    }
}
