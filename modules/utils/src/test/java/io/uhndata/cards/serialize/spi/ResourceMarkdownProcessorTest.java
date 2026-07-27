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

import org.apache.sling.api.resource.Resource;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the default methods of {@link ResourceMarkdownProcessor}.
 *
 * @version $Id$
 */
public class ResourceMarkdownProcessorTest
{
    /** A processor relying on the default methods of the interface. */
    private final ResourceMarkdownProcessor processor = new ResourceMarkdownProcessor()
    {
        @Override
        public String serialize(final Resource resource)
        {
            return "";
        }
    };

    @Test
    public void canProcessReturnsFalseByDefault()
    {
        assertFalse(this.processor.canProcess(mock(Resource.class)));
    }
}
