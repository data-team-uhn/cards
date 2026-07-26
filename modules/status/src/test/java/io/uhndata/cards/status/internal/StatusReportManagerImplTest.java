/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.status.internal;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.uhndata.cards.status.spi.StatusReport;
import io.uhndata.cards.status.spi.StatusReporter;

/**
 * Unit tests for {@link StatusReportManagerImpl}.
 *
 * @version $Id$
 */
public class StatusReportManagerImplTest
{
    /**
     * A simple configurable reporter.
     */
    private static final class FakeReporter implements StatusReporter
    {
        private final String name;

        private final Set<String> tags;

        private final StatusReport report;

        FakeReporter(final String name, final Set<String> tags, final StatusReport report)
        {
            this.name = name;
            this.tags = tags;
            this.report = report;
        }

        @Override
        public String getName()
        {
            return this.name;
        }

        @Override
        public Set<String> getTags()
        {
            return this.tags;
        }

        @Override
        public StatusReport report(final boolean unprivileged)
        {
            return this.report;
        }
    }

    private final StatusReportManagerImpl manager = new StatusReportManagerImpl();

    private final StatusReporter uptime = new FakeReporter("Uptime", Set.of("status"),
        new StatusReport("Uptime", StatusReport.Status.INFO, "All day long"));

    private final StatusReporter problems = new FakeReporter("Problems", Set.of("problems"),
        new StatusReport("Problems", StatusReport.Status.WARNING, "Something is off"));

    private final StatusReporter chatter = new FakeReporter("Chatter", Set.of("status"),
        new StatusReport("Chatter", StatusReport.Status.DEBUG, "Noise"));

    private final StatusReporter silent = new FakeReporter("Silent", Set.of("status"), null);

    private final StatusReporter broken = new StatusReporter()
    {
        @Override
        public String getName()
        {
            return "Broken";
        }

        @Override
        public Set<String> getTags()
        {
            return Set.of("problems");
        }

        @Override
        public StatusReport report(final boolean unprivileged)
        {
            throw new IllegalStateException("boom");
        }
    };

    @Before
    public void setup() throws ReflectiveOperationException
    {
        inject(List.of(this.uptime, this.problems, this.chatter, this.silent));
    }

    @Test
    public void collectsReportsAboveTheTargetLevel()
    {
        // The default level is INFO: the DEBUG report is excluded, and so is the silent reporter
        Assert.assertEquals(List.of("Uptime", "Problems"),
            this.manager.getReports(true).stream().map(StatusReport::getName).toList());
        // Explicitly asking for DEBUG reports includes everything
        Assert.assertEquals(3, this.manager.getReports(true, StatusReport.Status.DEBUG, null).size());
        // A higher target level filters more
        Assert.assertEquals(List.of("Problems"),
            this.manager.getReports(true, StatusReport.Status.WARNING, null).stream()
                .map(StatusReport::getName).toList());
    }

    @Test
    public void filtersByTags()
    {
        Assert.assertEquals(List.of("Problems"),
            this.manager.getReports(true, StatusReport.Status.INFO, Set.of("problems")).stream()
                .map(StatusReport::getName).toList());
        // Null or empty tags include all reporters
        Assert.assertEquals(2, this.manager.getReports(true, StatusReport.Status.INFO, null).size());
        Assert.assertEquals(2, this.manager.getReports(true, StatusReport.Status.INFO, Set.of()).size());
        // Unknown tags match no reporter
        Assert.assertTrue(this.manager.getReports(true, StatusReport.Status.INFO, Set.of("unknown")).isEmpty());
    }

    @Test
    public void isolatesBrokenReporters() throws ReflectiveOperationException
    {
        inject(List.of(this.broken, this.uptime));

        final List<StatusReport> reports = this.manager.getReports(true);

        // The broken reporter is turned into an ERROR report, and doesn't prevent the other reports
        Assert.assertEquals(2, reports.size());
        Assert.assertEquals("Broken", reports.get(0).getName());
        Assert.assertEquals(StatusReport.Status.ERROR, reports.get(0).getStatus());
        Assert.assertTrue(reports.get(0).getText().contains("boom"));
        Assert.assertEquals("Uptime", reports.get(1).getName());
    }

    private void inject(final List<StatusReporter> reporters) throws ReflectiveOperationException
    {
        final Field reference = StatusReportManagerImpl.class.getDeclaredField("reporters");
        reference.setAccessible(true);
        reference.set(this.manager, reporters);
    }
}
