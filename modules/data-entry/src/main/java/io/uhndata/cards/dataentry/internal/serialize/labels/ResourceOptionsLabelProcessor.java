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
package io.uhndata.cards.dataentry.internal.serialize.labels;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Adds a label to Answer Option nodes in a questionnaire's JSON.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class ResourceOptionsLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceOptionsLabelProcessor.class);

    private static final String PROP_RESOURCE_LABEL = "labelProperty";

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

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
     * @return the label for that AmswerOption, obtained from the label property (indicated by the parent question)
     *     of resource it refers to
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

            ResourceResolver resolver = this.resolverFactory.getThreadResourceResolver();
            // Populate the labels in the map
            for (String value : new LinkedHashSet<>(valueLabelMap.keySet())) {
                if (value.startsWith("/")) {
                    valueLabelMap.put(value, getLabelForResource(value, resolver, labelPropertyName));
                }
            }

            if (valueLabelMap.size() == 1) {
                return Json.createValue((String) valueLabelMap.values().toArray()[0]);
            }

            return createJsonArrayFromList(valueLabelMap.values());
        } catch (final RepositoryException ex) {
            // Really shouldn't happen
        }
        return null;
    }

    /**
     * Given a resource question definition, find the resource property that should be used as the label for
     * answers of that type, if it exists.
     * @param question the Question node
     * @return the property name as a string, or null if the question definition doesn't have the labelProperty
     *     defined or its value is blank
     */
    private String getLabelPropertyName(final Node question)
    {
        try {
            if (question != null
                && question.getProperty(PROP_RESOURCE_LABEL) != null
                && !StringUtils.isBlank(question.getProperty(PROP_RESOURCE_LABEL).getString())) {
                return question.getProperty(PROP_RESOURCE_LABEL).getString();
            }
        } catch (final RepositoryException ex) {
            // Really shouldn't happen
        }
        return null;
    }

    /**
     * Given a resource path as a String, extract the value indicated by labelPropertyName
     * from the resource node and return it as the label.
     */
    private String getLabelForResource(final String resourcePath, final ResourceResolver resolver,
        final String labelPropertyName)
    {
        try {
            // Determine the resource which is the answer to this question
            final Resource resource = resolver.getResource(resourcePath);
            if (resource != null) {
                final Node resourceNode = resource.adaptTo(Node.class);
                // Default the label to the resource name if the value for labelProperty is missing
                if (labelPropertyName != null
                    && resourceNode.getProperty(labelPropertyName) != null
                    && !StringUtils.isBlank(resourceNode.getProperty(labelPropertyName).getString())) {
                    return resourceNode.getProperty(labelPropertyName).getString();
                } else {
                    return resource.getName();
                }
            } else {
                // No resource found
                return resourcePath;
            }
        } catch (final RepositoryException ex) {
            // Shouldn't be happening
        }
        return resourcePath;
    }
}
