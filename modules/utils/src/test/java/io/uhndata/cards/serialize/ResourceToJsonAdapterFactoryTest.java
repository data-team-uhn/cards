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
package io.uhndata.cards.serialize;

import java.lang.reflect.Field;
import java.util.List;

import jakarta.json.JsonObject;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;
import io.uhndata.cards.utils.SelectorUtils;

/**
 * Unit tests for how {@link ResourceToJsonAdapterFactory} chooses the processors to run.
 *
 * <p>
 * The resource adapts to no node, so serialization stops right after the enabled processors are started, which is
 * what each test checks.
 * </p>
 *
 * @version $Id$
 */
public class ResourceToJsonAdapterFactoryTest
{
    private ResourceToJsonAdapterFactory factory;

    private ResourceJsonProcessor deep;

    private ResourceJsonProcessor identify;

    @Before
    public void setUp() throws Exception
    {
        this.deep = processor("deep", false);
        this.identify = processor("identify", true);
        this.factory = new ResourceToJsonAdapterFactory();
        final Field processors = ResourceToJsonAdapterFactory.class.getDeclaredField("allProcessors");
        processors.setAccessible(true);
        processors.set(this.factory, List.of(this.deep, this.identify));
    }

    @After
    public void tearDown()
    {
        SelectorUtils.clearRequestSelectors();
    }

    @Test
    public void testDefaultsRunWithoutSelectors()
    {
        final Resource resource = resource(".json");
        this.factory.getAdapter(resource, JsonObject.class);
        Mockito.verify(this.identify).start(resource);
        Mockito.verify(this.deep, Mockito.never()).start(resource);
    }

    @Test
    public void testPathSelectorEnablesProcessor()
    {
        final Resource resource = resource(".deep.json");
        this.factory.getAdapter(resource, JsonObject.class);
        Mockito.verify(this.deep).start(resource);
    }

    @Test
    public void testQuerySelectorEnablesProcessor()
    {
        final Resource resource = resource(".json");
        SelectorUtils.setRequestSelectors(List.of("deep"));
        this.factory.getAdapter(resource, JsonObject.class);
        Mockito.verify(this.deep).start(resource);
    }

    @Test
    public void testQuerySelectorDisablesDefault()
    {
        final Resource resource = resource(".json");
        SelectorUtils.setRequestSelectors(List.of("-identify"));
        this.factory.getAdapter(resource, JsonObject.class);
        Mockito.verify(this.identify, Mockito.never()).start(resource);
    }

    @Test
    public void testMissingPathInfoRunsDefaults()
    {
        final Resource resource = resource(null);
        this.factory.getAdapter(resource, JsonObject.class);
        Mockito.verify(this.identify).start(resource);
        Mockito.verify(this.deep, Mockito.never()).start(resource);
    }

    private static ResourceJsonProcessor processor(final String name, final boolean enabledByDefault)
    {
        final ResourceJsonProcessor processor = Mockito.mock(ResourceJsonProcessor.class);
        Mockito.when(processor.getName()).thenReturn(name);
        Mockito.when(processor.canProcess(Mockito.any())).thenReturn(true);
        Mockito.when(processor.isEnabledByDefault(Mockito.any())).thenReturn(enabledByDefault);
        return processor;
    }

    private static Resource resource(final String pathInfo)
    {
        final ResourceMetadata metadata = new ResourceMetadata();
        metadata.setResolutionPathInfo(pathInfo);
        final Resource resource = Mockito.mock(Resource.class);
        Mockito.when(resource.getResourceMetadata()).thenReturn(metadata);
        return resource;
    }
}
