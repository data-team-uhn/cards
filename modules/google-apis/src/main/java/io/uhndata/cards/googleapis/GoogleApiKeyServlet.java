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

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.Servlet;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * A servlet that returns the current Google API key. It is bound to the configuration node itself, rather than to
 * the homepage, because {@code /content} is denied to patients, who need the key for the address question.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class },
    property = { "sling.auth.requirements=-/libs/cards/conf/GoogleApiKey.googleApiKey" })
@SlingServletResourceTypes(
    resourceTypes = { "cards/GoogleApiKeyConf" },
    methods = { "GET" },
    selectors = { "googleApiKey" })
public class GoogleApiKeyServlet extends SlingJakartaSafeMethodsServlet
{
    /**
     * The response from this service is a JSON object with this key holding the Google API key as its value.
     */
    private static final String RESPONSE_JSON_KEY = "apikey";

    private static final long serialVersionUID = -574543232589675813L;

    @Reference
    private GoogleApiKeyManager apiKeyManager;

    @Override
    public void doGet(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        response.setContentType("application/json");
        try (Writer out = response.getWriter(); JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            String key = this.apiKeyManager.getAPIKey();
            jsonGen.write(RESPONSE_JSON_KEY, key);
            jsonGen.writeEnd().flush();
        } catch (Exception e) {
            // This usually happens because we're closing the writer twice, through the generator and as itself
        }
    }
}
