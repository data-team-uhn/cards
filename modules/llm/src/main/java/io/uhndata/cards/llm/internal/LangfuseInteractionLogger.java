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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.llm.LLMInteraction;
import io.uhndata.cards.llm.LLMInteractionLogger;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMSettings;

/**
 * {@link LLMInteractionLogger} that ships interactions to a LangFuse instance through its ingestion API. Each
 * interaction is posted as a batch of two events: a {@code trace-create} describing the request and result, and
 * a {@code generation-create} carrying the model, parameters, input and output. Delivery happens on a small
 * background executor so the LLM call path is never blocked, and any failure is logged rather than propagated.
 * The component reads its host and credentials from {@link LangfuseLoggerConfig} and stays inert until logging
 * is both enabled and fully configured.
 *
 * @version $Id$
 */
@Component(service = LLMInteractionLogger.class, immediate = true)
@Designate(ocd = LangfuseLoggerConfig.class)
public class LangfuseInteractionLogger implements LLMInteractionLogger
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LangfuseInteractionLogger.class);

    private static final String ENV_PREFIX = "%ENV%";

    private static final String INGESTION_PATH = "/api/public/ingestion";

    private static final String TRACE_NAME = "cards-llm-chat";

    private static final String GENERATION_NAME = "chat";

    private static final String CONTENT_TYPE = "application/json";

    private static final int HTTP_BAD_REQUEST = 400;

    private static final int INGESTION_TIMEOUT_MILLIS = 10000;

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    private static final int WORKER_THREADS = 2;

    private volatile boolean enabled;

    private volatile String ingestionUrl;

    private volatile String authHeader;

    private volatile String environment;

    private volatile ExecutorService executor;

    @Activate
    void activate(final LangfuseLoggerConfig config)
    {
        this.environment = resolveEnv(config.environment());
        final String host = resolveEnv(config.host());
        final String publicKey = resolveEnv(config.publicKey());
        final String secretKey = resolveEnv(config.secretKey());
        final boolean configured = StringUtils.isNotBlank(host)
            && StringUtils.isNotBlank(publicKey) && StringUtils.isNotBlank(secretKey);
        this.enabled = config.enabled() && configured;
        if (this.enabled) {
            this.ingestionUrl = StringUtils.stripEnd(host.trim(), "/") + INGESTION_PATH;
            this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
            this.executor = Executors.newFixedThreadPool(WORKER_THREADS);
            LOGGER.info("LangFuse LLM interaction logging is enabled, sending to {}", this.ingestionUrl);
        } else if (config.enabled()) {
            LOGGER.warn("LangFuse LLM interaction logging is enabled but the host or credentials are not set;"
                + " logging will stay off");
        } else {
            LOGGER.debug("LangFuse LLM interaction logging is disabled");
        }
    }

    @Deactivate
    void deactivate()
    {
        this.enabled = false;
        final ExecutorService toStop = this.executor;
        this.executor = null;
        if (toStop != null) {
            shutdown(toStop);
        }
    }

    @Override
    public boolean isEnabled()
    {
        return this.enabled;
    }

    @Override
    public void logInteraction(final LLMInteraction interaction)
    {
        final ExecutorService currentExecutor = this.executor;
        if (!this.enabled || interaction == null || currentExecutor == null) {
            return;
        }
        try {
            currentExecutor.submit(() -> send(interaction));
        } catch (RuntimeException e) {
            LOGGER.debug("Could not enqueue an LLM interaction for LangFuse logging", e);
        }
    }

    private void send(final LLMInteraction interaction)
    {
        try {
            final HttpPost post = new HttpPost(this.ingestionUrl);
            post.setHeader("Authorization", this.authHeader);
            post.setHeader("Content-Type", CONTENT_TYPE);
            post.setConfig(timeoutConfig());
            post.setEntity(new StringEntity(buildPayload(interaction), StandardCharsets.UTF_8));
            try (CloseableHttpClient client = HttpClients.createDefault();
                CloseableHttpResponse response = client.execute(post)) {
                final int status = response.getStatusLine().getStatusCode();
                if (status >= HTTP_BAD_REQUEST) {
                    LOGGER.warn("LangFuse ingestion returned {}: {}", status, readBody(response));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to send an LLM interaction to LangFuse: {}", e.getMessage());
        }
    }

    private String buildPayload(final LLMInteraction interaction)
    {
        final LLMSettings settings = interaction.getSettings();
        final String traceId = UUID.randomUUID().toString();
        final String startTime = Instant.ofEpochMilli(interaction.getStartEpochMillis()).toString();
        final String endTime = Instant.ofEpochMilli(interaction.getEndEpochMillis()).toString();
        final boolean failed = interaction.getErrorMessage() != null;
        final JsonArray input = buildInput(interaction.getSystemPrompt(), interaction.getMessages());

        final JsonObjectBuilder metadata = Json.createObjectBuilder();
        if (settings != null) {
            metadata.add("provider", str(settings.getProviderName()))
                .add("model", str(settings.getModelName()));
        }

        final JsonObjectBuilder trace = Json.createObjectBuilder()
            .add("id", traceId)
            .add("timestamp", startTime)
            .add("name", TRACE_NAME)
            .add("input", input)
            .add("metadata", metadata);
        addNullable(trace, "output", interaction.getOutput());
        addEnvironment(trace);

        final JsonObjectBuilder generation = Json.createObjectBuilder()
            .add("id", UUID.randomUUID().toString())
            .add("traceId", traceId)
            .add("name", GENERATION_NAME)
            .add("startTime", startTime)
            .add("endTime", endTime)
            .add("input", input)
            .add("level", failed ? "ERROR" : "DEFAULT");
        addNullable(generation, "output", interaction.getOutput());
        addNullable(generation, "statusMessage", interaction.getErrorMessage());
        addModel(generation, settings);
        addEnvironment(generation);

        final JsonArrayBuilder batch = Json.createArrayBuilder()
            .add(event("trace-create", endTime, trace))
            .add(event("generation-create", endTime, generation));
        return Json.createObjectBuilder().add("batch", batch).build().toString();
    }

    private JsonArray buildInput(final String systemPrompt, final List<LLMMessage> messages)
    {
        final JsonArrayBuilder turns = Json.createArrayBuilder();
        if (StringUtils.isNotBlank(systemPrompt)) {
            turns.add(Json.createObjectBuilder().add("role", "system").add("content", systemPrompt));
        }
        for (final LLMMessage message : messages) {
            turns.add(Json.createObjectBuilder()
                .add("role", str(message.getRole()))
                .add("content", str(message.getContent())));
        }
        return turns.build();
    }

    private void addModel(final JsonObjectBuilder builder, final LLMSettings settings)
    {
        if (settings == null) {
            return;
        }
        builder.add("model", str(settings.getModelName()));
        builder.add("modelParameters", Json.createObjectBuilder()
            .add("temperature", settings.getTemperature())
            .add("max_tokens", settings.getMaxOutputTokens()));
    }

    private void addEnvironment(final JsonObjectBuilder builder)
    {
        if (StringUtils.isNotBlank(this.environment)) {
            builder.add("environment", this.environment);
        }
    }

    private JsonObjectBuilder event(final String type, final String timestamp, final JsonObjectBuilder body)
    {
        return Json.createObjectBuilder()
            .add("id", UUID.randomUUID().toString())
            .add("type", type)
            .add("timestamp", timestamp)
            .add("body", body);
    }

    private void shutdown(final ExecutorService service)
    {
        service.shutdown();
        try {
            if (!service.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void addNullable(final JsonObjectBuilder builder, final String name, final String value)
    {
        if (value == null) {
            builder.addNull(name);
        } else {
            builder.add(name, value);
        }
    }

    private static RequestConfig timeoutConfig()
    {
        return RequestConfig.custom()
            .setConnectTimeout(INGESTION_TIMEOUT_MILLIS)
            .setConnectionRequestTimeout(INGESTION_TIMEOUT_MILLIS)
            .setSocketTimeout(INGESTION_TIMEOUT_MILLIS)
            .build();
    }

    private static String readBody(final CloseableHttpResponse response)
    {
        try {
            if (response.getEntity() == null) {
                return "";
            }
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static String resolveEnv(final String value)
    {
        if (value != null && value.startsWith(ENV_PREFIX)) {
            return System.getenv(value.substring(ENV_PREFIX.length()));
        }
        return value;
    }

    private static String str(final String value)
    {
        return value == null ? "" : value;
    }
}
