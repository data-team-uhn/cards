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
package io.uhndata.cards.subjects.internal.serialize;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceTextProcessor;

/**
 * Plain text serializer that can process Subjects.
 *
 * @version $Id$
 */
@Component(service = ResourceTextProcessor.class)
public class SubjectToTextAdapterFactory extends AbstractSubjectToStringSerializer implements ResourceTextProcessor
{
    @Override
    public boolean canProcess(final Resource resource)
    {
        return resource.isResourceType("cards/Subject");
    }

    @Override
    public String serialize(Resource resource)
    {
        return toString(resource);
    }

    @Override
    void formatParent(String parent, StringBuilder result)
    {
        // Don't output the parent
    }

    @Override
    void formatSubjectTitle(String type, String identifier, StringBuilder result)
    {
        result.append(type).append(" ").append(identifier).append("\n");
    }

    @Override
    void formatCreationDate(String date, StringBuilder result)
    {
        result.append(date).append("\n");
    }

    @Override
    void formatResourceSeparator(final StringBuilder result)
    {
        result.append("\n\n\n\n");
    }

    @Override
    void formatForm(final Resource resource, final StringBuilder result)
    {
        result.append(resource.adaptTo(String.class));
    }
}
