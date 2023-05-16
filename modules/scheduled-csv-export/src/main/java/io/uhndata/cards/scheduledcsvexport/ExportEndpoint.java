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

package io.uhndata.cards.scheduledcsvexport;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;

import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/SubjectsHomepage" },
    methods = { "GET" },
    selectors = { "csvExport" })
public class ExportEndpoint extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -2619333182443055173L;

    private static final String ADMIN = "admin";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private volatile List<ExportConfig> configs;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        final Writer out = response.getWriter();

        // Ensure that this can only be run when logged in as admin
        final String remoteUser = request.getRemoteUser();
        if (remoteUser == null || !remoteUser.toLowerCase(Locale.ROOT).equals(ADMIN)) {
            // admin login required
            response.setStatus(403);
            out.write("Only admin can perform this operation.");
            return;
        }
        final String configName = request.getParameter("config");
        if (StringUtils.isBlank(configName)) {
            response.setStatus(400);
            out.write("Configuration name must be specified");
            return;
        }
        final ExportConfigDefinition config = this.configs.stream()
            .filter(c -> configName.equals(c.getConfig().name()))
            .map(ExportConfig::getConfig)
            .findFirst().orElse(null);
        if (config == null) {
            response.setStatus(404);
            out.write("Unknown configuration.");
            return;
        }

        final Runnable exportJob = new ExportTask(this.resolverFactory, this.rrp, config);
        final Thread thread = new Thread(exportJob);
        thread.start();
        response.setStatus(200);
        out.write("CSV export started");
    }
}
