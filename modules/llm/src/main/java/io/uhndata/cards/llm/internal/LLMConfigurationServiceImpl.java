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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.llm.LLMConfigurationService;
import io.uhndata.cards.llm.LLMSettings;

/**
 * Default {@link LLMConfigurationService} that reads the active provider and model from the JCR node at
 * {@link #CONFIG_PATH}, using a dedicated read-only service user.
 *
 * @version $Id$
 */
@Component(service = LLMConfigurationService.class)
public class LLMConfigurationServiceImpl implements LLMConfigurationService
{
    /** The JCR path of the LLM configuration node. */
    public static final String CONFIG_PATH = "/apps/cards/config/LLM";

    private static final String SUBSERVICE = "llmConfig";

    private static final String ACTIVE_PROVIDER = "activeProvider";

    private static final String ACTIVE_MODEL = "activeModel";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public LLMSettings getActiveSettings() throws IOException
    {
        try (ResourceResolver resolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, SUBSERVICE))) {
            final Resource config = resolver.getResource(CONFIG_PATH);
            if (config == null) {
                throw new IOException("LLM configuration not found at " + CONFIG_PATH);
            }
            final ValueMap configProps = config.getValueMap();
            final String providerName = configProps.get(ACTIVE_PROVIDER, String.class);
            final String modelName = configProps.get(ACTIVE_MODEL, String.class);
            if (StringUtils.isBlank(providerName) || StringUtils.isBlank(modelName)) {
                throw new IOException("No active LLM provider/model is selected in " + CONFIG_PATH);
            }
            final Resource provider = config.getChild(providerName);
            if (provider == null) {
                throw new IOException("Active LLM provider '" + providerName + "' does not exist");
            }
            final Resource model = provider.getChild(modelName);
            if (model == null) {
                throw new IOException("Active LLM model '" + modelName + "' does not exist under provider '"
                    + providerName + "'");
            }
            return new LLMSettings(providerName, toMap(provider.getValueMap()),
                modelName, toMap(model.getValueMap()));
        } catch (LoginException e) {
            throw new IOException("Could not access the LLM configuration", e);
        }
    }

    private static Map<String, Object> toMap(final ValueMap valueMap)
    {
        final Map<String, Object> result = new HashMap<>();
        for (final Map.Entry<String, Object> entry : valueMap.entrySet()) {
            final String key = entry.getKey();
            if (!key.startsWith("jcr:") && !key.startsWith("sling:")) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }
}
