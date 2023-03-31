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
package io.uhndata.cards.spi;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

/**
 * Unit tests for {@link SearchParametersFactory}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SearchParametersFactoryTest
{

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private SearchParametersFactory searchParametersFactory;

    @Test
    public void newSearchParametersReturnsNewInstance()
    {
        assertNotNull(this.searchParametersFactory);
    }

    @Test
    public void buildWithoutTypeThrowsIllegalStateException()
    {
        assertThrows("Query type not set yet, withType(type) must be called before build()",
                IllegalStateException.class, () -> this.searchParametersFactory.build());
    }

    @Test
    public void buildWithoutQueryThrowsIllegalStateException()
    {
        String type = "quick";
        this.searchParametersFactory.withType(type);
        assertThrows("Query not set yet, withQuery(query) must be called before build()",
                IllegalStateException.class, () -> this.searchParametersFactory.build());
    }

    @Test
    public void buildCreatesNewSearchParametersInstance()
    {
        setSearchParameters("quick", "search", false, false);
        this.searchParametersFactory.withMaxResults(5);

        SearchParameters searchParameters = this.searchParametersFactory.build();
        assertNotNull(searchParameters);
        assertEquals("quick", searchParameters.getType());
        assertEquals("search", searchParameters.getQuery());
        assertEquals(5, searchParameters.getMaxResults());
        assertFalse(searchParameters.isEscaped());
        assertFalse(searchParameters.showTotalResults());
    }

    @Test
    public void withMaxResultsForNegativeNumberThrowsIllegalArgumentException()
    {
        assertThrows("maxResults must be > 0", IllegalArgumentException.class,
            () -> this.searchParametersFactory.withMaxResults(-1));
    }


    @Before
    public void setUp()
    {
        this.searchParametersFactory = SearchParametersFactory.newSearchParameters();
    }

    private void setSearchParameters(String type, String query, boolean escaped, boolean showTotalResults)
    {
        this.searchParametersFactory.withType(type);
        this.searchParametersFactory.withQuery(query);
        this.searchParametersFactory.withEscaped(escaped);
        this.searchParametersFactory.withShowTotalResults(showTotalResults);
    }
}
