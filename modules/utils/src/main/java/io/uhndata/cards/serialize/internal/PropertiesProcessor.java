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
import javax.jcr.Property;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Serialize all node properties. The name of this processor is {@code properties}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class PropertiesProcessor implements ResourceJsonProcessor
{
    @Reference
    private FormUtils formUtils;

    @Override
    public String getName()
    {
        return "properties";
    }

    @Override
    public int getPriority()
    {
        return 0;
    }

    @Override
    public boolean isEnabledByDefault(Resource resource)
    {
        return true;
    }

    @Override
    public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        // By default this should be the base serializer for properties, but in case someone wants special serialization
        // for a property, leave the previous value unmodified. For example, to skip serializing extra large binary
        // content, a processor with a lower priority may already replace the content with a download link.
        if (input != null) {
            return input;
        }
        // The default is to simply serialize all properties
        return this.formUtils.serializeProperty(property);
    }
}
