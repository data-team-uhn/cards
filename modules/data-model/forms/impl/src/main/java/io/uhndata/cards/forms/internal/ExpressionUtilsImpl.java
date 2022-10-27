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
package io.uhndata.cards.forms.internal;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.jackrabbit.oak.api.Type;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.ExpressionUtils;

@Component(service = ExpressionUtils.class)
public final class ExpressionUtilsImpl implements ExpressionUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionUtilsImpl.class);

    @Reference
    private ScriptEngineManager manager;

    @Override
    public Set<String> getDependencies(final Node question)
    {
        return parseExpressionInputs(
            getExpressionFromQuestion(question), Collections.emptyMap()).getQuestions().keySet();
    }

    @Override
    public String getExpressionFromQuestion(final Node question)
    {
        try {
            return question.getProperty("expression").getString();
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to access computed question expression: {}", e.getMessage(), e);
            return "";
        }
    }

    @Override
    public Object evaluate(final Node question, final Map<String, Object> values, final Type<?> type)
    {
        try {
            String expression = getExpressionFromQuestion(question);
            ExpressionUtilsImpl.ParsedExpression parsedExpression = parseExpressionInputs(expression, values);
            if (parsedExpression.hasMissingValue()) {
                return null;
            }

            ScriptEngine engine = this.manager.getEngineByName("JavaScript");

            Bindings env = engine.createBindings();
            parsedExpression.getQuestions().forEach((key, value) -> env.put(value.getArgument(), value.getValue()));
            Object result = engine.eval("(function(){" + parsedExpression.getExpression() + "})()", env);
            return ValueFormatter.formatResult(result, type);
        } catch (ScriptException e) {
            LOGGER.warn("Evaluating the expression for question {} failed: {}", question,
                e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Set<String> getQuestionsNames(Node question)
    {
        return expressionInputs(getExpressionFromQuestion(question));
    }

    private Set<String> expressionInputs(final String expression)
    {
        String expr = expression;

        Set<String> questionNames = new HashSet<>();

        int start = expr.indexOf(START_MARKER);
        int end = expr.indexOf(END_MARKER, start);

        while (start > -1 && end > -1) {
            int defaultStart = expr.indexOf(DEFAULT_MARKER, start);
            boolean hasDefault = defaultStart > -1 && defaultStart < end;

            String questionName;
            if (hasDefault) {
                questionName = expr.substring(start + START_MARKER.length(), defaultStart);
            } else {
                questionName = expr.substring(start + START_MARKER.length(), end);
            }

            questionNames.add(questionName);

            // Remove the start and end tags
            expr = expr.substring(0, start) + questionName + expr.substring(end + END_MARKER.length());

            start = expr.indexOf(START_MARKER);
            end = expr.indexOf(END_MARKER, start);
        }
        return questionNames;
    }

    private ExpressionUtilsImpl.ParsedExpression parseExpressionInputs(final String expression,
        final Map<String, Object> values)
    {
        String expr = expression;

        Boolean missingValue = false;
        Map<String, ExpressionArgument> questions = new HashMap<>();

        int start = expr.indexOf(START_MARKER);
        int end = expr.indexOf(END_MARKER, start);
        // For each argument in the expression, parse the question name and default value if present.
        // To prevent question names from breaking the evaluating funtion, replace them with a default
        // argument name in the evaluated expression.
        while (start > -1 && end > -1) {
            int defaultStart = expr.indexOf(DEFAULT_MARKER, start);
            boolean hasDefault = defaultStart > -1 && defaultStart < end;

            // Parse out the question name and default value if provided
            String questionName;
            String defaultValue = null;
            if (hasDefault) {
                questionName = expr.substring(start + START_MARKER.length(), defaultStart);
                defaultValue = expr.substring(defaultStart + DEFAULT_MARKER.length(), end);
            } else {
                questionName = expr.substring(start + START_MARKER.length(), end);
            }

            // Insert this question into the list of arguments
            if (!questions.containsKey(questionName)) {
                ExpressionArgument arg = new ExpressionArgument("arg" + questions.size(),
                    getQuestionValue(questionName, values, defaultValue));
                if (arg.getValue() == null) {
                    missingValue = true;
                }
                questions.put(questionName, arg);
            }

            // Remove the start and end tags and replace the question name with the argument name for this question
            expr = expr.substring(0, start) + questions.get(questionName).getArgument()
                + expr.substring(end + END_MARKER.length());

            start = expr.indexOf(START_MARKER);
            end = expr.indexOf(END_MARKER, start);
        }
        return new ParsedExpression(questions, expr, missingValue);
    }

    private Object getQuestionValue(String questionName, final Map<String, Object> values, String defaultValue)
    {
        Object value = values.get(questionName);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    private static final class ParsedExpression
    {
        private final Map<String, ExpressionArgument> questions;

        private final String expression;

        private final boolean missingValue;

        ParsedExpression(Map<String, ExpressionArgument> questions, String expression, boolean missingValue)
        {
            this.questions = questions;
            this.expression = expression;
            this.missingValue = missingValue;
        }

        public Map<String, ExpressionArgument> getQuestions()
        {
            return this.questions;
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

    private static final class ExpressionArgument
    {
        private final String argument;

        private final Object value;

        ExpressionArgument(String argument, Object value)
        {
            this.argument = argument;
            this.value = value;
        }

        public String getArgument()
        {
            return this.argument;
        }

        public Object getValue()
        {
            return this.value;
        }
    }

    private static final class ValueFormatter
    {
        static Object formatResult(final Object rawResult, final Type<?> type)
        {
            if (rawResult == null || (rawResult instanceof String && "null".equals(rawResult))) {
                return null;
            }

            if (type == Type.LONG) {
                return formatToLong(rawResult);
            } else if (type == Type.DOUBLE) {
                return formatToDouble(rawResult);
            } else if (type == Type.DECIMAL) {
                return formatToDecimal(rawResult);
            } else {
                return formatToString(rawResult);
            }
        }

        static Long formatToLong(final Object rawResult)
        {
            if (rawResult instanceof String) {
                return Long.valueOf((String) rawResult);
            } else if (rawResult instanceof Long) {
                return (Long) rawResult;
            } else if (rawResult instanceof Double) {
                return ((Double) rawResult).longValue();
            } else {
                LOGGER.error("Could not parse Long from " + rawResult.getClass().toString());
                return null;
            }
        }

        static Double formatToDouble(final Object rawResult)
        {
            if (rawResult instanceof String) {
                return Double.valueOf((String) rawResult);
            } else if (rawResult instanceof Double) {
                return (Double) rawResult;
            } else {
                LOGGER.error("Could not parse Double from " + rawResult.getClass().toString());
                return null;
            }
        }

        static BigDecimal formatToDecimal(final Object rawResult)
        {
            if (rawResult instanceof String) {
                return new BigDecimal((String) rawResult);
            } else if (rawResult instanceof BigDecimal) {
                return (BigDecimal) rawResult;
            } else if (rawResult instanceof Double) {
                return BigDecimal.valueOf((Double) rawResult);
            } else {
                LOGGER.error("Could not parse BigDecimal from " + rawResult.getClass().toString());
                return null;
            }
        }

        static String formatToString(final Object rawResult)
        {
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
