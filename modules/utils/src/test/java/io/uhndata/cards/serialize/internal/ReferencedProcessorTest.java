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
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReferencedProcessor}.
 *
 * @version $Id$
 */
public class ReferencedProcessorTest
{
    private static final String REFERENCED = "@referenced";

    private final ReferencedProcessor processor = new ReferencedProcessor();

    @Test
    public void getNameReturnsReferenced()
    {
        assertEquals("referenced", this.processor.getName());
    }

    @Test
    public void getPriorityReturnsTen()
    {
        assertEquals(10, this.processor.getPriority());
    }

    @Test
    public void getDescriptionIsNotEmpty()
    {
        assertNotNull(this.processor.getDescription());
    }

    @Test
    public void leaveMarksReferencedNode() throws RepositoryException
    {
        JsonObject result = leaveWithReferences(true);
        assertTrue(result.getBoolean(REFERENCED));
    }

    @Test
    public void leaveMarksUnreferencedNode() throws RepositoryException
    {
        JsonObject result = leaveWithReferences(false);
        assertFalse(result.getBoolean(REFERENCED));
    }

    @Test
    public void leaveCatchesRepositoryException() throws RepositoryException
    {
        Node node = mock(Node.class);
        when(node.getReferences()).thenThrow(new RepositoryException());

        JsonObjectBuilder json = Json.createObjectBuilder();
        this.processor.leave(node, json, n -> JsonValue.NULL);
        assertFalse(json.build().containsKey(REFERENCED));
    }

    private JsonObject leaveWithReferences(final boolean hasReferences) throws RepositoryException
    {
        Node node = mock(Node.class);
        PropertyIterator references = mock(PropertyIterator.class);
        when(node.getReferences()).thenReturn(references);
        when(references.hasNext()).thenReturn(hasReferences);

        JsonObjectBuilder json = Json.createObjectBuilder();
        this.processor.leave(node, json, n -> JsonValue.NULL);
        return json.build();
    }
}
