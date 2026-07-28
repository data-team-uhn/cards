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
package io.uhndata.cards.clarity.importer.internal;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.service.cm.ConfigurationException;

/**
 * Unit tests for {@link ConfiguredDiscardFilter}.
 *
 * @version $Id$
 */
public class ConfiguredDiscardFilterTest
{
    private static ConfiguredDiscardFilter filter(final String... conditions) throws ConfigurationException
    {
        final ConfiguredDiscardFilter.Config config = Mockito.mock(ConfiguredDiscardFilter.Config.class);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.priority()).thenReturn(7);
        Mockito.when(config.conditions()).thenReturn(conditions);
        Mockito.when(config.service_pid()).thenReturn("some.factory.Pid~discardCancelled");
        return new ConfiguredDiscardFilter(config);
    }

    private static Map<String, String> row(final String... keysAndValues)
    {
        final Map<String, String> row = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }

    @Test
    public void aRowMatchingEveryConditionIsDiscarded() throws ConfigurationException
    {
        Assert.assertNull(filter("STATUS = Cancelled").processEntry(row("STATUS", "Cancelled")));
    }

    @Test
    public void aRowMissingOneConditionIsKeptUnchanged() throws ConfigurationException
    {
        final Map<String, String> input = row("STATUS", "Completed");
        Assert.assertSame(input, filter("STATUS = Cancelled").processEntry(input));
    }

    @Test
    public void everyConditionMustHoldBeforeARowIsDiscarded() throws ConfigurationException
    {
        final ConfiguredDiscardFilter filter = filter("STATUS = Cancelled", "CLINIC = A");
        Assert.assertNull(filter.processEntry(row("STATUS", "Cancelled", "CLINIC", "A")));
        Assert.assertNotNull(filter.processEntry(row("STATUS", "Cancelled", "CLINIC", "B")));
    }

    /** With nothing configured to look for, every row matches vacuously and is discarded. */
    @Test
    public void withoutConditionsEveryRowIsDiscarded() throws ConfigurationException
    {
        Assert.assertNull(filter().processEntry(row("STATUS", "Completed")));
    }

    @Test
    public void theConfiguredPriorityIsUsed() throws ConfigurationException
    {
        Assert.assertEquals(7, filter("STATUS = Cancelled").getPriority());
    }

    @Test
    public void anInvalidConditionIsRejected()
    {
        Assert.assertThrows(ConfigurationException.class, () -> filter("nonsense"));
    }
}
