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
package io.uhndata.cards.status;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.status.api.StatusReportManager;
import io.uhndata.cards.status.spi.StatusReport;

@Component(service = { Servlet.class }, property = { "sling.auth.requirements=-/system/status" })
@SlingServletPaths(value = "/system/status")
public class StatusReportEndpoint extends SlingJakartaSafeMethodsServlet
{
    private static final long serialVersionUID = -120234215527291L;

    @Reference
    private StatusReportManager manager;

    @Override
    public void doGet(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        response.setCharacterEncoding("UTF-8");
        final boolean unprivileged = !("admin".equals(request.getRemoteUser()));
        final StatusReport.Status targetStatus;
        try {
            targetStatus =
                StatusReport.Status.valueOf(StringUtils.defaultIfBlank(request.getParameter("targetStatus"), "INFO"));
        } catch (final IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().print(Json.createObjectBuilder()
                .add("status", "error")
                .add("error", "Invalid targetStatus: " + request.getParameter("targetStatus"))
                .build().toString());
            return;
        }
        final Set<String> tags = request.getParameterValues("tags") == null ? Collections.emptySet()
            : Set.of(request.getParameterValues("tags"));
        final boolean txtOutput = request.getPathInfo().endsWith(".txt");
        if (txtOutput) {
            final String result = StringUtils.join(
                this.manager.getReports(unprivileged, targetStatus, tags).stream()
                    .map(StatusReport::getText)
                    .map(StringUtils::defaultString)
                    .toList(),
                "\n\n");
            response.setContentType("text/plain");
            response.getWriter().print(result);
        } else {
            final JsonArrayBuilder results = Json.createArrayBuilder();
            this.manager.getReports(unprivileged, targetStatus, tags).stream()
                .map(StatusReport::toJson)
                .forEach(r -> results.add(r));
            response.setContentType("application/json");
            response.getWriter().print(results.build().toString());
        }
    }
}
