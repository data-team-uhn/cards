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

import io.uhndata.cards.serialize.spi.ResourceTextProcessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourceToTextAdapterFactory}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceToTextAdapterFactoryTest
{
    private static final String NODE_IDENTIFIER = "jcr:uuid";
    private static final String CREATED_BY_PROPERTY = "jcr:createdBy";
    private static final String TEST_FORM_PATH = "/Forms/f1";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private ResourceToTextAdapterFactory factory;

    @Test
    public void getAdapterForNullAdaptableObjectReturnsNull()
    {
        assertNull(this.factory.getAdapter(null, CharSequence.class));
    }

    @Test
    public void getAdapterForResourceAdaptableObjectReturnsSerializedAdapter()
    {
        Resource adaptable = mock(Resource.class);
        String identifier = UUID.randomUUID().toString();
        ResourceTextProcessor processor = mock(ResourceTextProcessor.class);
        String data = NODE_IDENTIFIER + "," + CREATED_BY_PROPERTY + "\n"
                + identifier + ",admin";

        when(processor.canProcess(adaptable)).thenReturn(true);
        when(processor.serialize(adaptable)).thenReturn(data);

        Whitebox.setInternalState(this.factory, "allProcessors", List.of(processor));
        String adapter = this.factory.getAdapter(adaptable, String.class);
        assertNotNull(adapter);
        assertEquals(data, adapter);
    }

    @Test
    public void getAdapterForResourceAdaptableObjectReturnsResourcePath()
    {
        Resource adaptable = mock(Resource.class);
        ResourceTextProcessor processor = mock(ResourceTextProcessor.class);

        when(adaptable.getPath()).thenReturn(TEST_FORM_PATH);

        Whitebox.setInternalState(this.factory, "allProcessors", List.of(processor));
        String adapter = this.factory.getAdapter(adaptable, String.class);
        assertNotNull(adapter);
        assertEquals(TEST_FORM_PATH, adapter);
    }

}
