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

import org.apache.commons.lang3.tuple.Pair;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.BaseFilterFactory;
import io.uhndata.cards.serialize.spi.DataFilter;
import io.uhndata.cards.serialize.spi.DataFilterFactory;
import io.uhndata.cards.utils.DateUtils;

/**
 * Filter forms based on when they were last modified. Use {@code modifiedAfter=datetime} to filter only forms modified
 * at or after that time. Only one value is allowed, and if multiple instances of this filter are used, only the first
 * value is taken into account.
 *
 * @version $Id$
 * @since 0.9.22
 */
@Component
public class ModifiedAfter extends BaseFilterFactory implements DataFilterFactory
{
    @Override
    public List<DataFilter> parseFilters(List<Pair<String, String>> filters, List<String> allSelectors)
    {
        return parseSingletonFilter(filters, allSelectors, "modifiedAfter", ModifiedAfterFilter::new);
    }

    static class ModifiedAfterFilter implements DataFilter
    {
        private final String time;

        ModifiedAfterFilter(final String time)
        {
            this.time = DateUtils.normalize(time);
        }

        @Override
        public String getName()
        {
            return "modifiedAfter";
        }

        @Override
        public String getExtraQueryConditions(final String defaultSelectorName)
        {
            return " and " + defaultSelectorName + ".[jcr:lastModified] >= '" + this.time.replaceAll("'", "''") + "'";
        }

        @Override
        public String toString()
        {
            return "modifiedAfter=" + this.time;
        }
    }
}
