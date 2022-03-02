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

package io.uhndata.cards.proms.slacknotifications;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.performancenotifications.PerformanceUtils;

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

    private static String readInputStream(InputStream stream) throws IOException
    {
        final BufferedReader br = new BufferedReader(new InputStreamReader(stream, "utf-8"));
        String responseLine = null;
        final StringBuilder retVal = new StringBuilder();
        while ((responseLine = br.readLine()) != null) {
            retVal.append(responseLine.trim());
        }
        return retVal.toString();
    }

    String getPostResponse(final String url, final String data, final String token) throws IOException
    {
        final URLConnection con = new URL(url).openConnection();
        final HttpURLConnection http = (HttpURLConnection) con;

        http.setRequestMethod("POST");
        http.setRequestProperty("Content-Type", "application/json");
        if (!"".equals(token)) {
            http.setRequestProperty("Authorization", token);
        }
        http.setDoOutput(true);

        // Write our POST data to the server
        try (OutputStream os = con.getOutputStream()) {
            final byte[] input = data.getBytes("utf-8");
            os.write(input, 0, input.length);
        } catch (IOException e) {
            // If we can obtain a better error message from the POST result, log it
            InputStream errorStream = http.getErrorStream();
            if (errorStream != null) {
                throw new IOException("Error during POST: " + SlackNotificationsTask.readInputStream(errorStream));
            } else {
                throw e;
            }
        }

        return SlackNotificationsTask.readInputStream(con.getInputStream());
    }

    private void postToSlack(String slackUrl, String msg)
    {
        try {
            JsonObject slackApiReq = Json.createObjectBuilder()
                .add("text", msg)
                .build();
            String slackResp = getPostResponse(slackUrl, slackApiReq.toString(), "");
            LOGGER.warn("Performance update Slack response: {}", slackResp);
        } catch (IOException e) {
            LOGGER.warn("Failed to send performance update to Slack");
        }
    }

    @Override
    public void run()
    {
        LOGGER.warn("Running SlackNotificationsTask");
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(ResourceResolverFactory.SUBSERVICE, "SlackNotifications");
            ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(params);

            Map<String, Long> initialEmailsSent =
                PerformanceUtils.getAndSetPreviousPerformanceStatistic(resolver, "InitialEmailsSent");
            Map<String, Long> reminderEmailsSent =
                PerformanceUtils.getAndSetPreviousPerformanceStatistic(resolver, "ReminderEmailsSent");
            Map<String, Long> totalSurveysSubmitted =
                PerformanceUtils.getAndSetPreviousPerformanceStatistic(resolver, "TotalSurveysSubmitted");
            Map<String, Long> appointmentSurveysSubmitted =
                PerformanceUtils.getAndSetPreviousPerformanceStatistic(resolver, "AppointmentSurveysSubmitted");
            Map<String, Long> importedAppointments =
                PerformanceUtils.getAndSetPreviousPerformanceStatistic(resolver, "ImportedAppointments");

            resolver.close();

            String slackNotificationString = "";
            if (initialEmailsSent != null)
            {
                slackNotificationString = buildNotificationLine(
                    slackNotificationString,
                    initialEmailsSent,
                    "Initial Emails Sent"
                );
            } else {
                LOGGER.warn("Couldn't get #Initial Emails Sent");
            }

            if (reminderEmailsSent != null)
            {
                slackNotificationString = buildNotificationLine(
                    slackNotificationString,
                    reminderEmailsSent,
                    "Reminder Emails Sent"
                );
            } else {
                LOGGER.warn("Couldn't get #Reminder Emails Sent");
            }

            if (totalSurveysSubmitted != null)
            {
                slackNotificationString = buildNotificationLine(
                    slackNotificationString,
                    totalSurveysSubmitted,
                    "Total Surveys Submitted"
                );
            } else {
                LOGGER.warn("Couldn't get #Total Surveys Submitted");
            }

            if (appointmentSurveysSubmitted != null)
            {
                slackNotificationString = buildNotificationLine(
                    slackNotificationString,
                    appointmentSurveysSubmitted,
                    "Appointment Surveys Submitted"
                );
            } else {
                LOGGER.warn("Couldn't get #Appointment Surveys Submitted");
            }

            if (importedAppointments != null)
            {
                slackNotificationString = buildNotificationLine(
                    slackNotificationString,
                    importedAppointments,
                    "Imported Appointments"
                );
            } else {
                LOGGER.warn("Couldn't get #Imported Appointments");
            }
            if (slackNotificationString.length() == 0) {
                slackNotificationString = "*ERROR*: Could not gather any performance statistics";
            }
            LOGGER.warn("Sending Slack Notification: {}", slackNotificationString);
            postToSlack(SLACK_PERFORMANCE_URL, slackNotificationString);

        } catch (LoginException e) {
            LOGGER.warn("Failed to results.next().getPath()");
        }
    }
}
