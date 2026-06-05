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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
    name = "Anthropic API Client",
    description = "Configuration for the Anthropic Claude Messages API client.")
public @interface AnthropicConfiguration
{
    @AttributeDefinition(
        name = "API Key Environment Variable",
        description = "Name of the environment variable that holds the Anthropic API key.")
    String apiKeyEnvVar() default "ANTHROPIC_API_KEY";

    @AttributeDefinition(
        name = "API Endpoint",
        description = "The Anthropic Messages API endpoint URL.")
    String apiEndpoint() default "https://api.anthropic.com/v1/messages";

    @AttributeDefinition(
        name = "Model",
        description = "Claude model ID to use (e.g. claude-sonnet-4-6, claude-opus-4-8, claude-haiku-4-5-20251001).")
    String model() default "claude-sonnet-4-6";

    @AttributeDefinition(
        name = "Max Tokens",
        description = "Maximum number of tokens in the response.")
    int maxTokens() default 1024;

    @AttributeDefinition(
        name = "API Version",
        description = "Value for the required anthropic-version request header.")
    String apiVersion() default "2023-06-01";
}
