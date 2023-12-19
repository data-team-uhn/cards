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

import java.util.HashMap;
import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.AbstractClarityDataProcessor;
import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that sends the long-form CPESIC questionnaire to a small percentage of patients from each
 * department. The percentage/department configuration is done through an OSGi service configuration.
 *
 * @version $Id$
 */
@Component
@Designate(ocd = SendCPESForDepartmentFrequency.SendCPESForDepartmentFrequencyConfigDefinition.class)
public class SendCPESForDepartmentFrequency extends AbstractClarityDataProcessor implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SendCPESForDepartmentFrequency.class);

    @ObjectClassDefinition(name = "Clarity import filter - CPESIC percentage",
        description = "Configuration for the Clarity importer filter sending the CPESIC questionnaire to a percentage"
            + " of the total visits registered for each department")
    public @interface SendCPESForDepartmentFrequencyConfigDefinition
    {
        @AttributeDefinition(name = "Default Frequency", description = "For example \"0.04\".", defaultValue = "0.04")
        double default_frequency();

        @AttributeDefinition(name = "Per Department Frequency", description = "For example \"Department name = 0.02\".")
        String[] frequency_per_department();
    }

    private final double defaultFrequency;

    private final Map<String, Double> perDepartmentFrequency;

    @Activate
    public SendCPESForDepartmentFrequency(SendCPESForDepartmentFrequencyConfigDefinition configuration)
    {
        super(true, new String[] { "prems" }, 120);
        this.defaultFrequency = configuration.default_frequency();
        this.perDepartmentFrequency = new HashMap<>(configuration.frequency_per_department().length);
        for (String clinic : configuration.frequency_per_department()) {
            String[] pieces = clinic.split("\\s*=\\s*");
            if (pieces.length != 2) {
                continue;
            }
            this.perDepartmentFrequency.put(pieces[0], Double.valueOf(pieces[1]));
        }
    }

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        final String department = input.get("DISCH_DEPT_NAME");
        if ((input.get("CLINIC") == null || input.get("CLINIC").length() == 0)
            && Math.random() < this.perDepartmentFrequency.getOrDefault(department, this.defaultFrequency)) {
            input.put("CLINIC", "/Survey/ClinicMapping/2075099");

            LOGGER.warn("Mapped visit {} to /Survey/ClinicMapping/2075099",
                input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"));
        }
        return input;
    }
}
