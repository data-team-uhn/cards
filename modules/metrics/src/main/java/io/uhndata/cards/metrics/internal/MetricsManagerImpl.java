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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.metrics.api.Metric;
import io.uhndata.cards.metrics.api.MetricsException;
import io.uhndata.cards.metrics.api.MetricsManager;

/**
 * Default implementation of {@link MetricsManager}, storing each metric as a child node of {@code /Metrics}. All
 * repository access happens through a dedicated service user, so callers don't need repository access of their own.
 * Metrics still stored the way older versions did are converted whenever they are touched, and in one sweep started
 * at activation; activation itself does nothing that could make the components waiting for this service time out.
 *
 * @version $Id$
 * @since 0.9.42
 */
@Component(immediate = true)
public class MetricsManagerImpl implements MetricsManager
{
    /** Characters that cannot appear in a node name. */
    private static final Pattern INVALID_NAME_CHARACTERS = Pattern.compile("[/:\\[\\]|*]");

    /** How many times an operation that hit a conflicting concurrent change is attempted before giving up. */
    private static final int MAX_ATTEMPTS = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsManagerImpl.class);

    /**
     * The display order of the metrics: by category, uncategorized last, then by {@code defaultOrder} within
     * each category, and by name for metrics that agree on both, so the listing is stable. This compares the
     * resources rather than {@link Metric} handles, because every handle read opens a service session of its own,
     * while the resources are already in the one session the listing has open.
     */
    private static final Comparator<Resource> DISPLAY_ORDER =
        Comparator.comparing(MetricsManagerImpl::category, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(MetricsManagerImpl::defaultOrder)
            .thenComparing(Resource::getName);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private Scheduler scheduler;

    @Activate
    protected void activate()
    {
        try {
            final ScheduleOptions options = this.scheduler.NOW();
            options.name("cards-metrics-conversion");
            options.canRunConcurrently(false);
            this.scheduler.schedule((Runnable) () -> LegacyMetrics.convertAll(this.resolverFactory), options);
        } catch (final RuntimeException e) {
            // Metrics touched later are converted then; failing the activation would take down every component
            // that counts something, which is far worse than an old metric staying unconverted for a while
            LOGGER.error("Failed to start converting the metrics stored in the old layout: {}", e.getMessage(), e);
        }
    }

    @Override
    public MetricBuilder createMetric(final String name)
    {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid metric name: " + name + "; expecting a valid node name");
        }
        return new MetricBuilderImpl(name);
    }

    @Override
    public Optional<Metric> getMetric(final String name)
    {
        if (!isValidName(name)) {
            return Optional.empty();
        }
        return withRetries(name, resolver -> {
            final Resource resource = resolver.getResource(MetricImpl.METRICS_PATH + "/" + name);
            if (resource != null && LegacyMetrics.ensureCurrent(resource)) {
                commit(resolver);
            }
            return MetricImpl.isMetric(resolver.getResource(MetricImpl.METRICS_PATH + "/" + name))
                ? Optional.of(new MetricImpl(this.resolverFactory, name))
                : Optional.empty();
        });
    }

    @Override
    public void increment(final String name, final long amount)
    {
        if (amount == 0) {
            return;
        }
        try {
            // The handle's increment never throws either
            getMetric(name).ifPresent(metric -> metric.increment(amount));
        } catch (final RuntimeException e) {
            // Losing a count is better than breaking the operation being counted, whatever went wrong
            LOGGER.error("Failed to increment metric {}: {}", name, e.getMessage(), e);
        }
    }

    @Override
    public List<Metric> getMetrics()
    {
        try (ResourceResolver resolver = MetricImpl.openServiceResolver(this.resolverFactory)) {
            return StreamSupport.stream(getHomepage(resolver).getChildren().spliterator(), false)
                .filter(MetricImpl::isMetric)
                .sorted(DISPLAY_ORDER)
                // Hand the properties to the handle: this session has already read them, and a listing is a
                // point-in-time view anyway, so re-reading each one per accessor would only cost sessions
                .<Metric>map(metric -> new MetricImpl(this.resolverFactory, metric.getName(), metric.getValueMap()))
                .toList();
        }
    }

    /**
     * Check that a name can identify a metric: usable as a single node name, which names that configurations have
     * always used are. The name must not be padded with whitespace either, since that would make two different
     * nodes look like the same metric.
     *
     * @param name the name to check, may be {@code null}
     * @return {@code true} if the name is valid
     */
    private static boolean isValidName(final String name)
    {
        if (name == null || name.isEmpty() || !name.equals(name.strip())) {
            return false;
        }
        return !".".equals(name) && !"..".equals(name) && !INVALID_NAME_CHARACTERS.matcher(name).find();
    }

    /**
     * Run a repository operation on a fresh service session, retrying when a conflicting concurrent change prevents
     * its commit - in particular when an old metric is being converted by the startup sweep at the same time.
     *
     * @param <T> the type of the operation's result
     * @param name the metric the operation is about, for the error message
     * @param operation the operation, which throws a {@link ConflictException} to be retried
     * @return the result of the operation
     * @throws MetricsException if the operation cannot complete even after retrying
     */
    private <T> T withRetries(final String name, final Function<ResourceResolver, T> operation)
    {
        for (int attempt = 1;; ++attempt) {
            try (ResourceResolver resolver = MetricImpl.openServiceResolver(this.resolverFactory)) {
                return operation.apply(resolver);
            } catch (final ConflictException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new MetricsException("Failed to update metric " + name + ": " + e.getMessage(), e.getCause());
                }
                LOGGER.debug("Retrying an operation on metric {} after a concurrent change", name);
            }
        }
    }

    private static void commit(final ResourceResolver resolver)
    {
        try {
            resolver.commit();
        } catch (final PersistenceException e) {
            throw new ConflictException(e);
        }
    }

    /**
     * The category a metric node belongs to, for ordering purposes.
     *
     * @param metric a metric resource
     * @return the category, or {@code null} when the metric is uncategorized, which sorts it last
     */
    private static String category(final Resource metric)
    {
        return MetricImpl.category(metric.getValueMap());
    }

    /**
     * The requested placement of a metric node within its category.
     *
     * @param metric a metric resource
     * @return the order, {@code 0} for a metric that doesn't declare one
     */
    private static long defaultOrder(final Resource metric)
    {
        return metric.getValueMap().get(MetricImpl.PN_DEFAULT_ORDER, 0L);
    }

    /**
     * Fetch the node holding all the metrics.
     *
     * @param resolver the session to read through
     * @return the {@code /Metrics} resource
     * @throws MetricsException if the node is missing, meaning the repository wasn't properly initialized
     */
    private Resource getHomepage(final ResourceResolver resolver)
    {
        final Resource homepage = resolver.getResource(MetricImpl.METRICS_PATH);
        if (homepage == null) {
            throw new MetricsException("The metrics homepage is missing from the repository");
        }
        return homepage;
    }

    /**
     * Default implementation of {@link MetricBuilder}, persisting the definition under {@code /Metrics}.
     *
     * @since 0.9.42
     */
    private final class MetricBuilderImpl implements MetricBuilder
    {
        private final String name;

        private String label;

        private String description;

        private String category;

        private long defaultOrder;

        private Metric.AccessLevel accessLevel = Metric.AccessLevel.PUBLIC;

        private String rolloverSchedule;

        MetricBuilderImpl(final String name)
        {
            this.name = name;
        }

        @Override
        public MetricBuilder withLabel(final String label)
        {
            this.label = label;
            return this;
        }

        @Override
        public MetricBuilder withNumberedLabel(final String numberedLabel)
        {
            final NumberedLabel parsed = NumberedLabel.parse(numberedLabel);
            this.label = parsed.getLabel();
            if (parsed.getOrder() != null) {
                this.defaultOrder = parsed.getOrder();
            }
            return this;
        }

        @Override
        public MetricBuilder withDescription(final String description)
        {
            this.description = description;
            return this;
        }

        @Override
        public MetricBuilder withCategory(final String category)
        {
            this.category = category;
            return this;
        }

        @Override
        public MetricBuilder withDefaultOrder(final long defaultOrder)
        {
            this.defaultOrder = defaultOrder;
            return this;
        }

        @Override
        public MetricBuilder withAccessLevel(final Metric.AccessLevel accessLevel)
        {
            this.accessLevel = accessLevel;
            return this;
        }

        @Override
        public MetricBuilder withRolloverSchedule(final String rolloverSchedule)
        {
            this.rolloverSchedule = rolloverSchedule;
            return this;
        }

        @Override
        public Metric create()
        {
            return withRetries(this.name, resolver -> {
                final Resource homepage = getHomepage(resolver);
                final Resource existing = homepage.getChild(this.name);
                try {
                    if (existing == null) {
                        resolver.create(homepage, this.name, buildProperties());
                    } else {
                        // A metric stored the old way is converted first, keeping its count, then redefined
                        LegacyMetrics.ensureCurrent(existing);
                        final Resource current = resolver.getResource(existing.getPath());
                        updateMetadata(current == null ? existing : current);
                    }
                } catch (final PersistenceException e) {
                    throw new MetricsException("Failed to create metric " + this.name + ": " + e.getMessage(), e);
                }
                commit(resolver);
                return new MetricImpl(MetricsManagerImpl.this.resolverFactory, this.name);
            });
        }

        private Map<String, Object> buildProperties()
        {
            final Map<String, Object> properties = new HashMap<>();
            properties.put("jcr:primaryType", MetricImpl.NODE_TYPE);
            // The repository only maintains the counter when the mixin is explicitly listed on the node
            properties.put("jcr:mixinTypes", new String[] { "mix:atomicCounter" });
            // The protected sling:resourceType property is autocreated by the node type, it cannot be set manually
            properties.put(MetricImpl.PN_LABEL, this.label == null ? this.name : this.label);
            properties.put(MetricImpl.PN_DEFAULT_ORDER, this.defaultOrder);
            properties.put(MetricImpl.PN_ACCESS_LEVEL, this.accessLevel.asPropertyValue());
            properties.put(MetricImpl.PN_PREVIOUS_VALUE, 0L);
            if (this.description != null) {
                properties.put(MetricImpl.PN_DESCRIPTION, this.description);
            }
            if (this.category != null) {
                properties.put(MetricImpl.PN_CATEGORY, this.category);
            }
            if (this.rolloverSchedule != null) {
                properties.put(MetricImpl.PN_ROLLOVER_SCHEDULE, this.rolloverSchedule);
            }
            return properties;
        }

        private void updateMetadata(final Resource existing)
        {
            if (!MetricImpl.isMetric(existing)) {
                throw new MetricsException(
                    "The path " + existing.getPath() + " is already used by something that is not a metric");
            }
            final ModifiableValueMap properties = existing.adaptTo(ModifiableValueMap.class);
            if (properties == null) {
                throw new MetricsException("The metric " + this.name + " cannot be modified");
            }
            properties.put(MetricImpl.PN_LABEL, this.label == null ? this.name : this.label);
            properties.put(MetricImpl.PN_DEFAULT_ORDER, this.defaultOrder);
            properties.put(MetricImpl.PN_ACCESS_LEVEL, this.accessLevel.asPropertyValue());
            setOrRemove(properties, MetricImpl.PN_DESCRIPTION, this.description);
            setOrRemove(properties, MetricImpl.PN_CATEGORY, this.category);
            setOrRemove(properties, MetricImpl.PN_ROLLOVER_SCHEDULE, this.rolloverSchedule);
        }

        private void setOrRemove(final ModifiableValueMap properties, final String key, final String value)
        {
            if (value == null) {
                properties.remove(key);
            } else {
                properties.put(key, value);
            }
        }
    }

    /**
     * A commit failed, most likely because of a conflicting concurrent change, so the operation is worth retrying
     * on a fresh session.
     *
     * @since 0.9.42
     */
    private static final class ConflictException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        ConflictException(final PersistenceException cause)
        {
            super(cause.getMessage(), cause);
        }
    }
}
