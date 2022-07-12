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
package io.uhndata.cards.subjects.internal.serialize;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Simplify Subject serialization by including only simple labels for the name, type, and parents of a subject. It
 * should be used together with {@code -dereference} and {@code -identify}. The name of this processor is {@code bare}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class BareSubjectProcessor implements ResourceJsonProcessor
{
    @Override
    public String getName()
    {
        return "bare";
    }

    @Override
    public int getPriority()
    {
        return 95;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        return resource.isResourceType("cards/Subject");
    }

    @Override
    public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        if (property == null) {
            return null;
        }
        try {
            JsonValue result = input;
            result = simplifyType(node, property, result);
            result = simplifyParents(node, property, result);
            return result;
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("cards:Subject")) {
                // Replace the "identifier" property with a prettier "subject" property
                json.remove("identifier");
                json.add("subject", simplifyIdentifier(node));
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    private JsonValue simplifyIdentifier(final Node subject) throws RepositoryException
    {
        return Json.createValue(recursivelyCollect(subject, "identifier", "parents"));
    }

    private JsonValue simplifyType(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the "type" reference with the labels of the actual subject type (and its ancestors)
        if (node.isNodeType("cards:Subject") && "type".equals(property.getName())) {
            try {
                return Json.createValue(recursivelyCollect(property.getNode(), "label", "parent"));
            } catch (RepositoryException | NullPointerException e) {
                // Bad data, just return the original input
            }
        }
        return input;
    }

    private JsonValue simplifyParents(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the "parents" reference with the labels of the actual ancestors
        if (node.isNodeType("cards:Subject") && "parents".equals(property.getName())) {
            try {
                return Json.createValue(recursivelyCollect(property.getNode(), "identifier", "parents"));
            } catch (RepositoryException | NullPointerException e) {
                // Bad data, just return the original input
            }
        }
        return input;
    }

    private String recursivelyCollect(final Node start, final String propertyToRead, final String parentProperty)
        throws RepositoryException
    {
        Node node = start;
        final List<String> properties = new ArrayList<>();
        while (node != null) {
            properties.add(node.getProperty(propertyToRead).getString());
            if (!node.hasProperty(parentProperty)) {
                break;
            }
            node = node.getProperty(parentProperty).getNode();
        }

        return properties.stream().reduce((result, parent) -> parent + " / " + result).get();
    }
}
