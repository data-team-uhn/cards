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
package io.uhndata.cards.clarity.importer.spi;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractClarityDataProcessor} and for the defaults of {@link ClarityDataProcessor}.
 *
 * @version $Id$
 */
public class AbstractClarityDataProcessorTest
{
    /** A processor that returns its input unchanged, so that only the base class behaviour is under test. */
    private static final class TestProcessor extends AbstractClarityDataProcessor
    {
        TestProcessor(final boolean enabled, final String[] types, final int priority)
        {
            super(enabled, types, priority);
        }

        @Override
        public Map<String, String> processEntry(final Map<String, String> input)
        {
            return input;
        }
    }

    @Test
    public void getPriorityReturnsTheConfiguredValue()
    {
        Assert.assertEquals(42, new TestProcessor(true, null, 42).getPriority());
    }

    @Test
    public void supportsImportTypeAcceptsEverythingWhenNoTypeIsListed()
    {
        final ClarityDataProcessor processor = new TestProcessor(true, new String[0], 0);
        Assert.assertTrue(processor.supportsImportType("anything"));
        Assert.assertTrue(processor.supportsImportType(null));
    }

    @Test
    public void supportsImportTypeAcceptsEverythingWhenTheTypesAreNull()
    {
        Assert.assertTrue(new TestProcessor(true, null, 0).supportsImportType("anything"));
    }

    @Test
    public void supportsImportTypeOnlyAcceptsTheListedTypes()
    {
        final ClarityDataProcessor processor = new TestProcessor(true, new String[] { "visits", "deaths" }, 0);
        Assert.assertTrue(processor.supportsImportType("visits"));
        Assert.assertTrue(processor.supportsImportType("deaths"));
        Assert.assertFalse(processor.supportsImportType("appointments"));
    }

    @Test
    public void supportsImportTypeIsCaseSensitive()
    {
        Assert.assertFalse(new TestProcessor(true, new String[] { "visits" }, 0).supportsImportType("Visits"));
    }

    @Test
    public void aDisabledProcessorSupportsNothing()
    {
        Assert.assertFalse(new TestProcessor(false, new String[0], 0).supportsImportType("anything"));
        Assert.assertFalse(new TestProcessor(false, new String[] { "visits" }, 0).supportsImportType("visits"));
    }

    @Test
    public void startAndEndDoNothingByDefault()
    {
        final ClarityDataProcessor processor = new TestProcessor(true, null, 0);
        processor.start();
        processor.end();
        // Reaching this point without an exception is the assertion
        Assert.assertEquals(0, processor.getPriority());
    }

    @Test
    public void processorsAreOrderedByAscendingPriority()
    {
        final ClarityDataProcessor low = new TestProcessor(true, null, 5);
        final ClarityDataProcessor high = new TestProcessor(true, null, 200);
        Assert.assertTrue(low.compareTo(high) < 0);
        Assert.assertTrue(high.compareTo(low) > 0);
    }

    /** Equal priorities must still give a stable order, otherwise sorting the processors isn't reproducible. */
    @Test
    public void processorsWithTheSamePriorityAreOrderedByClassName()
    {
        final ClarityDataProcessor first = new TestProcessor(true, null, 5);
        final ClarityDataProcessor second = new AbstractClarityDataProcessor(true, null, 5)
        {
            @Override
            public Map<String, String> processEntry(final Map<String, String> input)
            {
                return input;
            }
        };
        Assert.assertEquals(0, first.compareTo(first));
        Assert.assertEquals(
            Integer.signum(first.getClass().getName().compareTo(second.getClass().getName())),
            Integer.signum(first.compareTo(second)));
        Assert.assertEquals(-Integer.signum(first.compareTo(second)), Integer.signum(second.compareTo(first)));
    }
}
