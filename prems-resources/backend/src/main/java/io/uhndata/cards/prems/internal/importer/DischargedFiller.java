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

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that fills visit status "discharged" into all imported forms.
 *
 * @version $Id$
 */
@Component
public class DischargedFiller implements ClarityDataProcessor
{
    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        // All visits we receive are discharged, add this to the output so it can get inserted into the visit
        input.put("STATUS", "discharged");
        return input;
    }

    @Override
    public int getPriority()
    {
        return 30;
    }
}
