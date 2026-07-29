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
package io.uhndata.cards.serialize.internal;

import java.util.List;

import org.junit.Test;

import io.uhndata.cards.serialize.spi.DataFilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link DefaultDataFilters}.
 *
 * @version $Id$
 */
public class DefaultDataFiltersTest
{
    @Test
    public void getFiltersReturnsUnmodifiableList()
    {
        DataFilter filter = new TestFilter("status", " selector", " condition", false);
        DefaultDataFilters filters = new DefaultDataFilters(List.of(filter));
        assertEquals(List.of(filter), filters.getFilters());
        assertThrows(UnsupportedOperationException.class, () -> filters.getFilters().clear());
    }

    @Test
    public void getExtraQuerySelectorsSkipsRepeatedFilterNames()
    {
        DefaultDataFilters filters = new DefaultDataFilters(List.of(
            new TestFilter("status", " join1", " c1", false),
            new TestFilter("status", " join2", " c2", false),
            new TestFilter("modified", " join3", " c3", false)));
        assertEquals(" join1 join3", filters.getExtraQuerySelectors());
    }

    @Test
    public void getExtraQuerySelectorsKeepsRepeatedPerInstanceFilters()
    {
        DefaultDataFilters filters = new DefaultDataFilters(List.of(
            new TestFilter("status", " join1", " c1", true),
            new TestFilter("status", " join2", " c2", true)));
        assertEquals(" join1 join2", filters.getExtraQuerySelectors());
    }

    @Test
    public void getExtraQueryConditionsConcatenatesAllConditions()
    {
        DefaultDataFilters filters = new DefaultDataFilters(List.of(
            new TestFilter("status", " join1", " c1", false),
            new TestFilter("status", " join2", " c2", false)));
        assertEquals(" c1 c2", filters.getExtraQueryConditions());
    }

    @Test
    public void emptyFiltersProduceEmptyStrings()
    {
        DefaultDataFilters filters = new DefaultDataFilters(List.of());
        assertTrue(filters.getFilters().isEmpty());
        assertEquals("", filters.getExtraQuerySelectors());
        assertEquals("", filters.getExtraQueryConditions());
    }

    private static final class TestFilter implements DataFilter
    {
        private final String name;

        private final String selectors;

        private final String conditions;

        private final boolean perInstance;

        TestFilter(final String name, final String selectors, final String conditions, final boolean perInstance)
        {
            this.name = name;
            this.selectors = selectors;
            this.conditions = conditions;
            this.perInstance = perInstance;
        }

        @Override
        public String getName()
        {
            return this.name;
        }

        @Override
        public boolean areExtraSelectorsPerFilterInstance()
        {
            return this.perInstance;
        }

        @Override
        public String getExtraQuerySelectors(final String defaultSelectorName)
        {
            return this.selectors;
        }

        @Override
        public String getExtraQueryConditions(final String defaultSelectorName)
        {
            return this.conditions;
        }
    }
}
