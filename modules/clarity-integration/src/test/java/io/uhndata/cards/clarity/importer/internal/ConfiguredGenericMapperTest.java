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
 * Unit tests for {@link ConfiguredGenericMapper}.
 *
 * @version $Id$
 */
public class ConfiguredGenericMapperTest
{
    private static ConfiguredGenericMapper mapper(final String column, final String value,
        final String... conditions) throws ConfigurationException
    {
        return mapper(column, value, true, conditions);
    }

    private static ConfiguredGenericMapper mapper(final String column, final String value, final boolean logging,
        final String... conditions) throws ConfigurationException
    {
        final ConfiguredGenericMapper.Config config = Mockito.mock(ConfiguredGenericMapper.Config.class);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.priority()).thenReturn(0);
        Mockito.when(config.conditions()).thenReturn(conditions);
        Mockito.when(config.column()).thenReturn(column);
        Mockito.when(config.value()).thenReturn(value);
        Mockito.when(config.enableLogging()).thenReturn(logging);
        Mockito.when(config.service_pid()).thenReturn("some.factory.Pid~setStatus");
        return new ConfiguredGenericMapper(config);
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
    public void aMatchingRowGetsTheConfiguredValue() throws ConfigurationException
    {
        final Map<String, String> input = row("STATUS", "Completed");
        Assert.assertEquals("yes", mapper("SEND_SURVEY", "yes", "STATUS = Completed").processEntry(input)
            .get("SEND_SURVEY"));
    }

    @Test
    public void aRowThatDoesNotMatchIsLeftAlone() throws ConfigurationException
    {
        final Map<String, String> input = row("STATUS", "Cancelled");
        Assert.assertNull(mapper("SEND_SURVEY", "yes", "STATUS = Completed").processEntry(input).get("SEND_SURVEY"));
    }

    /** A value of the form %{COLUMN}% copies another column instead of being used literally. */
    @Test
    public void aValueReferringToAnotherColumnCopiesIt() throws ConfigurationException
    {
        final Map<String, String> input = row("STATUS", "Completed", "SOURCE", "copied value");
        Assert.assertEquals("copied value",
            mapper("TARGET", "%{SOURCE}%", "STATUS = Completed").processEntry(input).get("TARGET"));
    }

    @Test
    public void copyingAnAbsentColumnClearsTheTarget() throws ConfigurationException
    {
        final Map<String, String> input = row("STATUS", "Completed");
        Assert.assertNull(mapper("TARGET", "%{MISSING}%", "STATUS = Completed").processEntry(input).get("TARGET"));
    }

    @Test
    public void aValueOnlyPartlyLookingLikeAReferenceIsUsedLiterally() throws ConfigurationException
    {
        final Map<String, String> input = row("STATUS", "Completed");
        Assert.assertEquals("prefix %{SOURCE}%",
            mapper("TARGET", "prefix %{SOURCE}%", "STATUS = Completed").processEntry(input).get("TARGET"));
    }

    @Test
    public void loggingCanBeTurnedOffWithoutChangingTheResult() throws ConfigurationException
    {
        final Map<String, String> input = row("STATUS", "Completed");
        Assert.assertEquals("yes",
            mapper("SEND_SURVEY", "yes", false, "STATUS = Completed").processEntry(input).get("SEND_SURVEY"));
    }

    @Test
    public void noRowIsEverDiscarded() throws ConfigurationException
    {
        Assert.assertNotNull(mapper("X", "y", "STATUS = Completed").processEntry(row("STATUS", "Cancelled")));
    }
}
