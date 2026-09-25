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

import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.metrics.api.Metric;
import io.uhndata.cards.metrics.api.MetricsException;
import io.uhndata.cards.utils.DateUtils;

/**
 * Default implementation of {@link Metric}: a lightweight handle over a metric node. A handle obtained by name opens
 * a short-lived service session for each read, so its values are always current and it can be held for as long as
 * its owner lives; one obtained from a listing answers reads from the properties the listing already read, so that
 * rendering a page of metrics costs a single session. Updates always go to the repository either way: increments go
 * through its atomic counter support, and updates that hit a conflicting concurrent change are retried on a fresh
 * session a few times before giving up.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class MetricImpl implements Metric
{
    /** The absolute path of the node holding all the metrics. */
    static final String METRICS_PATH = "/Metrics";

    /** The node type of a metric node. */
    static final String NODE_TYPE = "cards:Metric";

    /** The name of the subservice performing all repository access. */
    static final String SUBSERVICE = "metrics";

    /** The name of the property holding the human-readable name. */
    static final String PN_LABEL = "label";

    /** The name of the property holding the description. */
    static final String PN_DESCRIPTION = "description";

    /** The name of the property holding the category. */
    static final String PN_CATEGORY = "category";

    /** The name of the property holding the placement within the category, spelled as everywhere in the platform. */
    static final String PN_DEFAULT_ORDER = "cards:defaultOrder";

    /** The name of the property holding the access level. */
    static final String PN_ACCESS_LEVEL = "accessLevel";

    /** The name of the property holding the baseline for delta computations. */
    static final String PN_PREVIOUS_VALUE = "previousValue";

    /** The name of the property holding the delta frozen at the last roll-over. */
    static final String PN_LAST_DELTA = "lastDelta";

    /** The name of the property holding the last increment date. */
    static final String PN_LAST_UPDATED = "lastUpdated";

    /** The name of the property holding the last roll-over date. */
    static final String PN_LAST_ROLLOVER = "lastRollover";

    /** The name of the property holding the Quartz cron expression for automatic roll-overs. */
    static final String PN_ROLLOVER_SCHEDULE = "rolloverSchedule";

    /** The name of the protected property in which the repository maintains the current count. */
    static final String PN_COUNTER = "oak:counter";

    /** The name of the transient property consumed by the repository when an increment is committed. */
    static final String PN_INCREMENT = "oak:increment";

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricImpl.class);

    /** How many times an update is attempted before giving up. */
    private static final int MAX_ATTEMPTS = 3;

    private final ResourceResolverFactory resolverFactory;

    private final String name;

    /** The properties this handle reads from, or {@code null} when each read goes to the repository instead. */
    private final ValueMap snapshot;

    /**
     * Basic constructor, for a handle that reads the repository on every access.
     *
     * @param resolverFactory used to open a service session for each operation
     * @param name the identifier of the metric
     */
    MetricImpl(final ResourceResolverFactory resolverFactory, final String name)
    {
        this.resolverFactory = resolverFactory;
        this.name = name;
        this.snapshot = null;
    }

    /**
     * Constructor for a handle whose reads are answered from properties that have already been read, so that listing
     * many metrics costs one session instead of one per metric per property.
     *
     * @param resolverFactory used to open a service session for updates, which never use the snapshot
     * @param name the identifier of the metric
     * @param properties the properties of the metric node, copied since the session they were read in is closed by
     *            the time this handle is used
     */
    MetricImpl(final ResourceResolverFactory resolverFactory, final String name, final ValueMap properties)
    {
        this.resolverFactory = resolverFactory;
        this.name = name;
        this.snapshot = new ValueMapDecorator(Collections.unmodifiableMap(new HashMap<>(properties)));
    }

    /**
     * Check that a resource is an actual metric node, and not something else that happens to be under
     * {@code /Metrics}, like an access control policy.
     *
     * @param resource a resource to check, may be {@code null}
     * @return {@code true} if the resource is a metric node
     */
    static boolean isMetric(final Resource resource)
    {
        return resource != null && NODE_TYPE.equals(resource.getValueMap().get("jcr:primaryType", String.class));
    }

    /**
     * Open a new session as the metrics service user.
     *
     * @param resolverFactory the resource resolver factory service
     * @return a service resource resolver, must be closed by the caller
     * @throws MetricsException if the service session cannot be opened
     */
    static ResourceResolver openServiceResolver(final ResourceResolverFactory resolverFactory)
    {
        try {
            return resolverFactory.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, SUBSERVICE));
        } catch (final LoginException e) {
            throw new MetricsException("Failed to access the repository: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName()
    {
        return this.name;
    }

    @Override
    public String getLabel()
    {
        return read(properties -> properties.get(PN_LABEL, this.name));
    }

    @Override
    public String getDescription()
    {
        return read(properties -> properties.get(PN_DESCRIPTION, String.class));
    }

    @Override
    public String getCategory()
    {
        return read(MetricImpl::category);
    }

    @Override
    public long getDefaultOrder()
    {
        return read(MetricImpl::defaultOrder);
    }

    @Override
    public AccessLevel getAccessLevel()
    {
        return read(properties -> AccessLevel.fromPropertyValue(properties.get(PN_ACCESS_LEVEL, String.class)));
    }

    @Override
    public long getCurrentValue()
    {
        return read(MetricImpl::currentValue);
    }

    @Override
    public long getPreviousValue()
    {
        return read(MetricImpl::previousValue);
    }

    @Override
    public long getCurrentDelta()
    {
        return read(MetricImpl::currentDelta);
    }

    @Override
    public long getLastDelta()
    {
        return read(properties -> properties.get(PN_LAST_DELTA, 0L));
    }

    @Override
    public ZonedDateTime getLastUpdated()
    {
        return read(properties -> toDateTime(properties.get(PN_LAST_UPDATED, Calendar.class)));
    }

    @Override
    public ZonedDateTime getLastRollover()
    {
        return read(properties -> toDateTime(properties.get(PN_LAST_ROLLOVER, Calendar.class)));
    }

    @Override
    public String getRolloverSchedule()
    {
        return read(properties -> properties.get(PN_ROLLOVER_SCHEDULE, String.class));
    }

    @Override
    public void increment()
    {
        increment(1);
    }

    @Override
    public void increment(final long amount)
    {
        if (amount == 0) {
            return;
        }
        try {
            write(properties -> {
                properties.put(PN_INCREMENT, amount);
                properties.put(PN_LAST_UPDATED, Calendar.getInstance());
            });
        } catch (final RuntimeException e) {
            // Losing a count is better than breaking the operation being counted, whatever went wrong
            LOGGER.error("Failed to increment metric {}: {}", this.name, e.getMessage(), e);
        }
    }

    @Override
    public void rollOver()
    {
        write(properties -> {
            properties.put(PN_LAST_DELTA, currentDelta(properties));
            properties.put(PN_PREVIOUS_VALUE, currentValue(properties));
            properties.put(PN_LAST_ROLLOVER, Calendar.getInstance());
        });
    }

    @Override
    public JsonObject toJson()
    {
        return read(properties -> {
            final JsonObjectBuilder json = Json.createObjectBuilder()
                .add("name", this.name)
                .add("label", properties.get(PN_LABEL, this.name))
                .add("description", properties.get(PN_DESCRIPTION, ""))
                .add("category", Objects.requireNonNullElse(category(properties), ""))
                .add("defaultOrder", defaultOrder(properties))
                .add("accessLevel",
                    AccessLevel.fromPropertyValue(properties.get(PN_ACCESS_LEVEL, String.class)).asPropertyValue())
                .add("currentValue", currentValue(properties))
                .add("previousValue", previousValue(properties))
                .add("currentDelta", currentDelta(properties))
                .add("lastDelta", properties.get(PN_LAST_DELTA, 0L));
            addDate(json, PN_LAST_UPDATED, properties.get(PN_LAST_UPDATED, Calendar.class));
            addDate(json, PN_LAST_ROLLOVER, properties.get(PN_LAST_ROLLOVER, Calendar.class));
            final String schedule = properties.get(PN_ROLLOVER_SCHEDULE, String.class);
            if (schedule != null) {
                json.add(PN_ROLLOVER_SCHEDULE, schedule);
            }
            return json.build();
        });
    }

    /**
     * Read something from the current state of the metric node.
     *
     * @param <T> the type of data extracted
     * @param extractor computes the result from the current properties of the metric node
     * @return the extracted data
     * @throws MetricsException if the metric node cannot be accessed
     */
    private <T> T read(final Function<ValueMap, T> extractor)
    {
        if (this.snapshot != null) {
            return extractor.apply(this.snapshot);
        }
        try (ResourceResolver resolver = openServiceResolver(this.resolverFactory)) {
            return extractor.apply(getMetricResource(resolver).getValueMap());
        }
    }

    /**
     * Apply changes to the metric node and commit them, retrying on a fresh session when a conflicting concurrent
     * change prevents the commit.
     *
     * @param changes the changes to apply
     * @throws MetricsException if the changes cannot be persisted even after retrying
     */
    private void write(final Consumer<ModifiableValueMap> changes)
    {
        for (int attempt = 1;; ++attempt) {
            try (ResourceResolver resolver = openServiceResolver(this.resolverFactory)) {
                final ModifiableValueMap properties = getMetricResource(resolver).adaptTo(ModifiableValueMap.class);
                if (properties == null) {
                    throw new MetricsException("The metric " + this.name + " cannot be modified");
                }
                changes.accept(properties);
                resolver.commit();
                return;
            } catch (final PersistenceException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new MetricsException(
                        "Failed to update metric " + this.name + ": " + e.getMessage(), e);
                }
                LOGGER.debug("Retrying the update of metric {} after a concurrent change", this.name);
            }
        }
    }

    /**
     * Fetch the node backing this metric.
     *
     * @param resolver the session to read through
     * @return the metric resource
     * @throws MetricsException if the metric node no longer exists
     */
    private Resource getMetricResource(final ResourceResolver resolver)
    {
        final Resource resource = resolver.getResource(METRICS_PATH + "/" + this.name);
        if (!isMetric(resource)) {
            throw new MetricsException("The metric " + this.name + " no longer exists");
        }
        return resource;
    }

    private static long currentValue(final ValueMap properties)
    {
        return properties.get(PN_COUNTER, 0L);
    }

    private static long previousValue(final ValueMap properties)
    {
        return properties.get(PN_PREVIOUS_VALUE, 0L);
    }

    private static long defaultOrder(final ValueMap properties)
    {
        return properties.get(PN_DEFAULT_ORDER, 0L);
    }

    /**
     * The category of a metric, a blank one counting as none, so that listings, reports and the JSON all agree.
     *
     * @param properties the properties of a metric node
     * @return the category, or {@code null} if the metric is uncategorized
     */
    static String category(final ValueMap properties)
    {
        return StringUtils.trimToNull(properties.get(PN_CATEGORY, String.class));
    }

    private static long currentDelta(final ValueMap properties)
    {
        return currentValue(properties) - previousValue(properties);
    }

    private static ZonedDateTime toDateTime(final Calendar date)
    {
        return date == null ? null : date.toInstant().atZone(date.getTimeZone().toZoneId());
    }

    private static void addDate(final JsonObjectBuilder json, final String key, final Calendar date)
    {
        final String serialized = DateUtils.toString(date);
        if (serialized != null) {
            json.add(key, serialized);
        }
    }
}
