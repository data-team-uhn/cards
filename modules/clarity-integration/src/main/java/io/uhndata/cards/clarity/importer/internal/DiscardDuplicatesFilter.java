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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
 * Clarity import processor that monitors which patients have been encountered so far in this import batch, and discards
 * any duplicates.
 *
 * @version $Id$
 */
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = DiscardDuplicatesFilter.Config.class)
public class DiscardDuplicatesFilter implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscardDuplicatesFilter.class);

    private final ThreadLocal<Set<String>> seenIdentifiers = ThreadLocal.withInitial(HashSet::new);

    private final boolean enabled;

    private final String subjectType;

    @ObjectClassDefinition(name = "Clarity import filter - Discard duplicates",
        description = "Configuration for the Clarity importer to discard duplicate entries for the same subject")
    public @interface Config
    {
        @AttributeDefinition(name = "Enabled")
        boolean enable() default false;

        @AttributeDefinition(name = "Subject type",
            description = "Subject type for which to discard duplicates: a patient or a visit.")
        String subjectType() default "/SubjectTypes/Patient";
    }

    @Activate
    public DiscardDuplicatesFilter(Config config)
    {
        this.enabled = config.enable();
        this.subjectType = config.subjectType();
    }

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        if (this.enabled) {
            final String subjectIdentifier = input.get(this.subjectType);
            if (subjectIdentifier != null && !this.seenIdentifiers.get().add(subjectIdentifier)) {
                LOGGER.warn("DiscardDuplicatesFilter discarded visit {} as duplicate",
                    input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"));
                return null;
            }
        }
        return input;
    }

    @Override
    public void end()
    {
        this.seenIdentifiers.remove();
    }

    @Override
    public int getPriority()
    {
        // Running this later allows us to support this scenario:
        // - patient discharged from ED to IP at 00:15
        // - patient discharged from IP same day at 20:00
        // In this case we want to only import the second discharge event, not the first, so we want to give a chance to
        // another filter to remove the first event.
        return 200;
    }
}
