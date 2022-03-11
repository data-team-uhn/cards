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

package io.uhndata.cards.performancenotifications;

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

public final class PerformanceUtils
{
    private static final String LABEL_TODAY = "today";
    private static final String LABEL_TOTAL = "total";
    private static final String PERFORMANCE_STATISTICS_PATH = "/PerformanceStatistics/";

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceUtils.class);

    // Hide the utility class constructor
    private PerformanceUtils()
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
        Resource statResourcePrevTotal = resolver.getResource(PERFORMANCE_STATISTICS_PATH + statName + "/prevTotal");
        Resource statResourceTotal = resolver.getResource(PERFORMANCE_STATISTICS_PATH + statName + "/total");
        if (statResourcePrevTotal == null || statResourceTotal == null) {
            return null;
        }

        ValueMap statMapPrevTotal = statResourcePrevTotal.getValueMap();
        ValueMap statMapTotal = statResourceTotal.getValueMap();
        long prevTotalCount = statMapPrevTotal.get("value", (long) (-1));
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
     *     the JCR as the PerformanceLogger service user
     * @param statName the name of the performance statistic to increment
     * @param incrementValue the value to increment the performance statistic by
     */
    public static void increment(final ResourceResolverFactory resolverFactory,
        final String statName, final long incrementValue)
    {
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, "PerformanceLogger");
        try {
            ResourceResolver resolver = resolverFactory.getServiceResourceResolver(params);
            incrementPerformanceStatistic(resolver, statName, incrementValue);
            resolver.close();
        } catch (LoginException e) {
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
     * @param resolver a ResourceResolver for querying the /PerformanceStatistics/ JCR nodes
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
                PERFORMANCE_STATISTICS_PATH + statName + "/prevTotal");
            ModifiableValueMap statMapPrevTotal = statResourcePrevTotal.adaptTo(ModifiableValueMap.class);
            statMapPrevTotal.put("value", totalCount);
            resolver.commit();
        } catch (PersistenceException e) {
            return null;
        }
        return statsMap;
    }

    private static void incrementPerformanceStatistic(ResourceResolver resolver, String statName, long incrementBy)
    {
        try {
            Resource statResourceTotal = resolver.getResource(PERFORMANCE_STATISTICS_PATH + statName + "/total");
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
