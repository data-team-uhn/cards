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
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Gets the human-readable answer for numeric range questions. A range is stored as a two-valued property
 * {@code [lower, upper]}, which the default label processor would turn into a two-element {@code displayedValue}
 * array. Since a range is a single logical value, this processor overrides that with one combined label of the form
 * {@code "lower - upper"} (using an em dash), appending the unit of measurement once when configured, e.g.
 * {@code "5 - 10 mmHg"}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class NumberRangeLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    /** Separator between the lower and upper limits, an em dash (U+2014) surrounded by spaces. */
    private static final String RANGE_SEPARATOR = " — ";

    @Override
    public String getDescription()
    {
        return "Get the human readable answer for numeric range questions, combining the lower and upper limits into "
            + "a single value, e.g. '5 — 10 mmHg' instead of the two separate values '5 mmHg' and '10 mmHg'.";
    }

    @Override
    public int getPriority()
    {
        // Label processors run in ascending priority order, each overwriting the displayedValue set by the previous
        // one. This must run after the other processors that also produce a label for a number answer node: the base
        // DefaultLabelProcessor (70) and AnswerOptionsLabelProcessor (75, which fires for any answer whose question
        // has options). 76 puts it last, so the combined range label is the one that wins.
        return 76;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (isNumberAnswer(node) && node.hasProperty(PROP_VALUE)) {
                final Node question = getQuestionNode(node);
                if (isRange(question)) {
                    addProperty(node, json, serializeNode);
                }
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    @Override
    public JsonValue getAnswerLabel(final Node node, final Node question)
    {
        try {
            final Property property = node.getProperty(PROP_VALUE);
            // A well-formed range has exactly two values; anything else is left to the default handling.
            if (property.isMultiple()) {
                final Value[] values = property.getValues();
                if (values.length == 2) {
                    final StringBuilder label = new StringBuilder()
                        .append(values[0].getString())
                        .append(RANGE_SEPARATOR)
                        .append(values[1].getString());
                    if (question != null && question.hasProperty(PROP_UNITS)) {
                        label.append(' ').append(question.getProperty(PROP_UNITS).getString());
                    }
                    return Json.createValue(label.toString());
                }
            }
        } catch (final RepositoryException ex) {
            // Really shouldn't happen
        }
        // Not a two-valued range: fall back to the default per-value label.
        return super.getAnswerLabel(node, question);
    }

    private boolean isNumberAnswer(final Node node) throws RepositoryException
    {
        return node.isNodeType("cards:LongAnswer")
            || node.isNodeType("cards:DoubleAnswer")
            || node.isNodeType("cards:DecimalAnswer");
    }

    private boolean isRange(final Node question) throws RepositoryException
    {
        return question != null && question.hasProperty("isRange")
            && question.getProperty("isRange").getBoolean();
    }
}
