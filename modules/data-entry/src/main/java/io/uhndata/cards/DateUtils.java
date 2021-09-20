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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class DateUtils
{
    /**
     * Hide the utility class constructor.
     */
    private DateUtils()
    {
    }

    /**
     * Returns the timezone as a GMT offset string for the given date string
     * provided in the format yyyy-MM-dd. If date string parsing fails, the
     * GMT offset string for the current date/time is returned instead.
     *
     * @param dateString a date string in the form of yyyy-MM-dd
     * @return the GMT offset string for the given date string at 00:00:00
     *     in the server's local timezone
     */
    public static String getTimezoneForDateString(String dateString)
    {
        SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        try {
            return new SimpleDateFormat("XXX").format(inputDateFormat.parse(dateString));
        } catch (ParseException e) {
            return new SimpleDateFormat("XXX").format(new Date());
        }
    }
}
