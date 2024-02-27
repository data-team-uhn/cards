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
package io.uhndata.cards.serialize.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.serialize.DataFiltersParser;
import io.uhndata.cards.serialize.spi.DataFilter;
import io.uhndata.cards.serialize.spi.DataFilterFactory;

/**
 * Default implementation for {@link DataFiltersParser}.
 *
 * @version $Id$
 * @since 0.9.22
 */
@Component
public class DefaultDataFiltersParser implements DataFiltersParser
{
    @Reference
    private volatile List<DataFilterFactory> filterFactories;

    @Override
    public DefaultDataFilters parseFilters(String selectorString)
    {
        // First parse the selectors string into individual selectors.
        // Split by unescaped dots. A backslash escapes a dot, but two backslashes are just one escaped backslash.
        // Match by:
        // - no preceding backslash, i.e. start counting at the first backslash (?<!\)
        // - an even number of backslashes, i.e. any number of groups of two backslashes (?:\\)*
        // - a literal dot \.
        // Each backslash, except the \., is escaped twice, once as a special escape char inside a Java string, and
        // once as a special escape char inside a RegExp. The one before the dot is escaped only once as a special
        // char inside a Java string, since it must retain its escaping meaning in the RegExp.
        final List<String> selectors = Arrays.asList(selectorString.split("(?<!\\\\)(?:\\\\\\\\)*\\."));

        // Then, parse the dataFilter selectors into key=value pairs
        final List<Pair<String, String>> filterStrings = selectors.stream()
            .filter(s -> StringUtils.startsWith(s, "dataFilter:"))
            .map(s -> StringUtils.substringAfter(s, "dataFilter:"))
            .map(s -> Pair.of(StringUtils.substringBefore(s, "="),
                // Also unescape inner dots, if present
                // Escaped dot: \.
                // As part of a regular expression, both characters need to be escaped: \\\.
                // And as a Java string literal, each backslash must be escaped: \\\\\\.
                StringUtils.substringAfter(s, "=").replaceAll("\\\\\\.", ".")))
            .collect(Collectors.toList());

        // Finally, pass the extracted filter pairs to all the filter factories and gather the result
        final List<DataFilter> filters = new ArrayList<>();
        this.filterFactories.forEach(factory -> filters.addAll(factory.parseFilters(filterStrings, selectors)));

        return new DefaultDataFilters(filters);
    }
}
