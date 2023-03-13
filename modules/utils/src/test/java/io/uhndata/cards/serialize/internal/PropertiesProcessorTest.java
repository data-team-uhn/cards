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
package io.uhndata.cards.serialize.internal;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.forms.api.FormUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PropertiesProcessor}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertiesProcessorTest
{
    private static final String NAME = "properties";
    private static final int PRIORITY = 0;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private PropertiesProcessor propertiesProcessor;

    @Mock
    private FormUtils formUtils;

    @Test
    public void getNameReturnProperties()
    {
        assertEquals(NAME, this.propertiesProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        assertEquals(PRIORITY, this.propertiesProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultTest()
    {
        assertTrue(this.propertiesProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void processPropertyForNullJsonValueInputReturnsSerializedProperty()
    {
        JsonValue serializedProperty = mock(JsonValue.class);
        when(this.formUtils.serializeProperty(any())).thenReturn(serializedProperty);

        JsonValue jsonValue = this.propertiesProcessor.processProperty(mock(Node.class), mock(Property.class), null,
                mock(Function.class));
        assertNotNull(jsonValue);
        assertEquals(serializedProperty, jsonValue);
    }

    @Test
    public void processPropertyForNotNullJsonValueInputReturnsInputValue()
    {
        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.propertiesProcessor.processProperty(mock(Node.class), mock(Property.class), input,
                mock(Function.class));
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

}
