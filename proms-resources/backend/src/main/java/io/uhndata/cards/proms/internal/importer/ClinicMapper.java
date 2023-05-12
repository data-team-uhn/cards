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

package io.uhndata.cards.proms.internal.importer;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Clarity import processor that turns the {@code ENCOUNTER_CLINIC} value into a Clinic path, and discards visits with
 * a missing or unmapped clinic name.
 *
 * @version $Id$
 */
@Component
public class ClinicMapper implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicMapper.class);

    @Reference
    private ThreadResourceResolverProvider trrp;

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        final String clinicName = input.get("ENCOUNTER_CLINIC");

        if (StringUtils.isBlank(clinicName)) {
            LOGGER.warn("Discarded visit {} due to missing clinic",
                input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"));
            return null;
        }

        final String clinicPath = "/Survey/ClinicMapping/" + clinicName;
        final Resource clinic = this.trrp.getThreadResourceResolver().getResource(clinicPath);

        if (clinic == null) {
            LOGGER.warn("Discarded visit {} due to unknown clinic: {}",
                input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"), clinicPath);
            return null;
        }

        input.put("ENCOUNTER_CLINIC", clinicPath);
        LOGGER.warn("Updated visit {} ENCOUNTER_CLINIC to {}",
            input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"), clinicPath);
        return input;
    }

    @Override
    public int getPriority()
    {
        return 50;
    }
}
