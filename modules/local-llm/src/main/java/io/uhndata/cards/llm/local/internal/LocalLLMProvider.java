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

package io.uhndata.cards.llm.local.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMProvider;

/**
 * {@link LLMProvider} that sends requests to any OpenAI-compatible chat completions endpoint,
 * such as Ollama ({@code http://localhost:11434/v1/chat/completions}) or LM Studio.
 * Register in the LLM Router by setting its {@code activeProvider} config to {@code "local"}.
 *
 * @version $Id$
 */
@Component(
    service = LLMProvider.class,
    property = { "llm.provider=local" },
    immediate = true)
@Designate(ocd = LocalLLMConfiguration.class)
public class LocalLLMProvider implements LLMProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalLLMProvider.class);

    private static final String PROVIDER_NAME = "local";

    private String endpoint;

    private String model;

    private int maxTokens;

    private String apiKeyEnvVar;

    @Activate
    @Modified
    void activate(final LocalLLMConfiguration config)
    {
        this.endpoint = config.endpoint();
        this.model = config.model();
        this.maxTokens = config.maxTokens();
        this.apiKeyEnvVar = config.apiKeyEnvVar();
        LOGGER.info("Local LLM provider configured: endpoint={}, model={}", this.endpoint, this.model);
    }

    @Override
    public String getProviderName()
    {
        return PROVIDER_NAME;
    }

    @Override
    public String chat(final String userMessage) throws IOException
    {
        return doChat(null, Collections.singletonList(new LLMMessage("user", userMessage)));
    }

    @Override
    public String chat(final String systemPrompt, final String userMessage) throws IOException
    {
        return doChat(systemPrompt, Collections.singletonList(new LLMMessage("user", userMessage)));
    }

    @Override
    public String chat(final String systemPrompt, final List<LLMMessage> messages) throws IOException
    {
        return doChat(systemPrompt, messages);
    }

    private String doChat(final String systemPrompt, final List<LLMMessage> messages) throws IOException
    {
        final String requestBody = buildRequestBody(systemPrompt, messages);
        final String responseBody = sendRequest(requestBody);
        return extractContent(responseBody);
    }

    private String buildRequestBody(final String systemPrompt, final List<LLMMessage> messages)
    {
        final JsonObjectBuilder body = Json.createObjectBuilder()
            .add("model", this.model)
            .add("max_tokens", this.maxTokens);

        final JsonArrayBuilder turns = Json.createArrayBuilder();
        if (StringUtils.isNotBlank(systemPrompt)) {
            turns.add(Json.createObjectBuilder()
                .add("role", "system")
                .add("content", systemPrompt));
        }
        for (final LLMMessage msg : messages) {
            turns.add(Json.createObjectBuilder()
                .add("role", msg.getRole())
                .add("content", msg.getContent()));
        }
        body.add("messages", turns);

        return body.build().toString();
    }

    private String sendRequest(final String requestBody) throws IOException
    {
        final HttpPost post = new HttpPost(this.endpoint);
        post.setHeader("content-type", "application/json");
        if (StringUtils.isNotBlank(this.apiKeyEnvVar)) {
            final String apiKey = System.getenv(this.apiKeyEnvVar);
            if (StringUtils.isNotBlank(apiKey)) {
                post.setHeader("Authorization", "Bearer " + apiKey);
            }
        }
        post.setEntity(new StringEntity(requestBody, "UTF-8"));

        try (CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(post)) {
            final int statusCode = response.getStatusLine().getStatusCode();
            final String body = readResponse(response);
            if (statusCode != 200) {
                LOGGER.error("Local LLM returned {}: {}", statusCode, body);
                throw new IOException("Local LLM error " + statusCode + ": " + body);
            }
            return body;
        }
    }

    private String readResponse(final CloseableHttpResponse response) throws IOException
    {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"))) {
            final StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String extractContent(final String responseBody) throws IOException
    {
        try (JsonReader reader = Json.createReader(new StringReader(responseBody))) {
            final JsonObject response = reader.readObject();
            // OpenAI-compatible format: choices[0].message.content
            final JsonArray choices = response.getJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("No choices in local LLM response");
            }
            final JsonObject message = choices.getJsonObject(0).getJsonObject("message");
            if (message == null) {
                throw new IOException("No message in local LLM response choice");
            }
            return message.getString("content");
        }
    }
}
