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

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import io.uhndata.cards.serialize.spi.DataFilter;
import io.uhndata.cards.serialize.spi.DataFilterFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultDataFiltersParser}.
 *
 * @version $Id$
 */
public class DefaultDataFiltersParserTest
{
    private final DefaultDataFiltersParser parser = new DefaultDataFiltersParser();

    private final DataFilterFactory factory1 = mock(DataFilterFactory.class);

    private final DataFilterFactory factory2 = mock(DataFilterFactory.class);

    private final DataFilter filter1 = mock(DataFilter.class);

    private final DataFilter filter2 = mock(DataFilter.class);

    @Before
    public void setUp() throws IllegalAccessException
    {
        FieldUtils.writeField(this.parser, "filterFactories", List.of(this.factory1, this.factory2), true);
    }

    @Test
    public void parseFiltersCollectsFiltersFromAllFactories()
    {
        when(this.factory1.parseFilters(anyList(), anyList())).thenReturn(List.of(this.filter1));
        when(this.factory2.parseFilters(anyList(), anyList())).thenReturn(List.of(this.filter2));

        DefaultDataFilters result = this.parser.parseFilters("bare.dataFilter:status=DRAFT");
        assertEquals(List.of(this.filter1, this.filter2), result.getFilters());
    }

    @Test
    public void parseFiltersPassesParsedOptionsAndSelectorsToFactories()
    {
        when(this.factory1.parseFilters(anyList(), anyList())).thenReturn(List.of());
        when(this.factory2.parseFilters(anyList(), anyList())).thenReturn(List.of());

        this.parser.parseFilters("bare.dataFilter:status=DRAFT.dataFilter:createdAfter=2023-01-15");

        verify(this.factory1).parseFilters(
            List.of(Pair.of("status", "DRAFT"), Pair.of("createdAfter", "2023-01-15")),
            List.of("bare", "dataFilter:status=DRAFT", "dataFilter:createdAfter=2023-01-15"));
    }

    @Test
    public void parseFiltersWithNoFactoryResultsReturnsEmptyFilters()
    {
        when(this.factory1.parseFilters(any(), any())).thenReturn(List.of());
        when(this.factory2.parseFilters(any(), any())).thenReturn(List.of());

        DefaultDataFilters result = this.parser.parseFilters("bare");
        assertTrue(result.getFilters().isEmpty());
    }
}
