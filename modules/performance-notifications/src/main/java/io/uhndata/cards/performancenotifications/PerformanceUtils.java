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
    public static Map<String, Long> getPerformanceStatistic(ResourceResolver resolver, String statName)
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

    public static void updatePerformanceCounter(final ResourceResolverFactory resolverFactory,
        final String statName, final long incrementValue)
    {
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, "SlackNotifications");
        try {
            ResourceResolver resolver = resolverFactory.getServiceResourceResolver(params);
            incrementPerformanceStatistic(resolver, statName, incrementValue);
            resolver.close();
        } catch (LoginException e) {
            return;
        }
    }

    public static Map<String, Long> getAndSetPreviousPerformanceStatistic(ResourceResolver resolver, String statName)
    {
        Map<String, Long> statsMap = getPerformanceStatistic(resolver, statName);
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
