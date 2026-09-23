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

/**
 * Unit tests for {@link DiscardDuplicatesFilter}.
 *
 * @version $Id$
 */
public class DiscardDuplicatesFilterTest
{
    private static final String PATIENT = "/SubjectTypes/Patient";

    private static final String VISIT = "/SubjectTypes/Patient/Visit";

    private static DiscardDuplicatesFilter filter(final boolean enabled, final String subjectType)
    {
        final DiscardDuplicatesFilter.Config config = Mockito.mock(DiscardDuplicatesFilter.Config.class);
        Mockito.when(config.enable()).thenReturn(enabled);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.subjectType()).thenReturn(subjectType);
        return new DiscardDuplicatesFilter(config);
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
    public void theFirstRowForASubjectIsKept()
    {
        final Map<String, String> input = row(PATIENT, "P1");
        Assert.assertSame(input, filter(true, PATIENT).processEntry(input));
    }

    @Test
    public void aLaterRowForTheSameSubjectIsDiscarded()
    {
        final DiscardDuplicatesFilter filter = filter(true, PATIENT);
        Assert.assertNotNull(filter.processEntry(row(PATIENT, "P1")));
        Assert.assertNull(filter.processEntry(row(PATIENT, "P1", VISIT, "V2")));
    }

    @Test
    public void rowsForDifferentSubjectsAreAllKept()
    {
        final DiscardDuplicatesFilter filter = filter(true, PATIENT);
        Assert.assertNotNull(filter.processEntry(row(PATIENT, "P1")));
        Assert.assertNotNull(filter.processEntry(row(PATIENT, "P2")));
        Assert.assertNotNull(filter.processEntry(row(PATIENT, "P3")));
    }

    @Test
    public void aRowWithoutTheSubjectColumnIsAlwaysKept()
    {
        final DiscardDuplicatesFilter filter = filter(true, PATIENT);
        Assert.assertNotNull(filter.processEntry(row()));
        Assert.assertNotNull(filter.processEntry(row()));
    }

    @Test
    public void duplicatesCanBeDetectedOnTheVisitInstead()
    {
        final DiscardDuplicatesFilter filter = filter(true, VISIT);
        Assert.assertNotNull(filter.processEntry(row(PATIENT, "P1", VISIT, "V1")));
        // Same patient but a different visit, which this configuration lets through
        Assert.assertNotNull(filter.processEntry(row(PATIENT, "P1", VISIT, "V2")));
        Assert.assertNull(filter.processEntry(row(PATIENT, "P1", VISIT, "V1")));
    }

    /** end() clears the seen identifiers, so the next import run starts over. */
    @Test
    public void theSeenIdentifiersAreForgottenAtTheEndOfARun()
    {
        final DiscardDuplicatesFilter filter = filter(true, PATIENT);
        Assert.assertNotNull(filter.processEntry(row(PATIENT, "P1")));
        Assert.assertNull(filter.processEntry(row(PATIENT, "P1")));
        filter.end();
        Assert.assertNotNull(filter.processEntry(row(PATIENT, "P1")));
    }

    @Test
    public void theFilterRunsLateSoOtherFiltersCanDropTheEarlierEventFirst()
    {
        Assert.assertEquals(200, filter(true, PATIENT).getPriority());
    }

    @Test
    public void aDisabledFilterSupportsNoImportType()
    {
        Assert.assertFalse(filter(false, PATIENT).supportsImportType("visits"));
        Assert.assertTrue(filter(true, PATIENT).supportsImportType("visits"));
    }
}
