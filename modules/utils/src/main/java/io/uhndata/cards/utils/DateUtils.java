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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public final class DateUtils
{
    /** The preferred date format, as a formatter object that can format Date/Calendar instances. */
    public static final DateFormat PREFERRED_CALENDAR_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /** The preferred date format, as a formatter object that can format {@code ZonedDateTime} instances. */
    public static final DateTimeFormatter PREFERRED_DATETIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    /** Supported date formats. */
    public static final List<DateFormat> CALENDAR_FORMATS = Arrays.asList(
        PREFERRED_CALENDAR_FORMAT,
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSz"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ssz"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm"),
        new SimpleDateFormat("yyyy-MM-dd"),
        new SimpleDateFormat("M/d/y"));

    /** Supported date formats. */
    public static final List<DateTimeFormatter> DATETIME_FORMATS = Arrays.asList(
        PREFERRED_DATETIME_FORMAT,
        getFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz"),
        getFormat("yyyy-MM-dd' 'HH:mm:ss.SSSz"),
        getFormat("yyyy-MM-dd'T'HH:mm:ssz"),
        getFormat("yyyy-MM-dd' 'HH:mm:ssz"),
        getFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        getFormat("yyyy-MM-dd' 'HH:mm:ss.SSS"),
        getFormat("yyyy-MM-dd'T'HH:mm:ss"),
        getFormat("yyyy-MM-dd' 'HH:mm:ss"),
        getFormat("yyyy-MM-dd'T'HH:mm"),
        getFormat("yyyy-MM-dd' 'HH:mm"),
        getFormat("yyyy-MM-dd"),
        getFormat("M/d/y"));

    /**
     * Hide the utility class constructor.
     */
    private DateUtils()
    {
        // Nothing to do, just making the class uninstantiable
    }

    private static DateTimeFormatter getFormat(final String format)
    {
        return new DateTimeFormatterBuilder()
            .appendPattern(format)
            .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .toFormatter();
    }

    /**
     * Parses a date from the given input string as a {@link Calendar} object.
     *
     * @param str the serialized date to parse
     * @return the parsed date, or {@code null} if the date cannot be parsed
     */
    public static Calendar parseCalendar(final String str)
    {
        final Date date = CALENDAR_FORMATS.stream().map(format -> {
            try {
                return format.parse(str);
            } catch (Exception ex) {
                return null;
            }
        }).filter(Objects::nonNull).findFirst().orElse(null);
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }

    /**
     * Parses a date from the given input string as a {@link ZonedDateTime} object.
     *
     * @param str the serialized date to parse
     * @return the parsed date, or {@code null} if the date cannot be parsed
     */
    public static ZonedDateTime parseDateTime(final String str)
    {
        // We try parsing the date with each of the accepted formats, in descending order of specificity
        final ZonedDateTime date = DATETIME_FORMATS.stream().map(format -> {
            try {
                // The date string may or may not have a timezone offset, try both a zoned and a local datetime
                TemporalAccessor result = format.parseBest(str, ZonedDateTime::from, LocalDateTime::from);
                if (result instanceof ZonedDateTime) {
                    // Good, we managed to parse the timezone from the string, just return the result
                    return (ZonedDateTime) result;
                } else if (result instanceof LocalDateTime) {
                    // No timezone in the string, use the system default
                    return ((LocalDateTime) result).atZone(ZoneId.systemDefault());
                }
            } catch (Exception ex) {
                // Not important, the date string doesn't match the expected format, just try the next format
            }
            return null;
        }).filter(Objects::nonNull).findFirst().orElse(null);
        if (date == null) {
            return null;
        }
        return date;
    }

    /**
     * Serialize a date to the canonical format expected by JCR.
     *
     * @param date a date object, may be null
     * @return a date serialization in the {@code 1999-12-31T20:30:00.000+05:00} format, or {@code null} if the input
     *         date was {@code null}
     */
    public static String toString(final Calendar date)
    {
        if (date == null) {
            return null;
        }
        try {
            return PREFERRED_CALENDAR_FORMAT.format(date.getTime());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Serialize a date to the canonical format expected by JCR.
     *
     * @param date a date object, may be null
     * @return a date serialization in the {@code 1999-12-31T20:30:00.000+05:00} format, or {@code null} if the input
     *         date was {@code null}
     */
    public static String toString(final TemporalAccessor date)
    {
        if (date == null) {
            return null;
        }
        try {
            return PREFERRED_DATETIME_FORMAT.format(date);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reformat a date string to the format expected by JCR. This may involve adding missing information, e.g. setting
     * the time to midnight if no time is provided, or using the system default timezone if one isn't specified.
     *
     * @param date a date string in any of the supported {@link #DATETIME_FORMATS formats} may be {@code null}
     * @return a date serialization in the {@code 1999-12-31T20:30:00.000+05:00} format, or {@code null} if the input
     *         date was {@code null} or in an unsupported format
     */
    public static String normalize(final String date)
    {
        return toString(parseDateTime(date));
    }
}
