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
import javax.jcr.Session;
import javax.jcr.Value;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonValue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportableProcessor}.
 *
 * @version $Id$
 */
public class ImportableProcessorTest
{
    private static final String TEST_FORM_PATH = "/Forms/f1";

    private final ImportableProcessor processor = new ImportableProcessor();

    @Test
    public void getNameReturnsImportable()
    {
        assertEquals("importable", this.processor.getName());
    }

    @Test
    public void getPriorityReturnsOneHundred()
    {
        assertEquals(100, this.processor.getPriority());
    }

    @Test
    public void getDescriptionIsNotEmpty()
    {
        assertNotNull(this.processor.getDescription());
    }

    @Test
    public void processPropertyKeepsPrimaryType() throws RepositoryException
    {
        Property property = mockProperty("jcr:primaryType", PropertyType.NAME);
        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(mock(Node.class), property, input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyRemovesOtherJcrProperties() throws RepositoryException
    {
        Property property = mockProperty("jcr:uuid", PropertyType.STRING);
        assertNull(this.processor.processProperty(mock(Node.class), property, mock(JsonValue.class),
            n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyRemovesSlingProperties() throws RepositoryException
    {
        Property property = mockProperty("sling:resourceType", PropertyType.STRING);
        assertNull(this.processor.processProperty(mock(Node.class), property, mock(JsonValue.class),
            n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyConvertsSingleReferenceToPath() throws RepositoryException
    {
        Property property = mockProperty("questionnaire", PropertyType.REFERENCE);
        Node referenced = mock(Node.class);
        when(property.getNode()).thenReturn(referenced);
        when(referenced.getPath()).thenReturn("/Questionnaires/Test");

        JsonValue result = this.processor.processProperty(mock(Node.class), property, mock(JsonValue.class),
            n -> JsonValue.NULL);
        assertEquals(Json.createValue("/Questionnaires/Test"), result);
    }

    @Test
    public void processPropertyConvertsMultipleWeakReferencesToPaths() throws RepositoryException
    {
        Property property = mockProperty("relatedSubjects", PropertyType.WEAKREFERENCE);
        when(property.isMultiple()).thenReturn(true);
        Session session = mock(Session.class);
        Value value = mock(Value.class);
        Node referenced = mock(Node.class);
        when(property.getValues()).thenReturn(new Value[] { value });
        when(property.getSession()).thenReturn(session);
        when(value.getString()).thenReturn("uuid-1");
        when(session.getNodeByIdentifier("uuid-1")).thenReturn(referenced);
        when(referenced.getPath()).thenReturn("/Subjects/r1");

        JsonValue result = this.processor.processProperty(mock(Node.class), property, mock(JsonValue.class),
            n -> JsonValue.NULL);
        assertEquals(1, ((JsonArray) result).size());
        assertEquals("/Subjects/r1", ((JsonArray) result).getString(0));
    }

    @Test
    public void processPropertyKeepsOtherProperties() throws RepositoryException
    {
        Property property = mockProperty("label", PropertyType.STRING);
        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(mock(Node.class), property, input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyCatchesRepositoryExceptionReturnsInput() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenThrow(new RepositoryException());
        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processProperty(mock(Node.class), property, input, n -> JsonValue.NULL));
    }

    @Test
    public void processChildRemovesLinks() throws RepositoryException
    {
        Node child = mock(Node.class);
        when(child.isNodeType("cards:Links")).thenReturn(true);
        assertNull(this.processor.processChild(mock(Node.class), child, mock(JsonValue.class), n -> JsonValue.NULL));
    }

    @Test
    public void processChildKeepsOtherChildren() throws RepositoryException
    {
        Node child = mock(Node.class);
        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processChild(mock(Node.class), child, input, n -> JsonValue.NULL));
    }

    @Test
    public void processChildCatchesRepositoryExceptionReturnsInput() throws RepositoryException
    {
        Node child = mock(Node.class);
        when(child.isNodeType("cards:Links")).thenThrow(new RepositoryException());
        JsonValue input = mock(JsonValue.class);
        assertEquals(input, this.processor.processChild(mock(Node.class), child, input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyNamePrefixesReferences() throws RepositoryException
    {
        Property property = mockProperty("questionnaire", PropertyType.REFERENCE);
        assertEquals("jcr:reference:questionnaire",
            this.processor.processPropertyName(mock(Node.class), property, "questionnaire"));
    }

    @Test
    public void processPropertyNameKeepsOtherNames() throws RepositoryException
    {
        Property property = mockProperty("label", PropertyType.STRING);
        assertEquals("label", this.processor.processPropertyName(mock(Node.class), property, "label"));
    }

    @Test
    public void processPropertyNameCatchesRepositoryExceptionReturnsInput() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getType()).thenThrow(new RepositoryException());
        assertEquals("label", this.processor.processPropertyName(mock(Node.class), property, "label"));
    }

    private Property mockProperty(final String name, final int type) throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenReturn(name);
        when(property.getType()).thenReturn(type);
        return property;
    }
}
