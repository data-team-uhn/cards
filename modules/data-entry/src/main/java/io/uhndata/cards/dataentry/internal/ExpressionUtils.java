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
package io.uhndata.cards.dataentry.internal;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for parsing and evaluating an expression. TODO Turn into a public API.
 *
 * @version $Id$
 * @since 0.9.1
 */
public final class ExpressionUtils
{
    private static final String START_MARKER = "@{";

    private static final String END_MARKER = "}";

    private static final String DEFAULT_MARKER = ":-";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionUtils.class);

    private ExpressionUtils()
    {
        // Private constructor to avoid instantiation of an utility class
    }

    public static Set<String> getDependencies(final Node question)
    {
        return parseExpressionInputs(getExpressionFromQuestion(question), Collections.emptyMap()).getInputs().keySet();
    }

    static String getExpressionFromQuestion(final Node question)
    {
        try {
            return question.getProperty("expression").getString();
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to access computed question expression: {}", e.getMessage(), e);
            return "";
        }
    }

    static String evaluate(final Node question, final Map<String, Object> values)
    {
        try {
            String expression = getExpressionFromQuestion(question);
            ExpressionUtils.ParsedExpression parsedExpression = parseExpressionInputs(expression, values);
            if (parsedExpression.hasMissingValue()) {
                return null;
            }

            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");

            Bindings env = engine.createBindings();
            parsedExpression.getInputs().forEach((key, value) -> env.put(key, value));
            Object result = engine.eval("(function(){" + parsedExpression.getExpression() + "})()", env);
            return ValueFormatter.formatResult(result);
        } catch (ScriptException e) {
            LOGGER.warn("Evaluating the expression for question {} failed: {}", question,
                e.getMessage(), e);
        }
        return null;
    }

    private static ExpressionUtils.ParsedExpression parseExpressionInputs(final String expression,
        final Map<String, Object> values)
    {
        String expr = expression;
        Map<String, Object> inputs = new HashMap<>();
        int start = expr.indexOf(START_MARKER);
        int end = expr.indexOf(END_MARKER, start);
        boolean missingValue = false;

        while (start > -1 && end > -1) {
            int optionStart = expr.indexOf(DEFAULT_MARKER, start);
            boolean hasOption = optionStart > -1 && optionStart < end;

            String inputName;
            String defaultValue = null;

            if (hasOption) {
                inputName = expr.substring(start + START_MARKER.length(), optionStart);
                defaultValue = expr.substring(optionStart + DEFAULT_MARKER.length(), end);
            } else {
                inputName = expr.substring(start + START_MARKER.length(), end);
            }

            if (!inputs.containsKey(inputName)) {
                Object value = values.get(inputName);
                if (value == null) {
                    value = defaultValue;
                }
                if (value == null) {
                    missingValue = true;
                }
                inputs.put(inputName, value);
            }

            // Remove the start and end tags as well as the default option if provided, leaving just
            // the Javascript variable name
            expr = expr.substring(0, start) + expr.substring(start + START_MARKER.length(), hasOption
                ? optionStart : end) + expr.substring(end + END_MARKER.length());

            start = expr.indexOf(START_MARKER, (hasOption ? optionStart : end) - START_MARKER.length());
            end = expr.indexOf(END_MARKER, start);
        }
        return new ParsedExpression(inputs, expr, missingValue);
    }

    private static final class ParsedExpression
    {
        private final Map<String, Object> inputs;

        private final String expression;

        private final boolean missingValue;

        ParsedExpression(Map<String, Object> inputs, String expression, boolean missingValue)
        {
            this.inputs = inputs;
            this.expression = expression;
            this.missingValue = missingValue;
        }

        public Map<String, Object> getInputs()
        {
            return this.inputs;
        }

        public String getExpression()
        {
            return this.expression;
        }

        public boolean hasMissingValue()
        {
            return this.missingValue;
        }
    }

    private static final class ValueFormatter
    {
        static String formatResult(Object rawResult)
        {
            if (rawResult == null) {
                return null;
            }

            String formattedResult = String.valueOf(rawResult);

            if (rawResult instanceof Double || rawResult instanceof Float) {
                Number result = (Number) rawResult;
                if (result.doubleValue() == result.longValue()) {
                    formattedResult = String.valueOf(result.longValue());
                } else {
                    formattedResult = String.valueOf(result.doubleValue());
                }
            } else if (rawResult instanceof Date) {
                formattedResult = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .format(((Date) rawResult).toInstant().atZone(ZoneId.systemDefault()));
            } else if (rawResult instanceof Calendar) {
                Calendar result = (Calendar) rawResult;
                formattedResult = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .format(result.toInstant().atZone(result.getTimeZone().toZoneId()));
            }
            return formattedResult;
        }
    }
}
