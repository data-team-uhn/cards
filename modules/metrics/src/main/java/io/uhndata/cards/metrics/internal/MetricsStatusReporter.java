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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.metrics.Metrics;
import io.uhndata.cards.status.spi.StatusReport;
import io.uhndata.cards.status.spi.StatusReporter;

@Component(immediate = true)
public class MetricsStatusReporter implements StatusReporter
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsStatusReporter.class);

    private static final String TITLE = "Metrics";

    private static final String LABEL_TODAY = "today";

    private static final String LABEL_TOTAL = "total";

    /** Provides access to resources. */
    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Override
    public String getName()
    {
        return "Metrics";
    }

    @Override
    public StatusReport report(boolean unprivileged)
    {
        Map<String, Map<String, Long>> gatheredStatistics = getMetrics();

        // Build the notification update string to be sent to Slack
        StringBuilder slackNotificationString = new StringBuilder();
        gatheredStatistics.keySet().forEach(key -> buildNotificationLine(
            slackNotificationString,
            key.replaceAll("^\\{\\d+\\}", ""),
            gatheredStatistics.get(key)));
        if (slackNotificationString.length() == 0) {
            return new StatusReport(TITLE, StatusReport.Status.WARNING, "*WARNING*: Could not gather any metrics");
        }
        return new StatusReport(TITLE, StatusReport.Status.INFO, slackNotificationString.toString());
    }

    private Map<String, Map<String, Long>> getMetrics()
    {
        LOGGER.debug("Gathering metrics for the Slack notification");
        Map<String, Map<String, Long>> gatheredStatistics = new TreeMap<>();
        final Map<String, Object> params = Map.of(ResourceResolverFactory.SUBSERVICE, "MetricLogger");
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(params)) {

            // Get all the sling:Folder nodes under /Metrics/
            Iterator<Resource> metricsIter = resolver.findResources(
                "SELECT n.* FROM [sling:Folder] AS n WHERE isdescendantnode(n, '/Metrics')",
                "JCR-SQL2");

            while (metricsIter.hasNext()) {
                Resource thisResource = metricsIter.next();
                String thisJcrName = thisResource.getName();
                String thisHumanName = Metrics.getHumanName(resolver, thisJcrName);
                if (thisHumanName == null) {
                    continue;
                }
                Map<String, Long> thisMetricValue = Metrics.getAndReset(resolver, thisJcrName);
                if (thisMetricValue == null) {
                    continue;
                }
                gatheredStatistics.merge(thisHumanName, thisMetricValue, this::mergeStats);
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to create service session: {}", e, e.getMessage());
        }
        return gatheredStatistics;
    }

    private Map<String, Long> mergeStats(Map<String, Long> oldStats, Map<String, Long> newStats)
    {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, Long> newValue : newStats.entrySet()) {
            result.put(newValue.getKey(), oldStats.get(newValue.getKey()) + newValue.getValue());
        }
        return result;
    }

    private void buildNotificationLine(final StringBuilder result, final String name, final Map<String, Long> statMap)
    {
        if (result.length() > 0) {
            result.append("\n");
        }
        result
            .append("*")
            .append(name)
            .append("*")
            .append(" -- _Today_: ")
            .append(statMap.get(LABEL_TODAY))
            .append(", _Total_: ")
            .append(statMap.get(LABEL_TOTAL));
    }

    @Override
    public Set<String> getTags()
    {
        return Set.of("activity", "metrics");
    }
}
