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
package io.uhndata.cards.serialize;

import java.util.List;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.uhndata.cards.serialize.spi.ResourceTextProcessor;

/**
 * AdapterFactory that converts Apache Sling resources to plain text. This is just a shell, the actual serialization is
 * provided by implementations of the {@link ResourceTextProcessor} service.
 *
 * @version $Id$
 */
@Component(
    service = { AdapterFactory.class },
    property = { "adaptables=org.apache.sling.api.resource.Resource", "adapters=java.lang.String" })
public class ResourceToTextAdapterFactory
    implements AdapterFactory
{
    /** A list of all available processors. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<ResourceTextProcessor> allProcessors;

    @Override
    public <A> A getAdapter(final Object adaptable, final Class<A> type)
    {
        if (adaptable == null || adaptable.getClass().getName()
            .equals("org.apache.sling.jcr.resource.internal.helper.jcr.JcrPropertyResource")) {
            return null;
        }
        final Resource resource = (Resource) adaptable;
        final String result = this.allProcessors.stream()
            .filter(p -> p.canProcess(resource))
            .findFirst()
            .map(p -> p.serialize(resource))
            .orElse(resource.getPath());

        return type.cast(result);
    }
}
