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

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Remove any properties from the output that would not be present in an importable questionnaire XML.
 * This processor is intended to be run alongside the following other processors:
 * * .deep
 * * .-identify
 * The name of this processor is {@code importable}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class ImportableProcessor implements ResourceJsonProcessor
{
    @Override
    public String getName()
    {
        return "importable";
    }

    @Override
    public String getDescription()
    {
        return "Export a file that is suitable for using as a source file to be imported. "
            + "Removes any properties that are tied to a running instance such as unique ID and version control. "
            + "This processor is intended to be run alongside the `deep` and `-identify` processor.";
    }

    @Override
    public int getPriority()
    {
        return 100;
    }

    @Override
    public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            String propertyName = property.getName();

            JsonValue result = input;

            // Remove properties with certain prefixes
            if (propertyName.startsWith("jcr:") && !"jcr:primaryType".equals(propertyName)) {
                // Remove all jcr properties other than primaryType
                result = null;
            } else if (propertyName.startsWith("sling:")) {
                // Remove all sling properties
                result = null;
            } else if (isReference(property)) {
                // Convert all reference properties to the path being referenced
                if (property.isMultiple()) {
                    final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                    for (Value value : property.getValues()) {
                        Node referencedNode = property.getSession().getNodeByIdentifier(value.getString());
                        arrayBuilder.add(referencedNode.getPath());
                    }
                    result = arrayBuilder.build();
                } else {
                    result = Json.createValue(property.getNode().getPath());
                }
            }

            return result;
        } catch (RepositoryException e) {
            // Really shouldn't happen
            return input;
        }
    }

    @Override
    public JsonValue processChild(final Node node, final Node child, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            // Remove link nodes.
            // TODO: If inter-processor dependencies are added, add a dependency to .nolinks instead
            if (child.isNodeType("cards:Links")) {
                return null;
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    @Override
    public String processPropertyName(final Node node, final Property property, final String input)
    {
        try {
            if (isReference(property)) {
                // Prefix all reference property names so the json to xml script recognizes they should
                // be references and not strings
                return "jcr:reference:" + property.getName();
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    private boolean isReference(Property property)
        throws RepositoryException
    {
        return PropertyType.REFERENCE == property.getType() || PropertyType.WEAKREFERENCE == property.getType();
    }
}
