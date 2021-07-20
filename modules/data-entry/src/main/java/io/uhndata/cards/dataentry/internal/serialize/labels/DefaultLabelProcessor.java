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

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Gets the question answer for number questions.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DefaultLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    private static final String DEFAULT_RESOURCE_TYPE = "cards:Answer";

    @Override
    public int getPriority()
    {
        // Unlike all other label processors, this has a lower priority, since it provides a default label that can
        // later be customized for specific types of answers
        return 70;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType(DEFAULT_RESOURCE_TYPE)) {
                addProperty(node, json, serializeNode);
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }
}
