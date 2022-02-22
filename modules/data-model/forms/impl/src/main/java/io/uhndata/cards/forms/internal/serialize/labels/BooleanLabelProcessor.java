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
package io.uhndata.cards.forms.internal.serialize.labels;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.commons.lang3.BooleanUtils;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Gets the human-readable question answer for boolean questions.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class BooleanLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    private static final String YES_LABEL = "yesLabel";

    private static final String NO_LABEL = "noLabel";

    private static final String UNKNOWN_LABEL = "unknownLabel";

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("cards:BooleanAnswer")) {
                addProperty(node, json, serializeNode);
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    @Override
    public JsonValue getAnswerLabel(final Node node, final Node question)
    {
        try {
            int rawValue = (int) node.getProperty(PROP_VALUE).getLong();
            String yesLabel = "Yes";
            String noLabel = "No";
            String unknownLabel = "Unknown";

            if (question != null) {
                if (question.hasProperty(YES_LABEL)) {
                    yesLabel = question.getProperty(YES_LABEL).getString();
                }
                if (question.hasProperty(NO_LABEL)) {
                    noLabel = question.getProperty(NO_LABEL).getString();
                }
                if (question.hasProperty(UNKNOWN_LABEL)) {
                    unknownLabel = question.getProperty(UNKNOWN_LABEL).getString();
                }
            }
            Boolean value = BooleanUtils.toBooleanObject(rawValue, 1, 0, -1);
            return Json.createValue(BooleanUtils.toString(value, yesLabel, noLabel, unknownLabel));
        } catch (final RepositoryException ex) {
            // Really shouldn't happen
        }
        return null;
    }
}
