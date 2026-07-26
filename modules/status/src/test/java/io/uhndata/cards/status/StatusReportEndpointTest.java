/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.status;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;

import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockRequestPathInfo;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletResponse;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.uhndata.cards.status.api.StatusReportManager;
import io.uhndata.cards.status.spi.StatusReport;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StatusReportEndpoint}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class StatusReportEndpointTest
{
    @Rule
    public SlingContext context = new SlingContext();

    @Mock
    private StatusReportManager manager;

    @InjectMocks
    private StatusReportEndpoint endpoint;

    @Test
    public void serializesReportsAsJson() throws Exception
    {
        when(this.manager.getReports(true, StatusReport.Status.INFO, Set.of()))
            .thenReturn(List.of(
                new StatusReport("Uptime", StatusReport.Status.INFO, "All day long"),
                new StatusReport("Problems", StatusReport.Status.WARNING, "Something is off")));

        final MockSlingJakartaHttpServletResponse response = get("/system/status", Map.of());

        Assert.assertEquals("application/json;charset=UTF-8", response.getContentType());
        final JsonArray results = Json.createReader(new StringReader(response.getOutputAsString())).readArray();
        Assert.assertEquals(2, results.size());
        Assert.assertEquals("Uptime", results.getJsonObject(0).getString("name"));
        Assert.assertEquals("WARNING", results.getJsonObject(1).getString("status"));
    }

    @Test
    public void serializesReportsAsText() throws Exception
    {
        when(this.manager.getReports(true, StatusReport.Status.INFO, Set.of()))
            .thenReturn(List.of(
                new StatusReport("Uptime", StatusReport.Status.INFO, "All day long"),
                new StatusReport("Silence", StatusReport.Status.INFO, null),
                new StatusReport("Problems", StatusReport.Status.WARNING, "Something is off")));

        final MockSlingJakartaHttpServletResponse response = get("/system/status.txt", Map.of());

        Assert.assertEquals("text/plain;charset=UTF-8", response.getContentType());
        // Texts are joined by blank lines, and missing texts don't print as "null"
        Assert.assertEquals("All day long\n\n\n\nSomething is off", response.getOutputAsString());
    }

    @Test
    public void honorsTargetStatusAndTags() throws Exception
    {
        when(this.manager.getReports(true, StatusReport.Status.WARNING, Set.of("problems")))
            .thenReturn(List.of());

        final MockSlingJakartaHttpServletResponse response =
            get("/system/status", Map.of("targetStatus", "WARNING", "tags", "problems"));

        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("[]", response.getOutputAsString());
        verify(this.manager).getReports(true, StatusReport.Status.WARNING, Set.of("problems"));
    }

    @Test
    public void rejectsInvalidTargetStatus() throws Exception
    {
        final MockSlingJakartaHttpServletResponse response =
            get("/system/status", Map.of("targetStatus", "LOUDER"));

        Assert.assertEquals(400, response.getStatus());
        Assert.assertTrue(response.getOutputAsString().contains("Invalid targetStatus: LOUDER"));
        verifyNoInteractions(this.manager);
    }

    @Test
    public void blankTargetStatusMeansInfo() throws Exception
    {
        when(this.manager.getReports(true, StatusReport.Status.INFO, Set.of())).thenReturn(List.of());

        final MockSlingJakartaHttpServletResponse response =
            get("/system/status", Map.of("targetStatus", " "));

        Assert.assertEquals(200, response.getStatus());
        verify(this.manager).getReports(true, StatusReport.Status.INFO, Set.of());
    }

    @Test
    public void onlyTheAdministratorIsPrivileged() throws Exception
    {
        when(this.manager.getReports(false, StatusReport.Status.INFO, Set.of())).thenReturn(List.of());

        final MockSlingJakartaHttpServletRequest request = request("/system/status", Map.of());
        request.setRemoteUser("admin");
        this.endpoint.doGet(request, new MockSlingJakartaHttpServletResponse());

        verify(this.manager).getReports(false, StatusReport.Status.INFO, Set.of());
    }

    private MockSlingJakartaHttpServletRequest request(final String pathInfo, final Map<String, Object> parameters)
    {
        final MockSlingJakartaHttpServletRequest request =
            new MockSlingJakartaHttpServletRequest(this.context.resourceResolver(), this.context.bundleContext());
        request.setPathInfo(pathInfo);
        ((MockRequestPathInfo) request.getRequestPathInfo())
            .setExtension(pathInfo.endsWith(".txt") ? "txt" : null);
        request.setParameterMap(parameters);
        return request;
    }

    private MockSlingJakartaHttpServletResponse get(final String pathInfo, final Map<String, Object> parameters)
        throws Exception
    {
        final MockSlingJakartaHttpServletResponse response = new MockSlingJakartaHttpServletResponse();
        this.endpoint.doGet(request(pathInfo, parameters), response);
        return response;
    }
}
