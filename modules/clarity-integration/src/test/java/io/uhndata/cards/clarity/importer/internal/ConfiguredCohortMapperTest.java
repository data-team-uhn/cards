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
 * Unit tests for {@link ConfiguredCohortMapper}.
 *
 * @version $Id$
 */
public class ConfiguredCohortMapperTest
{
    private static final String CLINIC = "/Survey/ClinicMapping/123456789";

    private static ConfiguredCohortMapper mapper(final String clinic, final String... conditions)
        throws ConfigurationException
    {
        final ConfiguredCohortMapper.Config config = Mockito.mock(ConfiguredCohortMapper.Config.class);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.priority()).thenReturn(0);
        Mockito.when(config.conditions()).thenReturn(conditions);
        Mockito.when(config.clinic()).thenReturn(clinic);
        Mockito.when(config.service_pid()).thenReturn("some.factory.Pid~cohortA");
        return new ConfiguredCohortMapper(config);
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
    public void aMatchingRowIsAssignedTheClinic() throws ConfigurationException
    {
        final Map<String, String> input = row("DEPARTMENT", "Cardiology");
        final Map<String, String> output = mapper(CLINIC, "DEPARTMENT = Cardiology").processEntry(input);
        Assert.assertSame(input, output);
        Assert.assertEquals(CLINIC, output.get("CLINIC"));
    }

    @Test
    public void aRowThatDoesNotMatchKeepsItsClinic() throws ConfigurationException
    {
        final Map<String, String> input = row("DEPARTMENT", "Oncology", "CLINIC", "/Survey/ClinicMapping/existing");
        final Map<String, String> output = mapper(CLINIC, "DEPARTMENT = Cardiology").processEntry(input);
        Assert.assertEquals("/Survey/ClinicMapping/existing", output.get("CLINIC"));
    }

    @Test
    public void aMatchingRowHasItsExistingClinicReplaced() throws ConfigurationException
    {
        final Map<String, String> input = row("DEPARTMENT", "Cardiology", "CLINIC", "/Survey/ClinicMapping/old");
        Assert.assertEquals(CLINIC, mapper(CLINIC, "DEPARTMENT = Cardiology").processEntry(input).get("CLINIC"));
    }

    /** A row is never dropped by this mapper, only annotated. */
    @Test
    public void noRowIsEverDiscarded() throws ConfigurationException
    {
        Assert.assertNotNull(mapper(CLINIC, "DEPARTMENT = Cardiology").processEntry(row("DEPARTMENT", "Oncology")));
        Assert.assertNotNull(mapper(CLINIC, "DEPARTMENT = Cardiology").processEntry(row()));
    }

    /** A condition on CLINIC itself is how a mapping re-routes an already assigned cohort, and isn't worth logging. */
    @Test
    public void aConditionOnTheClinicColumnIsHandled() throws ConfigurationException
    {
        final Map<String, String> input = row("CLINIC", "/Survey/ClinicMapping/other");
        Assert.assertSame(input, mapper(CLINIC, "CLINIC = /Survey/ClinicMapping/expected").processEntry(input));
        Assert.assertEquals("/Survey/ClinicMapping/other", input.get("CLINIC"));
    }
}
