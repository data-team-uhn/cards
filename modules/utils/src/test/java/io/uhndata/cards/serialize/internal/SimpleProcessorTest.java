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
import javax.jcr.RepositoryException;

import jakarta.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SimpleProcessor}.
 *
 * @version $Id$
 */
public class SimpleProcessorTest
{
    private static final String BASE_VERSION = "jcr:baseVersion";
    private static final String CREATED = "jcr:created";
    private static final String RESOURCE_TYPE = "sling:resourceType";
    private static final String NAME = "simple";
    private static final int PRIORITY = 25;

    private final SimpleProcessor simpleProcessor = new SimpleProcessor();

    @Test
    public void getNameReturnsSimple()
    {
        assertEquals(NAME, this.simpleProcessor.getName());
    }

    @Test
    public void getPriorityReturnsTwentyFive()
    {
        assertEquals(PRIORITY, this.simpleProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultReturnsFalse()
    {
        assertFalse(this.simpleProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void getDescriptionIsNotEmpty()
    {
        assertFalse(this.simpleProcessor.getDescription().isEmpty());
    }

    @Test
    public void processPropertyForNullPropertyReturnsNull()
    {
        JsonValue jsonValue = this.simpleProcessor.processProperty(mock(Node.class), null, mock(JsonValue.class),
                node -> JsonValue.NULL);
        assertNull(jsonValue);
    }

    @Test
    public void processPropertyForNotNullPropertyCatchesRepositoryExceptionReturnsInput() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenThrow(new RepositoryException());
        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.simpleProcessor.processProperty(mock(Node.class), property, input,
                node -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

    @Test
    public void processPropertyForKeptJcrPropertyReturnsInput() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenReturn(CREATED);
        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.simpleProcessor.processProperty(mock(Node.class), property, input,
                node -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

    @Test
    public void processPropertyForJcrPropertyReturnsNull() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenReturn(BASE_VERSION);
        JsonValue jsonValue = this.simpleProcessor.processProperty(mock(Node.class), property, mock(JsonValue.class),
                node -> JsonValue.NULL);
        assertNull(jsonValue);
    }

    @Test
    public void processPropertyForSlingPropertyReturnsNull() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenReturn(RESOURCE_TYPE);
        JsonValue jsonValue = this.simpleProcessor.processProperty(mock(Node.class), property, mock(JsonValue.class),
                node -> JsonValue.NULL);
        assertNull(jsonValue);
    }

    @Test
    public void processPropertyForFormPropertyReturnsNull() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenReturn("form");
        JsonValue jsonValue = this.simpleProcessor.processProperty(mock(Node.class), property, mock(JsonValue.class),
                node -> JsonValue.NULL);
        assertNull(jsonValue);
    }
}
