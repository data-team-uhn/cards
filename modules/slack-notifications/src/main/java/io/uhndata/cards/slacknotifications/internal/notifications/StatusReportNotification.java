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

package io.uhndata.cards.slacknotifications.internal.notifications;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.slacknotifications.spi.SlackNotificationProducer;
import io.uhndata.cards.status.api.StatusReportManager;
import io.uhndata.cards.status.spi.StatusReport;

/**
 * Sends a status report to Slack. See the {@code cards-status-report} module for more details about status reports.
 * Each status message will be sent as a Slack attachment. The following parameters are supported:
 * <ul>
 * <li>{@code statusReport.targetStatusLevel}, the target {@link StatusReport.Status} to pass to
 * {@link StatusReportManager#getReports(boolean, io.uhndata.cards.status.spi.StatusReport.Status, Set)}, must be one of
 * the known levels; defaults to {@code INFO}</li>
 * <li>{@code statusReport.includeTags}, which status tags to include, passed to
 * {@link StatusReportManager#getReports(boolean, io.uhndata.cards.status.spi.StatusReport.Status, Set)}; defaults to
 * all tags</li>
 * <li>{@code statusReport.unprivileged}, whether the report should be generated for an unprivileged audience, passed to
 * {@link StatusReportManager#getReports(boolean, io.uhndata.cards.status.spi.StatusReport.Status, Set)}; defaults to
 * {@code false}, use {@code true} to switch to unprivileged mode</li>
 * </ul>
 *
 * @version $Id$
 * @since 0.9.38
 */
@Component(immediate = true)
public class StatusReportNotification implements SlackNotificationProducer
{
    @Reference
    private StatusReportManager statusReportManager;

    @Override
    public String getName()
    {
        return "status";
    }

    @Override
    public List<JsonObject> prepareMessages(final Map<String, String> extraParameters)
    {
        StatusReport.Status targetStatus = StatusReport.Status.INFO;
        String customTargetStatus = extraParameters.get("statusReport.targetStatusLevel");
        if (customTargetStatus != null) {
            targetStatus = StatusReport.Status.valueOf(customTargetStatus);
        }
        Set<String> tags = new HashSet<>();
        String customTags = extraParameters.get("statusReport.includeTags");
        if (customTags != null) {
            Collections.addAll(tags, customTags.split(","));
        }
        boolean unprivileged = Boolean.valueOf(extraParameters.get("statusReport.unprivileged"));
        final List<StatusReport> reports = this.statusReportManager.getReports(unprivileged, targetStatus, tags);
        final List<JsonObject> result = new ArrayList<>();
        for (StatusReport report : reports) {
            final JsonObjectBuilder json = Json.createObjectBuilder();
            json.add(TITLE, report.getName())
                .add(TEXT, report.getText());
            switch (report.getStatus()) {
                case SUCCESS:
                    json.add(COLOR, SUCCESS);
                    break;
                case WARNING:
                    json.add(COLOR, WARNING);
                    break;
                case ERROR:
                    json.add(COLOR, ERROR);
                    break;
                default:
                    json.add(COLOR, INFO);
                    break;
            }
            result.add(json.build());
        }
        return result.isEmpty() ? null : result;
    }
}
