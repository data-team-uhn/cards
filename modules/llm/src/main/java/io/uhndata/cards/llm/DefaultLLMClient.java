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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link LLMClient} implementations that talk to an HTTP chat API. It captures the behaviour
 * common to the OpenAI and Anthropic chat APIs: the {@link LLMClient} overloads, resolving the active
 * {@link LLMSettings}, building the shared request fields (model, max tokens, temperature) and message turns,
 * POSTing the request with a configurable timeout, and reading the response while turning non-200 statuses
 * into errors. Format-specific behaviour (endpoint resolution, authentication, the exact request body shape
 * and the response parsing) is supplied by subclasses through the abstract hooks.
 *
 * @version $Id$
 */
public abstract class DefaultLLMClient implements LLMClient
{
    /** Number of milliseconds in a second, for converting the configured timeout. */
    protected static final int MILLIS_PER_SECOND = 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLLMClient.class);

    private static final int HTTP_OK = 200;

    /**
     * The configuration service used to resolve the active settings. It is bound by each concrete component
     * (via its own {@code @Reference}), because OSGi Declarative Services does not inherit references declared
     * in a superclass that lives in a different bundle.
     */
    protected LLMConfigurationService configurationService;

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

    /**
     * Resolve the active settings, build and send the request, and extract the reply.
     *
     * @param systemPrompt the optional system prompt
     * @param messages the conversation turns
     * @return the assistant's reply
     * @throws IOException on configuration, network or API errors
     */
    protected String doChat(final String systemPrompt, final List<LLMMessage> messages) throws IOException
    {
        final LLMSettings settings = getConfigurationService().getActiveSettings();
        final String requestBody = buildRequestBody(settings, systemPrompt, messages);
        final String responseBody = sendRequest(settings, requestBody);
        return extractContent(responseBody);
    }

    /**
     * Build a request body pre-populated with the fields common to both APIs: the model, the maximum number of
     * output tokens, and the temperature.
     *
     * @param settings the active settings
     * @return a request body builder for the subclass to complete
     */
    protected JsonObjectBuilder baseRequestBody(final LLMSettings settings)
    {
        return Json.createObjectBuilder()
            .add("model", settings.getModelId())
            .add("max_tokens", settings.getMaxOutputTokens())
            .add("temperature", settings.getTemperature());
    }

    /**
     * Append the conversation turns to the given array builder, one object per message with its role and
     * content.
     *
     * @param turns the array builder to append to
     * @param messages the conversation turns
     */
    protected void addTurns(final JsonArrayBuilder turns, final List<LLMMessage> messages)
    {
        for (final LLMMessage message : messages) {
            turns.add(Json.createObjectBuilder()
                .add("role", message.getRole())
                .add("content", message.getContent()));
        }
    }

    /**
     * POST the request body to the resolved endpoint and return the raw response body.
     *
     * @param settings the active settings
     * @param requestBody the JSON request body
     * @return the raw response body
     * @throws IOException on network failure or a non-200 API response
     */
    protected String sendRequest(final LLMSettings settings, final String requestBody) throws IOException
    {
        final HttpPost post = new HttpPost(resolveEndpoint(settings.getEndpoint()));
        post.setHeader("content-type", "application/json");
        final int timeoutMillis = (int) (settings.getTimeoutSeconds() * MILLIS_PER_SECOND);
        post.setConfig(RequestConfig.custom()
            .setConnectTimeout(timeoutMillis)
            .setConnectionRequestTimeout(timeoutMillis)
            .setSocketTimeout(timeoutMillis)
            .build());
        configureRequest(post, settings);
        post.setEntity(new StringEntity(requestBody, "UTF-8"));

        try (CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(post)) {
            final int statusCode = response.getStatusLine().getStatusCode();
            final String body = readResponse(response);
            if (statusCode != HTTP_OK) {
                LOGGER.error("{} returned {}: {}", errorLabel(), statusCode, body);
                throw new IOException(errorLabel() + " error " + statusCode + ": " + body);
            }
            return body;
        }
    }

    /**
     * Read the full response body as a string.
     *
     * @param response the HTTP response
     * @return the response body
     * @throws IOException if reading the response fails
     */
    protected String readResponse(final CloseableHttpResponse response) throws IOException
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
     * Resolve the configured endpoint to the URL that requests are POSTed to.
     *
     * @param configuredEndpoint the endpoint from configuration
     * @return the URL to POST to
     */
    protected abstract String resolveEndpoint(String configuredEndpoint);

    /**
     * Add authentication and any other format-specific headers to the request.
     *
     * @param post the request being prepared
     * @param settings the active settings
     * @throws IOException if a required credential or header is missing
     */
    protected abstract void configureRequest(HttpPost post, LLMSettings settings) throws IOException;

    /**
     * Build the format-specific JSON request body.
     *
     * @param settings the active settings
     * @param systemPrompt the optional system prompt
     * @param messages the conversation turns
     * @return the JSON request body
     */
    protected abstract String buildRequestBody(LLMSettings settings, String systemPrompt,
        List<LLMMessage> messages);

    /**
     * Extract the assistant's text reply from the raw response body.
     *
     * @param responseBody the raw response body
     * @return the assistant's text reply
     * @throws IOException if the response cannot be parsed
     */
    protected abstract String extractContent(String responseBody) throws IOException;

    /**
     * A short label identifying this API, used in error messages and logs (e.g. {@code "Anthropic API"}).
     *
     * @return the API label
     */
    protected abstract String errorLabel();
}
