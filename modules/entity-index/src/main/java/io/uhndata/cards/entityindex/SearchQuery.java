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
package io.uhndata.cards.entityindex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The criteria for a search against the {@link EntityIndexer entity index}. All the specified criteria — structured
 * conditions, a full text filter, and a native Lucene query — are combined with AND. Results are sorted by the
 * requested sort field, by creation date if none is requested.
 *
 * @version $Id$
 * @since 0.9.41
 */
public final class SearchQuery
{
    private final List<SearchCondition> conditions = new ArrayList<>();

    private String nativeQuery;

    private String fulltext;

    private String sortField;

    private boolean sortNumeric;

    private boolean sortDescending;

    private int maxHits = 100;

    /**
     * Add a structured condition that results must match.
     *
     * @param condition a condition on one field
     * @return this object, for chaining
     */
    public SearchQuery withCondition(final SearchCondition condition)
    {
        this.conditions.add(condition);
        return this;
    }

    /**
     * Set a full text filter matched against the whole entity content.
     *
     * @param text words to look for, in the classic Lucene query syntax
     * @return this object, for chaining
     */
    public SearchQuery withFulltext(final String text)
    {
        this.fulltext = text;
        return this;
    }

    /**
     * Set a native Lucene query, using the field naming convention described in {@link IndexFields}.
     *
     * @param query a query in the standard Lucene query syntax
     * @return this object, for chaining
     */
    public SearchQuery withNativeQuery(final String query)
    {
        this.nativeQuery = query;
        return this;
    }

    /**
     * Set the field to sort the results by.
     *
     * @param field the index field name to sort by
     * @param numeric whether the field holds numeric (including boolean and date) values
     * @param descending whether to sort in descending order
     * @return this object, for chaining
     */
    public SearchQuery sortBy(final String field, final boolean numeric, final boolean descending)
    {
        this.sortField = field;
        this.sortNumeric = numeric;
        this.sortDescending = descending;
        return this;
    }

    /**
     * Set the maximum number of hits to retrieve.
     *
     * @param limit a positive number of hits
     * @return this object, for chaining
     */
    public SearchQuery withMaxHits(final int limit)
    {
        this.maxHits = limit;
        return this;
    }

    /**
     * The structured conditions that results must match.
     *
     * @return an unmodifiable list of conditions, may be empty
     */
    public List<SearchCondition> getConditions()
    {
        return Collections.unmodifiableList(this.conditions);
    }

    /**
     * The full text filter, if any.
     *
     * @return words to look for, may be {@code null}
     */
    public String getFulltext()
    {
        return this.fulltext;
    }

    /**
     * The native Lucene query, if any.
     *
     * @return a query in the standard Lucene query syntax, may be {@code null}
     */
    public String getNativeQuery()
    {
        return this.nativeQuery;
    }

    /**
     * The field to sort the results by.
     *
     * @return an index field name, may be {@code null} for the default sort by creation date
     */
    public String getSortField()
    {
        return this.sortField;
    }

    /**
     * Whether the sort field holds numeric values.
     *
     * @return {@code true} if the sort should compare numbers
     */
    public boolean isSortNumeric()
    {
        return this.sortNumeric;
    }

    /**
     * Whether to sort in descending order.
     *
     * @return {@code true} for descending order
     */
    public boolean isSortDescending()
    {
        return this.sortDescending;
    }

    /**
     * The maximum number of hits to retrieve.
     *
     * @return a positive number of hits
     */
    public int getMaxHits()
    {
        return this.maxHits;
    }
}
