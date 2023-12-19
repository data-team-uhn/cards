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

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.AbstractClarityDataProcessor;
import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Clarity import processor that copies the selected clinic's display name to the LOCATION computed column.
 *
 * @version $Id$
 */
@Component
public class ClinicToLocationFiller extends AbstractClarityDataProcessor implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicToLocationFiller.class);

    @Reference
    private ThreadResourceResolverProvider trrp;

    @Activate
    public ClinicToLocationFiller()
    {
        super(true, new String[] { "proms" }, 300);
    }

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        final String clinicPath = input.get("ENCOUNTER_CLINIC");
        if (clinicPath != null) {
            final Resource clinic = this.trrp.getThreadResourceResolver().getResource(clinicPath);
            if (clinic == null) {
                LOGGER.warn("Unknown clinic: {}", clinicPath);
                return input;
            }
            final String clinicLabel = (String) clinic.getValueMap().get("displayName");
            input.put("LOCATION", clinicLabel);
            LOGGER.warn("Set visit {} LOCATION to {}", input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"),
                clinicLabel);
        }
        return input;
    }
}
