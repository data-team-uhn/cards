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
package ca.sickkids.ccm.lfs.dataentry.internal.serialize;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

/**
 * Class with shared processor functionality to get the human-readable question answer.
 *
 * @version $Id$
 */
public class SimpleAnswerLabelProcessor
{
    protected static final String PROP_VALUE = "value";

    protected static final String PROP_QUESTION = "question";

    protected static final String PROP_DISPLAYED_VALUE = "displayedValue";

    protected static final String PROP_LABEL = "label";

    protected static final String PROP_UNITS = "unitOfMeasurement";

    /**
     * Adds the displayedValue property to the answer JSON.
     *
     * @param node the node being serialized, may be other than the top resource
     * @param json the JSON representation computed by the previous processors, may be an empty object but must not be
     *            {@code null}
     * @param serializeNode a function that can be invoked to serialize a new node, receiving a Node as input, and
     *            returning a JSON representation
     *
     */
    public void addProperty(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.hasProperty(PROP_VALUE)) {
                final Node question = getQuestionNode(node);
                json.add(PROP_DISPLAYED_VALUE, getAnswerLabel(node, question));
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    /**
     * Gets the question node associated with the answer.
     *
     * @param node the node being serialized, may be other than the top resource
     *
     * @return the question Node object associated with this answer or null
     */
    public Node getQuestionNode(final Node node)
    {
        try {
            if (node.hasProperty(PROP_QUESTION)) {
                return node.getProperty(PROP_QUESTION).getNode();
            }
        } catch (final RepositoryException ex) {
            return null;
        }
        return null;
    }

    /**
     * Basic method to get get the answer label associated with the question.
     *
     * @param node the node being serialized, may be other than the top resource
     * @param question the question node that is an answer's child
     *
     * @return the question answer associated with this question
     */
    public String getAnswerLabel(final Node node, final Node question)
    {
        try {
            String value = node.getProperty(PROP_VALUE).getValue().toString();
            if (question != null && question.hasProperty(PROP_UNITS)) {
                value = value + " " + question.getProperty(PROP_UNITS).getString();
            }
            return value;
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return null;
    }
}
