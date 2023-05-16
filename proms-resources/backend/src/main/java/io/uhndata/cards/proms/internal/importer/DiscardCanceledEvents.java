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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Clarity import processor doesn't import canceled visits, unless they were previously imported.
 *
 * @version $Id$
 */
@Component
public class DiscardCanceledEvents implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscardCanceledEvents.class);

    @Reference
    private ThreadResourceResolverProvider trrp;

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        final String status = input.get("ENCOUNTER_STATUS");
        if (StringUtils.equalsIgnoreCase("cancelled", status) && this.trrp.getThreadResourceResolver().getResource(
            "/Subjects/" + input.get("/SubjectTypes/Patient") + "/"
                + input.get("/SubjectTypes/Patient/Visit")) == null) {
            LOGGER.warn("Discarded canceled visit {} ", input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown"));
            return null;
        }
        return input;
    }

    @Override
    public int getPriority()
    {
        return 10;
    }
}
