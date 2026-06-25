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

package io.uhndata.cards.llm.internal;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMConfigurationService;
import io.uhndata.cards.llm.LLMInteraction;
import io.uhndata.cards.llm.LLMInteractionLogger;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMSettings;

/**
 * {@link LLMClient} decorator that records each chat interaction through an {@link LLMInteractionLogger} while
 * delegating the actual call to the wrapped client. When the logger is absent or disabled the call is forwarded
 * unchanged with no extra work; otherwise the request, the reply (or the failure), and the timing are captured
 * and handed off to the logger. Capturing never alters the result or masks an exception from the delegate.
 *
 * @version $Id$
 */
final class LoggingLLMClient implements LLMClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingLLMClient.class);

    private final LLMClient delegate;

    private final LLMInteractionLogger interactionLogger;

    private final LLMConfigurationService configurationService;

    LoggingLLMClient(final LLMClient delegate, final LLMInteractionLogger interactionLogger,
        final LLMConfigurationService configurationService)
    {
        this.delegate = delegate;
        this.interactionLogger = interactionLogger;
        this.configurationService = configurationService;
    }

    @Override
    public String chat(final String userMessage) throws IOException
    {
        return logged(null, Collections.singletonList(new LLMMessage("user", userMessage)),
            () -> this.delegate.chat(userMessage));
    }

    @Override
    public String chat(final String systemPrompt, final String userMessage) throws IOException
    {
        return logged(systemPrompt, Collections.singletonList(new LLMMessage("user", userMessage)),
            () -> this.delegate.chat(systemPrompt, userMessage));
    }

    @Override
    public String chat(final String systemPrompt, final List<LLMMessage> messages) throws IOException
    {
        return logged(systemPrompt, messages, () -> this.delegate.chat(systemPrompt, messages));
    }

    private String logged(final String systemPrompt, final List<LLMMessage> messages,
        final ChatInvocation invocation) throws IOException
    {
        if (this.interactionLogger == null || !this.interactionLogger.isEnabled()) {
            return invocation.invoke();
        }
        final long start = System.currentTimeMillis();
        String output = null;
        String error = null;
        try {
            output = invocation.invoke();
            return output;
        } catch (IOException e) {
            error = e.getMessage();
            throw e;
        } finally {
            recordInteraction(systemPrompt, messages, output, error, start, System.currentTimeMillis());
        }
    }

    private void recordInteraction(final String systemPrompt, final List<LLMMessage> messages, final String output,
        final String error, final long start, final long end)
    {
        try {
            final LLMSettings settings = this.configurationService.getActiveSettings();
            this.interactionLogger.logInteraction(
                new LLMInteraction(settings, systemPrompt, messages, output, error, start, end));
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Could not record an LLM interaction for logging", e);
        }
    }

    @FunctionalInterface
    private interface ChatInvocation
    {
        String invoke() throws IOException;
    }
}
