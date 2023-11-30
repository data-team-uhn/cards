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
package io.uhndata.cards.googleapis;

import java.io.IOException;
import java.io.Writer;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * A servlet that returns the current Google API key.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Homepage" },
    methods = { "GET" },
    selectors = { "googleApiKey" }
    )
public class GoogleApiKeyServlet extends SlingSafeMethodsServlet
{
    /**
     * The response from this service is a JSON object with this key holding the Google API key as its value.
     */
    private static final String RESPONSE_JSON_KEY = "apikey";
    private static final long serialVersionUID = -574543232589675813L;

    @Reference
    private GoogleApiKeyManager apiKeyManager;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json");
        try (Writer out = response.getWriter(); JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            String key = this.apiKeyManager.getAPIKey();
            jsonGen.write(RESPONSE_JSON_KEY, key);
            jsonGen.writeEnd().flush();
        }
    }
}
