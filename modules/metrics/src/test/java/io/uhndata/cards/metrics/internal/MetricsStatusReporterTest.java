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

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.uhndata.cards.metrics.api.Metric;
import io.uhndata.cards.metrics.api.MetricsManager;
import io.uhndata.cards.status.spi.StatusReport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link MetricsStatusReporter}.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class MetricsStatusReporterTest
{
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    /** The daily report: 9 AM in Toronto, September 25th. */
    private static final ZonedDateTime REPORT_TIME = ZonedDateTime.of(2026, 9, 25, 9, 0, 0, 0, TORONTO);

    /** The end-of-day roll-over just before the report. */
    private static final ZonedDateTime LAST_NIGHT = ZonedDateTime.of(2026, 9, 24, 23, 59, 0, 0, TORONTO);

    /** The imports' roll-over just before the report. */
    private static final ZonedDateTime THIS_MORNING = ZonedDateTime.of(2026, 9, 25, 8, 55, 0, 0, TORONTO);

    private final MetricsManager manager = Mockito.mock(MetricsManager.class);

    private MetricsStatusReporter reporter;

    @Before
    public void setUp() throws Exception
    {
        this.reporter = new MetricsStatusReporter();
        final Field reference = MetricsStatusReporter.class.getDeclaredField("metricsManager");
        reference.setAccessible(true);
        reference.set(this.reporter, this.manager);
        final Field clock = MetricsStatusReporter.class.getDeclaredField("clock");
        clock.setAccessible(true);
        clock.set(this.reporter, Clock.fixed(REPORT_TIME.toInstant(), TORONTO));
    }

    @Test
    public void identifiesItself()
    {
        assertEquals("Metrics", this.reporter.getName());
        assertEquals(Set.of("metrics", "activity"), this.reporter.getTags());
    }

    @Test
    public void nothingToReportWithoutMetrics()
    {
        final List<Metric> metrics = List.of();
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);
        assertNull(this.reporter.report(false));
    }

    @Test
    public void nothingToReportWhenAllMetricsAreRestricted()
    {
        final List<Metric> metrics = List.of(metric("Secret", null, Metric.AccessLevel.ADMIN, 5, 0, null));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);
        assertNull(this.reporter.report(true));
    }

    @Test
    public void reportsTheClosedPeriodAndTheTotal()
    {
        final List<Metric> metrics = List.of(
            metric("Imported Appointments", null, Metric.AccessLevel.PUBLIC, 12345, 42, THIS_MORNING),
            metric("Initial Emails Sent", null, Metric.AccessLevel.PUBLIC, 20318, 58, LAST_NIGHT));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        final StatusReport report = this.reporter.report(false);

        assertEquals("Metrics", report.getName());
        assertEquals(StatusReport.Status.INFO, report.getStatus());
        assertEquals("*Imported Appointments* -- _Today_: 42, _Total_: 12345\n"
            + "*Initial Emails Sent* -- _Yesterday_: 58, _Total_: 20318", report.getText());
    }

    @Test
    public void datesAPeriodThatClosedBeforeYesterday()
    {
        final List<Metric> metrics = List.of(metric("Stalled", null, Metric.AccessLevel.PUBLIC, 9, 4,
            ZonedDateTime.of(2026, 9, 20, 23, 59, 0, 0, TORONTO)));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        assertEquals("*Stalled* -- _2026-09-20_: 4, _Total_: 9", this.reporter.report(false).getText());
    }

    @Test
    public void namesTheDayInTheReportsTimeZone()
    {
        // 23:59 in Toronto is already the next day in UTC, where the roll-over was recorded
        final List<Metric> metrics = List.of(metric("Emails", null, Metric.AccessLevel.PUBLIC, 10, 3,
            LAST_NIGHT.withZoneSameInstant(ZoneId.of("UTC"))));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        assertEquals("*Emails* -- _Yesterday_: 3, _Total_: 10", this.reporter.report(false).getText());
    }

    @Test
    public void showsOnlyTheTotalBeforeTheFirstRollover()
    {
        final List<Metric> metrics = List.of(metric("New metric", null, Metric.AccessLevel.PUBLIC, 12, 0, null));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        assertEquals("*New metric* -- _Total_: 12", this.reporter.report(false).getText());
    }

    @Test
    public void addsUpTheMetricsSharingALabel()
    {
        // One label counted per clinic, split by another metric that comes in between in the display order
        final List<Metric> metrics = List.of(
            metric("IC Initial Emails Sent", null, Metric.AccessLevel.PUBLIC, 100, 10, LAST_NIGHT),
            metric("Imported Appointments", null, Metric.AccessLevel.PUBLIC, 500, 50, THIS_MORNING),
            metric("IC Initial Emails Sent", null, Metric.AccessLevel.PUBLIC, 200, 20, LAST_NIGHT),
            // Added today, so it has no closed period yet, and no count in it
            metric("IC Initial Emails Sent", null, Metric.AccessLevel.PUBLIC, 3, 0, null));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        assertEquals("*IC Initial Emails Sent* -- _Yesterday_: 30, _Total_: 303\n"
            + "*Imported Appointments* -- _Today_: 50, _Total_: 500", this.reporter.report(false).getText());
    }

    @Test
    public void addedUpMetricsWithoutAnyRolloverShowOnlyTheTotal()
    {
        final List<Metric> metrics = List.of(
            metric("Shared", null, Metric.AccessLevel.PUBLIC, 4, 0, null),
            metric("Shared", null, Metric.AccessLevel.PUBLIC, 5, 0, null));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        assertEquals("*Shared* -- _Total_: 9", this.reporter.report(false).getText());
    }

    @Test
    public void groupsMetricsByCategory()
    {
        // The manager hands them over in display order, uncategorized last; the reporter groups them without
        // reordering, so an uncategorized metric stays at the end instead of being sorted to the front.
        final List<Metric> metrics = List.of(
            metric("Errors", "Problems", Metric.AccessLevel.PUBLIC, 3, 0, null),
            metric("Submitted", "Submissions", Metric.AccessLevel.PUBLIC, 12, 4, LAST_NIGHT),
            metric("Loose metric", null, Metric.AccessLevel.PUBLIC, 1, 0, null));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        assertEquals("Problems:\n"
            + "*Errors* -- _Total_: 3\n"
            + "\n"
            + "Submissions:\n"
            + "*Submitted* -- _Yesterday_: 4, _Total_: 12\n"
            + "\n"
            + "Uncategorized:\n"
            + "*Loose metric* -- _Total_: 1", this.reporter.report(false).getText());
    }

    @Test
    public void unprivilegedReportsOnlyIncludePublicMetrics()
    {
        final List<Metric> metrics = List.of(
            metric("Visible", null, Metric.AccessLevel.PUBLIC, 4, 0, null),
            metric("Secret", null, Metric.AccessLevel.ADMIN, 5, 0, null));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        assertEquals("*Visible* -- _Total_: 4", this.reporter.report(true).getText());
    }

    @Test
    public void privilegedReportsIncludeRestrictedMetrics()
    {
        final List<Metric> metrics = List.of(
            metric("Visible", null, Metric.AccessLevel.PUBLIC, 4, 0, null),
            metric("Secret", null, Metric.AccessLevel.ADMIN, 5, 0, null));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        assertEquals("*Visible* -- _Total_: 4\n*Secret* -- _Total_: 5", this.reporter.report(false).getText());
    }

    private Metric metric(final String label, final String category, final Metric.AccessLevel accessLevel,
        final long currentValue, final long lastDelta, final ZonedDateTime lastRollover)
    {
        final Metric mocked = Mockito.mock(Metric.class);
        Mockito.when(mocked.getLabel()).thenReturn(label);
        Mockito.when(mocked.getCategory()).thenReturn(category);
        Mockito.when(mocked.getAccessLevel()).thenReturn(accessLevel);
        Mockito.when(mocked.getCurrentValue()).thenReturn(currentValue);
        Mockito.when(mocked.getLastDelta()).thenReturn(lastDelta);
        Mockito.when(mocked.getLastRollover()).thenReturn(lastRollover);
        return mocked;
    }
}
