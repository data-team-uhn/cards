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

package io.uhndata.cards.prems.internal.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    protected abstract Map<String, String> handleUnmatchedCondition(Map<String, String> input);

    protected abstract Map<String, String> handleAllConditionsMatched(Map<String, String> input);

    @Override
    public Map<String, String> processEntry(Map<String, String> input)
    {
        for (ConditionDefinition condition : this.conditions) {
            if (!condition.matches(input)) {
                return this.handleUnmatchedCondition(input);
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

    private class ConditionDefinition
    {
        private final String[] dualOperators = {"<>", "<=", "<", ">=", ">", "="};
        private final String[] singleOperators = {"is empty", "is not empty"};
        private final String column;
        private final String operator;
        private final String value;

        ConditionDefinition(String configuration)
        {
            for (String operatorOption : this.dualOperators) {
                String[] pieces = configuration.split("\\s*" + operatorOption + "\\s*");
                if (pieces.length == 2) {
                    this.operator = operatorOption;
                    this.column = pieces[0];
                    this.value = pieces[1];
                    return;
                }
            }
            for (String operatorOption : this.singleOperators) {
                if (configuration.endsWith(operatorOption)) {
                    this.operator = operatorOption;
                    this.column = configuration.substring(0, configuration.length() - operatorOption.length()).trim();
                    this.value = null;
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

        @SuppressWarnings("checkstyle:CyclomaticComplexity")
        public boolean matches(Map<String, String> input)
        {
            String inputValue = input.get(this.column);
            final boolean result;
            switch (this.operator) {
                case "<>":
                    // Return true value is null or values do not match
                    result = inputValue == null || !inputValue.matches(this.value);
                    break;
                case "=":
                    // Return true if column has entry and values match
                    result = inputValue != null && inputValue.matches(this.value);
                    break;
                case "<=":
                    result = this.compareDouble(
                        inputValue,
                        (double a, double b) -> a <= b
                    );
                    break;
                case "<":
                    result = this.compareDouble(
                        inputValue,
                        (double a, double b) -> a < b
                    );
                    break;
                case ">=":
                    result = this.compareDouble(
                        inputValue,
                        (double a, double b) -> a >= b
                    );
                    break;
                case ">":
                    result = this.compareDouble(
                        inputValue,
                        (double a, double b) -> a > b
                    );
                    break;
                case "is empty":
                    result = inputValue == null || inputValue.length() == 0;
                    break;
                case "is not empty":
                    result = inputValue != null && inputValue.length() > 0;
                    break;
                default:
                    result = false;
            }
            return result;
        }

        private boolean compareDouble(String input,
            AbstractConditionalClarityDataProcessor.DoubleComparator comparator)
        {
            try {
                double inputNumber = Double.parseDouble(input);
                double compareTo = Double.parseDouble(this.value);
                return comparator.op(inputNumber, compareTo);
            } catch (Exception e) {
                // Error parsing to double, does not match
                return false;
            }
        }

        @Override
        public String toString()
        {
            return this.column + " " + this.operator + (this.value != null ? " " + this.value : "");
        }
    }

    private interface DoubleComparator
    {
        boolean op(double a, double b);
    }
}
