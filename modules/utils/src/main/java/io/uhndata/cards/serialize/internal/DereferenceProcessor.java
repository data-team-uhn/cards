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

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Dereference properties of type {@code REFERENCE}, {@code WEAKREFERENCE} or {@code PATH}: instead of printing the
 * internal UUID, serialize the referenced node. The name of this processor is {@code dereference}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DereferenceProcessor implements ResourceJsonProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DereferenceProcessor.class);

    @Override
    public String getName()
    {
        return "dereference";
    }

    @Override
    public int getPriority()
    {
        return 10;
    }

    @Override
    public boolean isEnabledByDefault(Resource resource)
    {
        return true;
    }

    @Override
    public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (property.isMultiple()) {
                return serializeMultiValuedProperty(property, input, serializeNode);
            } else {
                return serializeSingleValuedProperty(property, input, serializeNode);
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Unexpected error while serializing property {} of node {}: {}", property, node,
                e.getMessage());
        }
        return input;
    }

    private JsonValue serializeSingleValuedProperty(final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
        throws RepositoryException
    {
        switch (property.getType()) {
            case PropertyType.REFERENCE:
            case PropertyType.WEAKREFERENCE:
            case PropertyType.PATH:
                try {
                    final Node node = property.getNode();
                    // Reference properties starting with "jcr:" deal with versioning,
                    // and the version trees have cyclic references.
                    // Also, the node history shouldn't be serialized.
                    if (property.getName().startsWith("jcr:")) {
                        return Json.createValue(node.getPath());
                    } else {
                        return serializeNode.apply(node);
                    }
                } catch (RepositoryException e) {
                    // If we can't access the node, just leave the input unmodified
                    return input;
                }
            default:
                // If this is not a reference property, just leave the input unmodified
                return input;
        }
    }

    private JsonValue serializeMultiValuedProperty(final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode) throws RepositoryException
    {
        final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        final String name = property.getName();

        if (property.getType() == PropertyType.REFERENCE || property.getType() == PropertyType.WEAKREFERENCE) {
            for (Value value : property.getValues()) {
                try {
                    final Node node = property.getSession().getNodeByIdentifier(value.getString());
                    // Reference properties starting with "jcr:" deal with versioning,
                    // and the version trees have cyclic references.
                    // Also, the node history shouldn't be serialized.
                    if (name.startsWith("jcr:")) {
                        arrayBuilder.add(node.getPath());
                    } else {
                        arrayBuilder.add(serializeNode.apply(node));
                    }
                } catch (RepositoryException e) {
                    // If we can't access the node, just leave the input unmodified
                    return input;
                }
            }
        } else if (property.getType() == PropertyType.PATH) {
            for (Value value : property.getValues()) {
                final String path = value.getString();
                try {
                    final Node node = path.charAt(0) == '/' ? property.getSession().getNode(path)
                        : property.getParent().getNode(path);
                    arrayBuilder.add(serializeNode.apply(node));
                } catch (RepositoryException e) {
                    // If we can't access a node, just add its path to the output
                    arrayBuilder.add(path);
                }
            }
        } else {
            // If this is not a reference property, just leave the input unmodified
            return input;
        }
        return arrayBuilder.build();
    }
}
