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

package io.uhndata.cards.utils;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link DateUtils}.
 *
 * @version $Id$
 */
public class DateUtilsTest
{
    @Test
    public void testPreferredFormatKeepsNumericZeroOffset()
    {
        // A zero offset must be written as +00:00, not as Z, since the repository expects a numeric offset
        final ZonedDateTime utc = ZonedDateTime.of(2020, 12, 31, 20, 30, 0, 0, ZoneOffset.UTC);
        Assert.assertEquals("2020-12-31T20:30:00.000+00:00", DateUtils.toString(utc));
    }

    @Test
    public void testToStringUsesTheDateOwnOffset()
    {
        final ZonedDateTime date = ZonedDateTime.of(2020, 12, 31, 20, 30, 0, 0, ZoneOffset.ofHours(5));
        Assert.assertEquals("2020-12-31T20:30:00.000+05:00", DateUtils.toString(date));
    }

    @Test
    public void testToStringOfCalendarUsesTheCalendarOwnTimezone()
    {
        // The date must be serialized the way it was recorded, not converted to the timezone of the server
        final Calendar date = new GregorianCalendar(TimeZone.getTimeZone("GMT+05:00"));
        date.clear();
        date.set(2020, Calendar.DECEMBER, 31, 20, 30, 0);
        Assert.assertEquals("2020-12-31T20:30:00.000+05:00", DateUtils.toString(date));
    }

    @Test
    public void testToZonedDateTimeKeepsTheStoredTimezone()
    {
        final Calendar date = new GregorianCalendar(TimeZone.getTimeZone("GMT-04:00"));
        date.clear();
        date.set(2020, Calendar.JUNE, 15, 9, 0, 0);
        final ZonedDateTime converted = DateUtils.toZonedDateTime(date);
        Assert.assertEquals(ZoneOffset.ofHours(-4), converted.getOffset());
        Assert.assertEquals(9, converted.getHour());
        Assert.assertEquals(15, converted.getDayOfMonth());
    }

    @Test
    public void testParseDateTimeReadsAnOffsetFromTheString()
    {
        final ZonedDateTime parsed = DateUtils.parseDateTime("2020-12-31T20:30:00.000+05:00");
        Assert.assertEquals(ZoneOffset.ofHours(5), parsed.getOffset());
        Assert.assertEquals(20, parsed.getHour());
    }

    @Test
    public void testParseDateTimeAcceptsAllTheSupportedFormats()
    {
        // Every one of these must resolve to the same local date and time, with the missing parts defaulted
        final String[] equivalent = {
            "2020-12-31T20:30:00.000",
            "2020-12-31 20:30:00.000",
            "2020-12-31T20:30:00",
            "2020-12-31 20:30:00",
            "2020-12-31T20:30",
            "2020-12-31 20:30"
        };
        for (String date : equivalent) {
            final ZonedDateTime parsed = DateUtils.parseDateTime(date);
            Assert.assertNotNull("Failed to parse " + date, parsed);
            Assert.assertEquals(date, 2020, parsed.getYear());
            Assert.assertEquals(date, 12, parsed.getMonthValue());
            Assert.assertEquals(date, 31, parsed.getDayOfMonth());
            Assert.assertEquals(date, 20, parsed.getHour());
            Assert.assertEquals(date, 30, parsed.getMinute());
            Assert.assertEquals(date, 0, parsed.getSecond());
        }
    }

    @Test
    public void testParseDateTimeDefaultsTheTimeToMidnight()
    {
        final ZonedDateTime parsed = DateUtils.parseDateTime("2020-12-31");
        Assert.assertEquals(0, parsed.getHour());
        Assert.assertEquals(0, parsed.getMinute());
        Assert.assertEquals(0, parsed.getSecond());
        Assert.assertEquals(31, parsed.getDayOfMonth());
    }

    @Test
    public void testParseDateTimeAcceptsSlashSeparatedDates()
    {
        final ZonedDateTime parsed = DateUtils.parseDateTime("3/15/2020");
        Assert.assertEquals(2020, parsed.getYear());
        Assert.assertEquals(3, parsed.getMonthValue());
        Assert.assertEquals(15, parsed.getDayOfMonth());
    }

    @Test
    public void testParseDateTimeUsesTheSystemTimezoneWhenTheStringHasNone()
    {
        final ZonedDateTime parsed = DateUtils.parseDateTime("2020-12-31T20:30:00");
        Assert.assertEquals(ZoneId.systemDefault().getRules().getOffset(parsed.toInstant()), parsed.getOffset());
    }

    @Test
    public void testParseDateTimeRejectsUnusableInput()
    {
        Assert.assertNull(DateUtils.parseDateTime(null));
        Assert.assertNull(DateUtils.parseDateTime(""));
        Assert.assertNull(DateUtils.parseDateTime("  "));
        Assert.assertNull(DateUtils.parseDateTime("not a date"));
        Assert.assertNull(DateUtils.parseDateTime("2020-13-45"));
    }

    @Test
    public void testParseCalendarMatchesParseDateTime()
    {
        final String date = "2020-12-31T20:30:00.000+05:00";
        Assert.assertEquals(DateUtils.parseDateTime(date).toInstant().toEpochMilli(),
            DateUtils.parseCalendar(date).getTimeInMillis());
    }

    @Test
    public void testParseCalendarRejectsUnusableInput()
    {
        Assert.assertNull(DateUtils.parseCalendar(null));
        Assert.assertNull(DateUtils.parseCalendar(""));
        Assert.assertNull(DateUtils.parseCalendar("not a date"));
    }

    @Test
    public void testAtMidnightDropsTheTimeOfDay()
    {
        final ZonedDateTime date = ZonedDateTime.of(2020, 12, 31, 20, 30, 45, 123000000, ZoneOffset.ofHours(5));
        final ZonedDateTime midnight = DateUtils.atMidnight(date);
        Assert.assertEquals("2020-12-31T00:00:00.000+05:00", DateUtils.toString(midnight));
    }

    @Test
    public void testAtMidnightOfCalendarLeavesTheSourceUntouched()
    {
        final Calendar date = new GregorianCalendar(TimeZone.getTimeZone("GMT+05:00"));
        date.clear();
        date.set(2020, Calendar.DECEMBER, 31, 20, 30, 45);
        final Calendar midnight = DateUtils.atMidnight(date);
        Assert.assertEquals("2020-12-31T00:00:00.000+05:00", DateUtils.toString(midnight));
        Assert.assertEquals("The source date must not be modified", 20, date.get(Calendar.HOUR_OF_DAY));
    }

    @Test
    public void testNormalizeRewritesAnySupportedFormatToTheCanonicalOne()
    {
        Assert.assertEquals("2020-12-31T20:30:00.000+05:00",
            DateUtils.normalize("2020-12-31 20:30:00.000+05:00"));
        Assert.assertNull(DateUtils.normalize("not a date"));
        Assert.assertNull(DateUtils.normalize(null));
    }

    @Test
    public void testToStringOfNullIsNull()
    {
        Assert.assertNull(DateUtils.toString((Calendar) null));
        Assert.assertNull(DateUtils.toString((ZonedDateTime) null));
        Assert.assertNull(DateUtils.toZonedDateTime(null));
    }
}
