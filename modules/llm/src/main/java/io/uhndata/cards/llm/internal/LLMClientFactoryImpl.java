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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMClientFactory;
import io.uhndata.cards.llm.LLMConfigurationService;

/**
 * Default {@link LLMClientFactory}. Collects all {@link LLMClient} services that declare an
 * {@code llm.provider} property, keyed by that name, and resolves the active provider's client using the
 * {@link LLMConfigurationService}.
 *
 * @version $Id$
 */
@Component(service = LLMClientFactory.class)
public class LLMClientFactoryImpl implements LLMClientFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LLMClientFactoryImpl.class);

    private static final String PROVIDER_PROPERTY = "llm.provider";

    private final Map<String, LLMClient> clients = new ConcurrentHashMap<>();

    @Reference
    private LLMConfigurationService configurationService;

    @Reference(service = LLMClient.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        target = "(" + PROVIDER_PROPERTY + "=*)")
    void bindClient(final LLMClient client, final Map<String, Object> props)
    {
        final String name = (String) props.get(PROVIDER_PROPERTY);
        if (name != null) {
            this.clients.put(name, client);
            LOGGER.debug("Registered LLM client for provider: {}", name);
        }
    }

    void unbindClient(final LLMClient client, final Map<String, Object> props)
    {
        final String name = (String) props.get(PROVIDER_PROPERTY);
        if (name != null) {
            this.clients.remove(name, client);
            LOGGER.debug("Unregistered LLM client for provider: {}", name);
        }
    }

    @Override
    public LLMClient getClient(final String providerName)
    {
        return providerName == null ? null : this.clients.get(providerName);
    }

    @Override
    public LLMClient getActiveClient() throws IOException
    {
        final String providerName = this.configurationService.getActiveSettings().getProviderName();
        final LLMClient client = getClient(providerName);
        if (client == null) {
            throw new IOException("No LLM client is registered for the active provider '" + providerName
                + "'. Registered providers: " + this.clients.keySet());
        }
        return client;
    }
}
