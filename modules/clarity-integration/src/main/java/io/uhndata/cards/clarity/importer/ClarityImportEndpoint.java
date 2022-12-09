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
package io.uhndata.cards.clarity.importer;

import java.io.IOException;
import java.util.Locale;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.utils.ThreadResourceResolverProvider;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/SubjectsHomepage" },
    extensions = { "importClarity" },
    methods = { "GET" })
public class ClarityImportEndpoint extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -2727980234215527292L;

    private static final String MAPPING_CONFIG = "/apps/cards/clarityImport";

    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        // Ensure that this can only be run when logged in as admin
        final String remoteUser = request.getRemoteUser();
        if (remoteUser == null || !"admin".equals(remoteUser.toLowerCase(Locale.ROOT))) {
            // admin login required
            writeError(403, "Only admin can perform this operation.", response);
            return;
        }

        // Load configuration from environment variables
        final Runnable importJob =
            new ClarityImportTask(this.resolverFactory,
                this.rrp.getThreadResourceResolver().getResource(MAPPING_CONFIG));
        final Thread thread = new Thread(importJob);
        thread.start();
        writeSuccess(response);
    }

    private void writeError(final int status, final String message, final SlingHttpServletResponse response)
        throws IOException
    {
        final JsonObjectBuilder json = Json.createObjectBuilder();
        json.add("status", "error");
        json.add("error", message);
        writeResponse(status, json.build().toString(), response);
    }

    private void writeSuccess(final SlingHttpServletResponse response)
        throws IOException
    {
        final JsonObjectBuilder json = Json.createObjectBuilder();
        json.add("status", "success");
        json.add("message", "Data import started");
        writeResponse(200, json.build().toString(), response);
    }

    private void writeResponse(final int status, final String body, final SlingHttpServletResponse response)
        throws IOException
    {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
    }
}
