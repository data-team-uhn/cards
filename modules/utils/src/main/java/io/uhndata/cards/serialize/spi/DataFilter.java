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
package io.uhndata.cards.serialize.spi;

/**
 * A service that can help filter data when exporting multiple resources, e.g. when applying the {@code .data} processor
 * to subjects or questionnaires.
 *
 * @version $Id$
 * @since 0.9.22
 */
public interface DataFilter
{
    /** The default name of the main selector used in queries. */
    String DEFAULT_SELECTOR_NAME = "form";

    /**
     * A name for this filter type, must be unique to identify multiple instances of the same filter.
     *
     * @return a short string
     */
    String getName();

    /**
     * If this filter has any extra selectors, check if each filter instance requires its own extra selectors, or they
     * all use the same one.
     *
     * @return {@code true} if for each instance of this filter the {@link #getExtraQuerySelectors()} method must be
     *         called and appended to the query, {@code false} if {@link #getExtraQuerySelectors()} should be called
     *         only once
     */
    default boolean areExtraSelectorsPerFilterInstance()
    {
        return false;
    }

    /**
     * Get the optional query selector that need to be appended to the query for running this filter. If no extra
     * selectors are needed, return an empty string. If there are multiple instances of this filter applied, this method
     * may be called more than once, in which case different selector names must be used, depending on what
     * {@link #areExtraSelectorsPerFilterInstance()} returns. This assumes that there is a {@code form} selector already
     * present in the query, which can be used for joins.
     *
     * @return extra selectors in the JCR-SQL2 syntax, must be an empty string if no extra selectors are needed, and
     *         must start with a leading space as separator
     */
    default String getExtraQuerySelectors()
    {
        return getExtraQuerySelectors(DEFAULT_SELECTOR_NAME);
    }

    /**
     * Get the optional query selector that need to be appended to the query for running this filter. If no extra
     * selectors are needed, return an empty string. If there are multiple instances of this filter applied, this method
     * may be called more than once, in which case different selector names must be used, depending on what
     * {@link #areExtraSelectorsPerFilterInstance()} returns. This assumes that the default selector is already present
     * in the query, which can be used for joins.
     *
     * @param defaultSelectorName the name of the default selector in the query, a valid JCR selector name
     * @return extra selectors in the JCR-SQL2 syntax, must be an empty string if no extra selectors are needed, and
     *         must start with a leading space as separator
     */
    default String getExtraQuerySelectors(String defaultSelectorName)
    {
        return "";
    }

    /**
     * Get the query filter fragment corresponding to this filter. This assumes that there is a {@code form} selector
     * already present in the query, which can be used to put conditions on.
     *
     * @return query fragment in the JCR-SQL2 syntax, must start with a leading space and {@code and } to connect to the
     *         rest of the query
     */
    default String getExtraQueryConditions()
    {
        return getExtraQueryConditions(DEFAULT_SELECTOR_NAME);
    }

    /**
     * Get the query filter fragment corresponding to this filter. This uses the passed selector as the default to use,
     * unless the filter uses its own selectors.
     *
     * @param defaultSelectorName the name of the default selector in the query, a valid JCR selector name
     * @return query fragment in the JCR-SQL2 syntax, must start with a leading space and {@code and } to connect to the
     *         rest of the query
     */
    String getExtraQueryConditions(String defaultSelectorName);
}
