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

package io.uhndata.cards.clarity.importer.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;

/**
 * Clarity import processor that checks if all provided conditions are matched.
 *
 * @version $Id$
 */
public abstract class AbstractConditionalClarityDataProcessor implements ClarityDataProcessor
{
    protected final int priority;

    protected final List<ConditionDefinition> conditions;

    AbstractConditionalClarityDataProcessor(int priority, String[] conditionStrings)
    {
        this.priority = priority;
        this.conditions = new ArrayList<>(conditionStrings.length);
        for (String conditionString : conditionStrings) {
            ConditionDefinition def = new ConditionDefinition(conditionString);
            if (def.isValid()) {
                this.conditions.add(def);
            }
        }
    }

    protected abstract Map<String, String> handleUnmatchedCondition(Map<String, String> input, String condition);

    protected abstract Map<String, String> handleAllConditionsMatched(Map<String, String> input);

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        for (ConditionDefinition condition : this.conditions) {
            if (!condition.matches(input)) {
                return this.handleUnmatchedCondition(input, condition.toString());
            }
        }
        // All filters match if data got here
        return this.handleAllConditionsMatched(input);
    }

    @Override
    public int getPriority()
    {
        return this.priority;
    }

    private enum Operator
    {
        NEQ("<>", false, (input, value) -> input == null || !input.equalsIgnoreCase(value)),
        LTE("<=", false, (input, value) -> compareDouble(input, value, (a, b) -> a <= b)),
        LT("<", false, (input, value) -> compareDouble(input, value, (a, b) -> a < b)),
        GTE(">=", false, (input, value) -> compareDouble(input, value, (a, b) -> a >= b)),
        GT(">", false, (input, value) -> compareDouble(input, value, (a, b) -> a > b)),
        EQ("=", false, (input, value) -> input != null && input.equalsIgnoreCase(value)),
        NOT_MATCHES(" not matches ", false, (input, value) -> input == null || !input.matches(value)),
        MATCHES(" matches ", false, (input, value) -> input != null && input.matches(value)),
        NOT_IN(" not in ", false, (input, value) -> input == null
            || !Arrays.stream(value.split("\\s*+;\\s*+")).anyMatch(input::equalsIgnoreCase)),
        IN(" in ", false, (input, value) -> input != null
            && Arrays.stream(value.split("\\s*+;\\s*+")).anyMatch(input::equalsIgnoreCase)),
        EMPTY(" is empty", true, (input, value) -> input == null || input.length() == 0),
        NOT_EMPTY(" is not empty", true, (input, value) -> input != null && input.length() > 0);

        private final String operator;

        private final boolean unary;

        private final BiFunction<String, String, Boolean> evaluator;

        Operator(final String operator, final boolean unary, final BiFunction<String, String, Boolean> evaluator)
        {
            this.operator = operator;
            this.unary = unary;
            this.evaluator = evaluator;
        }

        Pair<String, String> parse(String configuration)
        {
            if (this.unary) {
                if (configuration.endsWith(this.operator)) {
                    return Pair.of(StringUtils.removeEnd(configuration, this.operator).trim(), null);
                }
            } else {
                String[] pieces = configuration.split("\\s*" + this.operator + "\\s*", 2);
                if (pieces.length == 2) {
                    return Pair.of(pieces[0], pieces[1]);
                }
            }
            return null;
        }

        boolean matches(String column, String value)
        {
            return this.evaluator.apply(column, value);
        }

        private static boolean compareDouble(String input, String value,
            BiFunction<Double, Double, Boolean> comparator)
        {
            try {
                double inputNumber = Double.parseDouble(input);
                double compareTo = Double.parseDouble(value);
                return comparator.apply(inputNumber, compareTo);
            } catch (Exception e) {
                // Error parsing to double, does not match
                return false;
            }
        }

        @Override
        public String toString()
        {
            return this.operator;
        }
    }

    private class ConditionDefinition
    {
        private final String column;

        private final Operator operator;

        private final String value;

        ConditionDefinition(String configuration)
        {
            for (Operator o : Operator.values()) {
                Pair<String, String> result = o.parse(configuration);
                if (result != null) {
                    this.column = result.getKey();
                    this.operator = o;
                    this.value = result.getValue();
                    return;
                }
            }
            this.column = null;
            this.operator = null;
            this.value = null;
        }

        public boolean isValid()
        {
            return this.column != null;
        }

        public boolean matches(Map<String, String> input)
        {
            return this.operator.matches(input.get(this.column), this.value);
        }

        @Override
        public String toString()
        {
            return this.column + this.operator + (this.value != null ? this.value : "");
        }
    }
}
