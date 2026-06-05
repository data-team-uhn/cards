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
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.llm.LLMClient;
import io.uhndata.cards.llm.LLMMessage;

/**
 * Servlet that proxies POST requests to the configured LLM provider.
 * The provider and its credentials stay on the server; clients only send message content.
 *
 * <p>Endpoint: {@code POST /.llm}
 *
 * <p>Single-turn request:
 * <pre>{"message": "Hello", "system": "(optional)"}</pre>
 *
 * <p>Multi-turn request:
 * <pre>{"messages": [{"role": "user", "content": "Hello"}, ...], "system": "(optional)"}</pre>
 *
 * <p>Response: {@code {"response": "..."}}
 * <p>Error response: {@code {"error": "..."}} with an appropriate HTTP status code.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Homepage" },
    methods = { "POST" },
    selectors = { "llm" })
public class LLMServlet extends SlingJakartaAllMethodsServlet
{
    private static final long serialVersionUID = 4938271560024819437L;

    @Reference
    private LLMClient llmClient;

    @Override
    protected void doPost(final SlingJakartaHttpServletRequest request,
        final SlingJakartaHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");

        final JsonObject body;
        try (JsonReader reader = Json.createReader(request.getReader())) {
            body = reader.readObject();
        } catch (Exception e) {
            sendError(response, 400, "Invalid JSON request body");
            return;
        }

        final String system = body.getString("system", null);
        final String message = body.getString("message", null);
        final JsonArray messages = body.getJsonArray("messages");

        try {
            final String reply;
            if (messages != null) {
                reply = this.llmClient.chat(system, toMessageList(messages));
            } else if (StringUtils.isNotBlank(message)) {
                reply = StringUtils.isNotBlank(system)
                    ? this.llmClient.chat(system, message)
                    : this.llmClient.chat(message);
            } else {
                sendError(response, 400, "Request body must include 'message' or 'messages'");
                return;
            }

            try (Writer out = response.getWriter()) {
                out.write(Json.createObjectBuilder().add("response", reply).build().toString());
            }
        } catch (IOException e) {
            sendError(response, 502, e.getMessage());
        }
    }

    private List<LLMMessage> toMessageList(final JsonArray messages)
    {
        final List<LLMMessage> result = new ArrayList<>(messages.size());
        for (final JsonObject msg : messages.getValuesAs(JsonObject.class)) {
            result.add(new LLMMessage(msg.getString("role"), msg.getString("content")));
        }
        return result;
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
