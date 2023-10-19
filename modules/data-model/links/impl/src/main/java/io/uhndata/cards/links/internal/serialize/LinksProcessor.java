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
package io.uhndata.cards.links.internal.serialize;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.links.api.Links;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Simplify Link serialization by including only the type of link and the path to the linked resource. The name of this
 * processor is {@code links} and it is enabled by default.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class LinksProcessor implements ResourceJsonProcessor
{
    @Reference
    private Links links;

    @Override
    public String getName()
    {
        return "links";
    }

    @Override
    public int getPriority()
    {
        return 5;
    }

    @Override
    public boolean isEnabledByDefault(Resource resource)
    {
        return true;
    }

    @Override
    public JsonValue processChild(final Node node, final Node child, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (child.isNodeType("cards:Links")) {
                JsonArrayBuilder result = Json.createArrayBuilder();
                this.links.getLinks(node).forEach(link -> {
                    result.add(link.toJson());
                });
                return result.build();
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }
}
