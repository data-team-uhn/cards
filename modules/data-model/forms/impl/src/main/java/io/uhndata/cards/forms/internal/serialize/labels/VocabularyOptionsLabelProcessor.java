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
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Adds a label to Answer Option nodes in a questionnaire's JSON.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class VocabularyOptionsLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    @Override
    public boolean canProcess(Resource resource)
    {
        return resource.isResourceType("cards/Form") || resource.isResourceType("cards/Questionnaire");
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("cards:AnswerOption")
                && "vocabulary".equals(node.getParent().getProperty("dataType").getString())
                && !node.hasProperty(PROP_LABEL)) {
                json.add(PROP_LABEL, getAnswerLabel(node));
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    /**
     * Basic method to get the answer label associated with the vocabulary answer option.
     *
     * @param node the node being serialized, may be other than the top resource
     * @return the question answer associated with this question
     */
    private JsonValue getAnswerLabel(final Node node)
    {
        try {
            Map<String, String> propsMap = new LinkedHashMap<>();

            Property nodeProp = node.getProperty(PROP_VALUE);
            if (nodeProp.isMultiple()) {
                for (Value value : nodeProp.getValues()) {
                    propsMap.put(value.getString(), value.getString());
                }
            } else {
                propsMap.put(nodeProp.getString(), nodeProp.getString());
            }

            for (String value : propsMap.keySet()) {
                if (value.startsWith("/Vocabularies/") && node.getSession().nodeExists(value)) {
                    Node term = node.getSession().getNode(value);
                    if (term.hasProperty(PROP_LABEL)) {
                        String label = term.getProperty(PROP_LABEL).getValue().toString();
                        propsMap.put(value, label);
                    }
                }
            }

            return createJsonValue(propsMap.values(), nodeProp.isMultiple());
        } catch (final RepositoryException ex) {
            // Really shouldn't happen
        }
        return null;
    }
}
