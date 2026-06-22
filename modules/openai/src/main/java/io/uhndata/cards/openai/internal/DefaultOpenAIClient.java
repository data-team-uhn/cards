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

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpPost;

import io.uhndata.cards.llm.DefaultLLMClient;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMSettings;

/**
 * Base client for OpenAI-compatible chat completions endpoints (Prompter, Ollama, LM Studio, etc.). It
 * implements the OpenAI wire format on top of {@link DefaultLLMClient}: the endpoint has
 * {@code /chat/completions} appended when needed, the API key is sent as a Bearer token, the request body uses
 * the {@code messages} array (with the system prompt as a {@code system}-role entry) plus an optional
 * {@code project_id}, and the reply is read from {@code choices[0].message.content}. Concrete subclasses add
 * the OSGi registration and supply the configuration service.
 *
 * @version $Id$
 */
public abstract class DefaultOpenAIClient extends DefaultLLMClient
{
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final String PROJECT_ID = "projectId";

    @Override
    protected String resolveEndpoint(final String configuredEndpoint)
    {
        final String trimmed = StringUtils.stripEnd(StringUtils.trimToEmpty(configuredEndpoint), "/");
        if (trimmed.endsWith(CHAT_COMPLETIONS_PATH)) {
            return trimmed;
        }
        return trimmed + CHAT_COMPLETIONS_PATH;
    }

    @Override
    protected void configureRequest(final HttpPost post, final LLMSettings settings)
    {
        final String apiKeyEnvVar = settings.getApiKeyEnvVar();
        if (StringUtils.isNotBlank(apiKeyEnvVar)) {
            final String apiKey = System.getenv(apiKeyEnvVar);
            if (StringUtils.isNotBlank(apiKey)) {
                post.setHeader("Authorization", "Bearer " + apiKey);
            }
        }
    }

    @Override
    protected String buildRequestBody(final LLMSettings settings, final String systemPrompt,
        final List<LLMMessage> messages)
    {
        final JsonObjectBuilder body = baseRequestBody(settings);

        final String projectId = settings.getProviderProperty(PROJECT_ID);
        if (StringUtils.isNotBlank(projectId)) {
            body.add("project_id", projectId);
        }

        final JsonArrayBuilder turns = Json.createArrayBuilder();
        if (StringUtils.isNotBlank(systemPrompt)) {
            turns.add(Json.createObjectBuilder()
                .add("role", "system")
                .add("content", systemPrompt));
        }
        addTurns(turns, messages);
        body.add("messages", turns);

        return body.build().toString();
    }

    @Override
    protected String extractContent(final String responseBody) throws IOException
    {
        try (JsonReader reader = Json.createReader(new StringReader(responseBody))) {
            final JsonObject response = reader.readObject();
            // OpenAI-compatible format: choices[0].message.content
            final JsonArray choices = response.getJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("No choices in the OpenAI-compatible LLM response");
            }
            final JsonObject message = choices.getJsonObject(0).getJsonObject("message");
            if (message == null) {
                throw new IOException("No message in the OpenAI-compatible LLM response choice");
            }
            return message.getString("content");
        }
    }

    @Override
    protected String errorLabel()
    {
        return "OpenAI-compatible LLM";
    }
}
