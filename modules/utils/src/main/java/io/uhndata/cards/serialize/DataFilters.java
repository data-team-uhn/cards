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
package io.uhndata.cards.serialize;

import java.util.List;

import io.uhndata.cards.serialize.spi.DataFilter;

/**
 * Aggregate of data filters providing convenient access to the query fragments brought in by the filters.
 *
 * @version $Id$
 * @since 0.9.22
 */
public interface DataFilters
{
    /**
     * Get the list of filters being aggregated in this object.
     *
     * @return a list of filters, may be empty
     */
    List<DataFilter> getFilters();

    /**
     * Get the optional query selectors that need to be appended to the query for running these filters. If no extra
     * selectors are needed, return an empty string. This assumes that there is a {@code form} selector already present
     * in the query. The result, if not blank, will be a series of {@code inner join ...} statements.
     *
     * @return extra selectors in the JCR-SQL2 syntax, must be an empty string if no extra selectors are needed
     */
    String getExtraQuerySelectors();

    /**
     * Get the query filter fragment aggregated from the filters. The result, if not blank, will be a series of
     * {@code and ...} statements. This assumes that there is a {@code form} selector and a condition already present in
     * the query, since the result starts with an {@code and}. If there is no starting condition, the leading
     * {@code and} must be explicitly trimmed from the result.
     *
     * @return query fragment in the JCR-SQL2 syntax, starting with {@code and}, must be an empty string if no filters
     *         are present
     */
    String getExtraQueryConditions();
}
