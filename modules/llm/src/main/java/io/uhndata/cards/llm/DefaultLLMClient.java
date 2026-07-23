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
import java.util.Collections;
import java.util.List;

/**
 * Base class for {@link LLMClient} implementations. It wires the four {@link LLMClient} chat overloads to a
 * single {@link #doChat(String, List, LLMRequestOptions)} hook that the concrete client implements, and holds
 * the {@link LLMConfigurationService} each client binds (via its own {@code @Reference}) to resolve the active
 * {@link LLMSettings}. The transport, request shaping and response parsing are entirely the subclass's concern
 * — {@link io.uhndata.cards.llm.internal.OpenAIClient} builds an OpenAI-compatible request through the
 * LangChain4j SDK.
 *
 * @version $Id$
 */
public abstract class DefaultLLMClient implements LLMClient
{
    /**
     * The configuration service used to resolve the active settings. It is bound by each concrete component
     * (via its own {@code @Reference}), because OSGi Declarative Services does not inherit references declared
     * in a superclass that lives in a different bundle.
     */
    protected LLMConfigurationService configurationService;

    @Override
    public String chat(final String userMessage) throws IOException
    {
        return doChat(null, Collections.singletonList(new LLMMessage("user", userMessage)), null);
    }

    @Override
    public String chat(final String systemPrompt, final String userMessage) throws IOException
    {
        return doChat(systemPrompt, Collections.singletonList(new LLMMessage("user", userMessage)), null);
    }

    @Override
    public String chat(final String systemPrompt, final List<LLMMessage> messages) throws IOException
    {
        return doChat(systemPrompt, messages, null);
    }

    @Override
    public String chat(final String systemPrompt, final List<LLMMessage> messages, final LLMRequestOptions options)
        throws IOException
    {
        return doChat(systemPrompt, messages, options);
    }

    /**
     * The configuration service used to resolve the active settings, injected as an OSGi reference and shared
     * by all concrete clients.
     *
     * @return the configuration service
     */
    protected LLMConfigurationService getConfigurationService()
    {
        return this.configurationService;
    }

    /**
     * Send a conversation to the model and return its text reply. Concrete clients resolve the active settings
     * (via {@link #getConfigurationService()}), build and dispatch the request, and extract the reply.
     *
     * @param systemPrompt the optional system prompt (may be {@code null} or blank)
     * @param messages the conversation turns
     * @param options per-call overrides, or {@code null} to use the active model's settings unchanged
     * @return the assistant's reply
     * @throws IOException on configuration, network or API errors
     */
    protected abstract String doChat(String systemPrompt, List<LLMMessage> messages, LLMRequestOptions options)
        throws IOException;
}
