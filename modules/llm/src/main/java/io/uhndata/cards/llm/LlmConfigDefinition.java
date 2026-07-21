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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration for the {@link LlmEndpoint}, describing which OpenAI-compatible server to
 * forward prompts to. When no configuration is provided, the endpoint falls back to the constants
 * defined in {@link LlmEndpoint}.
 *
 * @version $Id$
 */
@ObjectClassDefinition(name = "CARDS LLM endpoint",
    description = "Connection details for the OpenAI-compatible chat completions server used by /llm")
public @interface LlmConfigDefinition
{
    /**
     * The base URL of the OpenAI-compatible server.
     *
     * @return a URL, e.g. {@code https://api.openai.com/v1}
     */
    @AttributeDefinition(name = "OpenAI base URL",
        description = "The base URL of the OpenAI-compatible chat completions server."
            + " If left empty, the default compiled into LlmEndpoint is used.")
    String openAiBaseUrl() default "";

    /**
     * The API key to authenticate against the OpenAI-compatible server.
     *
     * @return an API key
     */
    @AttributeDefinition(name = "OpenAI API key", type = org.osgi.service.metatype.annotations.AttributeType.PASSWORD,
        description = "The API key sent to the OpenAI-compatible server."
            + " If left empty, the default compiled into LlmEndpoint is used."
            + " Prefer setting this here rather than in source so the secret stays out of version control.")
    String openAiApiKey() default "";

    /**
     * The model name to request from the server.
     *
     * @return a model name, e.g. {@code gpt-4o-mini}
     */
    @AttributeDefinition(name = "Model",
        description = "The name of the chat model to request, for example gpt-4o-mini.")
    String model() default "gpt-4o-mini";
}
