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

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import io.uhndata.cards.metrics.api.Metric;
import io.uhndata.cards.metrics.api.MetricsException;
import io.uhndata.cards.metrics.api.MetricsManager;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link MetricsEndpoint}.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class MetricsEndpointTest
{
    @Rule
    public SlingContext context = new SlingContext();

    private final MetricsManager manager = Mockito.mock(MetricsManager.class);

    private MetricsEndpoint endpoint;

    @Before
    public void setUp() throws Exception
    {
        this.endpoint = new MetricsEndpoint();
        final Field reference = MetricsEndpoint.class.getDeclaredField("metricsManager");
        reference.setAccessible(true);
        reference.set(this.endpoint, this.manager);
    }

    @Test
    public void listsOnlyPublicMetricsForVisitors() throws Exception
    {
        final List<Metric> metrics = List.of(
            metric("visible", Metric.AccessLevel.PUBLIC),
            metric("secret", Metric.AccessLevel.ADMIN));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        final MockSlingJakartaHttpServletResponse response = get(null);

        assertEquals("application/json;charset=UTF-8", response.getContentType());
        final JsonObject result = Json.createReader(new StringReader(response.getOutputAsString())).readObject();
        assertEquals(1, result.getJsonArray("metrics").size());
        assertEquals("visible", result.getJsonArray("metrics").getJsonObject(0).getString("name"));
    }

    @Test
    public void listsAllMetricsForTheAdministrator() throws Exception
    {
        final List<Metric> metrics = List.of(
            metric("visible", Metric.AccessLevel.PUBLIC),
            metric("secret", Metric.AccessLevel.ADMIN));
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        final MockSlingJakartaHttpServletResponse response = get("admin");

        final JsonObject result = Json.createReader(new StringReader(response.getOutputAsString())).readObject();
        assertEquals(2, result.getJsonArray("metrics").size());
        assertEquals("secret", result.getJsonArray("metrics").getJsonObject(1).getString("name"));
    }

    @Test
    public void listsNoMetricsWhenNoneAreDefined() throws Exception
    {
        final List<Metric> metrics = List.of();
        Mockito.when(this.manager.getMetrics()).thenReturn(metrics);

        final MockSlingJakartaHttpServletResponse response = get(null);

        assertEquals(200, response.getStatus());
        final JsonObject result = Json.createReader(new StringReader(response.getOutputAsString())).readObject();
        assertEquals(0, result.getJsonArray("metrics").size());
    }

    @Test
    public void reportsFailuresAsAnError() throws Exception
    {
        Mockito.when(this.manager.getMetrics()).thenThrow(new MetricsException("repository unavailable"));

        final MockSlingJakartaHttpServletResponse response = get(null);

        assertEquals(500, response.getStatus());
        final JsonObject result = Json.createReader(new StringReader(response.getOutputAsString())).readObject();
        assertEquals("error", result.getString("status"));
        assertEquals("repository unavailable", result.getString("error"));
    }

    private Metric metric(final String name, final Metric.AccessLevel accessLevel)
    {
        final Metric mocked = Mockito.mock(Metric.class);
        Mockito.when(mocked.getAccessLevel()).thenReturn(accessLevel);
        Mockito.when(mocked.toJson()).thenReturn(Json.createObjectBuilder().add("name", name).build());
        return mocked;
    }

    /**
     * A GET from a named caller, identified by the request's remote user, as the status report endpoint does.
     */
    private MockSlingJakartaHttpServletResponse get(final String user) throws Exception
    {
        final MockSlingJakartaHttpServletRequest request =
            new MockSlingJakartaHttpServletRequest(this.context.resourceResolver(), this.context.bundleContext());
        request.setRemoteUser(user);
        final MockSlingJakartaHttpServletResponse response = new MockSlingJakartaHttpServletResponse();
        this.endpoint.service(request, response);
        return response;
    }
}
