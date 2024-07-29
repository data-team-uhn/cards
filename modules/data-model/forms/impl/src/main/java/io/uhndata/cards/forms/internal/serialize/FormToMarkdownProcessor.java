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

import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceMarkdownProcessor;

/**
 * Markdown serializer that can process Forms.
 *
 * @version $Id$
 */
@Component(service = ResourceMarkdownProcessor.class)
public class FormToMarkdownProcessor extends AbstractFormToStringSerializer implements ResourceMarkdownProcessor
{
    private static final String MD_LINE_BREAK = "  \n";

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
        // Don't output the subject
    }

    @Override
    void formatTitle(final String title, final StringBuilder result)
    {
        result.append("# ").append(title).append('\n');
    }

    @Override
    void formatDate(final String date, final StringBuilder result)
    {
        // Don't output the subject
    }

    @Override
    void formatMetadataSeparator(final StringBuilder result)
    {
        // Don't output additional separation
    }

    @Override
    void formatSectionTitle(final String title, final StringBuilder result)
    {
        result.append("\n### ").append(title).append('\n');
    }

    @Override
    void formatSectionSeparator(final StringBuilder result)
    {
        result.append('\n').append("----").append('\n');
    }

    @Override
    void formatQuestion(final String question, final StringBuilder result)
    {
        result.append("\n**").append(question).append("**  ");
    }

    @Override
    void formatEmptyAnswer(final StringBuilder result)
    {
        formatAnswer("—", result);
    }

    @Override
    void formatAnswer(final String answer, final StringBuilder result)
    {
        result.append('\n').append(answer).append('\n');
    }

    @Override
    void formatNote(final String note, final StringBuilder result)
    {
        result.append("\n**Notes**").append(MD_LINE_BREAK).append(note.replaceAll("\n", MD_LINE_BREAK)).append('\n');
    }

    @Override
    void formatInformation(final String text, final StringBuilder result)
    {
        result.append("\n");
        Stream.of(StringUtils.split(text, "\r\n"))
            .forEachOrdered(line -> result.append("> ").append(line).append("\n"));
    }

    @Override
    void formatPedigree(final String image, final StringBuilder result)
    {
        // TODO:
        // Wrap the SVG in a div and size the div to fit in the viewport
        // We're assuming the h/w ratio of the pedigree does not exceed the h/w ratio of the viewport
        // The wrapper will have:
        // * width = 90%
        // * height = 100vw * .9 * svg.height / svg.width
        // double imgHWRatio = imgH / imgW;
        double imgHWRatio = 1;
        result.append("<div style='")
            .append("display: inline-block; ")
            .append("width: 90%; ")
            .append("height: calc(100vw * ").append(0.9 * imgHWRatio).append(");")
            .append("overflow: hidden;")
            .append("'>")
            .append("<svg style='width: 100%' ")
            .append(image.substring(4))
            .append("</div>")
            .append('\n');
    }
}
