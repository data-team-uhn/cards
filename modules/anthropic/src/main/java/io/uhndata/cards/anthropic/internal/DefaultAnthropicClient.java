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

import io.uhndata.cards.anthropic.AnthropicClient;
import io.uhndata.cards.anthropic.AnthropicMessage;

@Component(service = AnthropicClient.class, immediate = true)
@Designate(ocd = AnthropicConfiguration.class)
public class DefaultAnthropicClient implements AnthropicClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAnthropicClient.class);

    private String apiKeyEnvVar;

    private String apiEndpoint;

    private String model;

    private int maxTokens;

    private String apiVersion;

    @Activate
    @Modified
    protected void activate(final AnthropicConfiguration config)
    {
        this.apiKeyEnvVar = config.apiKeyEnvVar();
        this.apiEndpoint = config.apiEndpoint();
        this.model = config.model();
        this.maxTokens = config.maxTokens();
        this.apiVersion = config.apiVersion();
        if (StringUtils.isBlank(System.getenv(this.apiKeyEnvVar))) {
            LOGGER.warn("Anthropic API key not found in environment variable: {}", this.apiKeyEnvVar);
        }
    }

    @Override
    public String chat(final String userMessage) throws IOException
    {
        return chat(null, Collections.singletonList(new AnthropicMessage("user", userMessage)));
    }

    @Override
    public String chat(final String systemPrompt, final String userMessage) throws IOException
    {
        return chat(systemPrompt, Collections.singletonList(new AnthropicMessage("user", userMessage)));
    }

    @Override
    public String chat(final String systemPrompt, final List<AnthropicMessage> messages) throws IOException
    {
        final String apiKey = System.getenv(this.apiKeyEnvVar);
        if (StringUtils.isBlank(apiKey)) {
            throw new IOException("Anthropic API key not set (env var: " + this.apiKeyEnvVar + ")");
        }
        final String responseBody = sendRequest(apiKey, buildRequestBody(systemPrompt, messages));
        return extractTextContent(responseBody);
    }

    private String buildRequestBody(final String systemPrompt, final List<AnthropicMessage> messages)
    {
        final JsonObjectBuilder body = Json.createObjectBuilder()
            .add("model", this.model)
            .add("max_tokens", this.maxTokens);

        if (StringUtils.isNotBlank(systemPrompt)) {
            body.add("system", systemPrompt);
        }

        final JsonArrayBuilder turns = Json.createArrayBuilder();
        for (final AnthropicMessage msg : messages) {
            turns.add(Json.createObjectBuilder()
                .add("role", msg.getRole())
                .add("content", msg.getContent()));
        }
        body.add("messages", turns);

        return body.build().toString();
    }

    private String sendRequest(final String apiKey, final String requestBody) throws IOException
    {
        final HttpPost post = new HttpPost(this.apiEndpoint);
        post.setHeader("x-api-key", apiKey);
        post.setHeader("anthropic-version", this.apiVersion);
        post.setHeader("content-type", "application/json");
        post.setEntity(new StringEntity(requestBody, "UTF-8"));

        try (CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(post)) {
            final int statusCode = response.getStatusLine().getStatusCode();
            final String body = readResponse(response);
            if (statusCode != 200) {
                LOGGER.error("Anthropic API returned {}: {}", statusCode, body);
                throw new IOException("Anthropic API error " + statusCode + ": " + body);
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

    private String extractTextContent(final String responseBody) throws IOException
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
}
