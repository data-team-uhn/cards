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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
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
    private static final String APIKEY_ENVIRONMENT_VARIABLE =
        "BIOPORTAL_APIKEY";

    /** The response form this service is a JSON objct with this JSON key containing the API key. */
    private static final String RESPONSE_JSON_KEY =
        "apikey";

    private static final Logger LOGGER = LoggerFactory.getLogger(VocabularyIndexerServlet.class);

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        // Our normal output would be ourselves as a JSONObject
        JsonObject json = request.getResource().adaptTo(JsonObject.class);
        response.setContentType("application/json");
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            jsonGen.write(RESPONSE_JSON_KEY, this.getAPIKeyFromEnvironment());
            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Retrieves BioPortal API key from the OS environment variable.
     * If the environment variable is not specified returns an empty string.
     *
     * @return BioPortal API key
     */
    private String getAPIKeyFromEnvironment()
    {
        String apiKey = System.getenv(APIKEY_ENVIRONMENT_VARIABLE);
        apiKey = StringUtils.isBlank(apiKey) ? "" : apiKey;
        LOGGER.info("BioPortal API key as set in the OS environment: [{}]", apiKey);
        return apiKey;
    }
}
