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
package io.uhndata.cards.forms.internal.serialize.labels;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Adds a label to Answer Option nodes for resource questions in a questionnaire's JSON.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class ResourceOptionsLabelProcessor extends AbstractResourceLabelProcessor implements ResourceJsonProcessor
{
    /** Provides access to resources. */
    @Reference
    private ThreadResourceResolverProvider rrp;

    @Override
    public boolean canProcess(Resource resource)
    {
        return resource.isResourceType("cards/Form") || resource.isResourceType("cards/Questionnaire");
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            Node question = node.getParent();
            if (node.isNodeType("cards:AnswerOption")
                && !node.hasProperty(PROP_LABEL)
                && "resource".equals(question.getProperty("dataType").getString())) {
                // Find the resource property that should be holding the label, as configured in the parent question
                // Default to the resource node's name if no such configuration is present
                String labelPropertyName = getLabelPropertyName(question);
                // Generate and add the label
                json.add(PROP_LABEL, getAnswerOptionLabel(node, labelPropertyName));
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    /**
     * Basic method to get the answer label associated with the resource answer option.
     *
     * @param node the AnswerOption node being serialized
     * @return the label for that AmswerOption, obtained from the label property (indicated by the parent question) of
     *         resource it refers to
     */
    private JsonValue getAnswerOptionLabel(final Node node, final String labelPropertyName)
    {
        try {
            // Gather all the values in a map to prepare for associating labels
            // The label will default to the value if no matching resource is found
            Map<String, String> valueLabelMap = new LinkedHashMap<>();

            Property valueProp = node.getProperty(PROP_VALUE);
            if (valueProp.isMultiple()) {
                for (Value value : valueProp.getValues()) {
                    valueLabelMap.put(value.getString(), value.getString());
                }
            } else {
                valueLabelMap.put(valueProp.getString(), valueProp.getString());
            }

            ResourceResolver resolver = this.rrp.getThreadResourceResolver();
            if (resolver != null) {
                // Populate the labels in the map
                for (String value : new LinkedHashSet<>(valueLabelMap.keySet())) {
                    if (value.startsWith("/")) {
                        valueLabelMap.put(value, getLabelForResource(value, resolver, labelPropertyName));
                    }
                }
            }

            return createJsonValue(valueLabelMap.values(), valueProp.isMultiple());
        } catch (final RepositoryException ex) {
            // Really shouldn't happen
        }
        return null;
    }
}
