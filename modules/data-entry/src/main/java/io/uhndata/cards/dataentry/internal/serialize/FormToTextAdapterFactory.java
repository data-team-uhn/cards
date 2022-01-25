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
import org.osgi.service.component.annotations.Component;

/**
 * AdapterFactory that converts forms to plain text.
 *
 * @version $Id$
 */
@Component(
    service = { AdapterFactory.class },
    property = { "adaptables=org.apache.sling.api.resource.Resource", "adapters=java.lang.String" })
public class FormToTextAdapterFactory
    extends AbstractFormToStringAdapterFactory
{
    @Override
    void formatMetadata(final String metadata, final StringBuilder result)
    {
        result.append(metadata.toUpperCase(Locale.ROOT)).append('\n');
    }

    @Override
    void formatSectionTitle(final String title, final StringBuilder result)
    {
        result.append(title.toUpperCase(Locale.ROOT)).append("\n\n");
    }

    @Override
    void formatSectionSeparator(final StringBuilder result)
    {
        result.append("---------------------------------------------").append("\n\n");
    }

    @Override
    void formatQuestion(final String question, final StringBuilder result)
    {
        result.append(question).append('\n');
    }

    @Override
    void formatEmptyAnswer(final StringBuilder result)
    {
        formatAnswer("-", result);
    }

    @Override
    void formatAnswer(final String answer, final StringBuilder result)
    {
        result.append("  ").append(answer.replaceAll("\n", "\n  ")).append('\n');
    }

    @Override
    void formatNote(final String note, final StringBuilder result)
    {
        result.append("\n  NOTES\n");
        formatAnswer(note, result);
    }

    @Override
    void formatPedigree(final String image, final StringBuilder result)
    {
        result.append("  Pedigree provided\n");
    }
}
