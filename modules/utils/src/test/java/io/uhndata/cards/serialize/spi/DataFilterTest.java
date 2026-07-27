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
package io.uhndata.cards.serialize.spi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Unit tests for the default methods of {@link DataFilter}.
 *
 * @version $Id$
 */
public class DataFilterTest
{
    /** A filter relying on all the default methods of the interface. */
    private final DataFilter filter = new DataFilter()
    {
        @Override
        public String getName()
        {
            return "status";
        }

        @Override
        public String getExtraQueryConditions(final String defaultSelectorName)
        {
            return " and " + defaultSelectorName + ".status = 'DRAFT'";
        }
    };

    @Test
    public void extraSelectorsAreSharedPerFilterNameByDefault()
    {
        assertFalse(this.filter.areExtraSelectorsPerFilterInstance());
    }

    @Test
    public void getExtraQuerySelectorsReturnsEmptyStringByDefault()
    {
        assertEquals("", this.filter.getExtraQuerySelectors("f"));
    }

    @Test
    public void getExtraQuerySelectorsUsesTheDefaultSelectorName()
    {
        assertEquals("", this.filter.getExtraQuerySelectors());
    }

    @Test
    public void getExtraQueryConditionsUsesTheDefaultSelectorName()
    {
        assertEquals(" and form.status = 'DRAFT'", this.filter.getExtraQueryConditions());
    }
}
