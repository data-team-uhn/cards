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
package io.uhndata.cards.utils;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link DateUtils}.
 *
 * @version $Id$
 */
public class DateUtilsTest
{
    @Test
    public void parseCalendarWithNullReturnsNull()
    {
        assertNull(DateUtils.parseCalendar(null));
    }

    @Test
    public void parseCalendarWithBlankReturnsNull()
    {
        assertNull(DateUtils.parseCalendar("  "));
    }

    @Test
    public void parseCalendarWithUnsupportedFormatReturnsNull()
    {
        assertNull(DateUtils.parseCalendar("not a date"));
    }

    @Test
    public void parseCalendarWithPreferredFormat()
    {
        Calendar result = DateUtils.parseCalendar("1999-12-31T20:30:00.000+05:00");
        assertEquals(1999, result.get(Calendar.YEAR));
        assertEquals(Calendar.DECEMBER, result.get(Calendar.MONTH));
        assertEquals(31, result.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void parseCalendarWithDateOnlyFormat()
    {
        Calendar result = DateUtils.parseCalendar("2023-01-15");
        assertEquals(2023, result.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, result.get(Calendar.MONTH));
        assertEquals(15, result.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, result.get(Calendar.HOUR_OF_DAY));
    }

    @Test
    public void parseCalendarWithSlashesFormat()
    {
        Calendar result = DateUtils.parseCalendar("1/15/2023");
        assertEquals(2023, result.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, result.get(Calendar.MONTH));
        assertEquals(15, result.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void parseDateTimeWithNullReturnsNull()
    {
        assertNull(DateUtils.parseDateTime(null));
    }

    @Test
    public void parseDateTimeWithBlankReturnsNull()
    {
        assertNull(DateUtils.parseDateTime(" "));
    }

    @Test
    public void parseDateTimeWithUnsupportedFormatReturnsNull()
    {
        assertNull(DateUtils.parseDateTime("not a date"));
    }

    @Test
    public void parseDateTimeWithTimezoneKeepsTimezone()
    {
        ZonedDateTime result = DateUtils.parseDateTime("1999-12-31T20:30:00.000+05:00");
        assertEquals(1999, result.getYear());
        assertEquals(12, result.getMonthValue());
        assertEquals(31, result.getDayOfMonth());
        assertEquals(20, result.getHour());
        assertEquals("+05:00", result.getOffset().getId());
    }

    @Test
    public void parseDateTimeWithoutTimezoneUsesSystemDefault()
    {
        ZonedDateTime result = DateUtils.parseDateTime("2023-01-15T10:20:30");
        assertEquals(2023, result.getYear());
        assertEquals(10, result.getHour());
        assertEquals(ZoneId.systemDefault(), result.getZone());
    }

    @Test
    public void parseDateTimeWithDateOnlyDefaultsToMidnight()
    {
        ZonedDateTime result = DateUtils.parseDateTime("2023-01-15");
        assertEquals(0, result.getHour());
        assertEquals(0, result.getMinute());
    }

    @Test
    public void atMidnightForZonedDateTimeResetsTime()
    {
        ZonedDateTime date = ZonedDateTime.of(2023, 1, 15, 10, 20, 30, 400, ZoneId.systemDefault());
        ZonedDateTime result = DateUtils.atMidnight(date);
        assertEquals(LocalDate.of(2023, 1, 15), result.toLocalDate());
        assertEquals(0, result.getHour());
        assertEquals(0, result.getMinute());
        assertEquals(0, result.getSecond());
        assertEquals(0, result.getNano());
    }

    @Test
    public void atMidnightForCalendarResetsTime()
    {
        Calendar date = Calendar.getInstance();
        date.set(2023, Calendar.JANUARY, 15, 10, 20, 30);
        Calendar result = DateUtils.atMidnight(date);
        assertEquals(2023, result.get(Calendar.YEAR));
        assertEquals(15, result.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, result.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, result.get(Calendar.MINUTE));
        assertEquals(0, result.get(Calendar.SECOND));
        assertEquals(0, result.get(Calendar.MILLISECOND));
        // The input calendar is not modified
        assertEquals(10, date.get(Calendar.HOUR_OF_DAY));
    }

    @Test
    public void toStringForNullCalendarReturnsNull()
    {
        assertNull(DateUtils.toString((Calendar) null));
    }

    @Test
    public void toStringForCalendarUsesPreferredFormat()
    {
        Calendar date = DateUtils.parseCalendar("1999-12-31T20:30:00.000+05:00");
        // The parsed calendar is in the system timezone, so re-serializing yields an equivalent, not identical, string
        assertEquals(date.getTimeInMillis(), DateUtils.parseCalendar(DateUtils.toString(date)).getTimeInMillis());
    }

    @Test
    public void toStringForUnformattableCalendarReturnsNull()
    {
        Calendar broken = Calendar.getInstance();
        broken.setLenient(false);
        broken.set(Calendar.MONTH, 42);
        assertNull(DateUtils.toString(broken));
    }

    @Test
    public void toStringForNullTemporalAccessorReturnsNull()
    {
        assertNull(DateUtils.toString((java.time.temporal.TemporalAccessor) null));
    }

    @Test
    public void toStringForZonedDateTimeUsesPreferredFormat()
    {
        ZonedDateTime date = ZonedDateTime.of(1999, 12, 31, 20, 30, 0, 0, ZoneId.of("+05:00"));
        assertEquals("1999-12-31T20:30:00.000+05:00", DateUtils.toString(date));
    }

    @Test
    public void toStringForUnsupportedTemporalAccessorReturnsNull()
    {
        // A MonthDay doesn't hold enough fields for the preferred datetime format
        assertNull(DateUtils.toString(MonthDay.of(12, 31)));
    }

    @Test
    public void normalizeCompletesPartialDate()
    {
        String result = DateUtils.normalize("2023-01-15");
        ZonedDateTime expected = LocalDate.of(2023, 1, 15).atStartOfDay(ZoneId.systemDefault());
        assertEquals(DateUtils.toString(expected), result);
    }

    @Test
    public void normalizeWithUnsupportedFormatReturnsNull()
    {
        assertNull(DateUtils.normalize("not a date"));
    }
}
