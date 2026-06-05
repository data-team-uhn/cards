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
 * SPI interface for LLM backend implementations. Register as an OSGi service with the
 * {@code llm.provider} service property set to a unique name (e.g. {@code "anthropic"} or {@code "local"}).
 * The {@link LLMClient} router will delegate to the provider whose name matches the configured
 * {@code activeProvider} value.
 *
 * @version $Id$
 */
public interface LLMProvider extends LLMClient
{
    /**
     * Returns the provider's unique identifier. Must match the {@code llm.provider} OSGi service property.
     *
     * @return a short, stable name such as {@code "anthropic"} or {@code "local"}
     */
    String getProviderName();
}
