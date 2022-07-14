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
package io.uhndata.cards.forms.internal.serialize;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Simplify Form serialization by removing unnecessary properties and children. The name of this processor is
 * {@code simple}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class SimpleFormProcessor implements ResourceJsonProcessor
{
    private static final List<String> KEEP_QUESTION_PROPERTIES =
        Arrays.asList("text", "description", "unitOfMeasurement", "enableNotes", "jcr:primaryType");

    @Override
    public String getName()
    {
        return "simple";
    }

    @Override
    public int getPriority()
    {
        return 50;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        // This only simplifies forms
        return resource.isResourceType("cards/Form");
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
            result = removeNestedJcrProperties(node, name, result);
            result = beautifySubject(node, name, result);
            result = simplifyQuestion(node, name, result);
            return result;
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    @Override
    public JsonValue processChild(final Node node, final Node child, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            // Do not recursively serialize questionnaire items
            if (node.isNodeType("cards:Questionnaire") || node.isNodeType("cards:Section")
                || node.isNodeType("cards:Question")) {
                return null;
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    private JsonValue removeNestedJcrProperties(final Node node, final String propertyName, final JsonValue input)
        throws RepositoryException
    {
        // Remove all JCR properties from non-root nodes
        if (!node.isNodeType("cards:Form") && propertyName.startsWith("jcr:")
            && !"jcr:primaryType".equals(propertyName)) {
            return null;
        }
        return input;
    }

    private JsonValue beautifySubject(final Node node, final String propertyName, final JsonValue input)
        throws RepositoryException
    {
        // Only keep the label of the subject type
        if (node.isNodeType("cards:Subject") && "type".equals(propertyName)) {
            return input.asJsonObject().get("label");
        }
        return input;
    }

    private JsonValue simplifyQuestion(final Node node, final String propertyName, final JsonValue input)
        throws RepositoryException
    {
        // Only keep important properties of the question
        if (node.isNodeType("cards:Question") && !KEEP_QUESTION_PROPERTIES.contains(propertyName)) {
            return null;
        }
        return input;
    }
}
