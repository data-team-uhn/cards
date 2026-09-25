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

package io.uhndata.cards.metrics.internal;

import java.io.IOException;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.metrics.api.Metric;
import io.uhndata.cards.metrics.api.MetricsException;
import io.uhndata.cards.metrics.api.MetricsManager;

/**
 * Serves all the metrics at {@code /Metrics.json} as a JSON object with a {@code metrics} array, each entry
 * serialized as by {@link Metric#toJson}. The endpoint is accessible without authentication so that dashboards and
 * monitoring tools can poll it, but metrics restricted to administrators are only listed when an administrator is
 * asking. Reading the metrics doesn't change them.
 *
 * @version $Id$
 * @since 0.9.42
 */
@Component(service = { Servlet.class }, property = { "sling.auth.requirements=-/Metrics" })
@SlingServletResourceTypes(resourceTypes = { "cards/MetricsHomepage" }, methods = { "GET" }, extensions = { "json" })
public class MetricsEndpoint extends SlingJakartaSafeMethodsServlet
{
    private static final long serialVersionUID = -2434169854854968565L;

    @Reference
    private transient MetricsManager metricsManager;

    @Override
    protected void doGet(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        // The same rule as the status report endpoint
        final boolean unprivileged = !"admin".equals(request.getRemoteUser());
        try {
            final JsonArrayBuilder metrics = Json.createArrayBuilder();
            this.metricsManager.getMetrics().stream()
                .filter(metric -> !unprivileged || metric.getAccessLevel() == Metric.AccessLevel.PUBLIC)
                .map(Metric::toJson)
                .forEach(metrics::add);
            response.getWriter().print(Json.createObjectBuilder().add("metrics", metrics).build().toString());
        } catch (final MetricsException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(Json.createObjectBuilder()
                .add("status", "error")
                .add("error", e.getMessage())
                .build().toString());
        }
    }
}
