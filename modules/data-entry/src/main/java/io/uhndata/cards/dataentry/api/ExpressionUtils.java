/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.dataentry.api;

import java.util.Map;
import java.util.Set;

import javax.jcr.Node;

/**
 * Utility class for parsing and evaluating an expression.
 *
 * @version $Id$
 * @since 0.9.1
 */
public interface ExpressionUtils
{
    /** Marker for the start of a variable. */
    String START_MARKER = "@{";

    /** Marker for the end of a variable. */
    String END_MARKER = "}";

    /** Marker for the default value to use for a variable when it doesn't have a value. */
    String DEFAULT_MARKER = ":-";

    /**
     * List the variable names used in the expression of a computed question.
     *
     * @param question the question node
     * @return a list of dependencies, may be empty
     */
    Set<String> getDependencies(Node question);

    /**
     * Fetch the expression of a computed question.
     *
     * @param question the question node
     * @return the expression, or an empty string if the passed node is not a computed question with an expression.
     */
    String getExpressionFromQuestion(Node question);

    /**
     * Evaluate a computed answer based on the other values present in the context.
     *
     * @param question the question node
     * @param values the other values in the form
     * @return a string representation of the result, may be {@code null} if the expression has unmet dependencies
     */
    String evaluate(Node question, Map<String, Object> values);
}
