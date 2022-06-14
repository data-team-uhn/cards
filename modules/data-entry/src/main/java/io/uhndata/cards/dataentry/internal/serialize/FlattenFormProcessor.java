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
package io.uhndata.cards.dataentry.internal.serialize;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

@Component(immediate = true)
public class FlattenFormProcessor implements ResourceJsonProcessor
{
    // Saves all children with type cards:Answer of root node
    private ThreadLocal<Map<String, JsonObject>> childrenJsons = ThreadLocal.withInitial(HashMap::new);
    // Saves all AnswerSection nodes ids with all children with type cards:Answer that belong them
    private ThreadLocal<Map<String, JsonObjectBuilder>> jsonSectionMap = ThreadLocal.withInitial(HashMap::new);

    @Override
    public String getName()
    {
        return "flatten";
    }

    @Override
    public int getPriority()
    {
        return 100;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        return resource.isResourceType("cards/Form");
    }

    // Each time this method is invoked, it is passed a JCR node and if that JCR node is a cards:Answer node, this node
    // is serialized and stored to a (this.childrenJsons) ThreadLocal object.
    // We need to change the serialized data representation only for cards:Answer and cards:AnswerSection nodes, which
    // is why nodes that are not cards:Answer or cards:AnswerSection, are simply passed-through.
    @Override
    public JsonValue processChild(Node node, Node child, JsonValue input, Function<Node, JsonValue> serializeNode)
    {
        if (input == null) {
            return null;
        }
        try {
            if (child.isNodeType("cards:Answer")) {
                this.childrenJsons.get().put(child.getIdentifier(), input.asJsonObject());
                return null;
            } else if (child.isNodeType("cards:AnswerSection")) {
                return null;
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
        return input;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        addAnswers(node, json);
    }

    @Override
    public void end(Resource resource)
    {
        this.childrenJsons.remove();
        this.jsonSectionMap.remove();
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private void addAnswers(final Node node, final JsonObjectBuilder json)
    {
        try {
            // The first time we encounter a section, we create a JsonArrayBuilder for it,
            // and add its children in the output.
            // While leaving the section we add all its children saved in Map getting them by section id.
            final NodeIterator children = node.getNodes();
            while (children.hasNext()) {
                final Node child = children.nextNode();
                final String childId = child.getIdentifier();
                final String idWithoutParent = childId.substring(childId.lastIndexOf("/") + 1);
                if (child.isNodeType("cards:Answer") && this.childrenJsons.get().containsKey(childId)) {
                    final JsonObject childJson = this.childrenJsons.get().get(childId);
                    if (node.isNodeType("cards:AnswerSection")) {
                        // if child node type is cards:Answer and its parent is cards:AnswerSection the method add the
                        // node to a separate appropriate to its AnswerSection jsonBuilder
                        if (this.jsonSectionMap.get().get(node.getIdentifier()) != null) {
                            this.jsonSectionMap.get().get(node.getIdentifier()).add(idWithoutParent, childJson);
                        } else {
                            JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                                    .add(idWithoutParent, childJson);
                            this.jsonSectionMap.get().put(node.getIdentifier(), jsonObjectBuilder);
                        }
                    } else {
                        // if child node type is cards:Answer and its parent is cards:Form the method simply add the
                        // node to output stream
                        json.add(idWithoutParent, childJson);
                    }
                    // in this case the child node is either added to final output stream or added to a separate
                    // jsonBuilder and will be added to output stream while leaving the root AnswerSection, so there is
                    // no need to save the child in childrenJsons
                    this.childrenJsons.get().remove(childId);
                } else if (child.isNodeType("cards:AnswerSection") && this.jsonSectionMap.get().containsKey(childId)) {
                    if (node.isNodeType("cards:AnswerSection")) {
                        // if child node type is cards:AnswerSection and its parent is cards:AnswerSection the method
                        // add all its children nodes to a separate appropriate to its AnswerSection jsonBuilder
                        if (this.jsonSectionMap.get().get(node.getIdentifier()) != null) {
                            this.jsonSectionMap.get().get(node.getIdentifier())
                                    .addAll(this.jsonSectionMap.get().get(childId));
                        } else {
                            this.jsonSectionMap.get().put(node.getIdentifier(), this.jsonSectionMap.get().get(childId));
                        }
                    } else {
                        // if child node is cards:AnswerSection and its parent is not cards:AnswerSection then it is a
                        // root AnswerSection and the method add to output stream all the cards:Answers that were saved
                        // under root cards:AnswerSection. There is no need to save the jsonBuilder
                        json.addAll(this.jsonSectionMap.get().get(childId));
                        this.jsonSectionMap.get().remove(childId);
                    }
                }
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
    }

}
