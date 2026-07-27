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

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DenyScriptsSlingPostProcessor}.
 *
 * @version $Id$
 */
public class DenyScriptsSlingPostProcessorTest
{
    private static final String SOURCE_PATH = "/Forms/f1";

    private static final String DESTINATION_PATH = "/Forms/f2";

    private final DenyScriptsSlingPostProcessor processor = new DenyScriptsSlingPostProcessor();

    private final SlingJakartaHttpServletRequest request = mock(SlingJakartaHttpServletRequest.class);

    private final ResourceResolver resourceResolver = mock(ResourceResolver.class);

    private final List<Modification> changes =
        List.of(new Modification(ModificationType.COPY, SOURCE_PATH, DESTINATION_PATH));

    @Test
    public void processAllowsResourceWithNullResourceMetadata() throws Exception
    {
        Resource resource = mock(Resource.class);

        when(this.request.getResourceResolver()).thenReturn(this.resourceResolver);
        when(this.resourceResolver.getResource(SOURCE_PATH)).thenReturn(resource);
        when(resource.getResourceMetadata()).thenReturn(null);

        this.processor.process(this.request, this.changes);
    }

    @Test
    public void processAllowsResourceWithNullContentType() throws Exception
    {
        when(this.request.getResourceResolver()).thenReturn(this.resourceResolver);
        mockResourceContentType(SOURCE_PATH, null);

        this.processor.process(this.request, this.changes);
    }

    @Test
    public void processScriptResourceThrowsException()
    {
        when(this.request.getResourceResolver()).thenReturn(this.resourceResolver);
        mockResourceContentType(SOURCE_PATH, "text/script;charset=UTF-8");

        Exception e = assertThrows(Exception.class, () -> this.processor.process(this.request, this.changes));
        assertEquals("Script files are not allowed", e.getMessage());
    }

    @Test
    public void processHtmlResourceThrowsException()
    {
        when(this.request.getResourceResolver()).thenReturn(this.resourceResolver);
        mockResourceContentType(SOURCE_PATH, "text/html;charset=UTF-8");

        Exception e = assertThrows(Exception.class, () -> this.processor.process(this.request, this.changes));
        assertEquals("HTML files are not allowed", e.getMessage());
    }

    @Test
    public void processScriptResourceIgnoresCase()
    {
        when(this.request.getResourceResolver()).thenReturn(this.resourceResolver);
        mockResourceContentType(SOURCE_PATH, "application/TypeScript");

        Exception e = assertThrows(Exception.class, () -> this.processor.process(this.request, this.changes));
        assertEquals("Script files are not allowed", e.getMessage());
    }

    @Test
    public void processHtmlResourceIgnoresCase()
    {
        when(this.request.getResourceResolver()).thenReturn(this.resourceResolver);
        mockResourceContentType(SOURCE_PATH, "application/XHTML");

        Exception e = assertThrows(Exception.class, () -> this.processor.process(this.request, this.changes));
        assertEquals("HTML files are not allowed", e.getMessage());
    }

    @Test
    public void processAllowsOtherContentTypeResource() throws Exception
    {
        when(this.request.getResourceResolver()).thenReturn(this.resourceResolver);
        mockResourceContentType(SOURCE_PATH, "text/plain;charset=UTF-8");

        this.processor.process(this.request, this.changes);
    }

    @Test
    public void processAllowsAllForAdminUser() throws Exception
    {
        // Even a forbidden content type is accepted when the admin is uploading it
        mockResourceContentType(SOURCE_PATH, "application/TypeScript");

        when(this.request.getRemoteUser()).thenReturn("admin");
        this.processor.process(this.request, this.changes);
    }

    @Test
    public void processAllowsNullResource() throws Exception
    {
        when(this.request.getResourceResolver()).thenReturn(this.resourceResolver);
        when(this.resourceResolver.getResource(SOURCE_PATH)).thenReturn(null);

        this.processor.process(this.request, this.changes);
    }

    private void mockResourceContentType(String resourcePath, String contentType)
    {
        Resource resource = mock(Resource.class);
        ResourceMetadata metadata = mock(ResourceMetadata.class);
        when(this.resourceResolver.getResource(resourcePath)).thenReturn(resource);
        when(resource.getResourceMetadata()).thenReturn(metadata);
        when(metadata.getContentType()).thenReturn(contentType);
    }
}
