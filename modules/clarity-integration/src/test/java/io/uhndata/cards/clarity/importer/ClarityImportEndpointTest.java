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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for the request validation of {@link ClarityImportEndpoint}. The paths that go on to start an import are
 * not covered here, since they need a reachable Clarity database.
 *
 * @version $Id$
 */
public class ClarityImportEndpointTest
{
    private ClarityImportEndpoint endpoint;

    private SlingJakartaHttpServletRequest request;

    private SlingJakartaHttpServletResponse response;

    private StringWriter written;

    @Before
    public void setUp() throws IOException
    {
        this.endpoint = new ClarityImportEndpoint();
        this.request = Mockito.mock(SlingJakartaHttpServletRequest.class);
        this.response = Mockito.mock(SlingJakartaHttpServletResponse.class);
        this.written = new StringWriter();
        Mockito.when(this.response.getWriter()).thenReturn(new PrintWriter(this.written));
    }

    private static ClarityImportConfig namedConfig(final String name)
    {
        final ClarityImportConfigDefinition definition = Mockito.mock(ClarityImportConfigDefinition.class);
        Mockito.when(definition.name()).thenReturn(name);
        final ClarityImportConfig config = Mockito.mock(ClarityImportConfig.class);
        Mockito.when(config.getConfig()).thenReturn(definition);
        return config;
    }

    private void givenConfigs(final List<ClarityImportConfig> configs)
    {
        TestUtils.setField(this.endpoint, "configs", configs);
    }

    @Test
    public void anAnonymousRequestIsRejected() throws IOException
    {
        Mockito.when(this.request.getRemoteUser()).thenReturn(null);
        this.endpoint.doGet(this.request, this.response);
        Mockito.verify(this.response).setStatus(403);
        Assert.assertTrue(this.written.toString().contains("Only admin can perform this operation."));
    }

    @Test
    public void aNonAdminRequestIsRejected() throws IOException
    {
        Mockito.when(this.request.getRemoteUser()).thenReturn("clinician");
        this.endpoint.doGet(this.request, this.response);
        Mockito.verify(this.response).setStatus(403);
    }

    @Test
    public void theAdminCheckIgnoresCase() throws IOException
    {
        Mockito.when(this.request.getRemoteUser()).thenReturn("ADMIN");
        givenConfigs(Collections.emptyList());
        this.endpoint.doGet(this.request, this.response);
        // Got past the permission check and failed on the configuration instead
        Mockito.verify(this.response).setStatus(400);
        Assert.assertTrue(this.written.toString().contains("No clarity import is configured"));
    }

    @Test
    public void anImportWithNoConfigurationAtAllIsRejected() throws IOException
    {
        Mockito.when(this.request.getRemoteUser()).thenReturn("admin");
        givenConfigs(Collections.emptyList());
        this.endpoint.doGet(this.request, this.response);
        Mockito.verify(this.response).setStatus(400);
        Assert.assertTrue(this.written.toString().contains("No clarity import is configured"));
    }

    @Test
    public void severalConfigurationsNeedOneToBeNamed() throws IOException
    {
        Mockito.when(this.request.getRemoteUser()).thenReturn("admin");
        givenConfigs(Arrays.asList(namedConfig("visits"), namedConfig("deaths")));
        this.endpoint.doGet(this.request, this.response);
        Mockito.verify(this.response).setStatus(400);
        Assert.assertTrue(this.written.toString().contains("Configuration name must be specified"));
    }

    @Test
    public void anUnknownConfigurationNameIsRejected() throws IOException
    {
        Mockito.when(this.request.getRemoteUser()).thenReturn("admin");
        Mockito.when(this.request.getParameter("config")).thenReturn("nosuchimport");
        givenConfigs(Collections.singletonList(namedConfig("visits")));
        this.endpoint.doGet(this.request, this.response);
        Assert.assertTrue(this.written.toString().contains("Unknown clarity import configuration"));
    }
}
