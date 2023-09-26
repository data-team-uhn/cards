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
package io.uhndata.cards.google;

import javax.jcr.Node;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * A component that provide access to Google API key through node or environment.
 *
 * @version $Id$
 */
@Component
public class DefaultGoogleApiKeyManager implements GoogleApiKeyManager
{
    /** OS environment variable which has the api key for Google API. */
    private static final String APIKEY_ENVIRONMENT_VARIABLE = "GOOGLE_APIKEY";

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultGoogleApiKeyManager.class);

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Override
    public String getAPIKey()
    {
        String key = this.getAPIKeyFromNode();
        if ("".equals(key)) {
            // if node does not exist, get api key from env variable
            key = this.getAPIKeyFromEnvironment();
        }
        return key;
    }

    @Override
    public String getAPIKeyFromEnvironment()
    {
        String apiKey = System.getenv(APIKEY_ENVIRONMENT_VARIABLE);
        apiKey = StringUtils.isBlank(apiKey) ? "" : apiKey;
        LOGGER.debug("Google API key as set in the OS environment: [{}]", apiKey);
        return apiKey;
    }

    @Override
    public String getAPIKeyFromNode()
    {
        String resourcePath = "/libs/cards/conf/GoogleApiKey";
        String apiKey = "";

        try {
            Resource res = this.rrp.getThreadResourceResolver().resolve(resourcePath);
            if (!res.isResourceType(Resource.RESOURCE_TYPE_NON_EXISTING)) {
                Node keyNode = res.adaptTo(Node.class);
                apiKey = keyNode.getProperty("key").getString();
                LOGGER.debug("Google API key as set in the GoogleApiKey node: [{}]", apiKey);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load Google API key from node: {}", e.getMessage(), e);
        }
        return apiKey;
    }
}
