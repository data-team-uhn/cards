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

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that sets a patients email consent to yes if they signed up for mychart.
 *
 * @version $Id$
 */
@Component
public class MychartEmailConsentMapper implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MychartEmailConsentMapper.class);

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        if ("Activated".equalsIgnoreCase(input.get("MYCHART_STATUS"))) {
            input.put("EMAIL_CONSENT_YN", "Yes");
            LOGGER.warn("Set visit {} EMAIL_CONSENT_YN to 'Yes' due to MYCHART_STATUS 'Activated'",
                input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"));
        }
        return input;
    }

    @Override
    public int getPriority()
    {
        return 0;
    }
}
