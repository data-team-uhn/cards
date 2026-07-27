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

package io.uhndata.cards.healthcheck.internal;

import java.util.Iterator;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.Result.Status;
import org.apache.felix.hc.api.ResultLog.Entry;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ServicesPresentHealthCheck}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class ServicesPresentHealthCheckTest
{
    @Mock
    private ResourceResolverFactory rrf;

    @Mock
    private ResourceResolver rr;

    @Mock
    private BundleContext bc;

    @Mock
    private Resource configurations;

    @Mock
    private Resource config1;

    @Mock
    private ValueMap props1;

    @Mock
    private Resource config2;

    @Mock
    private ValueMap props2;

    @Mock
    private Resource config3;

    @Mock
    private ValueMap props3;

    @Mock
    private ServiceReference<String> service;

    @InjectMocks
    private ServicesPresentHealthCheck checker;

    @Before
    public void setup() throws LoginException, RepositoryException, PersistenceException, InvalidSyntaxException
    {
        this.checker.activate(this.bc);

        when(this.rrf.getServiceResourceResolver(any())).thenReturn(this.rr);
        when(this.rr.getResource(ServicesPresentHealthCheck.CONFIGURATION_PATH)).thenReturn(this.configurations);
        when(this.configurations.isResourceType(Resource.RESOURCE_TYPE_NON_EXISTING)).thenReturn(false);

        when(this.config1.getValueMap()).thenReturn(this.props1);
        when(this.props1.get(ServicesPresentHealthCheck.SERVICE_PROPERTY, "")).thenReturn("test.Class1");
        when(this.props1.get(ServicesPresentHealthCheck.FILTER_PROPERTY, "")).thenReturn("");
        when(this.bc.getAllServiceReferences("test.Class1", null)).thenReturn(null);

        when(this.config2.getValueMap()).thenReturn(this.props2);
        when(this.props2.get(ServicesPresentHealthCheck.SERVICE_PROPERTY, "")).thenReturn("test.Class2");
        when(this.props2.get(ServicesPresentHealthCheck.FILTER_PROPERTY, "")).thenReturn("(service.pid=PID2)");
        when(this.bc.getAllServiceReferences("test.Class2", "(service.pid=PID2)"))
            .thenReturn(new ServiceReference<?>[] { this.service });

        when(this.config3.getValueMap()).thenReturn(this.props3);
        when(this.props3.get(ServicesPresentHealthCheck.SERVICE_PROPERTY, "")).thenReturn("test.Class3");
        when(this.props3.get(ServicesPresentHealthCheck.FILTER_PROPERTY, "")).thenReturn("invalid");
        when(this.bc.getAllServiceReferences("test.Class3", "invalid"))
            .thenThrow(new InvalidSyntaxException("wrong syntax", "invalid"));
    }

    @Test
    public void testNoConfiguration() throws Exception
    {
        when(this.configurations.isResourceType(Resource.RESOURCE_TYPE_NON_EXISTING)).thenReturn(true);
        Result result = this.checker.execute();
        Assert.assertEquals(Status.WARN, result.getStatus());

        when(this.rr.getResource(ServicesPresentHealthCheck.CONFIGURATION_PATH)).thenReturn(null);
        result = this.checker.execute();
        Assert.assertEquals(Status.WARN, result.getStatus());
    }

    @Test
    public void testMissingService() throws Exception
    {
        when(this.configurations.getChildren()).thenReturn(List.of(this.config1));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.CRITICAL, result.getStatus());

        when(this.bc.getAllServiceReferences("test.Class1", null)).thenReturn(new ServiceReference<?>[] {});
        result = this.checker.execute();
        Assert.assertEquals(Status.CRITICAL, result.getStatus());
        verify(this.bc, times(2)).getAllServiceReferences("test.Class1", null);
    }

    @Test
    public void testValidService() throws Exception
    {
        when(this.configurations.getChildren()).thenReturn(List.of(this.config2));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.OK, result.getStatus());
    }

    @Test
    public void testInvalidSyntax() throws Exception
    {
        when(this.configurations.getChildren()).thenReturn(List.of(this.config3));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, result.getStatus());
    }

    @Test
    public void testAggregation() throws Exception
    {
        when(this.configurations.getChildren()).thenReturn(List.of(this.config1, this.config2, this.config3));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, result.getStatus());
        Iterator<Entry> entries = result.iterator();
        Assert.assertEquals(Status.CRITICAL, entries.next().getStatus());
        Assert.assertEquals(Status.OK, entries.next().getStatus());
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, entries.next().getStatus());
        Assert.assertEquals(Status.OK, entries.next().getStatus());
        Assert.assertFalse(entries.hasNext());
    }

    @Test
    public void testBlankServiceClass() throws Exception
    {
        Resource config4 = org.mockito.Mockito.mock(Resource.class);
        ValueMap props4 = org.mockito.Mockito.mock(ValueMap.class);
        when(config4.getValueMap()).thenReturn(props4);
        when(props4.get(ServicesPresentHealthCheck.SERVICE_PROPERTY, "")).thenReturn(" ");
        when(this.configurations.getChildren()).thenReturn(List.of(config4));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, result.getStatus());
        // The service registry must not be queried with a blank class name
        verify(this.bc, times(0)).getAllServiceReferences(any(), any());
    }

    @Test
    public void testInvalidLogin() throws Exception
    {
        when(this.rrf.getServiceResourceResolver(any())).thenThrow(new LoginException("wrong login"));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, result.getStatus());
        Assert.assertNotNull(result.iterator().next().getMessage());
    }
}
