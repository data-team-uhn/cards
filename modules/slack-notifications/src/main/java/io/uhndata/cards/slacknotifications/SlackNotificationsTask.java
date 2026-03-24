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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.httprequests.HttpRequests;
import io.uhndata.cards.slacknotifications.spi.SlackNotificationProducer;

public class SlackNotificationsTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackNotificationsTask.class);

    private final String endpoint;

    private final List<String> include;

    private final String title;

    private Map<String, String> extraParameters;

    /** A list of all available notification producers. */
    private final List<SlackNotificationProducer> notifications;

    public SlackNotificationsTask(final Configuration config, final List<SlackNotificationProducer> notifications)
    {
        this.notifications = notifications;
        this.title = config.title();
        this.endpoint = config.endpoint();
        this.include = (config.include() == null ? Collections.emptyList() : List.of(config.include()));
        this.extraParameters = new HashMap<>();
        if (config.notificationParameters() != null) {
            for (var param : config.notificationParameters()) {
                String[] keyval = param.split("=", 2);
                if (keyval.length == 2) {
                    this.extraParameters.put(keyval[0], keyval[1]);
                }
            }
        }
    }

    @Override
    public void run()
    {
        LOGGER.debug("Running SlackNotificationsTask");
        List<List<JsonObject>> result = this.notifications.stream()
            .filter(n -> this.include.isEmpty() || this.include.contains(n.getName()))
            .map(n -> n.prepareMessages(this.extraParameters))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        LOGGER.debug("Got these results: {}", result);
        postToSlack(result);
        LOGGER.debug("Done SlackNotificationsTask");
    }

    private void postToSlack(List<List<JsonObject>> messages)
    {
        try {
            JsonObjectBuilder slackApiReq = Json.createObjectBuilder();
            JsonArrayBuilder attachments = Json.createArrayBuilder();
            messages.forEach(innerList -> innerList.forEach(attachments::add));

            if (StringUtils.isNotBlank(this.title)) {
                slackApiReq.add("text", "test");
            }
            slackApiReq.add("attachments", attachments);
            HttpRequests.getPostResponse(this.endpoint, slackApiReq.build().toString(), "application/json");
        } catch (IOException e) {
            LOGGER.warn("Failed to send performance update to Slack");
        }
    }
}
