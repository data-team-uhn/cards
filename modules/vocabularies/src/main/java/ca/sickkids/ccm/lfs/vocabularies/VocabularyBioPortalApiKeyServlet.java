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
package ca.sickkids.ccm.lfs.vocabularies;

import java.io.IOException;
import java.io.Writer;

import javax.jcr.Node;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servlet that returns the current BioPortal API key.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "lfs/VocabulariesHomepage" },
    methods = { "GET" },
    selectors = { "bioportalApiKey" }
    )
public class VocabularyBioPortalApiKeyServlet extends SlingSafeMethodsServlet
{
    /** OS environment variable which has the api key for bioontology portal. */
    private static final String APIKEY_ENVIRONMENT_VARIABLE = "BIOPORTAL_APIKEY";

    /** The response from this service is a JSON object with this key holding the BioPortal API key as its value. */
    private static final String RESPONSE_JSON_KEY = "apikey";

    private static final Logger LOGGER = LoggerFactory.getLogger(VocabularyIndexerServlet.class);

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        // req is the SlingHttpServletRequest
        ResourceResolver resourceResolver = request.getResourceResolver();

        response.setContentType("application/json");
        final Writer out = response.getWriter();
        JsonGenerator jsonGen = Json.createGenerator(out);
        jsonGen.writeStartObject();
        String key = this.getAPIKeyFromNode(resourceResolver);
        if (!"".equals(key)) {
            // if node exists
            jsonGen.write(RESPONSE_JSON_KEY, key);
        } else {
            // if node does not exist, get api key from env variable
            jsonGen.write(RESPONSE_JSON_KEY, this.getAPIKeyFromEnvironment());
        }
        jsonGen.writeEnd().flush();
    }

    /**
     * Retrieves BioPortal API key from the OS environment variable.
     * If the environment variable is not specified returns an empty string.
     *
     * @return BioPortal API key
     */
    public String getAPIKeyFromEnvironment()
    {
        String apiKey = System.getenv(APIKEY_ENVIRONMENT_VARIABLE);
        apiKey = StringUtils.isBlank(apiKey) ? "" : apiKey;
        LOGGER.info("BioPortal API key as set in the OS environment: [{}]", apiKey);
        return apiKey;
    }

    /**
     * Retrieves BioPortal API key from the node. If the key is not specified returns an empty string.
     *
     * @param resourceResolver resource resolver
     * @return BioPortal API key
     */
    public String getAPIKeyFromNode(ResourceResolver resourceResolver)
    {
        String resourcePath = "/libs/lfs/conf/BioportalApiKey";
        String apiKey = "";

        try {
            Resource res = resourceResolver.getResource(resourcePath);
            Node keyNode = res.adaptTo(Node.class);
            apiKey = keyNode.getProperty("key").getString();
            LOGGER.info("BioPortal API key as set in the BioportalApiKey node: [{}]", apiKey);
        } catch (Exception e) {
            LOGGER.error("Failed to load BioPortal API key from node: {}", e.getMessage(), e);
        }
        return apiKey;
    }
}
