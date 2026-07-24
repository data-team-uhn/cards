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
package io.uhndata.cards.entityindex;

/**
 * A single constraint on one field of an indexed entity, part of a {@link SearchQuery}. The field is identified by
 * its index field name: either the UUID of the question being answered, or one of the {@code @}-prefixed entity
 * metadata fields listed in {@link IndexFields}.
 *
 * @version $Id$
 * @since 0.9.41
 */
public final class SearchCondition
{
    /** Supported value types, dictating how values are compared. */
    public enum Type
    {
        /** Plain text, compared as strings. */
        TEXT,
        /** Whole numbers, also used for booleans. */
        LONG,
        /** Fractional numbers, both {@code double} and {@code decimal} values. */
        DOUBLE,
        /** Calendar dates, compared with whole-day precision. */
        DATE,
        /** References to other nodes, compared as exact UUID strings. */
        REFERENCE
    }

    /** Supported comparison operators. */
    public enum Operator
    {
        /** Equality. */
        EQ("="),
        /** Inequality, only matches entities where the field does have a value. */
        NEQ("<>"),
        /** Strictly lower than. */
        LT("<"),
        /** Lower than or equal. */
        LTE("<="),
        /** Strictly greater than. */
        GT(">"),
        /** Greater than or equal. */
        GTE(">="),
        /** Substring match on the value. */
        CONTAINS("contains"),
        /** Substring match on the notes accompanying the value. */
        NOTES_CONTAIN("notes contain"),
        /** The field has no value. */
        IS_EMPTY("is empty"),
        /** The field has at least one value. */
        IS_NOT_EMPTY("is not empty");

        private final String symbol;

        Operator(final String symbol)
        {
            this.symbol = symbol;
        }

        /**
         * Parse an operator from its query symbol, e.g. {@code =} or {@code contains}.
         *
         * @param symbol the symbol to parse
         * @return the matching operator, or {@code EQ} if the symbol is not recognized
         */
        public static Operator fromSymbol(final String symbol)
        {
            for (Operator o : values()) {
                if (o.symbol.equals(symbol)) {
                    return o;
                }
            }
            return EQ;
        }
    }

    private final String field;

    private final Operator operator;

    private final String value;

    private final Type type;

    /**
     * Basic constructor.
     *
     * @param field the index field name, either a question UUID or a {@code @}-prefixed metadata field
     * @param operator the comparison operator
     * @param value the value to compare against, in string form; ignored for the valueless {@code IS_EMPTY} and
     *            {@code IS_NOT_EMPTY} operators
     * @param type how the value should be interpreted
     */
    public SearchCondition(final String field, final Operator operator, final String value, final Type type)
    {
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.type = type;
    }

    /**
     * The targeted index field name.
     *
     * @return a field name
     */
    public String getField()
    {
        return this.field;
    }

    /**
     * The comparison operator.
     *
     * @return an operator
     */
    public Operator getOperator()
    {
        return this.operator;
    }

    /**
     * The value to compare against.
     *
     * @return a value in string form, may be {@code null} for valueless operators
     */
    public String getValue()
    {
        return this.value;
    }

    /**
     * How the value should be interpreted.
     *
     * @return a value type
     */
    public Type getType()
    {
        return this.type;
    }
}
