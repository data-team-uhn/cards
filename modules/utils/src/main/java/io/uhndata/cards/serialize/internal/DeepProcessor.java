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
import javax.json.JsonValue;

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Deep serialization of nodes: include all children in the serialization. The name of this processor is {@code deep}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DeepProcessor implements ResourceJsonProcessor
{
    @Override
    public String getName()
    {
        return "deep";
    }

    @Override
    public int getPriority()
    {
        return 10;
    }

    @Override
    public JsonValue processChild(final Node node, final Node child, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        // By default this should be the base serializer for children, but in case someone wants special serialization
        // for a type of children, leave the previous value unmodified. For example, to skip serializing files, a
        // processor with a lower priority may already serialize a simple download link.
        if (input != null) {
            return input;
        }
        return serializeNode.apply(child);
    }
}
