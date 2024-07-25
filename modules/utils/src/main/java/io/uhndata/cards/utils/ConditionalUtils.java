
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
package io.uhndata.cards.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.jackrabbit.oak.api.Type;

/**
 * A set of utility functions used to help evaluate conditions.
 * @version $Id$
 */
public final class ConditionalUtils
{
    /**
     * Verify that two operands are comprised of single entries.
     */
    public static final BiPredicate<Operand, Operand> SINGLE_LISTS =
        (a, b) -> (a.size() == 1 && b.size() == 1);

    /**
     * Hide the utility class constructor.
     */
    private ConditionalUtils()
    {
    }

    /**
     * The data type of the operand values, as indicated in the {@code dataType} property of the
     * {@code cards:Conditional} node.
     */
    @SuppressWarnings({"unchecked", "JavadocVariable"})
    public enum OperandType
    {
        TEXT(Type.STRINGS, v -> {
            try {
                return (Comparable<Object>) (Object) v.getString();
            } catch (IllegalStateException | RepositoryException e) {
                return (Comparable<Object>) (Object) v.toString();
            }
        }),
        DATE(Type.DATES, v -> {
            try {
                return (Comparable<Object>) (Object) v.getDate();
            } catch (IllegalStateException | RepositoryException e) {
                return (Comparable<Object>) (Object) v.toString();
            }
        }),
        LONG(Type.LONGS, v -> {
            try {
                return (Comparable<Object>) (Object) v.getLong();
            } catch (IllegalStateException | RepositoryException e) {
                return (Comparable<Object>) (Object) v.toString();
            }
        }),
        DECIMAL(Type.DECIMALS, v -> {
            try {
                return (Comparable<Object>) (Object) v.getDecimal();
            } catch (IllegalStateException | RepositoryException e) {
                return (Comparable<Object>) (Object) v.toString();
            }
        }),
        DOUBLE(Type.DOUBLES, v -> {
            try {
                return (Comparable<Object>) (Object) v.getDouble();
            } catch (IllegalStateException | RepositoryException e) {
                return (Comparable<Object>) (Object) v.toString();
            }
        });

        private final Type<?> type;

        private final Function<Value, Comparable<Object>> valueExtractor;

        OperandType(Type<?> type, final Function<Value, Comparable<Object>> valueExtractor)
        {
            this.type = type;
            this.valueExtractor = valueExtractor;
        }

        /**
         * The Oak type that should be used to extract the values with the correct type from the {@code PropertyState}
         * object.
         *
         * @return an Oak multivalue type
         */
        public Type<?> getOakType()
        {
            return this.type;
        }

        /**
         * Extract the value in the intended type from a JCR {@code Value} object.
         *
         * @param jcrValue a Value object
         * @return the raw value stored in the object
         */
        public Comparable<Object> getValue(final Value jcrValue)
        {
            return this.valueExtractor.apply(jcrValue);
        }

        /**
         * Convert the value of the {@code dataType} property into an enum item.
         *
         * @param label the value stored in the conditional node
         * @return an enum instance, {@code TEXT} if the passed label isn't one of the known types
         */
        public static OperandType parse(final String label)
        {
            try {
                return valueOf(label.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException | NullPointerException ex) {
                return TEXT;
            }
        }
    }

    /**
     * An operand of a conditional containing a value or set of values.
     */
    public static class Operand
    {
        protected final List<Comparable<Object>> values;

        public Operand(final Property property)
            throws RepositoryException
        {
            this(OperandType.TEXT, property);
        }

        public Operand(final OperandType type, final Property property)
            throws RepositoryException
        {
            this.values = new ArrayList<>();
            if (property.isMultiple()) {
                for (Value v : property.getValues()) {
                    this.values.add(type.getValue(v));
                }
            } else {
                this.values.add(type.getValue(property.getValue()));
            }
        }

        public Operand(Iterable<Comparable<Object>> values)
        {
            this.values = new ArrayList<>();
            values.forEach(v -> this.values.add(v));
        }

        public Operand()
        {
            this.values = new ArrayList<>();
        }

        public Stream<Comparable<Object>> stream()
        {
            return this.values.stream();
        }

        public Comparable<Object> get(int index)
        {
            return this.values.get(index);
        }

