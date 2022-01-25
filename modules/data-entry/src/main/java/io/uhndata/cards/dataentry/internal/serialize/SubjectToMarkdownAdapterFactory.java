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
package io.uhndata.cards.dataentry.internal.serialize;

import java.util.Locale;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

/**
 * AdapterFactory that converts Subject to markdown.
 *
 * @version $Id$
 */
@Component(
    service = { AdapterFactory.class },
    property = { "adaptables=org.apache.sling.api.resource.Resource", "adapters=java.lang.CharSequence" })
public class SubjectToMarkdownAdapterFactory
    extends AbstractSubjectToStringAdapterFactory
{

    private static final String MD_LINE_END = "  \n";

    @Override
    void formatMetadata(final String metadata, final StringBuilder result)
    {
        result.append(metadata.toUpperCase(Locale.ROOT)).append(MD_LINE_END).append('\n');
    }

    @Override
    void formatType(final String type, final StringBuilder result)
    {
        result.append("\n\n### ").append(type).append(MD_LINE_END).append('\n');
    }

    @Override
    void formatForm(final Resource resource, final StringBuilder result)
    {
        result.append("\n\n### ").append(resource.adaptTo(CharSequence.class)).append(MD_LINE_END).append('\n');
    }
}
