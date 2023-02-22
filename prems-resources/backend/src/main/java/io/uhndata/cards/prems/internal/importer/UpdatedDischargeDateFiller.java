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

package io.uhndata.cards.prems.internal.importer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that updates the discharge date to be more recent. This is temporarily needed to allow
 * importing patients from more than 7 days ago.
 *
 * @version $Id$
 */
@Component
public class UpdatedDischargeDateFiller implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdatedDischargeDateFiller.class);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public Map<String, String> processEntry(final Map<String, String> input)
    {
        try {
            final Calendar discharge = Calendar.getInstance();
            final Calendar cutoff = Calendar.getInstance();
            cutoff.add(Calendar.DATE, -7);
            discharge.setTime(DATE_FORMAT.parse(input.getOrDefault("HOSP_DISCHARGE_DTTM", "")));
            final long length = ChronoUnit.DAYS.between(cutoff.toInstant(), discharge.toInstant());
            if (length <= 0) {
                input.put("HOSP_DISCHARGE_DTTM", DATE_FORMAT.format(cutoff.getTime()));
                LOGGER.warn("Updated visit {} discharge date from {} to {}",
                    input.getOrDefault("PAT_ENC_CSN_ID", "Unknown"), DATE_FORMAT.format(discharge.getTime()),
                    DATE_FORMAT.format(cutoff.getTime()));
            }
        } catch (ParseException | NullPointerException e) {
            // We don't do anything if the date is missing or malformed
        }
        return input;
    }

    @Override
    public int getPriority()
    {
        return 300;
    }
}
