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
    private ThreadLocal<Map<String, JsonObject>> childrenJsons = ThreadLocal.withInitial(HashMap::new);
    private ThreadLocal<JsonObjectBuilder> jsonSection = ThreadLocal.withInitial(Json::createObjectBuilder);
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
        this.jsonSection.remove();
        this.jsonSectionMap.remove();
    }

    private void addAnswers(final Node node, final JsonObjectBuilder json)
    {
        try {
            final NodeIterator children = node.getNodes();
            while (children.hasNext()) {
                final Node child = children.nextNode();
                final String childId = child.getIdentifier();
                if (child.isNodeType("cards:Answer") && this.childrenJsons.get().containsKey(childId)) {
                    final String answerId = childId.substring(childId.length() - 36);
                    final JsonObject childJson = this.childrenJsons.get().get(childId);
                    if (child.getParent().isNodeType("cards:AnswerSection")) {
                        this.jsonSection.get().add(answerId, childJson);
                        this.jsonSectionMap.get().put(child.getParent().getIdentifier(), this.jsonSection.get());
                    } else {
                        json.add(answerId, childJson);
                    }
                    this.childrenJsons.get().remove(childId);
                } else if (child.isNodeType("cards:AnswerSection")) {
                    json.addAll(this.jsonSectionMap.get().get(child.getIdentifier()));
                    this.jsonSectionMap.get().remove(child.getIdentifier());
                }
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
    }

}
