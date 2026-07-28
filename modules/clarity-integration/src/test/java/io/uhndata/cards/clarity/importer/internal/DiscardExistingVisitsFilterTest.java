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

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import io.uhndata.cards.clarity.importer.TestUtils;

/**
 * Unit tests for {@link DiscardExistingVisitsFilter}. Only the paths that stop before reaching the repository are
 * covered; deleting the previously imported visits needs a live one.
 *
 * @version $Id$
 */
public class DiscardExistingVisitsFilterTest
{
    private static final String DATE_COLUMN = "VISIT_DATE";

    private static DiscardExistingVisitsFilter filter(final boolean enabled, final boolean discardNew,
        final String... clinics)
    {
        final DiscardExistingVisitsFilter.Config config = Mockito.mock(DiscardExistingVisitsFilter.Config.class);
        Mockito.when(config.enable()).thenReturn(enabled);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.dateColumn()).thenReturn(DATE_COLUMN);
        Mockito.when(config.clinics()).thenReturn(clinics);
        Mockito.when(config.discardNew()).thenReturn(discardNew);
        return new DiscardExistingVisitsFilter(config);
    }

    /** An unreadable date stops the filter before it looks anything up, and the row is kept by default. */
    @Test
    public void aRowWithoutAUsableDateIsKept()
    {
        final Map<String, String> input = TestUtils.row();
        Assert.assertSame(input, filter(true, false).processEntry(input));
    }

    @Test
    public void aRowWithAnUnparseableDateIsKept()
    {
        final Map<String, String> input = TestUtils.row(DATE_COLUMN, "not a date");
        Assert.assertSame(input, filter(true, false).processEntry(input));
    }

    /** When configured to discard the new event too, nothing is imported even if the lookup failed. */
    @Test
    public void discardingTheNewEventAlsoDropsARowWithoutADate()
    {
        Assert.assertNull(filter(true, true).processEntry(TestUtils.row()));
    }

    @Test
    public void aDisabledFilterSupportsNoImportType()
    {
        Assert.assertFalse(filter(false, false).supportsImportType("visits"));
        Assert.assertTrue(filter(true, false).supportsImportType("visits"));
    }

    @Test
    public void theFilterRunsLate()
    {
        Assert.assertEquals(100, filter(true, false).getPriority());
    }

    @Test
    public void anEmptyClinicListIsAccepted()
    {
        Assert.assertNotNull(filter(true, false));
        Assert.assertNotNull(filter(true, false, "/Survey/ClinicMapping/1"));
    }

    @Test
    public void nullClinicsAreAccepted()
    {
        final DiscardExistingVisitsFilter.Config config = Mockito.mock(DiscardExistingVisitsFilter.Config.class);
        Mockito.when(config.enable()).thenReturn(true);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.dateColumn()).thenReturn(DATE_COLUMN);
        Mockito.when(config.clinics()).thenReturn(null);
        Mockito.when(config.discardNew()).thenReturn(false);
        final Map<String, String> input = TestUtils.row();
        Assert.assertSame(input, new DiscardExistingVisitsFilter(config).processEntry(input));
    }
}
