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

import javax.jcr.RepositoryException;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.Result.Status;
import org.apache.felix.hc.api.ResultLog.Entry;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;

import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ServicesPresentHealthCheck}.
 *
 * @version $Id$
 */
public class DuplicateJarsHealthCheckTest
{
    @Mock
    private BundleContext bc;

    @Mock
    private Bundle b1;

    @Mock
    private Bundle b1a;

    @Mock
    private Bundle b2;

    @Mock
    private Bundle b2a;

    @Mock
    private Bundle b2b;

    @Mock
    private Bundle b3;

    @Mock
    private Bundle b4;

    @InjectMocks
    private DuplicateJarsHealthCheck checker;

    @Before
    public void setup() throws LoginException, RepositoryException, PersistenceException, InvalidSyntaxException
    {
        MockitoAnnotations.initMocks(this);
        this.checker.activate(this.bc);

        when(this.b1.getSymbolicName()).thenReturn("example.bundle1");
        when(this.b1a.getSymbolicName()).thenReturn("example.bundle1");
        when(this.b2.getSymbolicName()).thenReturn("example.bundle2");
        when(this.b2a.getSymbolicName()).thenReturn("example.bundle2");
        when(this.b2b.getSymbolicName()).thenReturn("example.bundle2");
        when(this.b3.getSymbolicName()).thenReturn("example.bundle3");
        when(this.b4.getSymbolicName()).thenReturn("example.bundle4");
    }

    @Test
    public void testNoBundles() throws Exception
    {
        when(this.bc.getBundles()).thenReturn(null);
        Result result = this.checker.execute();
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, result.getStatus());

        when(this.bc.getBundles()).thenReturn(new Bundle[0]);
        result = this.checker.execute();
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, result.getStatus());
    }

    @Test
    public void testNoDuplicates() throws Exception
    {
        when(this.bc.getBundles()).thenReturn(new Bundle[] { this.b1, this.b2, this.b3, this.b4 });
        Result result = this.checker.execute();
        Assert.assertEquals(Status.OK, result.getStatus());
        Assert.assertEquals("Checked 4 bundles", result.iterator().next().getMessage());
    }

    @Test
    public void testDuplicates() throws Exception
    {
        when(this.bc.getBundles()).thenReturn(new Bundle[] { this.b1, this.b1a });
        Result result = this.checker.execute();
        Assert.assertEquals(Status.WARN, result.getStatus());
        Iterator<Entry> log = result.iterator();
        Assert.assertEquals("Possible duplicate jar: example.bundle1", log.next().getMessage());
        Assert.assertEquals("Checked 2 bundles", log.next().getMessage());
    }

    @Test
    public void testTriplicates() throws Exception
    {
        when(this.bc.getBundles()).thenReturn(new Bundle[] { this.b2, this.b2a, this.b2b });
        Result result = this.checker.execute();
        Assert.assertEquals(Status.WARN, result.getStatus());
        Iterator<Entry> log = result.iterator();
        Assert.assertEquals("Possible duplicate jar: example.bundle2", log.next().getMessage());
        Assert.assertEquals("Possible duplicate jar: example.bundle2", log.next().getMessage());
        Assert.assertEquals("Checked 3 bundles", log.next().getMessage());
    }

    @Test
    public void testAggregation() throws Exception
    {
        when(this.bc.getBundles())
            .thenReturn(new Bundle[] { this.b1, this.b1a, this.b2, this.b2a, this.b2b, this.b3, this.b4 });
        Result result = this.checker.execute();
        Assert.assertEquals(Status.WARN, result.getStatus());
        Iterator<Entry> log = result.iterator();
        Assert.assertEquals(Status.WARN, log.next().getStatus());
        Assert.assertEquals(Status.WARN, log.next().getStatus());
        Assert.assertEquals("Possible duplicate jar: example.bundle2", log.next().getMessage());
        Assert.assertEquals(Status.OK, log.next().getStatus());
        Assert.assertFalse(log.hasNext());
    }
}
