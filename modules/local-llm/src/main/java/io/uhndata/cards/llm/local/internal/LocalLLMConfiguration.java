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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
    name = "Local LLM Provider",
    description = "Configuration for a local or self-hosted LLM accessible via the OpenAI-compatible "
        + "chat completions API (e.g. Ollama at http://localhost:11434/v1/chat/completions, "
        + "LM Studio at http://localhost:1234/v1/chat/completions).")
public @interface LocalLLMConfiguration
{
    @AttributeDefinition(
        name = "Endpoint URL",
        description = "Full URL of the OpenAI-compatible /v1/chat/completions endpoint.")
    String endpoint() default "http://localhost:11434/v1/chat/completions";

    @AttributeDefinition(
        name = "Model",
        description = "Model name as understood by the local server (e.g. llama3.2, mistral, phi4).")
    String model() default "llama3.2";

    @AttributeDefinition(
        name = "Max Tokens",
        description = "Maximum number of tokens to generate in the response.")
    int maxTokens() default 1024;

    @AttributeDefinition(
        name = "API Key Environment Variable",
        description = "Optional. Name of the environment variable that holds a Bearer token "
            + "for servers that require authentication. Leave blank if no auth is needed.")
    String apiKeyEnvVar() default "";
}
