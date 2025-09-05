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
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.serialize.DataFiltersParser;
import io.uhndata.cards.serialize.spi.DataFilter;
import io.uhndata.cards.serialize.spi.DataFilterFactory;
import io.uhndata.cards.utils.SelectorUtils;

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
        final List<String> selectors = SelectorUtils.parseSelectors(selectorString);
        final List<Pair<String, String>> filterStrings = SelectorUtils.parseOptions("dataFilter:", selectorString);
        final List<DataFilter> filters = new ArrayList<>();
        this.filterFactories.forEach(factory -> filters.addAll(factory.parseFilters(filterStrings, selectors)));

        return new DefaultDataFilters(filters);
    }
}
