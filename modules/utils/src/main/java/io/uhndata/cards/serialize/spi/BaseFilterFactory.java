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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.osgi.service.component.annotations.Component;

/**
 * Base implementation for {@link DataFilterFactory} providing utility methods for parsing filters into a single value,
 * a set of values, or two sets of values.
 *
 * @version $Id$
 * @since 0.9.22
 */
@Component
public abstract class BaseFilterFactory implements DataFilterFactory
{
    /**
     * Extract a single value from the filters and pass it to a {@link DataFilter} constructor. If no value was found,
     * no filter is instantiated and an empty list is returned.
     *
     * @param filters the filter strings parsed from the selectors, as pairs of {@code filter name -> filter value},
     *            with an empty string value if the selector didn't specify one
     * @param allSelectors all the selectors, in case a filter may have other settings configurable through selectors
     * @param filterName the name of the filter to look for
     * @param filterConstructor the constructor for the actual {@link DataFilter} implementation, accepting one single
     *            parameter, the filter value
     * @return a list containing one filter that could be parsed by this factory, using the first value encountered for
     *         the filter name, or an empty list if nothing matched
     */
    protected List<DataFilter> parseSingletonFilter(final List<Pair<String, String>> filters,
        final List<String> allSelectors,
        final String filterName,
        final Function<String, DataFilter> filterConstructor)
    {
        String filterValue = StringUtils.isNotBlank(filterName) ? filters.stream()
            .filter(f -> filterName.equals(f.getKey()))
            .map(Pair::getValue)
            // Sling is weird with URL unescaping, it may end up still escaped at this point
            .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
            .findFirst().orElse(null)
            : null;
        if (StringUtils.isNotBlank(filterValue)) {
            return Collections.singletonList(filterConstructor.apply(filterValue));
        }
        return Collections.emptyList();
    }

    /**
     * Extract values from the filters and pass each of them to a {@link DataFilter} constructor. If no value was found,
     * no filter is instantiated and an empty list is returned.
     *
     * @param filters the filter strings parsed from the selectors, as pairs of {@code filter name -> filter value},
     *            with an empty string value if the selector didn't specify one
     * @param allSelectors all the selectors, in case a filter may have other settings configurable through selectors
     * @param filterName the name of the filter to look for
     * @param filterConstructor the constructor for the actual {@link DataFilter} implementation, accepting one single
     *            parameter, the filter value
     * @return a list containing as many filters as values for the filter name, or an empty list if nothing matched
     */
    protected List<DataFilter> parseMultipleSingletonFilters(final List<Pair<String, String>> filters,
        final List<String> allSelectors,
        final String filterName,
        final Function<String, DataFilter> filterConstructor)
    {
        if (StringUtils.isNotBlank(filterName)) {
            return filters.stream()
                .filter(f -> filterName.equals(f.getKey()))
                .map(Pair::getValue)
                // Sling is weird with URL unescaping, it may end up still escaped at this point
                .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
                .map(filterConstructor::apply)
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Extract all values from the filters and pass them to a {@link DataFilter} constructor. If no value was found, no
     * filter is instantiated and an empty list is returned.
     *
     * @param filters the filter strings parsed from the selectors, as pairs of {@code filter name -> filter value},
     *            with an empty string value if the selector didn't specify one
     * @param allSelectors all the selectors, in case a filter may have other settings configurable through selectors
     * @param filterName the name of the filter to look for
     * @param filterConstructor the constructor for the actual {@link DataFilter} implementation, accepting one single
     *            parameter, a set of all the filter values found for the filter name
     * @return a list containing one filter that could be parsed by this factory, using all the values encountered for
     *         the filter name, or an empty list if nothing matched
     */
    protected List<DataFilter> parseSetFilter(final List<Pair<String, String>> filters,
        final List<String> allSelectors,
        final String filterName,
        final Function<Set<String>, DataFilter> filterConstructor)
    {
        Set<String> filterValues = StringUtils.isNotBlank(filterName) ? filters.stream()
            .filter(f -> filterName.equals(f.getKey()))
            .map(Pair::getValue)
            // Sling is weird with URL unescaping, it may end up still escaped at this point
            .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
            .collect(Collectors.toSet())
            : Collections.emptySet();
        if (!filterValues.isEmpty()) {
            return Collections.singletonList(filterConstructor.apply(filterValues));
        }
        return Collections.emptyList();
    }

    /**
     * Extract two lists of values from the filters, one positive and one negative and pass each of them to a
     * {@link DataFilter} constructor. If only one of the filter names has matching values, only one filter is returned.
     * If no matching value was found, no filter is instantiated and an empty list is returned.
     *
     * @param filters the filter strings parsed from the selectors, as pairs of {@code filter name -> filter value},
     *            with an empty string value if the selector didn't specify one
     * @param allSelectors all the selectors, in case a filter may have other settings configurable through selectors
     * @param positiveFilterName the name of the first filter to look for, which will be passed to a constructor along
     *            with the {@code true} second parameter
     * @param negativeFilterName the name of the second filter to look for, which will be passed to a constructor along
     *            with the {@code false} second parameter
     * @param filterConstructor the constructor for the actual {@link DataFilter} implementation, accepting two paraters
     *            parameter, a set of filter values and a boolean parameter indicating if the values match the positive
     *            ({@code true}) or negative ({@code false}) filter name
     * @return a list containing one or two filters that could be parsed by this factory, using all the values
     *         encountered for the two filter names, or an empty list if nothing matched
     */
    protected List<DataFilter> parseDoubleSetFilters(final List<Pair<String, String>> filters,
        final List<String> allSelectors,
        final String positiveFilterName, final String negativeFilterName,
        final BiFunction<Set<String>, Boolean, DataFilter> filterConstructor)
    {
        final List<DataFilter> result = new ArrayList<>();
        Set<String> positiveFilterValues = StringUtils.isNotBlank(positiveFilterName) ? filters.stream()
            .filter(f -> positiveFilterName.equals(f.getKey()))
            .map(Pair::getValue)
            // Sling is weird with URL unescaping, it may end up still escaped at this point
            .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
            .collect(Collectors.toSet())
            : Collections.emptySet();
        if (!positiveFilterValues.isEmpty()) {
            result.add(filterConstructor.apply(positiveFilterValues, true));
        }
        Set<String> negativeFilterValues = StringUtils.isNotBlank(negativeFilterName) ? filters.stream()
            .filter(f -> negativeFilterName.equals(f.getKey()))
            .map(Pair::getValue)
            // Sling is weird with URL unescaping, it may end up still escaped at this point
            .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
            .collect(Collectors.toSet())
            : Collections.emptySet();
        if (!negativeFilterValues.isEmpty()) {
            result.add(filterConstructor.apply(negativeFilterValues, false));
        }
        return result;
    }
}
