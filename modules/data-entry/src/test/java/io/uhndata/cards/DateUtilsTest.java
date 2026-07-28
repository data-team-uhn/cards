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
package io.uhndata.cards;

import java.time.Instant;
import java.util.TimeZone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link DateUtils}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class DateUtilsTest
{
    @Test
    public void getTimezoneForDateString()
    {
        String timezone = DateUtils.getTimezoneForDateString("2023-01-01");
        String expectedTimezone = TimeZone.getDefault().toZoneId().getRules().getStandardOffset(Instant.now()).getId();
        assertEquals(expectedTimezone, timezone);
    }

    @Test
    public void getTimezoneForDateStringCatchesException()
    {
        String timezone = DateUtils.getTimezoneForDateString("notParsable");
        String expectedTimezone = TimeZone.getDefault().toZoneId().getRules().getOffset(Instant.now()).getId();
        assertEquals(expectedTimezone, timezone);
    }

}
