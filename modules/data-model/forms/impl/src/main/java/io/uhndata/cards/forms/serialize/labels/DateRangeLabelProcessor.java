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
package io.uhndata.cards.forms.serialize.labels;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Gets the human-readable answer for date interval (range) questions. An interval is stored as a two-valued property
 * {@code [start, end]}, which {@link DateLabelProcessor} would format into a two-element {@code displayedValue} array.
 * Since an interval is a single logical value, this processor overrides that with one combined label of the form
 * {@code "start - end"} (using an em dash), e.g. {@code "Jan 1, 2020 - Jan 5, 2020"}. The individual dates are
 * formatted by reusing {@link DateLabelProcessor#getAnswerLabel(Node, Node)}, so the question's {@code dateFormat}
 * (and the {@code yyyy} special case) are honored identically to single dates.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DateRangeLabelProcessor extends DateLabelProcessor implements ResourceJsonProcessor
{
    /** The value of a question's {@code type} property that marks it as a date interval. */
    private static final String INTERVAL_TYPE = "interval";

    private static final String PROP_TYPE = "type";

    /** Separator between the start and end dates, an em dash (U+2014) surrounded by spaces. */
    private static final String RANGE_SEPARATOR = " — ";

    @Override
    public String getDescription()
    {
        return "Get the human readable answer for date interval questions, combining the start and end dates into a "
            + "single value, e.g. 'Jan 1, 2020 — Jan 5, 2020' instead of the two separate dates.";
    }

    @Override
    public int getPriority()
    {
        // Runs just after DateLabelProcessor (75), so its combined label overwrites the two-element array.
        return 76;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("cards:DateAnswer") && isInterval(getQuestionNode(node))) {
                addProperty(node, json, serializeNode);
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    @Override
    public JsonValue getAnswerLabel(final Node node, final Node question)
    {
        final JsonValue label = super.getAnswerLabel(node, question);
        // A well-formed interval formats to exactly two dates; anything else is left to the default handling.
        if (label != null && label.getValueType() == JsonValue.ValueType.ARRAY) {
            final JsonArray dates = label.asJsonArray();
            if (dates.size() == 2) {
                return Json.createValue(dates.getString(0) + RANGE_SEPARATOR + dates.getString(1));
            }
        }
        return label;
    }

    private boolean isInterval(final Node question) throws RepositoryException
    {
        return question != null && question.hasProperty(PROP_TYPE)
            && INTERVAL_TYPE.equals(question.getProperty(PROP_TYPE).getString());
    }
}
