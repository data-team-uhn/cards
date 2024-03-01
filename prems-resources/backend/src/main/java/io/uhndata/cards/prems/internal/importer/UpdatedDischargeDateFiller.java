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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.AbstractClarityDataProcessor;
import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that updates the discharge date to be more recent. This is temporarily needed to allow
 * importing patients from more than 7 days ago.
 *
 * @version $Id$
 */
@Designate(ocd = UpdatedDischargeDateFiller.Config.class)
@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class UpdatedDischargeDateFiller extends AbstractClarityDataProcessor implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdatedDischargeDateFiller.class);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final int pastDaysLimit;

    @ObjectClassDefinition(name = "Clarity import filter - Update discharge date",
        description = "Configuration for the Clarity importer to set a more recent discharge date for events that"
            + " happened too long ago")
    public @interface Config
    {
        @AttributeDefinition(name = "Enabled")
        boolean enable() default false;

        @AttributeDefinition(name = "Past days limit", description = "How many days ago is the cutoff before which the"
            + " discharge date is moved up. 1 means anything older than 24 hours ago will be moved to exactly 24 hours"
            + " before the import time.")
        int pastDaysLimit() default 7;
    }

    @Activate
    public UpdatedDischargeDateFiller(Config config)
    {
        super(config.enable(), new String[] { "inpatient-ed" }, 300);
        this.pastDaysLimit = config.pastDaysLimit();
    }

    @Override
    public Map<String, String> processEntry(final Map<String, String> input)
    {
        try {
            final Calendar discharge = Calendar.getInstance();
            final Calendar cutoff = Calendar.getInstance();
            cutoff.add(Calendar.DATE, -this.pastDaysLimit);
            discharge.setTime(DATE_FORMAT.parse(input.getOrDefault("HOSP_DISCHARGE_DTTM", "")));
            final long length = ChronoUnit.DAYS.between(cutoff.toInstant(), discharge.toInstant());
            if (length < 0) {
                input.put("HOSP_DISCHARGE_DTTM", DATE_FORMAT.format(cutoff.getTime()));
                LOGGER.warn("Updated visit {} discharge date from {} to {}",
                    input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"),
                    DATE_FORMAT.format(discharge.getTime()), DATE_FORMAT.format(cutoff.getTime()));
            }
        } catch (ParseException | NullPointerException e) {
            // We don't do anything if the date is missing or malformed
        }
        return input;
    }
}
