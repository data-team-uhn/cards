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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable record of a single LLM chat interaction, captured for observability logging by an
 * {@link LLMInteractionLogger}. It bundles the resolved {@link LLMSettings} (provider, model and generation
 * parameters) with the request (the optional system prompt and the conversation turns), the result (either the
 * assistant's reply or an error message), and the wall-clock start and end times of the call.
 *
 * @version $Id$
 */
public final class LLMInteraction
{
    private final LLMSettings settings;

    private final String systemPrompt;

    private final List<LLMMessage> messages;

    private final String output;

    private final String errorMessage;

    private final long startEpochMillis;

    private final long endEpochMillis;

    /**
     * Create an interaction record.
     *
     * @param settings the settings resolved for this call (provider, model and generation parameters)
     * @param systemPrompt the system prompt sent with the request, or {@code null} if none
     * @param messages the conversation turns sent with the request
     * @param output the assistant's reply, or {@code null} if the call failed
     * @param errorMessage the failure description, or {@code null} if the call succeeded
     * @param startEpochMillis the wall-clock time the call started, in milliseconds since the epoch
     * @param endEpochMillis the wall-clock time the call ended, in milliseconds since the epoch
     */
    public LLMInteraction(final LLMSettings settings, final String systemPrompt, final List<LLMMessage> messages,
        final String output, final String errorMessage, final long startEpochMillis, final long endEpochMillis)
    {
        this.settings = settings;
        this.systemPrompt = systemPrompt;
        this.messages = messages == null
            ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(messages));
        this.output = output;
        this.errorMessage = errorMessage;
        this.startEpochMillis = startEpochMillis;
        this.endEpochMillis = endEpochMillis;
    }

    /**
     * The settings resolved for this call.
     *
     * @return the settings, or {@code null} if they could not be resolved
     */
    public LLMSettings getSettings()
    {
        return this.settings;
    }

    /**
     * The system prompt sent with the request.
     *
     * @return the system prompt, or {@code null} if none was sent
     */
    public String getSystemPrompt()
    {
        return this.systemPrompt;
    }

    /**
     * The conversation turns sent with the request.
     *
     * @return an unmodifiable list of the conversation turns, never {@code null}
     */
    public List<LLMMessage> getMessages()
    {
        return this.messages;
    }

    /**
     * The assistant's reply.
     *
     * @return the reply, or {@code null} if the call failed
     */
    public String getOutput()
    {
        return this.output;
    }

    /**
     * The failure description.
     *
     * @return the error message, or {@code null} if the call succeeded
     */
    public String getErrorMessage()
    {
        return this.errorMessage;
    }

    /**
     * The wall-clock time the call started.
     *
     * @return the start time in milliseconds since the epoch
     */
    public long getStartEpochMillis()
    {
        return this.startEpochMillis;
    }

    /**
     * The wall-clock time the call ended.
     *
     * @return the end time in milliseconds since the epoch
     */
    public long getEndEpochMillis()
    {
        return this.endEpochMillis;
    }
}
