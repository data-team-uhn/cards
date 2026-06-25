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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration for the LangFuse interaction logger. The string settings accept a literal value or the
 * {@code %ENV%NAME} form, which is resolved from the {@code NAME} environment variable at activation time so
 * that secrets need not be stored in the configuration. Logging stays off until {@link #enabled()} is set and
 * the host and credentials resolve to non-blank values.
 *
 * @version $Id$
 */
@ObjectClassDefinition(name = "CARDS LLM - LangFuse interaction logging",
    description = "Sends a trace and generation to a LangFuse instance for every LLM chat interaction.")
public @interface LangfuseLoggerConfig
{
    /**
     * Whether interaction logging is turned on.
     *
     * @return {@code true} to enable logging
     */
    @AttributeDefinition(name = "Enabled",
        description = "Send LLM interactions to LangFuse. Requires the host and credentials below to be set.")
    boolean enabled() default false;

    /**
     * The base URL of the LangFuse instance.
     *
     * @return the host URL, or a {@code %ENV%NAME} reference to one
     */
    @AttributeDefinition(name = "Host",
        description = "Base URL of the LangFuse instance, e.g. https://langfuse.example.org. The ingestion path"
            + " is appended automatically. Accepts %ENV%NAME to read from an environment variable.")
    String host() default "%ENV%LANGFUSE_HOST";

    /**
     * The LangFuse public key.
     *
     * @return the public key, or a {@code %ENV%NAME} reference to one
     */
    @AttributeDefinition(name = "Public key",
        description = "LangFuse project public key (used as the Basic auth username)."
            + " Accepts %ENV%NAME to read from an environment variable.")
    String publicKey() default "%ENV%LANGFUSE_PUBLIC_KEY";

    /**
     * The LangFuse secret key.
     *
     * @return the secret key, or a {@code %ENV%NAME} reference to one
     */
    @AttributeDefinition(name = "Secret key",
        description = "LangFuse project secret key (used as the Basic auth password)."
            + " Accepts %ENV%NAME to read from an environment variable.")
    String secretKey() default "%ENV%LANGFUSE_SECRET_KEY";

    /**
     * The optional LangFuse environment tag applied to every trace and generation.
     *
     * @return the environment tag, a {@code %ENV%NAME} reference to one, or blank for none
     */
    @AttributeDefinition(name = "Environment",
        description = "Optional LangFuse environment tag (e.g. production, staging) applied to every trace."
            + " Accepts %ENV%NAME to read from an environment variable. Leave blank for none.")
    String environment() default "%ENV%LANGFUSE_TRACING_ENVIRONMENT";
}
