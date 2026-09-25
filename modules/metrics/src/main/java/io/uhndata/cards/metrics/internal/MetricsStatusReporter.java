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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.metrics.api.Metric;
import io.uhndata.cards.metrics.api.MetricsManager;
import io.uhndata.cards.status.spi.StatusReport;
import io.uhndata.cards.status.spi.StatusReporter;

/**
 * Reports every metric's last closed period and total, as an {@code INFO} status report formatted for Slack. Reading
 * the metrics doesn't change them, so polling the status never influences the reported values: the periods are
 * closed by each metric's scheduled {@link Metric#rollOver roll-overs}. Metrics sharing a label are reported as one
 * line with their counts added up, since configurations routinely spread one reported number over several metrics,
 * for example one per clinic. Metrics restricted to administrators are left out of unprivileged reports.
 *
 * @version $Id$
 * @since 0.9.42
 */
@Component(immediate = true)
public class MetricsStatusReporter implements StatusReporter
{
    private static final String TITLE = "Metrics";

    /** The clock telling which day the report is for; replaced in tests. */
    private Clock clock = Clock.systemDefaultZone();

    @Reference
    private MetricsManager metricsManager;

    @Override
    public String getName()
    {
        return TITLE;
    }

    @Override
    public StatusReport report(final boolean unprivileged)
    {
        // getMetrics() already returns them in display order, so grouping into maps that keep insertion order
        // reproduces it here: categories in the order they are met, uncategorized ones last, and each label where
        // its first metric is. Sorting the keys instead would put the uncategorized group first.
        final Map<String, Map<String, List<Metric>>> categories = this.metricsManager.getMetrics().stream()
            .filter(metric -> !unprivileged || metric.getAccessLevel() == Metric.AccessLevel.PUBLIC)
            .collect(Collectors.groupingBy(
                metric -> Objects.requireNonNullElse(metric.getCategory(), ""), LinkedHashMap::new,
                Collectors.groupingBy(Metric::getLabel, LinkedHashMap::new, Collectors.toList())));
        if (categories.isEmpty()) {
            // No metrics to display, or none that may be displayed here, better say nothing than show an empty list
            return null;
        }
        final LocalDate today = LocalDate.now(this.clock);
        final List<String> lines = new ArrayList<>();
        categories.forEach((category, labels) -> {
            if (categories.size() > 1) {
                if (!lines.isEmpty()) {
                    lines.add("");
                }
                lines.add((category.isEmpty() ? "Uncategorized" : category) + ":");
            }
            labels.forEach((label, metrics) -> lines.add(buildLine(label, metrics, today)));
        });
        return new StatusReport(TITLE, StatusReport.Status.INFO, String.join("\n", lines));
    }

    @Override
    public Set<String> getTags()
    {
        return Set.of("metrics", "activity");
    }

    /**
     * Display the metrics sharing one label: the count of the last closed period, named after the day it closed on,
     * and the total. A metric that was never rolled over has no closed period and contributes nothing to it; when
     * none was, the period is left out.
     *
     * @param label the label the metrics share
     * @param metrics the metrics, at least one
     * @param today the day the report is for
     * @return a display line, e.g. {@code *Imported appointments* -- _Yesterday_: 42, _Total_: 12345}
     */
    private String buildLine(final String label, final List<Metric> metrics, final LocalDate today)
    {
        final StringBuilder line = new StringBuilder("*").append(label).append("* -- ");
        final ZonedDateTime lastRollover = metrics.stream()
            .map(Metric::getLastRollover)
            .filter(Objects::nonNull)
            .max(ZonedDateTime::compareTo)
            .orElse(null);
        if (lastRollover != null) {
            line.append('_').append(periodName(lastRollover, today)).append("_: ")
                .append(metrics.stream().mapToLong(Metric::getLastDelta).sum()).append(", ");
        }
        return line.append("_Total_: ").append(metrics.stream().mapToLong(Metric::getCurrentValue).sum()).toString();
    }

    /**
     * Name a closed period after the day it closed on, relative to the report: a period closed before this morning's
     * report is "Today", one closed last night is "Yesterday", and an older one, which means roll-overs stopped
     * happening, shows its date so that nobody mistakes it for a recent count.
     *
     * @param lastRollover when the period closed
     * @param today the day the report is for
     * @return {@code Today}, {@code Yesterday}, or a date
     */
    private String periodName(final ZonedDateTime lastRollover, final LocalDate today)
    {
        final LocalDate closed = lastRollover.withZoneSameInstant(this.clock.getZone()).toLocalDate();
        if (closed.equals(today)) {
            return "Today";
        } else if (closed.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return closed.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
