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
package io.uhndata.cards.export;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.export.spi.DataFormatter;
import io.uhndata.cards.export.spi.DataRetriever;
import io.uhndata.cards.export.spi.DataStore;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/ResourceHomepage" },
    methods = { "GET" },
    selectors = { "export" })
public class TriggeredExportEndpoint extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -1615592669184694092L;

    private static final String ADMIN = "admin";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private volatile List<ExportConfig> configs;

    @Reference
    private volatile List<DataRetriever> retrievers;

    @Reference
    private volatile List<DataFormatter> formatters;

    @Reference
    private volatile List<DataStore> stores;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        // Ensure that this can only be run when logged in as admin
        final String remoteUser = request.getRemoteUser();
        if (remoteUser == null || !ADMIN.equals(remoteUser.toLowerCase(Locale.ROOT))) {
            writeError(403, "Only admin can perform this operation.", response);
            return;
        }

        final String configName = request.getParameter("config");
        ExportConfigDefinition config;
        if (StringUtils.isBlank(configName)) {
            if (this.configs.size() != 1) {
                writeError(400, this.configs.size() > 1 ? "Configuration name must be specified"
                    : "No export is configured", response);
                return;
            }
            config = this.configs.get(0).getConfig();
        } else {
            config = this.configs.stream()
                .filter(c -> configName.equals(c.getConfig().name()))
                .map(ExportConfig::getConfig)
                .findFirst().orElse(null);
            if (config == null) {
                writeError(400, "Unknown export configuration", response);
                return;
            }
        }

        final LocalDate dateLowerBound = this.strToDate(request.getParameter("dateLowerBound"));
        final LocalDate dateUpperBound = this.strToDate(request.getParameter("dateUpperBound"));
        final String exportRunMode = (dateLowerBound != null) ? "manual" : "today";
        final DataPipeline pipeline = buildPipeline(config, response);
        if (pipeline == null) {
            return;
        }

        final Runnable exportJob = new ExportTask(this.resolverFactory, this.rrp, config, pipeline, exportRunMode,
            dateLowerBound, dateUpperBound);
        final Thread thread = new Thread(exportJob);
        thread.start();
        writeSuccess("S3 export started", response);
    }

    private DataPipeline buildPipeline(final ExportConfigDefinition config, final SlingHttpServletResponse response)
        throws IOException
    {
        final DataRetriever retriever =
            this.retrievers.stream().filter(r -> StringUtils.equals(config.retriever(), r.getName())).findFirst()
                .orElse(null);
        if (retriever == null) {
            writeError(400, "Invalid export configuration, unknown data retriever specified", response);
            return null;
        }
        final DataFormatter formatter =
            this.formatters.stream().filter(f -> StringUtils.equals(config.formatter(), f.getName())).findFirst()
                .orElse(null);
        if (formatter == null) {
            writeError(400, "Invalid export configuration, unknown data formatter specified", response);
            return null;
        }
        final DataStore store =
            this.stores.stream().filter(s -> StringUtils.equals(config.storage(), s.getName())).findFirst()
                .orElse(null);
        if (store == null) {
            writeError(400, "Invalid export configuration, unknown data store specified", response);
            return null;
        }
        return new DataPipeline(retriever, formatter, store);
    }

    private LocalDate strToDate(final String date)
    {
        if (date == null) {
            return null;
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void writeError(final int status, final String message, final SlingHttpServletResponse response)
        throws IOException
    {
        final JsonObjectBuilder json = Json.createObjectBuilder();
        json.add("status", "error");
        json.add("error", message);
        writeResponse(status, json.build().toString(), response);
    }

    private void writeSuccess(final String message, final SlingHttpServletResponse response)
        throws IOException
    {
        final JsonObjectBuilder json = Json.createObjectBuilder();
        json.add("status", "success");
        json.add("message", message);
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
