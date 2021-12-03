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
package io.uhndata.cards.proms.internal.importer;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/SubjectsHomepage" },
    selectors = { "import" })
public class ImportEndpoint extends SlingSafeMethodsServlet
{
    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        final Writer out = response.getWriter();

        //Ensure that this can only be run when logged in as admin
        final String remoteUser = request.getRemoteUser();
        if (!"admin".equals(remoteUser)) {
            //admin login required
            response.setStatus(403);
            out.write("Only admin can perform this operation.");
            return;
        }

        // Load configuration from environment variables
        try {
            int daysToParse = Integer.parseInt(System.getenv("PROM_DAYS_TO_QUERY"));
            String authURL = System.getenv("PROM_AUTH_URL");
            String endpointURL = System.getenv("PROM_TORCH_URL");
            final Runnable importJob = new ImportTask(this.resolverFactory, authURL, endpointURL, daysToParse);
            final Thread thread = new Thread(importJob);
            thread.start();
            out.write("Data import started");
        } catch (NumberFormatException e) {
            out.write("The PROM_DAYS_TO_QUERY variable should be set to an integer before running this endpoint.");
        }
    }
}
