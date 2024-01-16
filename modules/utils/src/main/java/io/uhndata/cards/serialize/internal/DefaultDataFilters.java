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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.uhndata.cards.serialize.DataFilters;
import io.uhndata.cards.serialize.spi.DataFilter;

/**
 * Default implementation for {@link DataFilters}.
 *
 * @version $Id$
 * @since 0.9.22
 */
public class DefaultDataFilters implements DataFilters
{
    private final List<DataFilter> filters;

    DefaultDataFilters(final List<DataFilter> filters)
    {
        this.filters = Collections.unmodifiableList(filters);
    }

    @Override
    public List<DataFilter> getFilters()
    {
        return this.filters;
    }

    @Override
    public String getExtraQuerySelectors()
    {
        final StringBuilder result = new StringBuilder();
        final Set<String> seenFilters = new HashSet<>();
        for (DataFilter filter : this.filters) {
            if (filter.areExtraSelectorsPerFilterInstance() || seenFilters.add(filter.getName())) {
                result.append(filter.getExtraQuerySelectors());
            }
        }
        return result.toString();
    }

    @Override
    public String getExtraQueryConditions()
    {
        final StringBuilder result = new StringBuilder();
        for (DataFilter filter : this.filters) {
            result.append(filter.getExtraQueryConditions());
        }
        return result.toString();
    }
}
