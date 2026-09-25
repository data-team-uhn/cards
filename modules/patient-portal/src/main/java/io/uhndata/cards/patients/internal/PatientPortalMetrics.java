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
package io.uhndata.cards.patients.internal;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.metrics.api.MetricsManager;

/**
 * Defines the metrics the patient portal always counts: the appointments imported, the patients who responded, and
 * the surveys submitted. The daily report goes out in the morning, so the imports close their period just before it,
 * to show whether that night's imports did their job, while the survey counts close theirs at the end of the day.
 * Both schedules can be moved for a deployment whose report goes out at another time.
 *
 * @version $Id$
 * @since 0.9.42
 */
@Designate(ocd = PatientPortalMetrics.Config.class)
@Component(immediate = true)
public class PatientPortalMetrics
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PatientPortalMetrics.class);

    @Reference
    private MetricsManager metricsManager;

    @ObjectClassDefinition(name = "Patient portal metrics",
        description = "When the metrics counted by the patient portal close their periods")
    public @interface Config
    {
        @AttributeDefinition(name = "Imports roll-over schedule",
            description = "Quartz cron expression closing the period of the imported appointments count, just before"
                + " the daily report, so that it shows that night's imports")
        String importsRolloverSchedule() default "0 55 8 * * ?";

        @AttributeDefinition(name = "Surveys roll-over schedule",
            description = "Quartz cron expression closing the period of the survey submission counts")
        String surveysRolloverSchedule() default MetricsManager.END_OF_DAY;
    }

    @Activate
    protected void activate(final Config config)
    {
        define("ImportedAppointments", "Imported Appointments", 1, config.importsRolloverSchedule());
        define("AppointmentSurveysSubmitted", "Number Of Patients Who Responded", 2, config.surveysRolloverSchedule());
        define("TotalSurveysSubmitted", "Number Of Surveys Submitted", 3, config.surveysRolloverSchedule());
    }

    private void define(final String name, final String label, final long order, final String schedule)
    {
        try {
            this.metricsManager.createMetric(name).withLabel(label).withDefaultOrder(order)
                .withRolloverSchedule(schedule).create();
        } catch (final RuntimeException e) {
            // Counting is not worth failing over, the metric is defined again at the next activation
            LOGGER.error("Failed to define the metric {}: {}", name, e.getMessage(), e);
        }
    }
}
