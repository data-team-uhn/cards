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
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;

import jakarta.json.JsonValue;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sling.api.resource.Resource;
import org.junit.Before;
import org.junit.Test;

import io.uhndata.cards.forms.api.FormUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExcludeDefaultPropertiesProcessor}.
 *
 * @version $Id$
 */
public class ExcludeDefaultPropertiesProcessorTest
{
    private static final String STATUS = "status";

    private final ExcludeDefaultPropertiesProcessor processor = new ExcludeDefaultPropertiesProcessor();

    private final FormUtils formUtils = mock(FormUtils.class);

    private final Node node = mock(Node.class);

    private final NodeType nodeType = mock(NodeType.class);

    @Before
    public void setUp() throws IllegalAccessException, RepositoryException
    {
        FieldUtils.writeField(this.processor, "formUtils", this.formUtils, true);
        when(this.node.getPrimaryNodeType()).thenReturn(this.nodeType);
        when(this.nodeType.getPropertyDefinitions()).thenReturn(new PropertyDefinition[0]);
    }

    @Test
    public void getNameReturnsExcludeDefaultProperties()
    {
        assertEquals("excludeDefaultProperties", this.processor.getName());
    }

    @Test
    public void getPriorityReturnsFiftySix()
    {
        assertEquals(56, this.processor.getPriority());
    }

    @Test
    public void getDescriptionIsNotEmpty()
    {
        assertNotNull(this.processor.getDescription());
    }

    @Test
    public void processPropertyExcludesFalseBooleanWithoutDefault() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.BOOLEAN);
        when(property.getBoolean()).thenReturn(false);

        assertNull(this.processor.processProperty(this.node, property, mock(JsonValue.class), n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyKeepsTrueBooleanWithoutDefault() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.BOOLEAN);
        when(property.getBoolean()).thenReturn(true);

        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(this.node, property, input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyExcludesEmptyStringWithoutDefault() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        when(property.getString()).thenReturn("");

        assertNull(this.processor.processProperty(this.node, property, mock(JsonValue.class), n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyKeepsNonEmptyStringWithoutDefault() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        when(property.getString()).thenReturn("DRAFT");

        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(this.node, property, input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyKeepsNonBooleanNonStringPropertyWithoutDefault() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.LONG);

        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(this.node, property, input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyKeepsMultivaluedPropertyWithoutDefault() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        when(property.isMultiple()).thenReturn(true);

        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(this.node, property, input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyExcludesValueMatchingDefault() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        mockDefaultValues(STATUS, "DRAFT");
        when(this.formUtils.getValue(property)).thenReturn("DRAFT");

        assertNull(this.processor.processProperty(this.node, property, mock(JsonValue.class), n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyKeepsValueDifferentFromDefault() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        mockDefaultValues(STATUS, "DRAFT");
        when(this.formUtils.getValue(property)).thenReturn("SUBMITTED");

        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(this.node, property, input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyExcludesValuelessPropertyWithDefault() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        mockDefaultValues(STATUS, "DRAFT");
        when(this.formUtils.getValue(property)).thenReturn(null);

        assertNull(this.processor.processProperty(this.node, property, mock(JsonValue.class), n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyExcludesMultipleValuesMatchingDefaults() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        when(property.isMultiple()).thenReturn(true);
        mockDefaultValues(STATUS, "DRAFT", "SUBMITTED");
        when(this.formUtils.getValue(property)).thenReturn(new Object[] { "DRAFT", "SUBMITTED" });

        assertNull(this.processor.processProperty(this.node, property, mock(JsonValue.class), n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyKeepsMultipleValuesDifferentFromDefaults() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        when(property.isMultiple()).thenReturn(true);
        mockDefaultValues(STATUS, "DRAFT", "SUBMITTED");
        when(this.formUtils.getValue(property)).thenReturn(new Object[] { "DRAFT" });

        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(this.node, property, input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyCachesDefaultsPerNodeType() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        mockDefaultValues(STATUS, "DRAFT");
        when(this.formUtils.getValue(property)).thenReturn("SUBMITTED");

        this.processor.processProperty(this.node, property, mock(JsonValue.class), n -> JsonValue.NULL);
        this.processor.processProperty(this.node, property, mock(JsonValue.class), n -> JsonValue.NULL);
        // The property definitions are only read once, the second invocation uses the cached defaults
        verify(this.nodeType, times(1)).getPropertyDefinitions();
    }

    @Test
    public void endDiscardsTheCachedDefaults() throws RepositoryException
    {
        Property property = mockProperty(STATUS, PropertyType.STRING);
        mockDefaultValues(STATUS, "DRAFT");
        when(this.formUtils.getValue(property)).thenReturn("SUBMITTED");

        this.processor.processProperty(this.node, property, mock(JsonValue.class), n -> JsonValue.NULL);
        this.processor.end(mock(Resource.class));
        this.processor.processProperty(this.node, property, mock(JsonValue.class), n -> JsonValue.NULL);
        // The cache was discarded in between, so the property definitions are read again
        verify(this.nodeType, times(2)).getPropertyDefinitions();
    }

    @Test
    public void processPropertyCatchesRepositoryExceptionReturnsInput() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenThrow(new RepositoryException());

        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(this.node, property, input, n -> JsonValue.NULL));
    }

    private Property mockProperty(final String name, final int type) throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenReturn(name);
        when(property.getType()).thenReturn(type);
        return property;
    }

    private void mockDefaultValues(final String propertyName, final String... defaults) throws RepositoryException
    {
        PropertyDefinition definition = mock(PropertyDefinition.class);
        when(definition.getName()).thenReturn(propertyName);
        Value[] values = new Value[defaults.length];
        for (int i = 0; i < defaults.length; i++) {
            Value value = mock(Value.class);
            when(this.formUtils.getValue(value)).thenReturn(defaults[i]);
            values[i] = value;
        }
        when(definition.getDefaultValues()).thenReturn(values);
        when(this.nodeType.getPropertyDefinitions()).thenReturn(new PropertyDefinition[] { definition });
    }
}
