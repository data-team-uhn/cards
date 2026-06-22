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

package io.uhndata.cards.anthropic.internal;

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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.llm.DefaultLLMClient;
import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMConfigurationService;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMSettings;

/**
 * Client for the Anthropic Claude Messages API. It implements the Anthropic wire format on top of
 * {@link DefaultLLMClient}: the endpoint is used as-is, the API key is sent in the {@code x-api-key} header
 * together with the required {@code anthropic-version} header, the system prompt is a top-level {@code system}
 * field, and the reply is read from the first {@code text} block in {@code content}. It serves the
 * {@code "claude"} provider; all settings come from the active provider and model in the JCR LLM
 * configuration.
 *
 * @version $Id$
 */
@Component(
    service = LLMClient.class,
    property = { "llm.provider=claude" },
    immediate = true)
public class DefaultAnthropicClient extends DefaultLLMClient
{
    private static final String API_VERSION = "apiVersion";

    @Reference
    private LLMConfigurationService configurationService;

    @Override
    protected LLMConfigurationService getConfigurationService()
    {
        return this.configurationService;
    }

    @Override
    protected String resolveEndpoint(final String configuredEndpoint)
    {
        return configuredEndpoint;
    }

    @Override
    protected void configureRequest(final HttpPost post, final LLMSettings settings) throws IOException
    {
        final String apiKeyEnvVar = settings.getApiKeyEnvVar();
        final String apiKey = StringUtils.isNotBlank(apiKeyEnvVar) ? System.getenv(apiKeyEnvVar) : null;
        if (StringUtils.isBlank(apiKey)) {
            throw new IOException("Anthropic API key not set (env var: " + apiKeyEnvVar + ")");
        }
        final String apiVersion = settings.getProviderProperty(API_VERSION);
        if (StringUtils.isBlank(apiVersion)) {
            throw new IOException("Anthropic provider is missing the required 'apiVersion' property");
        }
        post.setHeader("x-api-key", apiKey);
        post.setHeader("anthropic-version", apiVersion);
    }

    @Override
    protected String buildRequestBody(final LLMSettings settings, final String systemPrompt,
        final List<LLMMessage> messages)
    {
        final JsonObjectBuilder body = baseRequestBody(settings);

        if (StringUtils.isNotBlank(systemPrompt)) {
            body.add("system", systemPrompt);
        }

        final JsonArrayBuilder turns = Json.createArrayBuilder();
        addTurns(turns, messages);
        body.add("messages", turns);

        return body.build().toString();
    }

    @Override
    protected String extractContent(final String responseBody) throws IOException
    {
        try (JsonReader reader = Json.createReader(new StringReader(responseBody))) {
            final JsonObject response = reader.readObject();
            final JsonArray content = response.getJsonArray("content");
            if (content == null || content.isEmpty()) {
                throw new IOException("Empty content in Anthropic response");
            }
            for (final JsonObject block : content.getValuesAs(JsonObject.class)) {
                if ("text".equals(block.getString("type", ""))) {
                    return block.getString("text");
                }
            }
            throw new IOException("No text content block in Anthropic response");
        }
    }

    @Override
    protected String errorLabel()
    {
        return "Anthropic API";
    }
}