        public int size()
        {
            return this.values.size();
        }
    }

    /**
     * The operator of a conditional, includes a method to evaluate it on actual operand values.
     */
    public enum Operator
    {
        /** Check that two operands are equal. */
        EQ("=", (left, right) -> left.stream()
            .allMatch(vl -> right.stream().anyMatch(vr -> (vl.compareTo(vr) == 0)))
            && right.stream()
                .allMatch(vr -> left.stream().anyMatch(vl -> (vl.compareTo(vr) == 0))),
            true),
        /** Check that two operands are not equal. */
        NEQ("<>", (left, right) -> !EQ.evaluate(left, right), true),
        /** Check that the first operand is less than the second. Requires single valued operands. */
        LT("<", (left, right) -> SINGLE_LISTS.test(left, right)
            && (left.get(0).compareTo(right.get(0)) < 0),
            false),
        /** Check that the first operand is less than or equal to the second. Requires single valued operands. */
        LTE("<=", (left, right) -> SINGLE_LISTS.test(left, right)
            && (left.get(0).compareTo(right.get(0)) <= 0), false),
        /** Check that the first operand is greater than the second. Requires single valued operands. */
        GT(">", (left, right) -> SINGLE_LISTS.test(left, right)
            && (left.get(0).compareTo(right.get(0)) > 0), false),
        /** Check that the first operand is greater than or equal to the second. Requires single valued operands. */
        GTE(">=", (left, right) -> SINGLE_LISTS.test(left, right)
            && (left.get(0).compareTo(right.get(0)) >= 0), false),
        /** Check that both operands are empty. */
        EMPTY("is empty", (left, right) -> left.size() == 0 && right.size() == 0, true),
        /** Check that both operands are not empty. */
        NOT_EMPTY("is not empty", (left, right) -> !EMPTY.evaluate(left, right), true),
        /** Check that the second operand contains all values from the first operand. */
        INCLUDES("includes",
            (left, right) -> right.stream().allMatch(
                vl -> left.stream().anyMatch(vr -> (vr.compareTo(vl) == 0))),
            true),
        /** Check that the second operand contains at least one value from the first operand. */
        INCLUDES_ANY("includes any",
            (left, right) -> right.stream().anyMatch(
                vl -> left.stream().anyMatch(vr -> (vr.compareTo(vl) == 0))),
            true),
        /** Check that the second operand does not contain any values from the first operand. */
        EXCLUDES("excludes",
            (left, right) -> right.stream().noneMatch(
                vl -> left.stream().anyMatch(vr -> (vr.compareTo(vl) == 0))),
            true),
        /** Check that the second operand does not contain at least one value from the first operand. */
        EXCLUDES_ANY("excludes any",
            (left, right) -> right.stream().anyMatch(
                vl -> left.stream().noneMatch(vr -> (vr.compareTo(vl) == 0))),
            true);

        private final String operatorStr;

        private final boolean supportsMultivalue;

        private final BiPredicate<Operand, Operand> evaluator;

        Operator(final String operatorStr, final BiPredicate<Operand, Operand> evaluator,
            final boolean supportsMultivalue)
        {
            this.operatorStr = operatorStr;
            this.evaluator = evaluator;
            this.supportsMultivalue = supportsMultivalue;
        }

        /**
         * Evaluate the two operands according to the rules of this operator.
         *
         * @param left the left operand
         * @param right the right operand, may be empty if this is an unary operator
         * @return {@code true} if the two operands pass this operator, {@code false} otherwise
         */
        public boolean evaluate(final Operand left, final Operand right)
        {
            if (!this.supportsMultivalue && (left.size() > 1 || right.size() > 1)) {
                return false;
            }
            return this.evaluator.test(left, right);
        }

        /**
         * Convert the value of the {@code comparator} property of the conditional node into an enum item.
         *
         * @param operatorStr the value stored in the conditional node
         * @return an enum instance
         * @throws IllegalArgumentException if the value passed is not a known operator
         */
        public static Operator parse(final String operatorStr)
        {
            for (Operator o : Operator.values()) {
                if (o.operatorStr.equals(operatorStr)) {
                    return o;
                }
            }
            throw new IllegalArgumentException("Unknown operator: " + operatorStr);
        }
    }
}
