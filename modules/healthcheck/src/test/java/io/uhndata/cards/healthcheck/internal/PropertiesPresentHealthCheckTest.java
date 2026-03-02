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

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.Result.Status;
import org.apache.felix.hc.api.ResultLog.Entry;
import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.apache.jackrabbit.value.LongValue;
import org.apache.jackrabbit.value.StringValue;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PropertiesPresentHealthCheck}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertiesPresentHealthCheckTest
{
    @Mock
    private ResourceResolverFactory rrf;

    @Mock
    private ResourceResolver rr;

    @Mock
    private Session session;

    @Mock
    private Node configurations;

    @Mock
    private Node config1;

    @Mock
    private Property path1;

    @Mock
    private Node config2;

    @Mock
    private Property path2;

    @Mock
    private Node config3;

    @Mock
    private Property path3;

    @Mock
    private Property value3;

    private Value expectedValue = new StringValue("a");

    @Mock
    private Property value;

    @InjectMocks
    private PropertiesPresentHealthCheck checker;

    @Before
    public void setup() throws LoginException, RepositoryException
    {
        when(this.rrf.getServiceResourceResolver(any())).thenReturn(this.rr);
        when(this.rr.adaptTo(Session.class)).thenReturn(this.session);
        when(this.session.nodeExists(PropertiesPresentHealthCheck.CONFIGURATION_PATH)).thenReturn(true);
        when(this.session.getNode(PropertiesPresentHealthCheck.CONFIGURATION_PATH)).thenReturn(this.configurations);

        when(this.config1.getProperty(PropertiesPresentHealthCheck.PATH_PROPERTY)).thenReturn(this.path1);
        when(this.path1.getString()).thenReturn("/node/property1");
        when(this.session.propertyExists("/node/property1")).thenReturn(false);

        when(this.config2.getProperty(PropertiesPresentHealthCheck.PATH_PROPERTY)).thenReturn(this.path2);
        when(this.path2.getString()).thenReturn("/node/property2");
        when(this.session.propertyExists("/node/property2")).thenReturn(true);
        when(this.config2.hasProperty(PropertiesPresentHealthCheck.VALUE_PROPERTY)).thenReturn(false);

        when(this.config3.getProperty(PropertiesPresentHealthCheck.PATH_PROPERTY)).thenReturn(this.path3);
        when(this.path3.getString()).thenReturn("/node/property3");
        when(this.session.propertyExists("/node/property3")).thenReturn(true);
        when(this.config3.hasProperty(PropertiesPresentHealthCheck.VALUE_PROPERTY)).thenReturn(true);
        when(this.config3.getProperty(PropertiesPresentHealthCheck.VALUE_PROPERTY)).thenReturn(this.value3);
        when(this.value3.getValue()).thenReturn(this.expectedValue);
        when(this.session.getProperty("/node/property3")).thenReturn(this.value);
    }

    @Test
    public void testNoConfiguration() throws Exception
    {
        when(this.session.nodeExists(PropertiesPresentHealthCheck.CONFIGURATION_PATH)).thenReturn(false);
        Result result = this.checker.execute();
        Assert.assertEquals(Status.WARN, result.getStatus());
    }

    @Test
    public void testMissingProperty() throws Exception
    {
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.CRITICAL, result.getStatus());
    }

    @Test
    public void testExistingProperty() throws Exception
    {
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config2)));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.OK, result.getStatus());
    }

    @Test
    public void testWrongValue() throws Exception
    {
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config3)));
        when(this.value.getValue()).thenReturn(new LongValue(2));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.CRITICAL, result.getStatus());
    }

    @Test
    public void testCorrectValue() throws Exception
    {
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config3)));
        when(this.value.getValue()).thenReturn(new StringValue("a"));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.OK, result.getStatus());
    }

    @Test
    public void testJcrError() throws Exception
    {
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        when(this.config1.getProperty(any())).thenThrow(new PathNotFoundException("missing property"));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, result.getStatus());
    }

    @Test
    public void testAggregation() throws Exception
    {
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1, this.config2)));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.CRITICAL, result.getStatus());
        Iterator<Entry> entries = result.iterator();
        Assert.assertEquals(Status.CRITICAL, entries.next().getStatus());
        Assert.assertEquals(Status.OK, entries.next().getStatus());
        Assert.assertEquals(Status.OK, entries.next().getStatus());
        Assert.assertFalse(entries.hasNext());
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
