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

package io.uhndata.cards.anthropic;

import java.io.IOException;
import java.util.List;

/**
 * OSGi service for sending requests to the Anthropic Claude Messages API.
 * Configure the API key via the {@code ANTHROPIC_API_KEY} environment variable.
 *
 * @version $Id$
 */
public interface AnthropicClient
{
    /**
     * Send a single user message and return the assistant's reply.
     *
     * @param userMessage the user turn content
     * @return the assistant's text response
     * @throws IOException on network failure or a non-200 API response
     */
    String chat(String userMessage) throws IOException;

    /**
     * Send a single user message with a system prompt and return the assistant's reply.
     *
     * @param systemPrompt optional system instructions (may be null or blank)
     * @param userMessage the user turn content
     * @return the assistant's text response
     * @throws IOException on network failure or a non-200 API response
     */
    String chat(String systemPrompt, String userMessage) throws IOException;

    /**
     * Send a multi-turn conversation with an optional system prompt.
     *
     * @param systemPrompt optional system instructions (may be null or blank)
     * @param messages the ordered list of conversation turns; must alternate user/assistant
     * @return the assistant's text response
     * @throws IOException on network failure or a non-200 API response
     */
    String chat(String systemPrompt, List<AnthropicMessage> messages) throws IOException;
}
