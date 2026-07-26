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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.Assert;
import org.junit.Test;

import io.uhndata.cards.status.spi.StatusReport;

/**
 * Unit tests for {@link UptimeStatusReporter}.
 *
 * @version $Id$
 */
public class UptimeStatusReporterTest
{
    private final UptimeStatusReporter reporter = new UptimeStatusReporter();

    @Test
    public void reportsTheStartupTime()
    {
        final StatusReport report = this.reporter.report(true);

        Assert.assertEquals("System Started", report.getName());
        Assert.assertEquals(StatusReport.Status.INFO, report.getStatus());
        // The reported time is a valid ISO timestamp, not later than the present
        final ZonedDateTime started =
            ZonedDateTime.parse(report.getText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Assert.assertFalse(started.isAfter(ZonedDateTime.now()));
        // The startup time is not sensitive, the same report is served in unprivileged mode
        Assert.assertEquals(report.getText(), this.reporter.report(false).getText());
    }

    @Test
    public void describesItself()
    {
        Assert.assertEquals("System Started", this.reporter.getName());
        Assert.assertTrue(this.reporter.getTags().contains("status"));
        Assert.assertTrue(this.reporter.getTags().contains("systemStarted"));
    }
}
