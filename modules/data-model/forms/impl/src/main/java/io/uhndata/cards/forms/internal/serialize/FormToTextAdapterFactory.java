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
package io.uhndata.cards.forms.internal.serialize;

import java.util.Locale;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceTextProcessor;

/**
 * Plain text serializer that can process Forms.
 *
 * @version $Id$
 */
@Component(service = ResourceTextProcessor.class)
public class FormToTextAdapterFactory extends AbstractFormToStringSerializer implements ResourceTextProcessor
{
    @Override
    public boolean canProcess(final Resource resource)
    {
        return resource.isResourceType("cards/Form");
    }

    @Override
    public String serialize(Resource resource)
    {
        return toString(resource);
    }

    @Override
    void formatSubject(final String subject, final StringBuilder result)
    {
        result.append(subject).append('\n');
    }

    @Override
    void formatTitle(final String title, final StringBuilder result)
    {
        result.append(title.toUpperCase(Locale.ROOT)).append('\n');
    }

    @Override
    void formatDate(final String date, final StringBuilder result)
    {
        result.append(date).append('\n');
    }

    @Override
    void formatMetadataSeparator(final StringBuilder result)
    {
        // Don't output additional separation
    }

    @Override
    void formatSectionTitle(final String title, final StringBuilder result)
    {
        result.append('\n').append(title.toUpperCase(Locale.ROOT)).append('\n');
    }

    @Override
    void formatSectionSeparator(final StringBuilder result)
    {
        result.append('\n').append("---------------------------------------------").append('\n');
    }

    @Override
    void formatQuestion(final String question, final StringBuilder result)
    {
        result.append('\n').append(question);
    }

    @Override
    void formatEmptyAnswer(final StringBuilder result)
    {
        formatAnswer("-", result);
    }

    @Override
    void formatAnswer(final String answer, final StringBuilder result)
    {
        result.append('\n').append("  ").append(answer.replaceAll("\n", "\n  ")).append('\n');
    }

    @Override
    void formatNote(final String note, final StringBuilder result)
    {
        result.append("\n  NOTES");
        formatAnswer(note, result);
    }

    @Override
    void formatPedigree(final String image, final StringBuilder result)
    {
        formatAnswer("Pedigree provided", result);
    }
}
