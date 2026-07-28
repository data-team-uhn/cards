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
package io.uhndata.cards.clarity.importer.internal;

import java.util.Collections;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.uhndata.cards.clarity.importer.TestUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Unit tests for {@link RecentVisitDiscardFilter}.
 *
 * @version $Id$
 */
public class RecentVisitDiscardFilterTest
{
    private static final String PATIENT = "/SubjectTypes/Patient";

    private ThreadResourceResolverProvider rrp;

    private ResourceResolver resolver;

    @Before
    public void setUp()
    {
        this.rrp = Mockito.mock(ThreadResourceResolverProvider.class);
        this.resolver = Mockito.mock(ResourceResolver.class);
        Mockito.when(this.rrp.getThreadResourceResolver()).thenReturn(this.resolver);
    }

    private RecentVisitDiscardFilter filter(final int frequency, final String... clinics)
    {
        final RecentVisitDiscardFilter.Config config = Mockito.mock(RecentVisitDiscardFilter.Config.class);
        Mockito.when(config.enable()).thenReturn(true);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.minimum_visit_frequency()).thenReturn(frequency);
        Mockito.when(config.clinics()).thenReturn(clinics);
        final RecentVisitDiscardFilter filter = new RecentVisitDiscardFilter(config);
        TestUtils.setField(filter, "rrp", this.rrp);
        return filter;
    }

    /** With no minimum frequency configured the filter has nothing to enforce and stays out of the way. */
    @Test
    public void aFrequencyOfZeroLetsEverythingThrough()
    {
        final Map<String, String> input = TestUtils.row(PATIENT, "P1");
        Assert.assertSame(input, filter(0).processEntry(input));
        Mockito.verifyNoInteractions(this.resolver);
    }

    @Test
    public void aNegativeFrequencyLetsEverythingThrough()
    {
        final Map<String, String> input = TestUtils.row(PATIENT, "P1");
        Assert.assertSame(input, filter(-1).processEntry(input));
    }

    @Test
    public void aRowWithoutASubjectIdentifierIsDiscarded()
    {
        Assert.assertNull(filter(30).processEntry(TestUtils.row()));
        Assert.assertNull(filter(30).processEntry(TestUtils.row(PATIENT, "")));
    }

    @Test
    public void aRowForAnUnknownPatientIsKept()
    {
        Mockito.when(this.resolver.findResources(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Collections.<Resource>emptyList().iterator());
        final Map<String, String> input = TestUtils.row(PATIENT, "P1");
        Assert.assertSame(input, filter(30).processEntry(input));
    }

    @Test
    public void aPatientWithNoVisitsIsKept() throws RepositoryException
    {
        final Resource subjectResource = Mockito.mock(Resource.class);
        final Node subjectNode = Mockito.mock(Node.class);
        final NodeIterator visits = Mockito.mock(NodeIterator.class);
        Mockito.when(this.resolver.findResources(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Collections.singletonList(subjectResource).iterator());
        Mockito.when(subjectResource.adaptTo(Node.class)).thenReturn(subjectNode);
        Mockito.when(subjectNode.getNodes()).thenReturn(visits);
        Mockito.when(visits.hasNext()).thenReturn(false);

        final Map<String, String> input = TestUtils.row(PATIENT, "P1");
        Assert.assertSame(input, filter(30).processEntry(input));
    }

    /** If the history cannot be read, the safe answer is to not send another survey. */
    @Test
    public void aRepositoryFailureDiscardsTheRow() throws RepositoryException
    {
        final Resource subjectResource = Mockito.mock(Resource.class);
        final Node subjectNode = Mockito.mock(Node.class);
        Mockito.when(this.resolver.findResources(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Collections.singletonList(subjectResource).iterator());
        Mockito.when(subjectResource.adaptTo(Node.class)).thenReturn(subjectNode);
        Mockito.when(subjectNode.getNodes()).thenThrow(new RepositoryException("boom"));

        Assert.assertNull(filter(30).processEntry(TestUtils.row(PATIENT, "P1")));
    }

    @Test
    public void theFilterRunsEarly()
    {
        Assert.assertEquals(10, filter(30).getPriority());
    }

    @Test
    public void aDisabledFilterSupportsNoImportType()
    {
        final RecentVisitDiscardFilter.Config config = Mockito.mock(RecentVisitDiscardFilter.Config.class);
        Mockito.when(config.enable()).thenReturn(false);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.clinics()).thenReturn(new String[0]);
        Assert.assertFalse(new RecentVisitDiscardFilter(config).supportsImportType("visits"));
    }

    @Test
    public void nullClinicsAreAccepted()
    {
        final RecentVisitDiscardFilter.Config config = Mockito.mock(RecentVisitDiscardFilter.Config.class);
        Mockito.when(config.enable()).thenReturn(true);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        Mockito.when(config.minimum_visit_frequency()).thenReturn(0);
        Mockito.when(config.clinics()).thenReturn(null);
        final RecentVisitDiscardFilter filter = new RecentVisitDiscardFilter(config);
        final Map<String, String> input = TestUtils.row(PATIENT, "P1");
        Assert.assertSame(input, filter.processEntry(input));
    }
}
