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

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Gets the file name of the file question answer.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class FileLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("cards:FileAnswer")) {
                addProperty(node, json, serializeNode);
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    @Override
    public void addProperty(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.hasProperty("value")) {
                json.add(PROP_DISPLAYED_VALUE, getAnswerLabel(node, null));
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    @Override
    public JsonValue getAnswerLabel(final Node node, final Node question)
    {
        try {
            String fullPath = node.getPath() + "/";
            Property property = node.getProperty("value");
            if (property.isMultiple()) {
                List<String> names = new ArrayList<>();
                for (Value item : property.getValues()) {
                    String fileName = item.getString().replace(fullPath, "");
                    names.add(fileName);
                }
                return createJsonArrayFromList(names);
            } else {
                String fileName = property.getValue().getString().replace(fullPath, "");
                return Json.createValue(fileName);
            }
        } catch (final RepositoryException ex) {
            // Really shouldn't happen
        }
        return null;
    }
}
