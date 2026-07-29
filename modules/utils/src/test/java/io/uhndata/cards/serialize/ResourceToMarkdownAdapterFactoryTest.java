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

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sling.api.resource.Resource;
import org.junit.Test;

import io.uhndata.cards.serialize.spi.ResourceMarkdownProcessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourceToMarkdownAdapterFactory}.
 *
 * @version $Id$
 */
public class ResourceToMarkdownAdapterFactoryTest
{
    private static final String NODE_IDENTIFIER = "jcr:uuid";
    private static final String CREATED_BY_PROPERTY = "jcr:createdBy";
    private static final String TEST_FORM_PATH = "/Forms/f1";

    private final ResourceToMarkdownAdapterFactory factory = new ResourceToMarkdownAdapterFactory();

    @Test
    public void getAdapterForNullAdaptableObjectReturnsNull()
    {
        assertNull(this.factory.getAdapter(null, CharSequence.class));
    }

    @Test
    public void getAdapterForResourceAdaptableObjectReturnsSerializedAdapter() throws IllegalAccessException
    {
        Resource adaptable = mock(Resource.class);
        String identifier = UUID.randomUUID().toString();
        ResourceMarkdownProcessor processor = mock(ResourceMarkdownProcessor.class);
        String data = NODE_IDENTIFIER + "," + CREATED_BY_PROPERTY + "\n"
                + identifier + ",admin";

        when(processor.canProcess(adaptable)).thenReturn(true);
        when(processor.serialize(adaptable)).thenReturn(data);

        setProcessors(List.of(processor));
        CharSequence adapter = this.factory.getAdapter(adaptable, CharSequence.class);
        assertNotNull(adapter);
        assertEquals(data, adapter);
    }

    @Test
    public void getAdapterForUnsupportedResourceReturnsResourcePath() throws IllegalAccessException
    {
        Resource adaptable = mock(Resource.class);
        ResourceMarkdownProcessor processor = mock(ResourceMarkdownProcessor.class);

        when(adaptable.getPath()).thenReturn(TEST_FORM_PATH);

        setProcessors(List.of(processor));
        CharSequence adapter = this.factory.getAdapter(adaptable, CharSequence.class);
        assertNotNull(adapter);
        assertEquals(TEST_FORM_PATH, adapter);
    }

    @Test
    public void getAdapterWithNoProcessorsReturnsResourcePath() throws IllegalAccessException
    {
        Resource adaptable = mock(Resource.class);

        when(adaptable.getPath()).thenReturn(TEST_FORM_PATH);

        setProcessors(List.of());
        CharSequence adapter = this.factory.getAdapter(adaptable, CharSequence.class);
        assertNotNull(adapter);
        assertEquals(TEST_FORM_PATH, adapter);
    }

    @Test
    public void getAdapterUsesFirstProcessorThatCanProcess() throws IllegalAccessException
    {
        Resource adaptable = mock(Resource.class);

        ResourceMarkdownProcessor processor1 = mock(ResourceMarkdownProcessor.class);
        when(processor1.canProcess(adaptable)).thenReturn(false);

        ResourceMarkdownProcessor processor2 = mock(ResourceMarkdownProcessor.class);
        when(processor2.canProcess(adaptable)).thenReturn(true);
        String data = NODE_IDENTIFIER + "," + CREATED_BY_PROPERTY + "\n"
                + UUID.randomUUID() + ",admin";
        when(processor2.serialize(adaptable)).thenReturn(data);

        ResourceMarkdownProcessor processor3 = mock(ResourceMarkdownProcessor.class);

        setProcessors(List.of(processor1, processor2, processor3));
        CharSequence adapter = this.factory.getAdapter(adaptable, CharSequence.class);
        verify(processor1, times(0)).serialize(adaptable);
        verify(processor2, times(1)).serialize(adaptable);
        verify(processor3, times(0)).serialize(adaptable);
        assertNotNull(adapter);
        assertEquals(data, adapter);
    }

    private void setProcessors(final List<ResourceMarkdownProcessor> processors) throws IllegalAccessException
    {
        FieldUtils.writeField(this.factory, "allProcessors", processors, true);
    }
}
