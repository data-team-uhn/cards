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
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.uhndata.cards.llm.DefaultLLMClient;
import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMConfigurationService;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMRequestOptions;
import io.uhndata.cards.llm.LLMSettings;

/**
 * {@link LLMClient} for OpenAI-compatible chat completions endpoints (Prompter, Ollama, LM Studio, etc.),
 * registered for the {@code "openai"} API. Providers select it through their {@code api} property rather than by
 * name, so a single client serves every OpenAI-compatible provider. The request is dispatched with the
 * LangChain4j {@link OpenAiChatModel} (on its JDK-HTTP-client transport, wired explicitly to avoid an OSGi
 * {@code ServiceLoader} lookup): the configured endpoint becomes the model's base URL, the API key is sent as a
 * Bearer token, temperature / max-output-tokens / timeout come from the active model, and the OpenAI-specific
 * extras that LangChain4j does not model directly — a {@code response_format} JSON Schema for structured
 * outputs, {@code chat_template_kwargs.enable_thinking=false}, and an optional {@code project_id} — are passed
 * verbatim through the model's {@code customParameters} (serialized as top-level request fields). All settings
 * come from the active provider and model in the JCR LLM configuration.
 *
 * @version $Id$
 */
@Component(
    service = LLMClient.class,
    property = { "llm.provider=openai" },
    immediate = true)
public class OpenAIClient extends DefaultLLMClient
{
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final String PROJECT_ID = "projectId";

    @Reference
    void setConfigurationService(final LLMConfigurationService service)
    {
        this.configurationService = service;
    }

    @Override
    protected String doChat(final String systemPrompt, final List<LLMMessage> messages,
        final LLMRequestOptions options) throws IOException
    {
        final LLMSettings settings = getConfigurationService().getActiveSettings();
        final OpenAiChatModel model = buildModel(settings, options);
        try {
            final ChatResponse response = model.chat(toChatMessages(systemPrompt, messages));
            return response.aiMessage().text();
        } catch (final RuntimeException e) {
            // LangChain4j signals transport / HTTP / provider errors with runtime exceptions; the pipeline
            // expects an IOException it can surface to the servlet, so translate rather than let it escape raw.
            throw new IOException("OpenAI-compatible LLM request failed: " + e.getMessage(), e);
        }
    }

    private static OpenAiChatModel buildModel(final LLMSettings settings, final LLMRequestOptions options)
    {
        final long maxTokens = options == null
            ? settings.getMaxOutputTokens() : options.resolveMaxOutputTokens(settings.getMaxOutputTokens());
        final OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
            .httpClientBuilder(new JdkHttpClientBuilder())
            .baseUrl(resolveBaseUrl(settings.getEndpoint()))
            .modelName(settings.getModelName())
            .temperature(settings.getTemperature())
            .maxTokens((int) maxTokens)
            .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
            .customParameters(customParameters(settings, options));
        final String apiKey = resolveApiKey(settings);
        if (StringUtils.isNotBlank(apiKey)) {
            builder.apiKey(apiKey);
        }
        return builder.build();
    }

    /**
     * The base URL LangChain4j appends {@code /chat/completions} to. The configured endpoint may already carry
     * that suffix (the previous client appended it explicitly); strip it here so the final URL is unchanged.
     *
     * @param endpoint the configured provider endpoint
     * @return the base URL without a trailing {@code /chat/completions} or slash
     */
    private static String resolveBaseUrl(final String endpoint)
    {
        final String trimmed = StringUtils.stripEnd(StringUtils.trimToEmpty(endpoint), "/");
        if (trimmed.endsWith(CHAT_COMPLETIONS_PATH)) {
            return trimmed.substring(0, trimmed.length() - CHAT_COMPLETIONS_PATH.length());
        }
        return trimmed;
    }

    private static String resolveApiKey(final LLMSettings settings)
    {
        final String apiKeyEnvVar = settings.getApiKeyEnvVar();
        return StringUtils.isNotBlank(apiKeyEnvVar) ? System.getenv(apiKeyEnvVar) : null;
    }

