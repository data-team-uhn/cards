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
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeepProcessor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class DeepProcessorTest
{
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String NAME = "deep";
    private static final int PRIORITY = 10;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private DeepProcessor deepProcessor;

    @Test
    public void getNameReturnDeep()
    {
        assertEquals(NAME, this.deepProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        assertEquals(PRIORITY, this.deepProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultTest()
    {
        assertFalse(this.deepProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void processChildForNullJsonValueInputReturnsSerializedChild() throws RepositoryException
    {
        Node child = mock(Node.class);
        when(child.getPath()).thenReturn(TEST_FORM_PATH);

        JsonValue jsonValue = this.deepProcessor.processChild(mock(Node.class), child, null, this::serializeNode);
        assertNotNull(jsonValue);
        assertEquals(Json.createValue(TEST_FORM_PATH), jsonValue);
    }

    @Test
    public void processChildForNotNullJsonValueInputReturnsInputValue()
    {
        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.deepProcessor.processChild(mock(Node.class), mock(Node.class), input,
                mock(Function.class));
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

    private JsonValue serializeNode(Node node)
    {
        try {
            return Json.createValue(node.getPath());
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }
}
