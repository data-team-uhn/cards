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
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Gets the human-readable question answer for text and number questions with options.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class AnswerOptionsLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("cards:Answer") && hasAnswerOption(getQuestionNode(node))) {
                addProperty(node, json, serializeNode);
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    /**
     * Returns true if the input node has an answer option.
     *
     * @param question a question Node object to check
     * @return True if there is at least one answer option
     */
    private boolean hasAnswerOption(final Node question)
    {
        try {
            if (question == null) {
                return false;
            }
            NodeIterator childNodes = question.getNodes();
            if (childNodes.getSize() <= 0) {
                return false;
            }

            while (childNodes.hasNext()) {
                Node optionNode = childNodes.nextNode();
                if ("cards:AnswerOption".equals(optionNode.getPrimaryNodeType().getName())
                    && optionNode.hasProperty(PROP_VALUE)) {
                    return true;
                }
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
        return false;
    }

    @Override
    public JsonValue getAnswerLabel(final Node node, final Node question)
    {
        try {
            final Map<String, String> propsMap = new LinkedHashMap<>();
            final Property nodeProp = node.getProperty(PROP_VALUE);
            final boolean multivalued = nodeProp.isMultiple();
            if (multivalued) {
                for (Value value : nodeProp.getValues()) {
                    propsMap.put(value.getString(), value.getString());
                }
            } else {
                propsMap.put(nodeProp.getString(), nodeProp.getString());
            }

            if (question == null) {
                return createJsonValue(propsMap.values(), multivalued);
            }

            processOptions(question, propsMap);

            return createJsonValue(propsMap.values(), multivalued);
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return null;
    }

    protected void processOptions(final Node question, final Map<String, String> propsMap)
    {
        try {
            NodeIterator childNodes = question.getNodes();
            if (childNodes.getSize() > 0) {
                int count = 0;
                while (childNodes.hasNext()) {
                    Node optionNode = childNodes.nextNode();
                    if (!"cards:AnswerOption".equals(optionNode.getPrimaryNodeType().getName())
                        || !optionNode.hasProperty(PROP_VALUE)) {
                        continue;
                    }

                    String option = optionNode.getProperty(PROP_VALUE).getString();
                    if (propsMap.containsKey(option) && optionNode.hasProperty(PROP_LABEL)) {
                        propsMap.put(option, optionNode.getProperty(PROP_LABEL).getString());
                        count++;
                    }

                    if (propsMap.size() == count) {
                        break;
                    }
                }
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }
}
