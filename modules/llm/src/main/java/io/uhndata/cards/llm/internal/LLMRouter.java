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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMMessage;
import io.uhndata.cards.llm.LLMProvider;

/**
 * Routes {@link LLMClient} calls to the configured {@link LLMProvider}.
 * The active provider is selected by name via OSGi configuration; switching it takes effect immediately
 * without restarting the bundle.
 *
 * @version $Id$
 */
@Component(service = LLMClient.class, immediate = true)
@Designate(ocd = LLMConfiguration.class)
public class LLMRouter implements LLMClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LLMRouter.class);

    private final Map<String, LLMProvider> providers = new ConcurrentHashMap<>();

    private volatile String activeProvider;

    @Reference(service = LLMProvider.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC)
    void bindProvider(final LLMProvider provider, final Map<String, Object> props)
    {
        final String name = (String) props.get("llm.provider");
        if (name != null) {
            this.providers.put(name, provider);
            LOGGER.debug("Registered LLM provider: {}", name);
        }
    }

    void unbindProvider(final LLMProvider provider, final Map<String, Object> props)
    {
        final String name = (String) props.get("llm.provider");
        if (name != null) {
            this.providers.remove(name, provider);
            LOGGER.debug("Unregistered LLM provider: {}", name);
        }
    }

    @Activate
    @Modified
    void activate(final LLMConfiguration config)
    {
        this.activeProvider = config.activeProvider();
        LOGGER.info("LLM active provider set to: {}", this.activeProvider);
    }

    @Override
    public String chat(final String userMessage) throws IOException
    {
        return getProvider().chat(userMessage);
    }

    @Override
    public String chat(final String systemPrompt, final String userMessage) throws IOException
    {
        return getProvider().chat(systemPrompt, userMessage);
    }

    @Override
    public String chat(final String systemPrompt, final List<LLMMessage> messages) throws IOException
    {
        return getProvider().chat(systemPrompt, messages);
    }

    private LLMProvider getProvider() throws IOException
    {
        final LLMProvider provider = this.providers.get(this.activeProvider);
        if (provider == null) {
            throw new IOException("LLM provider '" + this.activeProvider
                + "' is not available. Registered providers: " + this.providers.keySet());
        }
        return provider;
    }
}
