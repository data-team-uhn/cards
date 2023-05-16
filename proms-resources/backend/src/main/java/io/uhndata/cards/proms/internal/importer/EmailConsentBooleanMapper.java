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

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that replaces the email consent column to Yes or No instead of UHN_EXTERNAL_EMAIL or NULL.
 *
 * @version $Id$
 */
@Component
public class EmailConsentBooleanMapper implements ClarityDataProcessor
{
    private static final String COLUMN = "EMAIL_CONSENT";

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        if (input.get(COLUMN) == null) {
            input.put(COLUMN, "No");
        } else if ("UHN_EXTERNAL_EMAIL".equals(input.get(COLUMN))) {
            input.put(COLUMN, "Yes");
        }
        return input;
    }

    @Override
    public int getPriority()
    {
        return 0;
    }
}
