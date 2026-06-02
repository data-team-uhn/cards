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

package io.uhndata.cards.status.internal;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.status.spi.StatusReport;
import io.uhndata.cards.status.spi.StatusReporter;

@Component(immediate = true)
public class UptimeStatusReporter implements StatusReporter
{
    private static final String TITLE = "System Started";

    private static final ZonedDateTime STARTED = ZonedDateTime.now();

    @Override
    public String getName()
    {
        return TITLE;
    }

    @Override
    public StatusReport report(boolean unprivileged)
    {
        return new StatusReport(TITLE, StatusReport.Status.INFO,
            STARTED.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    @Override
    public Set<String> getTags()
    {
        return Set.of("status", "systemStarted");
    }
}
