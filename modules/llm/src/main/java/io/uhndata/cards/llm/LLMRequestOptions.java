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

/**
 * Per-call overrides for a single {@link LLMClient} request. Every override is optional: an unset value means
 * "fall back to the active model's configured setting" (see {@link LLMSettings}). Two overrides are supported:
 * <ul>
 * <li>the maximum number of output tokens, so a caller can raise the ceiling for one call without changing the
 * shared JCR configuration — for instance the intake call sizes {@code max_tokens} from the document's section
 * count so a section-heavy document does not have its tag map silently truncated by the global default; and</li>
 * <li>a JSON Schema the provider must constrain the response to (structured outputs), so the reply is guaranteed
 * to be a JSON object of the required shape rather than free text that has to be parsed defensively.</li>
 * </ul>
 * Instances are immutable; build them with {@link #builder()} or one of the static factory methods.
 *
 * @version $Id$
 */
public final class LLMRequestOptions
{
    private final Long maxOutputTokens;

    private final String responseSchemaName;

    private final String responseSchema;

    private LLMRequestOptions(final Builder builder)
    {
        this.maxOutputTokens = builder.maxOutputTokens;
        this.responseSchemaName = builder.responseSchemaName;
        this.responseSchema = builder.responseSchema;
    }

    /**
     * A builder for assembling request options.
     *
     * @return a new, empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Options that override nothing; every setting falls back to the active model's configuration.
     *
     * @return a request-options instance with no overrides
     */
    public static LLMRequestOptions defaults()
    {
        return new Builder().build();
    }

    /**
     * Options that override only the maximum number of output tokens for this call.
     *
     * @param maxOutputTokens the maximum number of tokens to generate; must be positive
     * @return a request-options instance carrying the given output-token ceiling
     */
    public static LLMRequestOptions withMaxOutputTokens(final long maxOutputTokens)
    {
        return new Builder().maxOutputTokens(maxOutputTokens).build();
    }

    /**
     * The per-call maximum number of output tokens, or {@code null} when the model default should be used.
     *
     * @return the override, or {@code null} when not set
     */
    public Long getMaxOutputTokens()
    {
        return this.maxOutputTokens;
    }

    /**
     * The per-call maximum number of output tokens, or the given fallback when this call sets no override.
     *
     * @param fallback the value to use when no per-call override is set (typically
     *            {@link LLMSettings#getMaxOutputTokens()})
     * @return the per-call override when set, otherwise {@code fallback}
     */
    public long resolveMaxOutputTokens(final long fallback)
    {
        return this.maxOutputTokens == null ? fallback : this.maxOutputTokens;
    }

    /**
     * The name the provider associates with the response JSON Schema (structured outputs).
     *
     * @return the schema name, or {@code null} when no schema override is set
     */
    public String getResponseSchemaName()
    {
        return this.responseSchemaName;
    }

    /**
     * The JSON Schema, as a raw JSON string, the provider must constrain the response to (structured outputs).
     *
     * @return the schema JSON, or {@code null} when no schema override is set
     */
    public String getResponseSchema()
    {
        return this.responseSchema;
    }

    /**
     * Whether this call requests a schema-constrained (structured-output) response.
     *
     * @return {@code true} when both a schema name and a schema body are set
     */
    public boolean hasResponseSchema()
    {
        return this.responseSchema != null && !this.responseSchema.isBlank()
            && this.responseSchemaName != null && !this.responseSchemaName.isBlank();
    }

    /**
     * Builder for {@link LLMRequestOptions}.
     */
    public static final class Builder
    {
        private Long maxOutputTokens;

        private String responseSchemaName;

        private String responseSchema;

        private Builder()
        {
        }

        /**
         * Set the per-call maximum number of output tokens.
         *
         * @param tokens the maximum number of tokens to generate; must be positive
         * @return this builder
         */
        public Builder maxOutputTokens(final long tokens)
        {
            this.maxOutputTokens = tokens;
            return this;
        }

        /**
         * Request a schema-constrained (structured-output) response.
         *
         * @param name the name the provider associates with the schema (e.g. {@code cards_is_protocol_gate})
         * @param schema the JSON Schema, as a raw JSON string, the response must conform to
         * @return this builder
         */
        public Builder jsonSchema(final String name, final String schema)
        {
            this.responseSchemaName = name;
            this.responseSchema = schema;
            return this;
        }

        /**
         * Build an immutable {@link LLMRequestOptions} from this builder's state.
         *
         * @return the assembled request options
         */
        public LLMRequestOptions build()
        {
            return new LLMRequestOptions(this);
        }
    }
}
