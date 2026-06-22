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

/**
 * Resolves the {@link LLMClient} that handles a given provider. Each client is registered as an OSGi service
 * with an {@code llm.provider} property naming the provider it serves; this factory looks them up by that
 * name. The provider names match the provider node names in the JCR LLM configuration (e.g. {@code "prompter"}
 * or {@code "claude"}).
 *
 * @version $Id$
 */
public interface LLMClientFactory
{
    /**
     * Return the client registered for the given provider name.
     *
     * @param providerName the provider name, matching a client's {@code llm.provider} service property
     * @return the matching client, or {@code null} if no client is registered for that provider
     */
    LLMClient getClient(String providerName);

    /**
     * Return the client for the provider that is currently active in the JCR LLM configuration.
     *
     * @return the active provider's client
     * @throws IOException if there is no active selection or no client is registered for the active provider
     */
    LLMClient getActiveClient() throws IOException;
}
