/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.serialize.internal;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import jakarta.json.JsonValue;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link ImportableProcessor}, covering the removal of the host-specific {@code form} property.
 *
 * @version $Id$
 */
public class ImportableProcessorTest
{
    private static final String FORM = "form";

    private static final String ANSWER = "cards:Answer";

    private static final String ANSWER_SECTION = "cards:AnswerSection";

    // A stand-in for whatever the serializer produced for the property; any non-null value will do, and the
    // API's own constant avoids needing a JSON provider on the test classpath.
    private static final JsonValue SERIALIZED = JsonValue.NULL;

    private final ImportableProcessor processor = new ImportableProcessor();

    @Test
    public void removesTheFormPropertyFromAnswers() throws RepositoryException
    {
        Assert.assertNull(process(nodeOfType(ANSWER), stringProperty(FORM)));
    }

    @Test
    public void removesTheFormPropertyFromAnswerSections() throws RepositoryException
    {
        Assert.assertNull(process(nodeOfType(ANSWER_SECTION), stringProperty(FORM)));
    }

    @Test
    public void keepsTheOtherPropertiesOfAnswers() throws RepositoryException
    {
        Assert.assertEquals(SERIALIZED, process(nodeOfType(ANSWER), stringProperty("value")));
    }

    /**
     * Only answers and answer sections carry the denormalized pointer, so the name alone must not be enough to
     * drop a property: anything else is free to have a legitimate one of its own.
     */
    @Test
    public void keepsAFormPropertyOnOtherNodeTypes() throws RepositoryException
    {
        Assert.assertEquals(SERIALIZED, process(nodeOfType("cards:Subject"), stringProperty(FORM)));
    }

    private JsonValue process(final Node node, final Property property)
    {
        return this.processor.processProperty(node, property, SERIALIZED, n -> null);
    }

    private Node nodeOfType(final String type) throws RepositoryException
    {
        final Node node = Mockito.mock(Node.class);
        Mockito.when(node.isNodeType(Mockito.anyString())).thenReturn(false);
        Mockito.when(node.isNodeType(type)).thenReturn(true);
        return node;
    }

    private Property stringProperty(final String name) throws RepositoryException
    {
        final Property property = Mockito.mock(Property.class);
        Mockito.when(property.getName()).thenReturn(name);
        Mockito.when(property.getType()).thenReturn(PropertyType.STRING);
        return property;
    }
}
