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

package io.uhndata.cards.openai.internal;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMConfigurationService;

/**
 * {@link LLMClient} for the Prompter proxy ({@code https://prompter.uhndata.io/api/proxy/v1}), which exposes an
 * OpenAI-compatible chat completions API. It adds nothing beyond {@link DefaultOpenAIClient} except the OSGi
 * registration for the {@code "prompter"} provider and the configuration service reference; all settings come
 * from the active provider and model in the JCR LLM configuration.
 *
 * @version $Id$
 */
@Component(
    service = LLMClient.class,
    property = { "llm.provider=prompter" },
    immediate = true)
public class PrompterLLMClient extends DefaultOpenAIClient
{
    @Reference
    private LLMConfigurationService configurationService;

    @Override
    protected LLMConfigurationService getConfigurationService()
    {
        return this.configurationService;
    }
}
