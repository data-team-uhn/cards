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

    private ExpressionUtilsImpl.ParsedExpression parseExpressionInputs(final String expression,
        final Map<String, Object> values)
    {
        ExpressionParser parser = new ExpressionParser(expression, values);
        return parser.parse();
    }

    private final class ExpressionParser
    {
        private String expression;
        private final Map<String, Object> questionValues;
        private boolean missingValue;
        private Map<String, ExpressionArgument> questions = new HashMap<>();

        // Next argument details
        private boolean isArrayArgument;
        private String startMarker;
        private String endMarker;
        private int start;
        private int end;


        ExpressionParser(String expression, final Map<String, Object> questionValues)
        {
            this.expression = expression;
            this.questionValues = questionValues;

            scanNextArgument();
        }

        private void scanNextArgument()
        {
            this.isArrayArgument = isNextArgumentMultivalued(this.expression);
            this.startMarker = this.isArrayArgument ? START_MARKER_ARRAY : START_MARKER_SINGLE;
            this.endMarker = this.isArrayArgument ? END_MARKER_ARRAY : END_MARKER_SINGLE;
            this.start = this.expression.indexOf(this.startMarker);
            this.end = this.expression.indexOf(this.endMarker);
        }

        public ParsedExpression parse()
        {
            while (hasNextArgument()) {
                parseNextArgument();
            }
            return new ParsedExpression(this.questions, this.expression, this.missingValue);

        }

        private boolean hasNextArgument()
        {
            return this.start > -1 && this.end > -1;
        }

        private boolean isOptionalQuestion(boolean hasDefault, int defaultStart)
        {
            int endIndex = hasDefault ? defaultStart : this.end;
            String potentialOptionalString = this.expression.substring(endIndex - OPTIONAL_MARKER.length(), endIndex);

            return OPTIONAL_MARKER.equals(potentialOptionalString);
        }

        private String getQuestionName(boolean hasDefault, int defaultStart, boolean isOptional)
        {
            String questionName = this.expression.substring(this.start + this.startMarker.length(),
                hasDefault ? defaultStart : this.end);
            if (isOptional) {
                questionName = questionName.substring(0, questionName.length() - OPTIONAL_MARKER.length());
            }
            return questionName;
        }

        private String getDefaultValue(boolean hasDefault, int defaultStart)
        {
            return hasDefault ? this.expression.substring(defaultStart + DEFAULT_MARKER.length(), this.end) : null;
        }

        private void parseNextArgument()
        {
            // Parse the question name and default value if present.
            int defaultStart = this.expression.indexOf(DEFAULT_MARKER, this.start);
            boolean hasDefault = defaultStart > -1 && defaultStart < this.end;

            boolean isOptional = isOptionalQuestion(hasDefault, defaultStart);
            String questionName = getQuestionName(hasDefault, defaultStart, isOptional);

            // Insert this question into the list of arguments
            if (!this.questions.containsKey(questionName)) {
                Object questionValue = getQuestionValue(questionName, this.questionValues,
                    getDefaultValue(hasDefault, defaultStart), this.isArrayArgument);

                if (questionValue == null) {
                    if (!isOptional) {
                        this.missingValue = true;
                    } else if (isOptional && this.isArrayArgument) {
                        questionValue = new Object[]{};
                    }
                }

                ExpressionArgument arg = new ExpressionArgument("arg" + this.questions.size(), questionValue);

                this.questions.put(questionName, arg);
            }

            // Remove the start and end tags and replace the question name with the argument name for this question
            this.expression = this.expression.substring(0, this.start) + this.questions.get(questionName).getArgument()
                + this.expression.substring(this.end + this.endMarker.length());
            scanNextArgument();
        }

        private boolean isNextArgumentMultivalued(String expr)
        {
            int arrayStart = expr.indexOf(START_MARKER_ARRAY);
            int singleStart = expr.indexOf(START_MARKER_SINGLE);

            return (arrayStart > -1 && (singleStart < 0 || arrayStart <= singleStart));
        }

    }

    private Object getQuestionValue(String questionName, final Map<String, Object> values, String defaultValue,
        boolean shouldBeArray)
    {
        Object value = values.get(questionName);
        if (value == null) {
            value = defaultValue;
        }

        if (value != null) {
            if (value.getClass().isArray() && !shouldBeArray) {
                value = ((Object[]) value)[0];
            } else if (!value.getClass().isArray() && shouldBeArray) {
                value = new Object[]{value};
            }
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
