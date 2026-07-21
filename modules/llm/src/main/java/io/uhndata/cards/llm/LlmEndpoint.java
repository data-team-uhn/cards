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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * A servlet that forwards a prompt to an OpenAI-compatible chat completions server through the
 * LangChain4j SDK.
 * <p>
 * It answers {@code POST} requests to {@code /llm} with a JSON body of the form:
 * </p>
 *
 * <pre>
 * { "content": "Hello, world" }
 * </pre>
 *
 * <p>
 * The {@code content} value is sent as the user message to the configured chat model, and the
 * model's reply is returned as JSON:
 * </p>
 *
 * <pre>
 * { "content": "Hi there! ..." }
 * </pre>
 *
 * <p>
 * The target server and API key are read from the OSGi {@link LlmConfigDefinition configuration};
 * if that is not set, the {@link #DEFAULT_OPENAI_BASE_URL} and {@link #DEFAULT_OPENAI_API_KEY}
 * constants are used as fallbacks.
 * </p>
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletPaths(value = "/llm")
@Designate(ocd = LlmConfigDefinition.class)
public class LlmEndpoint extends SlingJakartaAllMethodsServlet
{
    /** The default base URL of the OpenAI-compatible server, used when no OSGi config is set. */
    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";

    /** The default API key for the OpenAI-compatible server, used when no OSGi config is set. */
    public static final String DEFAULT_OPENAI_API_KEY = "CHANGE_ME";

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmEndpoint.class);

    /** The resolved base URL, from configuration or {@link #DEFAULT_OPENAI_BASE_URL}. */
    private String baseUrl;

    /** The resolved API key, from configuration or {@link #DEFAULT_OPENAI_API_KEY}. */
    private String apiKey;

    /** The resolved model name. */
    private String model;

    @Activate
    public void activate(final LlmConfigDefinition config)
    {
        this.baseUrl = StringUtils.defaultIfBlank(config.openAiBaseUrl(), DEFAULT_OPENAI_BASE_URL);
        this.apiKey = StringUtils.defaultIfBlank(config.openAiApiKey(), DEFAULT_OPENAI_API_KEY);
        this.model = StringUtils.defaultIfBlank(config.model(), "gpt-4o-mini");
    }

    @Override
    public void doPost(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Extract the "content" to forward from the request body.
        final String content;
        try (JsonReader reader = Json.createReader(request.getReader())) {
            final JsonObject body = reader.readObject();
            content = body.getString("content", null);
        } catch (final RuntimeException e) {
            LOGGER.warn("Received a malformed request body: {}", e.getMessage());
            writeError(response, SlingJakartaHttpServletResponse.SC_BAD_REQUEST, "Invalid JSON request body");
            return;
        }

        if (StringUtils.isBlank(content)) {
            writeError(response, SlingJakartaHttpServletResponse.SC_BAD_REQUEST,
                "The request body must contain a non-empty \"content\" value");
            return;
        }

        try {
            final OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(this.baseUrl)
                .apiKey(this.apiKey)
                .modelName(this.model)
                .build();

            final String reply = chatModel.chat(content);

            response.getWriter().print(Json.createObjectBuilder()
                .add("content", reply)
                .build()
                .toString());
        } catch (final RuntimeException e) {
            LOGGER.error("Failed to forward the prompt to the LLM server: {}", e.getMessage(), e);
            writeError(response, SlingJakartaHttpServletResponse.SC_BAD_GATEWAY,
                "Failed to reach the LLM server");
        }
    }

    private void writeError(final SlingJakartaHttpServletResponse response, final int status, final String message)
        throws IOException
    {
        response.setStatus(status);
        response.getWriter().print(Json.createObjectBuilder()
            .add("error", message)
            .build()
            .toString());
    }
}
