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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Flattens a form so that all the answers, regardless of any sections and subsections they may normally be in, are
 * listed in the top level of the JSON. The name of this processor is {@code flatten}. It is incompatible with the
 * {@code bare} processor.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class FlattenFormProcessor implements ResourceJsonProcessor
{
    /**
     * Saves all JSONs of nodes with type {@code cards:Answer}. This maps from the node's name to the JSON. A linked map
     * preserves the order of the answers, as encountered in a top-to-bottom DFS pass through the form.
     */
    private ThreadLocal<Map<String, JsonObject>> childrenJsons = ThreadLocal.withInitial(LinkedHashMap::new);

    @Override
    public String getName()
    {
        return "flatten";
    }

    @Override
    public int getPriority()
    {
        // This should run last, after the JSON is finalized, since it prevents all following processors from seeing the
        // JSON
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
        // This removes the serialization of answers and sections from their original place, storing the serialization
        // of answers to be added later in the root of the JSON.
        if (input == null) {
            // No serialization coming from other processors, nothing to do
            return null;
        }
        try {
            if (child.isNodeType("cards:Answer")) {
                // Answers are stored for later, and discarded for now
                this.childrenJsons.get().put(child.getName(), input.asJsonObject());
                return null;
            } else if (child.isNodeType("cards:AnswerSection")) {
                // Answer sections are completely discarded
                return null;
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
        // All other nodes (except Answer and AnswerSection) are left as is
        return input;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("cards:Form")) {
                // Add the answers to the root node
                this.childrenJsons.get().entrySet().stream()
                    .forEach(entry -> json.add(entry.getKey(), entry.getValue()));
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
    }

    @Override
    public void end(Resource resource)
    {
        // Cleanup the state
        this.childrenJsons.remove();
    }
}
