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

import java.util.Collections;
import java.util.List;

/**
 * The outcome of a search against the {@link EntityIndexer entity index}: the paths of the matching entities, in the
 * requested order, up to the requested maximum. Note that the results reflect the content visible to the index
 * maintenance service; callers presenting results to a user must check that the user can actually read each result,
 * for example by resolving the paths through the user's own session. No count of matching documents is exposed on
 * purpose: a meaningful total can only be obtained by resolving the results and counting the readable ones, which is
 * the caller's responsibility.
 *
 * @version $Id$
 * @since 0.9.41
 */
public final class SearchResults
{
    private final List<String> paths;

    private final long searchTimeMillis;

    private final String luceneQuery;

    /**
     * Basic constructor.
     *
     * @param paths the paths of the retrieved entities, in order
     * @param searchTimeMillis how long the index lookup took, in milliseconds
     * @param luceneQuery the string form of the actual Lucene query that was executed, for diagnostics
     */
    public SearchResults(final List<String> paths, final long searchTimeMillis, final String luceneQuery)
    {
        this.paths = paths;
        this.searchTimeMillis = searchTimeMillis;
        this.luceneQuery = luceneQuery;
    }

    /**
     * The paths of the retrieved entities.
     *
     * @return an unmodifiable list of JCR paths, may be empty
     */
    public List<String> getPaths()
    {
        return Collections.unmodifiableList(this.paths);
    }

    /**
     * How long the index lookup took.
     *
     * @return a duration in milliseconds
     */
    public long getSearchTimeMillis()
    {
        return this.searchTimeMillis;
    }

    /**
     * The string form of the actual Lucene query that was executed, useful for diagnosing why a search returned a
     * given set of results.
     *
     * @return a Lucene query string, may be {@code null} when the index was not available
     */
    public String getLuceneQuery()
    {
        return this.luceneQuery;
    }
}
