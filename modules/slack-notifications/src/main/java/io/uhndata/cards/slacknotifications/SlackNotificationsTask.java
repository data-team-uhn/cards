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

package io.uhndata.cards.slacknotifications;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.httprequests.HttpRequests;
import io.uhndata.cards.metrics.Metrics;

public class SlackNotificationsTask implements Runnable
{
    private static final String SLACK_PERFORMANCE_URL = System.getenv("SLACK_PERFORMANCE_URL");

    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackNotificationsTask.class);
    private static final String LABEL_TODAY = "today";
    private static final String LABEL_TOTAL = "total";

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    SlackNotificationsTask(final ResourceResolverFactory resolverFactory)
    {
        this.resolverFactory = resolverFactory;
    }

    private String buildNotificationLine(String prevValue, Map<String, Long> statMap, String name)
    {
        String notificationLine = prevValue;
        if (notificationLine.length() > 0) {
            notificationLine += "\n";
        }
        notificationLine += "*";
        notificationLine += name;
        notificationLine += "*";
        notificationLine += " -- _Today_: ";
        notificationLine += statMap.get(LABEL_TODAY);
        notificationLine += ", _Total_: ";
        notificationLine += statMap.get(LABEL_TOTAL);
        return notificationLine;
    }

    private void postToSlack(String slackUrl, String msg)
    {
        try {
            JsonObject slackApiReq = Json.createObjectBuilder()
                .add("text", msg)
                .build();
            HttpRequests.getPostResponse(slackUrl, slackApiReq.toString(), "application/json");
        } catch (IOException e) {
            LOGGER.warn("Failed to send performance update to Slack");
        }
    }

    @Override
    public void run()
    {
        LOGGER.debug("Running SlackNotificationsTask");
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(ResourceResolverFactory.SUBSERVICE, "SlackNotifications");
            ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(params);

            Map<String, Map<String, Long>> gatheredStatistics = new TreeMap<>();

            // Get all the sling:Folder nodes under /Metrics/
            Iterator<Resource> metricsIter;
            metricsIter = resolver.findResources(
                "SELECT n.* FROM [sling:Folder] AS n WHERE isdescendantnode(n, '/Metrics')",
                "JCR-SQL2"
            );

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
                gatheredStatistics.put(thisHumanName, thisMetricValue);
            }

            resolver.close();

            // Build the notification update string to be sent to Slack
            String slackNotificationString = "";
            for (String key : gatheredStatistics.keySet())
            {
                slackNotificationString = buildNotificationLine(
                    slackNotificationString,
                    gatheredStatistics.get(key),
                    key.replaceAll("^\\{\\d+\\}", "")
                );
            }

            if (slackNotificationString.length() == 0) {
                slackNotificationString = "*ERROR*: Could not gather any performance statistics";
            }
            postToSlack(SLACK_PERFORMANCE_URL, slackNotificationString);

        } catch (LoginException e) {
            LOGGER.warn("Failed to results.next().getPath()");
        }
    }
}
