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

import org.apache.commons.lang3.StringUtils;

/**
 * Factory for building {@link SearchParameters} instances.
 *
 * @version $Id$
 */
public final class SearchParametersFactory
{
    private String type;

    private String query;

    private long maxResults = 10;

    private boolean escape = true;

    private boolean showTotalResults = true;

    private final class SearchParametersImpl implements SearchParameters
    {
        private final String type;

        private final String query;

        private final long maxResults;

        private final boolean escape;

        private final boolean showTotalResults;

        private SearchParametersImpl()
        {
            this.type = SearchParametersFactory.this.type;
            this.query = SearchParametersFactory.this.query;
            this.maxResults = SearchParametersFactory.this.maxResults;
            this.escape = SearchParametersFactory.this.escape;
            this.showTotalResults = SearchParametersFactory.this.showTotalResults;
        }

        @Override
        public String getType()
        {
            return this.type;
        }

        @Override
        public String getQuery()
        {
            return this.query;
        }

        @Override
        public long getMaxResults()
        {
            return this.maxResults;
        }

        @Override
        public boolean isEscaped()
        {
            return this.escape;
        }

        @Override
        public boolean showTotalResults()
        {
            return this.showTotalResults;
        }

    }

    /** Private constructor that can only be used by {@link #newSearchParameters()}. */
    private SearchParametersFactory()
    {
        // Private, the only way to instantiate this is through newSearchParameters()
    }

    /**
     * Start building a new {@link SearchParameters} instance.
     *
     * @return a factory instance
     */
    public static SearchParametersFactory newSearchParameters()
    {
        return new SearchParametersFactory();
    }

    /**
     * Set the {@link SearchParameters#getType() query type} to use.
     *
     * @param type the query type to use
     * @return this builder, for chaining calls
     */
    public SearchParametersFactory withType(final String type)
    {
        this.type = type;
        return this;
    }

    /**
     * Set the {@link SearchParameters#getQuery() query} to use.
     *
     * @param query the query to use
     * @return this builder, for chaining calls
     */
    public SearchParametersFactory withQuery(final String query)
    {
        this.query = query;
        return this;
    }

    /**
     * Set the {@link SearchParameters#getMaxResults() maximum results} configuration to use. If not specified,
     * {@code 10} is used as a default.
     *
     * @param maxResults the number of maximum results to return, a strictly positive number
     * @return this builder, for chaining calls
     * @throws IllegalArgumentException if {@code maxResults} is less than or equal to {@code 0}
     */
    public SearchParametersFactory withMaxResults(final long maxResults) throws IllegalArgumentException
    {
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be > 0");
        }
        this.maxResults = maxResults;
        return this;
    }

    /**
     * Set the {@link SearchParameters#isEscaped() query escape} configuration to use. If not specified, {@code true} is
     * used as a default.
     *
     * @param escape whether the query should be escaped or not
     * @return this builder, for chaining calls
     */
    public SearchParametersFactory withEscaped(final boolean escape)
    {
        this.escape = escape;
        return this;
    }

    /**
     * Set the {@link SearchParameters#showTotalResults() show total results} configuration to use. If not specified,
     * {@code true} is used as a default.
     *
     * @param showTotalResults whether the total number of matches must be computed and returned
     * @return this builder, for chaining calls
     */
    public SearchParametersFactory withShowTotalResults(final boolean showTotalResults)
    {
        this.showTotalResults = showTotalResults;
        return this;
    }

    /**
     * Build a {@link SearchParameters} instance as configured so far. The factory instance can continue to be
     * configured, but further changes will not affect the returned {@code SearchParameters} instance.
     *
     * @return a {@link SearchParameters} instance
     * @throws IllegalStateException if the query and query type have not been set yet
     */
    public SearchParameters build() throws IllegalStateException
    {
        if (StringUtils.isBlank(this.type)) {
            throw new IllegalStateException("Query type not set yet, withType(type) must be called before build()");
        }
        if (StringUtils.isBlank(this.query)) {
            throw new IllegalStateException("Query not set yet, withQuery(query) must be called before build()");
        }
        return new SearchParametersImpl();
    }
}
