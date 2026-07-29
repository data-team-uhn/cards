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
package io.uhndata.cards.utils.internal;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PreventVersionOverrideServletFilter}.
 *
 * @version $Id$
 */
public class PreventVersionOverrideServletFilterTest
{
    private static final String BASE_VERSION_PARAMETER = ":baseVersion";

    private static final String CURRENT_VERSION_PATH = "/jcr:system/jcr:versionStorage/f1/1.0";

    private final PreventVersionOverrideServletFilter filter = new PreventVersionOverrideServletFilter();

    private final SlingJakartaHttpServletRequest request = mock(SlingJakartaHttpServletRequest.class);

    private final ServletResponse response = mock(ServletResponse.class);

    private final FilterChain chain = mock(FilterChain.class);

    @Test
    public void initAndDestroyDoNothing() throws ServletException
    {
        this.filter.init(null);
        this.filter.destroy();
    }

    @Test
    public void doFilterContinuesForNonSlingRequest() throws IOException, ServletException
    {
        ServletRequest plainRequest = mock(ServletRequest.class);
        when(plainRequest.getParameter(BASE_VERSION_PARAMETER)).thenReturn(CURRENT_VERSION_PATH);
        this.filter.doFilter(plainRequest, this.response, this.chain);
        verify(this.chain).doFilter(plainRequest, this.response);
    }

    @Test
    public void doFilterContinuesWithoutBaseVersionParameter() throws IOException, ServletException
    {
        when(this.request.getParameter(BASE_VERSION_PARAMETER)).thenReturn(null);
        this.filter.doFilter(this.request, this.response, this.chain);
        verify(this.chain).doFilter(this.request, this.response);
    }

    @Test
    public void doFilterContinuesForNonVersionableResource() throws IOException, RepositoryException, ServletException
    {
        mockResource(null);
        this.filter.doFilter(this.request, this.response, this.chain);
        verify(this.chain).doFilter(this.request, this.response);
    }

    @Test
    public void doFilterContinuesWhenBaseVersionMatches() throws IOException, RepositoryException, ServletException
    {
        Node node = mockVersionedNode(CURRENT_VERSION_PATH);
        mockResource(node);
        this.filter.doFilter(this.request, this.response, this.chain);
        verify(this.chain).doFilter(this.request, this.response);
    }

    @Test
    public void doFilterRejectsOutdatedBaseVersion() throws IOException, RepositoryException, ServletException
    {
        Node node = mockVersionedNode("/jcr:system/jcr:versionStorage/f1/2.0");
        mockResource(node);
        assertThrows(ServletException.class, () -> this.filter.doFilter(this.request, this.response, this.chain));
        verify(this.request).setAttribute("jakarta.servlet.error.status_code", HttpServletResponse.SC_CONFLICT);
        verify(this.chain, never()).doFilter(this.request, this.response);
    }

    @Test
    public void doFilterContinuesOnRepositoryException() throws IOException, RepositoryException, ServletException
    {
        Node node = mock(Node.class);
        when(node.getProperty("jcr:baseVersion")).thenThrow(new RepositoryException());
        mockResource(node);
        this.filter.doFilter(this.request, this.response, this.chain);
        verify(this.chain).doFilter(this.request, this.response);
    }

    private void mockResource(final Node node)
    {
        when(this.request.getParameter(BASE_VERSION_PARAMETER)).thenReturn(CURRENT_VERSION_PATH);
        Resource resource = mock(Resource.class);
        when(this.request.getResource()).thenReturn(resource);
        when(resource.adaptTo(Node.class)).thenReturn(node);
    }

    private Node mockVersionedNode(final String baseVersionPath) throws RepositoryException
    {
        Node node = mock(Node.class);
        Property baseVersion = mock(Property.class);
        Node versionNode = mock(Node.class);
        when(node.getProperty("jcr:baseVersion")).thenReturn(baseVersion);
        when(baseVersion.getNode()).thenReturn(versionNode);
        when(versionNode.getPath()).thenReturn(baseVersionPath);
        return node;
    }
}
