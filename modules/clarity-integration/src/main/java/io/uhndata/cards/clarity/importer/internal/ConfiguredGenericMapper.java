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

package io.uhndata.cards.clarity.importer.internal;

import java.util.Map;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that assigns a specific value to a column based on OSGi configured conditions.
 *
 * @version $Id$
 */
@Component
@Designate(ocd = ConfiguredGenericMapper.Config.class, factory = true)
public class ConfiguredGenericMapper extends AbstractConditionalClarityDataProcessor implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguredGenericMapper.class);

    @ObjectClassDefinition(name = "Clarity import filter - Value mapping conditions",
        description = "Configuration for the Clarity importer to assign a specific value to a column")
    public @interface Config
    {
        @AttributeDefinition(name = "Priority", description = "Clarity Data Processor priority."
            + " Processors are run in ascending priority order")
        int priority();

        @AttributeDefinition(name = "Column", description = "The column to be assigned")
        String column();

        @AttributeDefinition(name = "Value",
            description = "The value to be assigned. Use \"%{COLUMN NAME}%\" to use the value of a different column.")
        String value();

        @AttributeDefinition(name = "Conditions",
            description = "Conditions for this value to be assigned."
                + " Included operators are:"
                + "\n - Case insensitive string comparisons '<>' and '='"
                + "\n - Case insensitive list comparisons 'in' and 'not in'. Split values by ';' eg. COLUMN in a; b; c"
                + "\n - Regex comparisons 'matches' and 'not matches'"
                + "\n - Double comparisons '<=', '<', '>=' and '>'"
                + "\n - Unary operators 'is empty' and 'is not empty'"
                + "\nFor example \"COLUMN_NAME is empty\".")
        String[] conditions();

        @AttributeDefinition
        String service_pid();
    }

    private final String column;

    private final String value;

    private final String id;

    @Activate
    public ConfiguredGenericMapper(Config configuration) throws ConfigurationException
    {
        super(configuration.priority(), configuration.conditions());
        this.column = configuration.column();
        this.value = configuration.value();
        String pid = configuration.service_pid();
        this.id = pid.substring(pid.lastIndexOf("~") + 1);
    }

    @Override
    protected Map<String, String> handleAllConditionsMatched(Map<String, String> input)
    {
        String usedValue = this.value;
        if (usedValue.matches("%\\{.*\\}%")) {
            usedValue = input.get(usedValue.substring(2, usedValue.length() - 1));
        }
        input.put(this.column, usedValue);
        LOGGER.warn("{} Updated visit {} value for column {} set to {} due to all conditions met", this.id,
            input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"), this.column, usedValue);
        return input;
    }

    @Override
    protected Map<String, String> handleUnmatchedCondition(Map<String, String> input, String condition)
    {
        return input;
    }
}
