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
import java.io.Writer;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;

/**
 * Servlet that exposes the LLM configuration catalog (providers and their models, with all parameters)
 * and the active selection, and lets administrators change which provider and model are active.
 *
 * <p>Endpoint: bound to the {@code cards/LLMConfiguration} node, selector {@code llm}, extension {@code json}.
 *
 * <p>{@code GET .../LLM.llm.json} returns:
 * <pre>{"activeProvider": "...", "activeModel": "...", "providers": [{"name": "...", ..., "models": [{...}]}]}</pre>
 *
 * <p>{@code POST .../LLM.llm.json} with parameters {@code activeProvider} and {@code activeModel} updates the
 * active selection and returns the refreshed catalog.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/LLMConfiguration" },
    selectors = { "llm" },
    extensions = { "json" },
    methods = { "GET", "POST" })
public class LLMConfigServlet extends SlingJakartaAllMethodsServlet
{
    private static final long serialVersionUID = -7913246809238126820L;

    private static final String ACTIVE_PROVIDER = "activeProvider";

    private static final String ACTIVE_MODEL = "activeModel";

    @Override
    protected void doGet(final SlingJakartaHttpServletRequest request,
        final SlingJakartaHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");
        try (Writer out = response.getWriter()) {
            out.write(buildCatalog(request.getResource()).toString());
        }
    }

    @Override
    protected void doPost(final SlingJakartaHttpServletRequest request,
        final SlingJakartaHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");

        final Resource config = request.getResource();
        final String provider = request.getParameter(ACTIVE_PROVIDER);
        final String model = request.getParameter(ACTIVE_MODEL);

        if (StringUtils.isBlank(provider) || StringUtils.isBlank(model)) {
            sendError(response, 400, "Both 'activeProvider' and 'activeModel' are required");
            return;
        }

        final Resource providerResource = config.getChild(provider);
        if (providerResource == null || providerResource.getChild(model) == null) {
            sendError(response, 400, "Unknown provider '" + provider + "' or model '" + model + "'");
            return;
        }

        final ModifiableValueMap properties = config.adaptTo(ModifiableValueMap.class);
        if (properties == null) {
            sendError(response, 403, "Not allowed to modify the LLM configuration");
            return;
        }
        properties.put(ACTIVE_PROVIDER, provider);
        properties.put(ACTIVE_MODEL, model);
        config.getResourceResolver().commit();

        try (Writer out = response.getWriter()) {
            out.write(buildCatalog(config).toString());
        }
    }

    private JsonObject buildCatalog(final Resource config)
    {
        final JsonObjectBuilder root = Json.createObjectBuilder();
        final ValueMap configProperties = config.getValueMap();
        addString(root, ACTIVE_PROVIDER, configProperties.get(ACTIVE_PROVIDER, String.class));
        addString(root, ACTIVE_MODEL, configProperties.get(ACTIVE_MODEL, String.class));

        final JsonArrayBuilder providers = Json.createArrayBuilder();
        for (final Resource provider : config.getChildren()) {
            final JsonObjectBuilder providerJson = propertiesToJson(provider);
            final JsonArrayBuilder models = Json.createArrayBuilder();
            for (final Resource model : provider.getChildren()) {
                models.add(propertiesToJson(model));
            }
            providerJson.add("models", models);
            providers.add(providerJson);
        }
        root.add("providers", providers);
        return root.build();
    }

    private JsonObjectBuilder propertiesToJson(final Resource resource)
    {
        final JsonObjectBuilder json = Json.createObjectBuilder();
        json.add("name", resource.getName());
        for (final Map.Entry<String, Object> entry : resource.getValueMap().entrySet()) {
            final String key = entry.getKey();
            if (!key.startsWith("jcr:") && !key.startsWith("sling:")) {
                addValue(json, key, entry.getValue());
            }
        }
        return json;
    }

    private void addValue(final JsonObjectBuilder json, final String key, final Object value)
    {
        if (value instanceof Double || value instanceof Float) {
            json.add(key, ((Number) value).doubleValue());
        } else if (value instanceof Number) {
            json.add(key, ((Number) value).longValue());
        } else if (value instanceof Boolean) {
            json.add(key, (Boolean) value);
        } else if (value != null) {
            json.add(key, value.toString());
        }
    }

    private void addString(final JsonObjectBuilder json, final String key, final String value)
    {
        if (value != null) {
            json.add(key, value);
        }
    }

    private void sendError(final SlingJakartaHttpServletResponse response, final int status, final String message)
        throws IOException
    {
        response.setStatus(status);
        try (Writer out = response.getWriter()) {
            out.write(Json.createObjectBuilder().add("error", message).build().toString());
        }
    }
}
