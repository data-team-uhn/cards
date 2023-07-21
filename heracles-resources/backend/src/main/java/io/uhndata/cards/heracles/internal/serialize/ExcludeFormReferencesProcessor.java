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
package io.uhndata.cards.heracles.internal.serialize;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Prevent all FormReferences from being exported. The name of this processor is
 * {@code excludeFormReferences}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class ExcludeFormReferencesProcessor implements ResourceJsonProcessor
{
    @Override
    public String getName()
    {
        return "excludeFormReferences";
    }

    @Override
    public int getPriority()
    {
        return 100;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        // This only runs on FormReferences
        return resource.isResourceType("cards/Form");
    }

    @Override
    public JsonValue processChild(final Node node, final Node child, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (child.isNodeType("cards:FormReferences"))
            {
                // Do not export form reference links
                return null;
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
        return input;
    }
}
