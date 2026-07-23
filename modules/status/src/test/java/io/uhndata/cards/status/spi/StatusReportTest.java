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
package io.uhndata.cards.status.spi;

import jakarta.json.JsonObject;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the {@link StatusReport} value object.
 *
 * @version $Id$
 */
public class StatusReportTest
{
    @Test
    public void exposesItsComponents()
    {
        final StatusReport report = new StatusReport("Uptime", StatusReport.Status.INFO, "All day long");

        Assert.assertEquals("Uptime", report.getName());
        Assert.assertEquals(StatusReport.Status.INFO, report.getStatus());
        Assert.assertEquals("All day long", report.getText());
        Assert.assertEquals("Uptime: INFO", report.toString());
    }

    @Test
    public void serializesToJson()
    {
        final JsonObject json = new StatusReport("Uptime", StatusReport.Status.SUCCESS, "All day long").toJson();

        Assert.assertEquals("Uptime", json.getString("name"));
        Assert.assertEquals("SUCCESS", json.getString("status"));
        Assert.assertEquals("All day long", json.getString("text"));
    }

    @Test
    public void toleratesMissingText()
    {
        final StatusReport report = new StatusReport("Silence", StatusReport.Status.WARNING, null);

        Assert.assertNull(report.getText());
        // A null text must not break the JSON serialization
        Assert.assertEquals("", report.toJson().getString("text"));
    }
}
