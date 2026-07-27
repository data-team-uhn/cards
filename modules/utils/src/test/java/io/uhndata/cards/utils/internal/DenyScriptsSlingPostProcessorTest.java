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

import java.util.List;

import org.apache.sling.api.request.builder.impl.SlingHttpServletRequestImpl;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DenyScriptsSlingPostProcessor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class DenyScriptsSlingPostProcessorTest
{

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private DenyScriptsSlingPostProcessor denyScriptsSlingPostProcessor;

    @Test
    public void processAllowsResourceWithNullResourceMetadata()
    {
        SlingHttpServletRequestImpl request = mock(SlingHttpServletRequestImpl.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        List<Modification> changes = List.of(new Modification(ModificationType.COPY, "/Forms/f1", "/Forms/f2"));
        Resource resource = mock(Resource.class);

        when(request.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.getResource("/Forms/f1")).thenReturn(resource);
        when(resource.getResourceMetadata()).thenReturn(null);

        Assertions.assertThatCode(() -> this.denyScriptsSlingPostProcessor.process(request, changes))
                .doesNotThrowAnyException();
    }

    @Test
    public void processAllowsResourceWithNullContentType()
    {
        SlingHttpServletRequestImpl request = mock(SlingHttpServletRequestImpl.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        List<Modification> changes = List.of(new Modification(ModificationType.COPY, "/Forms/f1", "/Forms/f2"));

        when(request.getResourceResolver()).thenReturn(resourceResolver);
        mockRecourseContentType(resourceResolver, "/Forms/f1", null);

        Assertions.assertThatCode(() -> this.denyScriptsSlingPostProcessor.process(request, changes))
                .doesNotThrowAnyException();
    }

    @Test
    public void processScriptResourceThrowsException()
    {
        SlingHttpServletRequestImpl request = mock(SlingHttpServletRequestImpl.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        List<Modification> changes = List.of(new Modification(ModificationType.COPY, "/Forms/f1", "/Forms/f2"));

        when(request.getResourceResolver()).thenReturn(resourceResolver);
        mockRecourseContentType(resourceResolver, "/Forms/f1", "text/script;charset=UTF-8");

        Assert.assertThrows("Script files are not allowed", Exception.class,
                () -> this.denyScriptsSlingPostProcessor.process(request, changes));
    }

    @Test
    public void processHtmlResourceThrowsException()
    {
        SlingHttpServletRequestImpl request = mock(SlingHttpServletRequestImpl.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        List<Modification> changes = List.of(new Modification(ModificationType.COPY, "/Forms/f1", "/Forms/f2"));

        when(request.getResourceResolver()).thenReturn(resourceResolver);
        mockRecourseContentType(resourceResolver, "/Forms/f1", "text/html;charset=UTF-8");

        Assert.assertThrows("HTML files are not allowed", Exception.class,
                () -> this.denyScriptsSlingPostProcessor.process(request, changes));
    }

    @Test
    public void processScriptResourceIgnoresCase()
    {
        SlingHttpServletRequestImpl request = mock(SlingHttpServletRequestImpl.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        List<Modification> changes = List.of(new Modification(ModificationType.COPY, "/Forms/f1", "/Forms/f2"));

        when(request.getResourceResolver()).thenReturn(resourceResolver);
        mockRecourseContentType(resourceResolver, "/Forms/f1", "application/TypeScript");

        Assert.assertThrows("Script files are not allowed", Exception.class,
                () -> this.denyScriptsSlingPostProcessor.process(request, changes));
    }

    @Test
    public void processHtmlResourceIgnoresCase()
    {
        SlingHttpServletRequestImpl request = mock(SlingHttpServletRequestImpl.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        List<Modification> changes = List.of(new Modification(ModificationType.COPY, "/Forms/f1", "/Forms/f2"));

        when(request.getResourceResolver()).thenReturn(resourceResolver);
        mockRecourseContentType(resourceResolver, "/Forms/f1", "application/XHTML");

        Assert.assertThrows("HTML files are not allowed", Exception.class,
                () -> this.denyScriptsSlingPostProcessor.process(request, changes));
    }

    @Test
    public void processAllowsOtherContentTypeResource()
    {
        SlingHttpServletRequestImpl request = mock(SlingHttpServletRequestImpl.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        List<Modification> changes = List.of(new Modification(ModificationType.COPY, "/Forms/f1", "/Forms/f2"));

        when(request.getResourceResolver()).thenReturn(resourceResolver);
        mockRecourseContentType(resourceResolver, "/Forms/f1", "text/plain;charset=UTF-8");
        mockRecourseContentType(resourceResolver, "/Forms/f2", "text/plain;charset=UTF-8");

        Assertions.assertThatCode(() -> this.denyScriptsSlingPostProcessor.process(request, changes))
                .doesNotThrowAnyException();
    }

    @Test
    public void processAllowsAllForAdminUser()
    {
        SlingHttpServletRequestImpl request = mock(SlingHttpServletRequestImpl.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        List<Modification> changes = List.of(new Modification(ModificationType.COPY, "/Forms/f1", "/Forms/f2"));

        when(request.getResourceResolver()).thenReturn(resourceResolver);
        mockRecourseContentType(resourceResolver, "/Forms/f1", "application/XHTML");
        mockRecourseContentType(resourceResolver, "/Forms/f2", "application/TypeScript");

        when(request.getRemoteUser()).thenReturn("admin");
        Assertions.assertThatCode(() -> this.denyScriptsSlingPostProcessor.process(request, changes))
                .doesNotThrowAnyException();
    }

    @Test
    public void processAllowsNullResource()
    {
        SlingHttpServletRequestImpl request = mock(SlingHttpServletRequestImpl.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        List<Modification> changes = List.of(new Modification(ModificationType.COPY, "/Forms/f1", "/Forms/f2"));

        when(request.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.getResource("/Forms/f1")).thenReturn(null);
        when(resourceResolver.getResource("/Forms/f2")).thenReturn(null);

        Assertions.assertThatCode(() -> this.denyScriptsSlingPostProcessor.process(request, changes))
                .doesNotThrowAnyException();
    }

    private void mockRecourseContentType(ResourceResolver resourceResolver, String resourcePath, String contentType)
    {
        Resource resource = mock(Resource.class);
        ResourceMetadata metadata = mock(ResourceMetadata.class);
        when(resourceResolver.getResource(eq(resourcePath))).thenReturn(resource);
        when(resource.getResourceMetadata()).thenReturn(metadata);
        when(metadata.getContentType()).thenReturn(contentType);
    }

}
