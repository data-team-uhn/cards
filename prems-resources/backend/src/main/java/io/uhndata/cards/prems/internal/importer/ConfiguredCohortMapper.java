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

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that assigns a cohort to an imported visit based on OSGI configured conditons.
 *
 * @version $Id$
 */
@Designate(ocd = ConfiguredCohortMapper.Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public class ConfiguredCohortMapper extends AbstractConditionalClarityDataProcessor implements ClarityDataProcessor
{
    @ObjectClassDefinition(name = "Clarity import filter - Cohort mapping conditions",
    description = "Configuration for the Clarity importer to map visits matching these conditions to a specified"
        + " cohort")
    public static @interface Config
    {
        @AttributeDefinition(name = "Priority", description = "Priority")
        int priority();

        @AttributeDefinition(name = "ClinicId",
            description = "Clinic mapping path that should be assigned if all conditions are met"
                + " (eg. /Survey/ClinicMapping/123456789)")
        String clinicId();

        @AttributeDefinition(name = "Conditions", description = "Conditions for this cohort to be assigned."
            + " For example \"COLUMN_NAME is empty\".")
        String[] conditions();
    }

    private final String cohort;

    @Activate
    public ConfiguredCohortMapper(Config configuration)
    {
        super(configuration.priority(), configuration.conditions());
        this.cohort = configuration.clinicId();
    }

    @Override
    protected Map<String, String> handleAllConditionsMatched(Map<String, String> input)
    {
        input.put("CLINIC", this.cohort);
        return input;
    }

    @Override
    protected Map<String, String> handleUnmatchedCondition(Map<String, String> input)
    {
        return input;
    }
}
