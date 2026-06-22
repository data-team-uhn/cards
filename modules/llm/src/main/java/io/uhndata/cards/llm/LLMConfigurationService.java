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

package io.uhndata.cards.llm;

import java.io.IOException;

/**
 * Service that resolves the active LLM provider and model from the JCR configuration stored under
 * {@code /apps/cards/config/LLM}. The catalog of available providers and models is seeded from initial
 * content; the active selection is set through the administration UI. Providers and the router use this
 * service to obtain the settings for the currently selected provider and model.
 *
 * @version $Id$
 */
public interface LLMConfigurationService
{
    /**
     * Resolve the settings for the currently active provider and model.
     *
     * @return the resolved settings for the active provider and model
     * @throws IOException if the configuration is missing, the active selection is not set, or it points to a
     *             provider or model that does not exist
     */
    LLMSettings getActiveSettings() throws IOException;
}
