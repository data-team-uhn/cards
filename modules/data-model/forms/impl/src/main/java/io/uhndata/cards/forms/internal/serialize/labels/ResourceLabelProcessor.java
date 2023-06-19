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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Fills in the human-readable question answer for resource questions with the value of the resource node property
 * specified in the question definition by `labelProperty`.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class ResourceLabelProcessor extends AbstractResourceLabelProcessor implements ResourceJsonProcessor
{
    /** Provides access to resources. */
    @Reference
    private ThreadResourceResolverProvider rrp;

    @Override
    public int getPriority()
    {
        return 90;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("cards:ResourceAnswer")) {
                addProperty(node, json, serializeNode);
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
    }

    @Override
    public JsonValue getAnswerLabel(final Node node, final Node question)
    {
        try {
            // Find the property of the resource that should be used as the label
            String labelPropertyName = getLabelPropertyName(question);
            ResourceResolver resolver = this.rrp.getThreadResourceResolver();
            if (resolver == null) {
                return null;
            }
            // The value property is either one resource path or an array of resource paths
            Property valueProperty = node.getProperty(PROP_VALUE);
            if (valueProperty.isMultiple()) {
                List<String> labels = new ArrayList<>();
                for (Value item : valueProperty.getValues()) {
                    if (StringUtils.isNotBlank(item.getString())) {
                        labels.add(getLabelForResource(item.getString(), resolver, labelPropertyName));
                    }
                }
                return createJsonArrayFromList(labels);
            } else if (StringUtils.isNotBlank(valueProperty.getString())) {
                return Json.createValue(getLabelForResource(valueProperty.getString(), resolver, labelPropertyName));
            }
        } catch (final RepositoryException ex) {
            // Shouldn't be happening
        }
        return null;
    }
}
