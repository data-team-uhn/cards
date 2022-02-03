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
import java.util.Locale;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/SubjectsHomepage" },
    extensions = { "importTorch" },
    methods = { "GET" })
public class ImportEndpoint extends SlingSafeMethodsServlet
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportEndpoint.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ImportConfig config;

    /** Number of days to look ahead when querying for appointments. */
    private int daysToQuery;

    /** Torch FIHR GraphQL endpoint. */
    private String endpointURL;

    /** Vault JWT refresh endpoint. */
    private String authURL;

    /** Vault JWT token. */
    private String vaultToken;

    /** Name of the clinic to query for. */
    private String clinicName = "";

    /** Pipe-delimited list of providers to filter our query to. */
    private String providerIDs = "";

    @Activate
    protected void activate(ComponentContext componentContext) throws Exception
    {
        this.authURL = this.config.getConfig().auth_url();
        this.endpointURL = this.config.getConfig().endpoint_url();
        this.daysToQuery = this.config.getConfig().days_to_query();
        this.vaultToken = this.config.getConfig().vault_token();
        this.clinicName = this.config.getConfig().clinic_name();
        this.providerIDs = this.config.getConfig().provider_name();
    }

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        final Writer out = response.getWriter();

        // Ensure that this can only be run when logged in as admin
        final String remoteUser = request.getRemoteUser();
        if (!"admin".equals(remoteUser.toLowerCase(Locale.ROOT))) {
            // admin login required
            response.setStatus(403);
            out.write("Only admin can perform this operation.");
            return;
        }

        // Load configuration from environment variables
        final Runnable importJob = new ImportTask(this.resolverFactory, this.authURL, this.endpointURL,
            this.daysToQuery, this.vaultToken, this.clinicName, this.providerIDs);
        final Thread thread = new Thread(importJob);
        thread.start();
        out.write("Data import started");
    }
}
