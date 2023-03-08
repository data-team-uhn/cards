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

package io.uhndata.cards.metrics;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Metrics
{
    private static final String LABEL_TODAY = "today";
    private static final String LABEL_TOTAL = "total";
    private static final String METRICS_PATH = "/Metrics/";
    private static final String PROP_VALUE = "value";

    private static final Logger LOGGER = LoggerFactory.getLogger(Metrics.class);

    // Hide the utility class constructor
    private Metrics()
    {
    }

    /**
     * Gets the 'today' and 'total' values for a performance statistic.
     * Returns null if the statistic cannot be found.
     *
     * @param resolver a ResourceResolver used to query the JCR
     * @param statName the name of the performance statistic to query
     * @return the map of 'today' and 'total' values for the performance statistic or null
     */
    public static Map<String, Long> get(ResourceResolver resolver, String statName)
    {
        Map<String, Long> perfStat = new HashMap<String, Long>();
        Resource statResourcePrevTotal = resolver.getResource(METRICS_PATH + statName + "/prevTotal");
        Resource statResourceTotal = resolver.getResource(METRICS_PATH + statName + "/total");
        if (statResourcePrevTotal == null || statResourceTotal == null) {
            return null;
        }

        ValueMap statMapPrevTotal = statResourcePrevTotal.getValueMap();
        ValueMap statMapTotal = statResourceTotal.getValueMap();
        long prevTotalCount = statMapPrevTotal.get(PROP_VALUE, (long) (-1));
        long totalCount = statMapTotal.get("oak:counter", (long) (-1));
        if (prevTotalCount < 0 || totalCount < 0) {
            return null;
        }
        perfStat.put(LABEL_TODAY, totalCount - prevTotalCount);
        perfStat.put(LABEL_TOTAL, totalCount);
        return perfStat;
    }

    /**
     * Updates the value of a performance counter by incrementing it by a set amount.
     *
     * @param resolverFactory a ResourceResolverFactory that can be used for querying
     *     the JCR as the MetricLogger service user
     * @param statName the name of the performance statistic to increment
     * @param incrementValue the value to increment the performance statistic by
     */
    public static void increment(final ResourceResolverFactory resolverFactory,
        final String statName, final long incrementValue)
    {
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, "MetricLogger");
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(params)) {
            increment(resolver, statName, incrementValue);
        } catch (LoginException e) {
            return;
        }
    }

    /**
     * Creates the performance metric node and its child nodes - total, prevTotal and name in the JCR.
     * If the performance metric node already exists, no changes are made to the JCR.
     *
     * @param resolverFactory a ResourceResolverFactory that can be used for inserting nodes into the JCR under /Metrics
     * @param statName the name of this performace metric to be placed in the JCR as /Metrics/{statName}
     * @param statHumanName the human readable description of this metric to be stored as the "value" property
     *     for /Metrics/{statName}/name
     */
    public static void createStatistic(final ResourceResolverFactory resolverFactory,
        final String statName, final String statHumanName)
    {
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, "MetricLogger");
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(params)) {
            // Get the /Metrics sling:Folder JCR Resource
            Resource metricsFolderResource = resolver.getResource(METRICS_PATH);
            if (metricsFolderResource == null) {
                return;
            }

            // If /Metrics/{statName} already exists, we don't need to try (and fail) to create it
            if (metricsFolderResource.getChild(statName) != null) {
                return;
            }

            // Create the statistic sling:Folder under /Metrics and get a reference to it
            final Map<String, Object> statNodeProperties = new HashMap<>();
            statNodeProperties.put("jcr:primaryType", "sling:Folder");
            resolver.create(metricsFolderResource, statName, statNodeProperties);
            Resource thisFolderResource = resolver.getResource(METRICS_PATH + statName);
            if (thisFolderResource == null) {
                return;
            }

            // Create the /Metrics/<STAT NAME>/name JCR node
            final Map<String, Object> metricNameProperties = new HashMap<>();
            metricNameProperties.put("jcr:primaryType", "nt:unstructured");
            metricNameProperties.put(PROP_VALUE, statHumanName);
            resolver.create(thisFolderResource, "name", metricNameProperties);

            // Create the /Metrics/<STAT NAME>/prevTotal JCR node
            final Map<String, Object> prevTotalProperties = new HashMap<>();
            prevTotalProperties.put("jcr:primaryType", "nt:unstructured");
            prevTotalProperties.put(PROP_VALUE, 0);
            resolver.create(thisFolderResource, "prevTotal", prevTotalProperties);

            // Create the /Metrics/<STAT NAME>/total JCR node
            final Map<String, Object> totalProperties = new HashMap<>();
            totalProperties.put("jcr:primaryType", "nt:unstructured");
            final String[] jcrMixinTypes = {"mix:atomicCounter"};
            totalProperties.put("jcr:mixinTypes", jcrMixinTypes);
            resolver.create(thisFolderResource, "total", totalProperties);

            // Commit these changes to JCR
            resolver.commit();
        } catch (LoginException | PersistenceException e) {
            LOGGER.error("createStatistic failed for {}", statName);
            return;
        }
    }

    /**
     * Gets a Map of the "today" and "total" values for a performance statistic and
     * sets the previous read value of the performance statistic to the current read
     * of the performance statistic's total value. This function should be used for
     * the periodic generation of performance notifications as it ensures that the
     * summation of the "today" values will always equal the "total" value for a
     * given performance statistic even in cases such as when a Form is submitted
     * at the exact same moment as the performance notification is generated. If
     * the performance statistic cannot be found, null is returned.
     *
     * @param resolver a ResourceResolver for querying the /Metrics/ JCR nodes
     * @param statName the name of the performance statistic to obtain its "today" and "total" values
     * @return the map of 'today' and 'total' values for the performance statistic or null
     */
    public static Map<String, Long> getAndReset(ResourceResolver resolver, String statName)
    {
        Map<String, Long> statsMap = get(resolver, statName);
        if (statsMap == null) {
            return null;
        }
        long totalCount = statsMap.get(LABEL_TOTAL);
        try {
            Resource statResourcePrevTotal = resolver.getResource(
                METRICS_PATH + statName + "/prevTotal");
            ModifiableValueMap statMapPrevTotal = statResourcePrevTotal.adaptTo(ModifiableValueMap.class);
            statMapPrevTotal.put(PROP_VALUE, totalCount);
            resolver.commit();
        } catch (PersistenceException e) {
            return null;
        }
        return statsMap;
    }

    /**
     * Gets the human-readable name associated with a performance metric.
     *
     * @param resolver a ResourceResolver for querying the /Metrics/ JCR nodes
     * @param statName the name of the performance statistic to obtain its human-readable name
     * @return the human-readable name associated with the performance metric or null
     */
    public static String getHumanName(ResourceResolver resolver, String statName)
    {
        Resource statResourceName = resolver.getResource(METRICS_PATH + statName + "/name");
        if (statResourceName == null) {
            return null;
        }
        String humanName = statResourceName.getValueMap().get(PROP_VALUE, "");
        if ("".equals(humanName)) {
            return null;
        }
        return humanName;
    }

    private static void increment(ResourceResolver resolver, String statName, long incrementBy)
    {
        try {
            Resource statResourceTotal = resolver.getResource(METRICS_PATH + statName + "/total");
            if (statResourceTotal == null) {
                return;
            }
            ModifiableValueMap statMapTotal = statResourceTotal.adaptTo(ModifiableValueMap.class);
            statMapTotal.put("oak:increment", incrementBy);
            resolver.commit();
        } catch (PersistenceException e) {
            return;
        }
    }
}