    /**
     * The OpenAI-compatible request extras carried verbatim as top-level body fields: always
     * {@code chat_template_kwargs.enable_thinking=false}, an optional {@code project_id}, and a
     * {@code response_format} JSON Schema when the call requests structured output.
     *
     * @param settings the active settings
     * @param options the per-call options, or {@code null}
     * @return the custom-parameters map for the LangChain4j model
     */
    private static Map<String, Object> customParameters(final LLMSettings settings, final LLMRequestOptions options)
    {
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("chat_template_kwargs", Collections.singletonMap("enable_thinking", Boolean.FALSE));
        final String projectId = settings.getProviderProperty(PROJECT_ID);
        if (StringUtils.isNotBlank(projectId)) {
            params.put("project_id", projectId);
        }
        if (options != null && options.hasResponseSchema()) {
            params.put("response_format", responseFormat(options));
        }
        return params;
    }

    /**
     * Build the OpenAI {@code response_format} object pinning the reply to a JSON Schema (structured outputs):
     * {@code {"type":"json_schema","json_schema":{"name":...,"strict":true,"schema":{...}}}}.
     *
     * @param options the per-call options carrying the schema name and body
     * @return the {@code response_format} value as a nested map
     */
    private static Map<String, Object> responseFormat(final LLMRequestOptions options)
    {
        final Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", options.getResponseSchemaName());
        jsonSchema.put("strict", Boolean.TRUE);
        jsonSchema.put("schema", parseSchema(options.getResponseSchema()));
        final Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("json_schema", jsonSchema);
        return format;
    }

    private static Object parseSchema(final String schema)
    {
        try (JsonReader reader = Json.createReader(new StringReader(schema))) {
            return toJava(reader.readValue());
        }
    }

    private static List<ChatMessage> toChatMessages(final String systemPrompt, final List<LLMMessage> messages)
    {
        final List<ChatMessage> turns = new ArrayList<>();
        if (StringUtils.isNotBlank(systemPrompt)) {
            turns.add(SystemMessage.from(systemPrompt));
        }
        for (final LLMMessage message : messages) {
            turns.add(toChatMessage(message));
        }
        return turns;
    }

    private static ChatMessage toChatMessage(final LLMMessage message)
    {
        final String role = message.getRole();
        if ("assistant".equalsIgnoreCase(role)) {
            return AiMessage.from(message.getContent());
        }
        if ("system".equalsIgnoreCase(role)) {
            return SystemMessage.from(message.getContent());
        }
        return UserMessage.from(message.getContent());
    }

    /**
     * Convert a parsed {@code jakarta.json} value into plain Java objects (maps, lists, strings, numbers,
     * booleans, null) so LangChain4j's Jackson serializer emits it as native nested JSON in the request body,
     * rather than trying to serialize the {@code jakarta.json} types as beans.
     *
     * @param value the parsed JSON value
     * @return the equivalent plain Java object
     */
    private static Object toJava(final JsonValue value)
    {
        final Object result;
        final JsonValue.ValueType type = value.getValueType();
        if (type == JsonValue.ValueType.OBJECT) {
            result = toMap(value.asJsonObject());
        } else if (type == JsonValue.ValueType.ARRAY) {
            result = toList(value.asJsonArray());
        } else if (type == JsonValue.ValueType.STRING) {
            result = ((JsonString) value).getString();
        } else if (type == JsonValue.ValueType.NUMBER) {
            result = toNumber((JsonNumber) value);
        } else if (type == JsonValue.ValueType.TRUE) {
            result = Boolean.TRUE;
        } else if (type == JsonValue.ValueType.FALSE) {
            result = Boolean.FALSE;
        } else {
            result = null;
        }
        return result;
    }

    private static Map<String, Object> toMap(final JsonObject object)
    {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonValue> entry : object.entrySet()) {
            map.put(entry.getKey(), toJava(entry.getValue()));
        }
        return map;
    }

    private static List<Object> toList(final JsonArray array)
    {
        final List<Object> list = new ArrayList<>();
        for (final JsonValue item : array) {
            list.add(toJava(item));
        }
        return list;
    }

    private static Object toNumber(final JsonNumber number)
    {
        return number.isIntegral() ? Long.valueOf(number.longValue()) : Double.valueOf(number.doubleValue());
    }
}
