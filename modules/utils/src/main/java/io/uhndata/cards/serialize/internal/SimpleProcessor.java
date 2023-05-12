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

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.json.JsonValue;

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Simplify serialization for all resource types by removing unnecessary properties. The name of this processor is
 * {@code simple}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class SimpleProcessor implements ResourceJsonProcessor
{
    private static final List<String> KEEP_JCR_PROPERTIES =
        Arrays.asList("jcr:created", "jcr:createdBy", "jcr:lastModified", "jcr:lastModifiedBy", "jcr:uuid",
            "jcr:primaryType");

    @Override
    public String getName()
    {
        return "simple";
    }

    @Override
    public int getPriority()
    {
        return 25;
    }

    @Override
    public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        if (property == null) {
            return null;
        }
        try {
            final String name = property.getName();
            JsonValue result = input;
            result = removeSlingProperties(name, result);
            result = removeJcrProperties(name, result);
            result = removeFormProperty(name, result);
            return result;
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    private JsonValue removeSlingProperties(final String propertyName, final JsonValue input)
    {
        if (propertyName.startsWith("sling:")) {
            return null;
        }
        return input;
    }

    private JsonValue removeJcrProperties(final String propertyName, final JsonValue input)
        throws RepositoryException
    {
        // Only keep important JCR properties
        if (propertyName.startsWith("jcr:") && !KEEP_JCR_PROPERTIES.contains(propertyName)) {
            return null;
        }
        return input;
    }

    private JsonValue removeFormProperty(final String propertyName, final JsonValue input)
        throws RepositoryException
    {
        if ("form".equals(propertyName)) {
            return null;
        }
        return input;
    }
}
