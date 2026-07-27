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
package io.uhndata.cards.serialize.spi;

import javax.jcr.Node;
import javax.jcr.Property;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the default methods of {@link ResourceJsonProcessor}.
 *
 * @version $Id$
 */
public class ResourceJsonProcessorTest
{
    private static final String NAME = "minimal";

    private static final String DESCRIPTION = "A minimal processor";

    /** A processor relying on all the default methods of the interface. */
    private final ResourceJsonProcessor processor = new ResourceJsonProcessor()
    {
        @Override
        public String getName()
        {
            return NAME;
        }

        @Override
        public int getPriority()
        {
            return 0;
        }

        @Override
        public String getDescription()
        {
            return DESCRIPTION;
        }
    };

    @Test
    public void getDetailsCollectsNameDescriptionAndEnabledFlag()
    {
        SelectorDetails details = this.processor.getDetails();
        assertEquals(NAME, details.getName());
        assertEquals(DESCRIPTION, details.getDescription());
        assertFalse(details.isEnabledByDefault());
    }

    @Test
    public void canProcessReturnsTrueByDefault()
    {
        assertTrue(this.processor.canProcess(mock(Resource.class)));
    }

    @Test
    public void isEnabledByDefaultReturnsFalseByDefault()
    {
        assertFalse(this.processor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void lifecycleMethodsDoNothingByDefault()
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        this.processor.start(mock(Resource.class));
        this.processor.enter(mock(Node.class), json, n -> JsonValue.NULL);
        this.processor.leave(mock(Node.class), json, n -> JsonValue.NULL);
        this.processor.end(mock(Resource.class));
        JsonObject result = json.build();
        assertTrue(result.isEmpty());
    }

    @Test
    public void processPropertyReturnsInputByDefault()
    {
        JsonValue input = mock(JsonValue.class);
        assertEquals(input,
            this.processor.processProperty(mock(Node.class), mock(Property.class), input, n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyNameReturnsInputByDefault()
    {
        assertEquals("label", this.processor.processPropertyName(mock(Node.class), mock(Property.class), "label"));
    }

    @Test
    public void processChildReturnsInputByDefault()
    {
        JsonValue input = mock(JsonValue.class);
        assertEquals(input,
            this.processor.processChild(mock(Node.class), mock(Node.class), input, n -> JsonValue.NULL));
    }
}
