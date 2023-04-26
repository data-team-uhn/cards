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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that only allows visits for patients with a valid email address who have given consent to
 * receiving emails.
 *
 * @version $Id$
 */
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = FilterEmailConsent.Config.class)
public class FilterEmailConsent implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterEmailConsent.class);

    private final boolean enabled;

    private final String consentColumn;

    private final String emailColumn;

    @ObjectClassDefinition(name = "Clarity import filter - Discard invalid or non-consented emails",
        description = "Configuration for the Clarity importer to discard entries for patients who did not consent to"
            + " receving emails, or who provided an invalid email address")
    public @interface Config
    {
        @AttributeDefinition(name = "Enabled")
        boolean enable() default false;

        @AttributeDefinition(name = "Email address column", description = "If not provided, validity is not checked")
        String emailColumn();

        @AttributeDefinition(name = "Email consent column.", description = "If not provided, consent is not checked")
        String emailConsentColumn();
    }

    @Activate
    public FilterEmailConsent(Config config)
    {
        this.enabled = config.enable();
        this.consentColumn = config.emailConsentColumn();
        this.emailColumn = config.emailColumn();
    }

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        if (!this.enabled) {
            return input;
        }

        // Discard invalid email addresses, if configured
        if (StringUtils.isNotBlank(this.emailColumn)
            && !EmailValidator.getInstance().isValid(input.get(this.emailColumn))) {
            LOGGER.warn("Discarded visit {} due to invalid email",
                input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"));
            return null;
        }

        // Discard non-consented patients, if configured
        if (StringUtils.isNotBlank(this.consentColumn) && !"Yes".equalsIgnoreCase(input.get(this.consentColumn))) {
            LOGGER.warn("Discarded visit {} due to patient not consenting to receiving emails",
                input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"));
            return null;
        }

        return input;
    }

    @Override
    public int getPriority()
    {
        return 5;
    }
}
