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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of the settings for the active LLM provider and model, resolved from the JCR
 * configuration. A provider carries connection-level settings (endpoint, credentials, timeout) plus
 * format-specific extras (such as {@code projectId} for Prompter), while a model carries generation
 * settings (the model identifier, token limits and temperature).
 *
 * @version $Id$
 */
public final class LLMSettings
{
    private static final String ENDPOINT = "endpoint";

    private static final String API_KEY_ENV_VAR = "apiKeyEnvVar";

    private static final String TIMEOUT_SECONDS = "timeoutSeconds";

    private static final String MAX_OUTPUT_TOKENS = "maxOutputTokens";

    private static final String TEMPERATURE = "temperature";

    private static final String CONTEXT_LIMIT_TOKENS = "contextLimitTokens";

    private static final String CHUNK_TOKEN_SIZE = "chunkTokenSize";

    private static final String DEVELOPER = "developer";

    private static final String MODEL_ID = "modelId";

    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    private static final long DEFAULT_MAX_OUTPUT_TOKENS = 1000;

    private final String providerName;

    private final String modelName;

    private final Map<String, Object> providerProperties;

    private final Map<String, Object> modelProperties;

    /**
     * Create a settings snapshot.
     *
     * @param providerName the name of the active provider node
     * @param providerProperties the properties of the active provider node
     * @param modelName the name of the active model node
     * @param modelProperties the properties of the active model node
     */
    public LLMSettings(final String providerName, final Map<String, Object> providerProperties,
        final String modelName, final Map<String, Object> modelProperties)
    {
        this.providerName = providerName;
        this.modelName = modelName;
        this.providerProperties = providerProperties == null
            ? Collections.emptyMap() : new HashMap<>(providerProperties);
        this.modelProperties = modelProperties == null
            ? Collections.emptyMap() : new HashMap<>(modelProperties);
    }

    /**
     * The name of the active provider node.
     *
     * @return the provider node name
     */
    public String getProviderName()
    {
        return this.providerName;
    }

    /**
     * The name of the active model node.
     *
     * @return the model node name
     */
    public String getModelName()
    {
        return this.modelName;
    }

    /**
     * The base URL of the active provider's API.
     *
     * @return the endpoint URL, or {@code null} if not set
     */
    public String getEndpoint()
    {
        return string(this.providerProperties, ENDPOINT);
    }

    /**
     * The name of the environment variable holding the active provider's API key.
     *
     * @return the environment variable name, or {@code null} if not set
     */
    public String getApiKeyEnvVar()
    {
        return string(this.providerProperties, API_KEY_ENV_VAR);
    }

    /**
     * The request timeout for the active provider, in seconds.
     *
     * @return the timeout in seconds, or a default of 120 if not set
     */
    public long getTimeoutSeconds()
    {
        return number(this.providerProperties, TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * The identifier of the active model, as sent to the API in the request body. This is the model's node
     * name in the JCR configuration, unless an explicit {@code modelId} property overrides it (useful when the
     * provider's model identifier is not a valid JCR node name, e.g. {@code llama3.2:3b}).
     *
     * @return the model identifier
     */
    public String getModelId()
    {
        final String explicit = string(this.modelProperties, MODEL_ID);
        return explicit != null ? explicit : this.modelName;
    }

    /**
     * The maximum number of tokens to generate in the response for the active model.
     *
     * @return the maximum output tokens, or a default of 1000 if not set
     */
    public long getMaxOutputTokens()
    {
        return number(this.modelProperties, MAX_OUTPUT_TOKENS, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    /**
     * The sampling temperature for the active model.
     *
     * @return the temperature, or 0.0 if not set
     */
    public double getTemperature()
    {
        final Object value = this.modelProperties.get(TEMPERATURE);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * The maximum context window of the active model, in tokens.
     *
     * @return the context limit in tokens, or 0 if not set
     */
    public long getContextLimitTokens()
    {
        return number(this.modelProperties, CONTEXT_LIMIT_TOKENS, 0);
    }

    /**
     * The number of input tokens to send per chunk when the input exceeds the context window.
     *
     * @return the chunk token size, or 0 if not set
     */
    public long getChunkTokenSize()
    {
        return number(this.modelProperties, CHUNK_TOKEN_SIZE, 0);
    }

    /**
     * The organization that developed the active model (e.g. {@code google}, {@code anthropic}, {@code alibaba}).
     *
     * @return the developer name, or {@code null} if not set
     */
    public String getDeveloper()
    {
        return string(this.modelProperties, DEVELOPER);
    }

    /**
     * Read an arbitrary, format-specific property of the active provider (such as {@code projectId} or
     * {@code apiVersion}).
     *
     * @param name the property name
     * @return the property value as a string, or {@code null} if not set
     */
    public String getProviderProperty(final String name)
    {
        return string(this.providerProperties, name);
    }

    /**
     * Read an arbitrary, format-specific property of the active model.
     *
     * @param name the property name
     * @return the property value as a string, or {@code null} if not set
     */
    public String getModelProperty(final String name)
    {
        return string(this.modelProperties, name);
    }

    private static String string(final Map<String, Object> properties, final String key)
    {
        final Object value = properties.get(key);
        return value == null ? null : value.toString();
    }

    private static long number(final Map<String, Object> properties, final String key, final long defaultValue)
    {
        final Object value = properties.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
