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

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.serialize.spi.ResourceCSVProcessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourceToCSVAdapterFactory}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceToCSVAdapterFactoryTest
{
    private static final String NODE_IDENTIFIER = "jcr:uuid";
    private static final String CREATED_BY_PROPERTY = "jcr:createdBy";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private ResourceToCSVAdapterFactory factory;

    @Test
    public void getAdapterForNullAdaptableObjectReturnsNull()
    {
        assertNull(this.factory.getAdapter(null, CSVString.class));
    }

    @Test
    public void getAdapterForResourceAdaptableObjectReturnsSerializedAdapter()
    {
        Resource adaptable = mock(Resource.class);
        String identifier = UUID.randomUUID().toString();
        ResourceCSVProcessor processor = mock(ResourceCSVProcessor.class);
        String data = NODE_IDENTIFIER + "," + CREATED_BY_PROPERTY + "\n"
                + identifier + ",admin";

        when(processor.canProcess(adaptable)).thenReturn(true);
        when(processor.serialize(adaptable)).thenReturn(data);

        Whitebox.setInternalState(this.factory, "allProcessors", List.of(processor));
        CSVString adapter = this.factory.getAdapter(adaptable, CSVString.class);
        assertNotNull(adapter);
        assertEquals(data, adapter.toString());
    }

    @Test
    public void getAdapterForUnsupportedResourceReturnsResourcePath()
    {
        Resource adaptable = mock(Resource.class);
        ResourceCSVProcessor processor = mock(ResourceCSVProcessor.class);

        when(adaptable.getPath()).thenReturn(TEST_SUBJECT_PATH);

        Whitebox.setInternalState(this.factory, "allProcessors", List.of(processor));
        CSVString adapter = this.factory.getAdapter(adaptable, CSVString.class);
        assertNotNull(adapter);
        assertEquals(TEST_SUBJECT_PATH, adapter.toString());
    }

    @Test
    public void getAdapterWithNoProcessorsReturnsResourcePath()
    {
        Resource adaptable = mock(Resource.class);

        when(adaptable.getPath()).thenReturn(TEST_SUBJECT_PATH);

        Whitebox.setInternalState(this.factory, "allProcessors", List.of());
        CSVString adapter = this.factory.getAdapter(adaptable, CSVString.class);
        assertNotNull(adapter);
        assertEquals(TEST_SUBJECT_PATH, adapter.toString());
    }

    @Test
    public void getAdapterUsesFirstProcessorThatCanProcess()
    {
        Resource adaptable = mock(Resource.class);

        ResourceCSVProcessor processor1 = mock(ResourceCSVProcessor.class);
        when(processor1.canProcess(adaptable)).thenReturn(false);

        ResourceCSVProcessor processor2 = mock(ResourceCSVProcessor.class);
        when(processor2.canProcess(adaptable)).thenReturn(true);
        String data = NODE_IDENTIFIER + "," + CREATED_BY_PROPERTY + "\n"
                + UUID.randomUUID() + ",admin";
        when(processor2.serialize(adaptable)).thenReturn(data);

        ResourceCSVProcessor processor3 = mock(ResourceCSVProcessor.class);

        Whitebox.setInternalState(this.factory, "allProcessors",
                List.of(processor1, processor2, processor3));
        CSVString adapter = this.factory.getAdapter(adaptable, CSVString.class);
        verify(processor1, times(0)).serialize(adaptable);
        verify(processor2, times(1)).serialize(adaptable);
        verify(processor3, times(0)).serialize(adaptable);
        assertNotNull(adapter);
        assertEquals(data, adapter.toString());
    }

}
