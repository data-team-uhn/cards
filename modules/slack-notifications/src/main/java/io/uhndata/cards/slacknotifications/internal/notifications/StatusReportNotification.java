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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusReportNotification.class);

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
        boolean unprivileged = Boolean.valueOf(extraParameters.get("statusReport.unprivileged"));
        final List<StatusReport> reports = this.statusReportManager.getReports(unprivileged,
            getTargetStatus(extraParameters), getTags(extraParameters));
        final List<JsonObject> result = new ArrayList<>();
        for (StatusReport report : reports) {
            final JsonObjectBuilder json = Json.createObjectBuilder();
            json.add(TITLE, report.getName())
                // A report with nothing more to say than its status has no body, and a null would break the builder
                .add(TEXT, report.getText() == null ? "" : report.getText());
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

    /*
     * The lowest status level to include. A configuration naming a level that does not exist falls back to the
     * default; it used to throw, which cost the whole notification.
     *
     * @param extraParameters the configured extra parameters
     * @return a status level, INFO unless another valid one was configured
     */
    private StatusReport.Status getTargetStatus(final Map<String, String> extraParameters)
    {
        final String configured = extraParameters.get("statusReport.targetStatusLevel");
        if (configured == null) {
            return StatusReport.Status.INFO;
        }
        try {
            return StatusReport.Status.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("{} is not a known status level, reporting from INFO up instead", configured);
            return StatusReport.Status.INFO;
        }
    }

    /*
     * Which status tags to include.
     *
     * @param extraParameters the configured extra parameters
     * @return the tag names, an empty set for all of them
     */
    private Set<String> getTags(final Map<String, String> extraParameters)
    {
        final Set<String> tags = new HashSet<>();
        final String configured = extraParameters.get("statusReport.includeTags");
        if (configured != null) {
            for (String tag : configured.split(",")) {
                if (StringUtils.isNotBlank(tag)) {
                    tags.add(tag.trim());
                }
            }
        }
        return tags;
    }
}
