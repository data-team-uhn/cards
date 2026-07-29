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
import io.uhndata.cards.httprequests.HttpResponse;
import io.uhndata.cards.slacknotifications.spi.SlackNotificationProducer;

public class SlackNotificationsTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackNotificationsTask.class);

    private final String endpoint;

    private final List<String> include;

    private final String title;

    private final boolean skipEmpty;

    private Map<String, String> extraParameters;

    /** A list of all available notification producers. */
    private final List<SlackNotificationProducer> notifications;

    public SlackNotificationsTask(final Configuration config, final List<SlackNotificationProducer> notifications)
    {
        this.notifications = notifications;
        this.title = config.title();
        this.endpoint = getEndpoint(config.endpoint());
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
        this.skipEmpty = config.skipEmpty();
    }

    @Override
    public void run()
    {
        LOGGER.debug("Running SlackNotificationsTask");
        // Flattened, because a producer with nothing to say gives back an empty list rather than nothing at all:
        // counting those as content made skipEmpty useless, posting an empty message on every run
        List<JsonObject> result = this.notifications.stream()
            .filter(n -> this.include.isEmpty() || this.include.contains(n.getName()))
            .map(n -> n.prepareMessages(this.extraParameters))
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .collect(Collectors.toList());
        LOGGER.debug("Got these results: {}", result);
        postToSlack(result);
        LOGGER.debug("Done SlackNotificationsTask");
    }

    private void postToSlack(List<JsonObject> messages)
    {
        if (messages.isEmpty() && this.skipEmpty) {
            return;
        }
        try {
            JsonObjectBuilder slackApiReq = Json.createObjectBuilder();
            JsonArrayBuilder attachments = Json.createArrayBuilder();
            if (messages.isEmpty()) {
                JsonObjectBuilder nothing = Json.createObjectBuilder();
                nothing.add(SlackNotificationProducer.TEXT, "Nothing to report")
                    .add(SlackNotificationProducer.TITLE, "All is good")
                    .add(SlackNotificationProducer.COLOR, SlackNotificationProducer.INFO);
                attachments.add(nothing);
            } else {
                messages.forEach(attachments::add);
            }

            if (StringUtils.isNotBlank(this.title)) {
                slackApiReq.add("text", this.title);
            }
            slackApiReq.add("attachments", attachments);
            final HttpResponse response =
                HttpRequests.doHttpPost(this.endpoint, slackApiReq.build().toString(), "application/json");
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                // Slack was reached and refused the message, which no exception would have told us about
                LOGGER.warn("Slack refused the notification with status {}: {}", response.getStatusCode(),
                    response.getResponsePayload());
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to post the notification to Slack: {}", e.getMessage(), e);
        }
    }

    private String getEndpoint(final String config)
    {
        String result = config;
        if (result != null && result.startsWith("%ENV%")) {
            result = System.getenv(config.substring("%ENV%".length()));
        }
        return result;
    }
}
