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

package io.uhndata.cards.metrics.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Unit tests for {@link Metric.AccessLevel}.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class AccessLevelTest
{
    @Test
    public void serializesAsLowercase()
    {
        assertEquals("public", Metric.AccessLevel.PUBLIC.asPropertyValue());
        assertEquals("admin", Metric.AccessLevel.ADMIN.asPropertyValue());
    }

    @Test
    public void parsesAdminInAnyCase()
    {
        assertSame(Metric.AccessLevel.ADMIN, Metric.AccessLevel.fromPropertyValue("admin"));
        assertSame(Metric.AccessLevel.ADMIN, Metric.AccessLevel.fromPropertyValue("ADMIN"));
    }

    @Test
    public void anythingElseIsPublic()
    {
        assertSame(Metric.AccessLevel.PUBLIC, Metric.AccessLevel.fromPropertyValue("public"));
        assertSame(Metric.AccessLevel.PUBLIC, Metric.AccessLevel.fromPropertyValue(null));
        assertSame(Metric.AccessLevel.PUBLIC, Metric.AccessLevel.fromPropertyValue("garbage"));
    }

    @Test
    public void listsBothLevels()
    {
        assertEquals(2, Metric.AccessLevel.values().length);
        assertSame(Metric.AccessLevel.PUBLIC, Metric.AccessLevel.valueOf("PUBLIC"));
        assertSame(Metric.AccessLevel.ADMIN, Metric.AccessLevel.valueOf("ADMIN"));
    }
}
