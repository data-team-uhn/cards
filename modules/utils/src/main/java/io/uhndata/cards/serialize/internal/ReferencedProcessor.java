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
package io.uhndata.cards.serialize.internal;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Report if a node is referenced by adding a {@code @referenced=true|false} property. The name of this processor is
 * {@code referenced}.
 *
 * @version $Id$
 * @since 0.9.17
 */
@Component(immediate = true)
public class ReferencedProcessor implements ResourceJsonProcessor
{
    @Override
    public String getName()
    {
        return "referenced";
    }

    @Override
    public int getPriority()
    {
        return 10;
    }

    @Override
    public void leave(final Node node, final JsonObjectBuilder json,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            json.add("@referenced", node.getReferences().hasNext());
        } catch (RepositoryException e) {
            // Unlikely, and not critical, just make sure the serialization doesn't fail
        }
    }
}
