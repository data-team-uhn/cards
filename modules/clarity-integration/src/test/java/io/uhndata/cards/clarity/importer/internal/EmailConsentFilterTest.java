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
 * Unit tests for {@link EmailConsentFilter}.
 *
 * @version $Id$
 */
public class EmailConsentFilterTest
{
    private static EmailConsentFilter filter(final String emailColumn, final String consentColumn)
    {
        final EmailConsentFilter.Config config = Mockito.mock(EmailConsentFilter.Config.class);
        Mockito.when(config.enable()).thenReturn(true);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.emailColumn()).thenReturn(emailColumn);
        Mockito.when(config.emailConsentColumn()).thenReturn(consentColumn);
        return new EmailConsentFilter(config);
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
    public void aValidEmailWithConsentIsKept()
    {
        final Map<String, String> input = row("EMAIL", "patient@example.com", "CONSENT", "Yes");
        Assert.assertSame(input, filter("EMAIL", "CONSENT").processEntry(input));
    }

    @Test
    public void anInvalidEmailIsDiscarded()
    {
        Assert.assertNull(filter("EMAIL", "CONSENT").processEntry(row("EMAIL", "not an email", "CONSENT", "Yes")));
    }

    @Test
    public void aMissingEmailIsDiscarded()
    {
        Assert.assertNull(filter("EMAIL", "CONSENT").processEntry(row("CONSENT", "Yes")));
    }

    @Test
    public void anEmptyEmailIsDiscarded()
    {
        Assert.assertNull(filter("EMAIL", "CONSENT").processEntry(row("EMAIL", "", "CONSENT", "Yes")));
    }

    @Test
    public void aRefusedConsentIsDiscarded()
    {
        Assert.assertNull(filter("EMAIL", "CONSENT").processEntry(
            row("EMAIL", "patient@example.com", "CONSENT", "No")));
    }

    @Test
    public void aMissingConsentIsDiscarded()
    {
        Assert.assertNull(filter("EMAIL", "CONSENT").processEntry(row("EMAIL", "patient@example.com")));
    }

    @Test
    public void theConsentValueIsCaseInsensitive()
    {
        Assert.assertNotNull(filter("EMAIL", "CONSENT").processEntry(
            row("EMAIL", "patient@example.com", "CONSENT", "yes")));
        Assert.assertNotNull(filter("EMAIL", "CONSENT").processEntry(
            row("EMAIL", "patient@example.com", "CONSENT", "YES")));
    }

    /** Leaving a column blank turns that half of the check off. */
    @Test
    public void anUnconfiguredEmailColumnSkipsTheAddressCheck()
    {
        Assert.assertNotNull(filter("", "CONSENT").processEntry(row("EMAIL", "not an email", "CONSENT", "Yes")));
    }

    @Test
    public void anUnconfiguredConsentColumnSkipsTheConsentCheck()
    {
        Assert.assertNotNull(filter("EMAIL", "").processEntry(row("EMAIL", "patient@example.com")));
    }

    @Test
    public void withNeitherColumnConfiguredEverythingIsKept()
    {
        final Map<String, String> input = row("EMAIL", "not an email");
        Assert.assertSame(input, filter("", "").processEntry(input));
    }

    @Test
    public void theFilterRunsEarly()
    {
        Assert.assertEquals(5, filter("EMAIL", "CONSENT").getPriority());
    }
}
