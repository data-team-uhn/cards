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

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Simplify Questionnaire serialization by removing unnecessary properties and children. The name of this processor is
 * {@code simple}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class SimpleQuestionnaireProcessor implements ResourceJsonProcessor
{
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
        // This only simplifies questionnaires
        return resource.isResourceType("cards/Questionnaire");
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
            // TODO Do we need the conditions and answer options?
            return result;
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    private JsonValue removeNestedJcrProperties(final Node node, final String propertyName, final JsonValue input)
        throws RepositoryException
    {
        // Remove all JCR properties from non-root nodes
        if (!node.isNodeType("cards:Questionnaire") && propertyName.startsWith("jcr:")
            && !"jcr:primaryType".equals(propertyName)) {
            return null;
        }
        return input;
    }
}
