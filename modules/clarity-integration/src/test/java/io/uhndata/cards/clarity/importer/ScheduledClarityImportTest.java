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
package io.uhndata.cards.clarity.importer;

import java.util.Arrays;
import java.util.Collections;

import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link ScheduledClarityImport}.
 *
 * @version $Id$
 */
public class ScheduledClarityImportTest
{
    private ScheduledClarityImport component;

    private Scheduler scheduler;

    private ScheduleOptions options;

    @Before
    public void setUp()
    {
        this.component = new ScheduledClarityImport();
        this.scheduler = Mockito.mock(Scheduler.class);
        this.options = Mockito.mock(ScheduleOptions.class);
        Mockito.when(this.scheduler.EXPR(Mockito.anyString())).thenReturn(this.options);
        TestUtils.setField(this.component, "processors", Collections.emptyList());
    }

    private static ClarityImportConfig config(final String name, final String schedule)
    {
        final ClarityImportConfigDefinition definition = Mockito.mock(ClarityImportConfigDefinition.class);
        Mockito.when(definition.name()).thenReturn(name);
        Mockito.when(definition.importSchedule()).thenReturn(schedule);
        final ClarityImportConfig config = Mockito.mock(ClarityImportConfig.class);
        Mockito.when(config.getConfig()).thenReturn(definition);
        return config;
    }

    /** The configurations can arrive before the scheduler is bound; activation will replay them. */
    @Test
    public void aConfigurationArrivingBeforeTheSchedulerIsIgnored()
    {
        this.component.configAdded(config("visits", "0 0 0 * * ? *"));
        Mockito.verifyNoInteractions(this.scheduler);
    }

    @Test
    public void aConfigurationWithAScheduleIsScheduled() throws Exception
    {
        TestUtils.setField(this.component, "scheduler", this.scheduler);
        this.component.configAdded(config("visits", "0 0 0 * * ? *"));
        Mockito.verify(this.scheduler).EXPR("0 0 0 * * ? *");
        Mockito.verify(this.options).name("ScheduledClarityImport-visits");
        Mockito.verify(this.options).canRunConcurrently(true);
        Mockito.verify(this.scheduler).schedule(Mockito.any(Runnable.class), Mockito.eq(this.options));
    }

    @Test
    public void aConfigurationWithoutAScheduleIsNotScheduled() throws Exception
    {
        TestUtils.setField(this.component, "scheduler", this.scheduler);
        this.component.configAdded(config("manual", "  "));
        Mockito.verify(this.scheduler, Mockito.never()).schedule(Mockito.any(), Mockito.any());
    }

    @Test
    public void aFailureToScheduleIsSwallowed() throws Exception
    {
        TestUtils.setField(this.component, "scheduler", this.scheduler);
        Mockito.when(this.scheduler.schedule(Mockito.any(), Mockito.any()))
            .thenThrow(new IllegalArgumentException("bad cron"));
        // The exception must not escape, otherwise one bad configuration would break the others
        this.component.configAdded(config("visits", "not a cron expression"));
    }

    @Test
    public void removingAConfigurationUnschedulesIt()
    {
        TestUtils.setField(this.component, "scheduler", this.scheduler);
        this.component.configRemoved(config("visits", "0 0 0 * * ? *"));
        Mockito.verify(this.scheduler).unschedule("ScheduledClarityImport-visits");
    }

    @Test
    public void activationSchedulesEveryKnownConfiguration() throws Exception
    {
        TestUtils.setField(this.component, "scheduler", this.scheduler);
        TestUtils.setField(this.component, "configs",
            Arrays.asList(config("visits", "0 0 0 * * ? *"), config("deaths", "0 0 1 * * ? *")));
        this.component.activate();
        Mockito.verify(this.scheduler, Mockito.times(2))
            .schedule(Mockito.any(Runnable.class), Mockito.eq(this.options));
    }
}
