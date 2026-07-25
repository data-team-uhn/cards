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
package io.uhndata.cards.entityindex.internal;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the pagination scan-limit computation of the {@link EntitySearchServlet}.
 *
 * @version $Id$
 */
public class EntitySearchServletTest
{
    private static final long HARD_LIMIT = 10000;

    @Test
    public void scanLimitCoversTheBatchContainingThePage()
    {
        // The same examples documented for the .paginate servlet, whose behavior this mirrors
        Assert.assertEquals(101, EntitySearchServlet.scanLimit(0, 10, false));
        Assert.assertEquals(301, EntitySearchServlet.scanLimit(110, 10, false));
        Assert.assertEquals(751, EntitySearchServlet.scanLimit(500, 25, false));
        Assert.assertEquals(1001, EntitySearchServlet.scanLimit(501, 25, false));
    }

    @Test
    public void scanLimitIsCappedAtTheHardLimit()
    {
        // A very large offset would look far ahead, but the scan never goes past the hard cap
        Assert.assertEquals(HARD_LIMIT, EntitySearchServlet.scanLimit(1_000_000, 10, false));
    }

    @Test
    public void scanLimitScansEverythingWhenTheTotalIsWanted()
    {
        // showTotalRows means count all readable results, still bounded by the hard cap
        Assert.assertEquals(HARD_LIMIT, EntitySearchServlet.scanLimit(0, 10, true));
        Assert.assertEquals(HARD_LIMIT, EntitySearchServlet.scanLimit(500, 25, true));
    }

    @Test
    public void scanLimitToleratesAZeroPageSize()
    {
        // A zero page size must not divide by zero; it falls back to a single-page batch
        Assert.assertEquals(11, EntitySearchServlet.scanLimit(0, 0, false));
    }
}
