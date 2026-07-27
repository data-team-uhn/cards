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

import javax.jcr.Node;
import javax.jcr.Property;

import jakarta.json.JsonValue;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sling.api.resource.Resource;
import org.junit.Before;
import org.junit.Test;

import io.uhndata.cards.forms.api.FormUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PropertiesProcessor}.
 *
 * @version $Id$
 */
public class PropertiesProcessorTest
{
    private static final String NAME = "properties";
    private static final int PRIORITY = 0;

    private final PropertiesProcessor propertiesProcessor = new PropertiesProcessor();

    private final FormUtils formUtils = mock(FormUtils.class);

    @Before
    public void setUp() throws IllegalAccessException
    {
        FieldUtils.writeField(this.propertiesProcessor, "formUtils", this.formUtils, true);
    }

    @Test
    public void getNameReturnsProperties()
    {
        assertEquals(NAME, this.propertiesProcessor.getName());
    }

    @Test
    public void getPriorityReturnsZero()
    {
        assertEquals(PRIORITY, this.propertiesProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultReturnsTrue()
    {
        assertTrue(this.propertiesProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void getDescriptionIsNotEmpty()
    {
        assertFalse(this.propertiesProcessor.getDescription().isEmpty());
    }

    @Test
    public void processPropertyForNullJsonValueInputReturnsSerializedProperty()
    {
        JsonValue serializedProperty = mock(JsonValue.class);
        when(this.formUtils.serializeProperty(any())).thenReturn(serializedProperty);

        JsonValue jsonValue = this.propertiesProcessor.processProperty(mock(Node.class), mock(Property.class), null,
                node -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(serializedProperty, jsonValue);
    }

    @Test
    public void processPropertyForNotNullJsonValueInputReturnsInputValue()
    {
        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.propertiesProcessor.processProperty(mock(Node.class), mock(Property.class), input,
                node -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

}
