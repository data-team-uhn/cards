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
package io.uhndata.cards.forms.internal.serialize.filters;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.BaseFilterFactory;
import io.uhndata.cards.serialize.spi.DataFilter;
import io.uhndata.cards.serialize.spi.DataFilterFactory;

/**
 * Filter forms based on their status flags. Use {@code status=FLAG} to filter only forms having that flag, and
 * {@code statusNot=FLAG} to filter only forms not having that flag. Multiple flags can be required or excluded by using
 * this filter multiple times.
 *
 * @version $Id$
 * @since 0.9.22
 */
@Component
public class StatusFlags extends BaseFilterFactory implements DataFilterFactory
{
    @Override
    public List<DataFilter> parseFilters(List<Pair<String, String>> filters, List<String> allSelectors)
    {
        return parseDoubleSetFilters(filters, allSelectors, "status", "statusNot", StatusFlagsFilter::new);
    }

    static class StatusFlagsFilter implements DataFilter
    {
        private final Set<String> flagsToInclude;

        private final boolean positiveCheck;

        StatusFlagsFilter(final Set<String> flags, final boolean positiveCheck)
        {
            this.flagsToInclude = flags;
            this.positiveCheck = positiveCheck;
        }

        @Override
        public String getName()
        {
            return "statusFlags";
        }

        @Override
        public String getExtraQueryConditions(final String defaultSelectorName)
        {
            return " and ("
                + StringUtils.join(
                    this.flagsToInclude.stream()
                        // SQL escape
                        .map(flag -> flag.replaceAll("'", "''"))
                        .map(flag -> defaultSelectorName + ".statusFlags = '" + flag + "'")
                        .map(condition -> (this.positiveCheck ? "" : "not ") + condition)
                        .collect(Collectors.toList()),
                    // TODO: make this configurable, it could be an "or"
                    " and ")
                + ")";
        }

        @Override
        public String toString()
        {
            return (this.positiveCheck ? "status" : "statusNot") + "=" + this.flagsToInclude;
        }
    }

}
