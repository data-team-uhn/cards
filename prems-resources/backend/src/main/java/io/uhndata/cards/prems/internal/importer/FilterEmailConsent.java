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

import org.apache.commons.validator.routines.EmailValidator;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that only allows visits for patients with a valid email address who have given consent to
 * receiving emails.
 *
 * @version $Id$
 */
@Component
public class FilterEmailConsent implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterEmailConsent.class);

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        final String email = input.get("EMAIL_ADDRESS");
        final Boolean consent = "Yes".equalsIgnoreCase(input.get("EMAIL_CONSENT_YN"));
        if (consent && EmailValidator.getInstance().isValid(email)) {
            return input;
        }
        LOGGER.warn("Discarded visit {}", input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"));
        return null;
    }

    @Override
    public int getPriority()
    {
        return 5;
    }
}
