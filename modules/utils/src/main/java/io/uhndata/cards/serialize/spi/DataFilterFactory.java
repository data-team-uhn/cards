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

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

/**
 * A factory for parsing data filters.
 *
 * @version $Id$
 * @since 0.9.22
 */
public interface DataFilterFactory
{
    /**
     * Turn the filters that this factory knows how to handle into actual {@link DataFilter} instances, ignoring the
     * ones handled by other factories.
     *
     * @param filters the filter strings parsed from the selectors, as pairs of {@code filter name -> filter value},
     *            with an empty string value if the selector didn't specify one
     * @param allSelectors all the selectors, in case a filter may have other settings configurable through selectors
     * @return a list of filters that could be parsed by this factory, an empty list if nothing matched
     */
    List<DataFilter> parseFilters(List<Pair<String, String>> filters, List<String> allSelectors);

    /**
     * The details about any filters that may be created by this factory.
     * Intended to be displayed to users looking to retrieve data.
     *
     * @return A list of details about filters that this factory supports.
     *         May have just 1 entry if only one selector is handled by this factory
     *         or multiple entries if multiple different selectors are handled.
     */
    List<SelectorDetails> getFilterDetails();
}
