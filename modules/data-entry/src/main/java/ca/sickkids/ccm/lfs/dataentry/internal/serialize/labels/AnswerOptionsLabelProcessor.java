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
package ca.sickkids.ccm.lfs.dataentry.internal.serialize.labels;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.osgi.service.component.annotations.Component;

import ca.sickkids.ccm.lfs.serialize.spi.ResourceJsonProcessor;

/**
 * Gets the human-readable question answer for text questions with options.
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
            if (node.isNodeType("lfs:TextAnswer")) {
                addProperty(node, json, serializeNode);
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    @Override
    public JsonValue getAnswerLabel(final Node node, final Node question)
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

            if (question == null) {
                return createJsonArrayFromList(propsMap.values());
            }

            processOptions(question, propsMap);

            if (propsMap.size() == 1) {
                return Json.createValue((String) propsMap.values().toArray()[0]);
            }

            return createJsonArrayFromList(propsMap.values());
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
                    if (!"lfs:AnswerOption".equals(optionNode.getPrimaryNodeType().getName())
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
