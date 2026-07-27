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
package io.uhndata.cards.serialize.spi;

import java.util.List;
import java.util.TreeSet;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the parsing helpers in {@link BaseFilterFactory}.
 *
 * @version $Id$
 */
public class BaseFilterFactoryTest
{
    private static final String CREATED_AFTER = "createdAfter";

    private static final String STATUS = "status";

    private static final String NO_STATUS = "statusNot";

    private static final List<Pair<String, String>> FILTERS = List.of(
        Pair.of(CREATED_AFTER, "2023-01-15"),
        Pair.of(STATUS, "DRAFT"),
        Pair.of(STATUS, "with%20escapes"),
        Pair.of(NO_STATUS, "SUBMITTED"));

    private final TestFilterFactory factory = new TestFilterFactory();

    @Test
    public void parseSingletonFilterReturnsFirstMatchingFilter()
    {
        List<DataFilter> result = this.factory.parseSingletonFilter(FILTERS, List.of(), CREATED_AFTER,
            TestFilter::new);
        assertEquals(1, result.size());
        assertEquals("2023-01-15", result.get(0).getName());
    }

    @Test
    public void parseSingletonFilterDecodesUrlEscapes()
    {
        List<DataFilter> result = this.factory.parseSingletonFilter(
            List.of(Pair.of(STATUS, "with%20escapes")), List.of(), STATUS, TestFilter::new);
        assertEquals("with escapes", result.get(0).getName());
    }

    @Test
    public void parseSingletonFilterWithBlankNameReturnsEmptyList()
    {
        assertTrue(this.factory.parseSingletonFilter(FILTERS, List.of(), " ", TestFilter::new).isEmpty());
    }

    @Test
    public void parseSingletonFilterWithNoMatchReturnsEmptyList()
    {
        assertTrue(this.factory.parseSingletonFilter(FILTERS, List.of(), "missing", TestFilter::new).isEmpty());
    }

    @Test
    public void parseMultipleSingletonFiltersReturnsAllMatchingFilters()
    {
        List<DataFilter> result = this.factory.parseMultipleSingletonFilters(FILTERS, List.of(), STATUS,
            TestFilter::new);
        assertEquals(2, result.size());
        assertEquals("DRAFT", result.get(0).getName());
        assertEquals("with escapes", result.get(1).getName());
    }

    @Test
    public void parseMultipleSingletonFiltersWithBlankNameReturnsEmptyList()
    {
        assertTrue(this.factory.parseMultipleSingletonFilters(FILTERS, List.of(), "", TestFilter::new).isEmpty());
    }

    @Test
    public void parseSetFilterCollectsAllValuesIntoOneFilter()
    {
        List<DataFilter> result = this.factory.parseSetFilter(FILTERS, List.of(), STATUS,
            values -> new TestFilter(String.join(",", new TreeSet<>(values))));
        assertEquals(1, result.size());
        assertEquals("DRAFT,with escapes", result.get(0).getName());
    }

    @Test
    public void parseSetFilterWithBlankNameReturnsEmptyList()
    {
        assertTrue(this.factory.parseSetFilter(FILTERS, List.of(), null, v -> new TestFilter("")).isEmpty());
    }

    @Test
    public void parseSetFilterWithNoMatchReturnsEmptyList()
    {
        assertTrue(this.factory.parseSetFilter(FILTERS, List.of(), "missing", v -> new TestFilter("")).isEmpty());
    }

    @Test
    public void parseDoubleSetFiltersReturnsPositiveAndNegativeFilters()
    {
        List<DataFilter> result = this.factory.parseDoubleSetFilters(FILTERS, List.of(), STATUS, NO_STATUS,
            (values, positive) -> new TestFilter((positive ? "+" : "-") + String.join(",", new TreeSet<>(values))));
        assertEquals(2, result.size());
        assertEquals("+DRAFT,with escapes", result.get(0).getName());
        assertEquals("-SUBMITTED", result.get(1).getName());
    }

    @Test
    public void parseDoubleSetFiltersWithOnlyNegativeMatchesReturnsOneFilter()
    {
        List<DataFilter> result = this.factory.parseDoubleSetFilters(FILTERS, List.of(), "missing", NO_STATUS,
            (values, positive) -> new TestFilter((positive ? "+" : "-") + String.join(",", values)));
        assertEquals(1, result.size());
        assertEquals("-SUBMITTED", result.get(0).getName());
    }

    @Test
    public void parseDoubleSetFiltersWithBlankNamesReturnsEmptyList()
    {
        assertTrue(this.factory.parseDoubleSetFilters(FILTERS, List.of(), "", null,
            (values, positive) -> new TestFilter("")).isEmpty());
    }

    private static final class TestFilterFactory extends BaseFilterFactory
    {
        @Override
        public List<DataFilter> parseFilters(final List<Pair<String, String>> filters, final List<String> selectors)
        {
            return List.of();
        }

        @Override
        public List<SelectorDetails> getFilterDetails()
        {
            return List.of();
        }
    }

    /** A simple filter storing the parsed value in its name. */
    private static final class TestFilter implements DataFilter
    {
        private final String value;

        TestFilter(final String value)
        {
            this.value = value;
        }

        @Override
        public String getName()
        {
            return this.value;
        }

        @Override
        public String getExtraQueryConditions(final String defaultSelectorName)
        {
            return "";
        }
    }
}
