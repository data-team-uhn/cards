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

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.utils.ConditionalUtils;

/**
 * A set of utility functions to help evaluate and apply conditional reference questions.
 * @version $Id$
 */
public final class ReferenceConditionUtils
{
    /** A status flag that should be applied to a reference answer when. */
    public static final String INVALID_SOURCE_FLAG = "INVALID SOURCE";
    private static final String PROP_PROPERTY = "conditionalProperty";
    private static final String PROP_OPERATOR = "conditionalOperator";
    private static final String PROP_VALUE = "conditionalValue";
    private static final String PROP_FALLBACK = "conditionalFallback";
    private static final String PROP_CONDITION_TYPE = "conditionalType";
    private static final String PROP_REFERENCE_TYPE = "dataType";

    /**
     * Hide the utility class constructor.
     */
    private ReferenceConditionUtils()
    {
    }

    /**
     * Check if a reference question has all the properties required for a conditional reference question.
     * @param referenceQuestion The reference question node to evaluate
     * @return True if the node has all the required properties
     * @throws RepositoryException If an unexpected error occurs
     */
    public static boolean referenceHasCondition(final Node referenceQuestion)
        throws RepositoryException
    {
        return referenceQuestion.hasProperty(PROP_PROPERTY)
            && referenceQuestion.hasProperty(PROP_OPERATOR)
            && referenceQuestion.hasProperty(PROP_VALUE);
    }

    /**
     * Check if a referenced answer satisfies the conditions on a conditional reference question.
     * @param formUtils A formUtils instance that can be used cto help evaluate the conditions
     * @param referenceQuestion The question containing the condition details
     * @param sourceAnswerNode The answer to evaluate the conditions on
     * @return True if the reference conditions are satisfied
     * @throws RepositoryException if an unexpected error occurs
     */
    public static boolean isReferenceConditionSatisfied(final FormUtils formUtils, final Node referenceQuestion,
        final Node sourceAnswerNode)
        throws RepositoryException
    {
        ConditionalUtils.OperandType type = referenceQuestion.hasProperty(PROP_CONDITION_TYPE)
            ? ConditionalUtils.OperandType.parse(
                referenceQuestion.getProperty(PROP_CONDITION_TYPE).getValue().getString())
            : ConditionalUtils.OperandType.TEXT;

        Node sourceForm = formUtils.getForm(sourceAnswerNode);
        String propertyName = referenceQuestion.getProperty(PROP_PROPERTY).getString();
        ConditionalUtils.Operand left = sourceForm.hasProperty(propertyName)
            ? new ConditionalUtils.Operand(type, sourceForm.getProperty(propertyName))
            : new ConditionalUtils.Operand();
        ConditionalUtils.Operator operator = ConditionalUtils.Operator.parse(
            referenceQuestion.getProperty(PROP_OPERATOR).getString());
        ConditionalUtils.Operand right = new ConditionalUtils.Operand(type,
            referenceQuestion.getProperty(PROP_VALUE));

        return operator.evaluate(left, right);
    }

    /**
     * Set the values of an answer NodeBuilder to the fallback values specified in a conditional reference question.
     * @param nodeBuilder The answer NodeBuilder to have the values assigned to
     * @param referenceQuestion The conditional reference question to pull the fallback values from
     * @throws RepositoryException if an unexpected error occurs
     */
    public static void setToFallback(final NodeBuilder nodeBuilder, final Node referenceQuestion)
        throws RepositoryException
    {
        ConditionalUtils.OperandType type = getType(referenceQuestion);
        int propertyType = type.getOakType().tag();

        if (referenceQuestion.hasProperty(PROP_FALLBACK)) {
            Property fallback = referenceQuestion.getProperty(PROP_FALLBACK);
            if (fallback.isMultiple()) {
                setToFallbackMultiple(nodeBuilder, fallback.getValues(), propertyType);
            } else {
                setToFallbackSingle(nodeBuilder, fallback.getValue(), propertyType);
            }
        } else {
            nodeBuilder.removeProperty(FormUtils.VALUE_PROPERTY);
        }
    }

    /**
     * Set the value on an answer node to a value of a specific type.
     * @param nodeBuilder the answer node that needs to have the value set
     * @param value the existing value to extract the new value from
     * @param propertyType the {@code PropertyType} that the value should have
     * @throws RepositoryException if an unexpected error occurs
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity"})
    private static void setToFallbackSingle(NodeBuilder nodeBuilder, Value value, int propertyType)
        throws RepositoryException
    {
        switch (propertyType) {
            case PropertyType.BINARY:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getBinary());
                break;
            case PropertyType.BOOLEAN:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getBoolean());
                break;
            case PropertyType.DATE:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getDate());
                break;
            case PropertyType.DECIMAL:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getDecimal());
                break;
            case PropertyType.DOUBLE:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getDouble());
                break;
            case PropertyType.LONG:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getLong());
                break;
            case PropertyType.NAME:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getString(), Type.NAME);
                break;
            case PropertyType.PATH:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getString(), Type.PATH);
                break;
            case PropertyType.REFERENCE:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getString(), Type.REFERENCE);
                break;
            case PropertyType.URI:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getString(), Type.URI);
                break;
            case PropertyType.WEAKREFERENCE:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getString(), Type.WEAKREFERENCE);
                break;
            case PropertyType.UNDEFINED:
            case PropertyType.STRING:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, value.getString());
                break;
            default:
                throw new ValueFormatException(String.format("Unknown PropertyType {}", propertyType));
        }
    }

    /**
     * Set the value on an answer node to a set of values of a specific type.
     * @param nodeBuilder the answer node that needs to have the value set
     * @param values the existing values to extract the new values from
     * @param propertyType the {@code PropertyType} that the values should have
     * @throws RepositoryException if an unexpected error occurs
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity"})
    private static void setToFallbackMultiple(NodeBuilder nodeBuilder, Value[] values, int propertyType)
        throws RepositoryException
    {
        switch (propertyType) {
            case PropertyType.BINARY:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToJavaTypeList(values, v -> v.getBinary()));
                break;
            case PropertyType.BOOLEAN:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToJavaTypeList(values, v -> v.getBoolean()));
                break;
            case PropertyType.DATE:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToJavaTypeList(values, v -> v.getDate()));
                break;
            case PropertyType.DECIMAL:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToJavaTypeList(values, v -> v.getBinary()));
                break;
            case PropertyType.DOUBLE:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToJavaTypeList(values, v -> v.getDouble()));
                break;
            case PropertyType.LONG:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToJavaTypeList(values, v -> v.getLong()));
                break;
            case PropertyType.NAME:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToStrings(values), Type.NAMES);
                break;
            case PropertyType.PATH:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToStrings(values), Type.PATHS);
                break;
            case PropertyType.REFERENCE:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToStrings(values), Type.REFERENCES);
                break;
            case PropertyType.URI:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToStrings(values), Type.URIS);
                break;
            case PropertyType.WEAKREFERENCE:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToStrings(values), Type.WEAKREFERENCES);
                break;
            case PropertyType.UNDEFINED:
            case PropertyType.STRING:
                nodeBuilder.setProperty(FormUtils.VALUE_PROPERTY, valuesToStrings(values));
                break;
            default:
                throw new ValueFormatException(String.format("Unknown PropertyType {}", propertyType));
        }
    }

    /**
     * Convert an arrays of values into a list of strings.
     * @param values The values to be converted
     * @return A list of strings
     * @throws RepositoryException if an unexpected error occurs
     */
    private static List<String> valuesToStrings(Value[] values)
        throws RepositoryException
    {
        return valuesToJavaTypeList(values, v -> v.getString());
    }

    /**
     * Convert an array of values into a list of a specified type using a getter function to extract the values.
     * @param <T> The type of the values that should be returned
     * @param values The existing values that the returned values should be extracted from
     * @param getter The method that should be called on the existing values to recieve the values in the correct type
     * @return A list of values in the specified type
     * @throws RepositoryException If the getter could not be run succesfully or if an unexpected error occurs
     */
    private static <T> List<T> valuesToJavaTypeList(Value[] values, RepositoryFunction<Value, T> getter)
        throws RepositoryException
    {
        ArrayList<T> result = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            result.add(getter.apply(values[i]));
        }
        return result;
    }

    /**
     * A {@code Function} that takes in a parameter of type {@code T} and returns a value of type {@code R}.
     * May throw a {@code RepositoryException}
     */
    @FunctionalInterface
    private interface RepositoryFunction<T, R>
    {
        R apply(T t) throws RepositoryException;
    }

    /**
     * Retrieve the fallback value from a conditional reference question.
     * @param session A sessions that can be used to obtain a {@code ValueFactory}
     * @param referenceQuestion The conditional reference question to retrieve the fallback value from
     * @return A {@code Value} or {@code Value[]} containing the fallback value specified by the reference question
     * @throws RepositoryException if an unexpected error occurs
     */
    public static Object getFallbackValue(final Session session, final Node referenceQuestion)
        throws RepositoryException
    {
        final ValueFactory valueFactory = session.getValueFactory();
        ConditionalUtils.OperandType type = getType(referenceQuestion);
        int propertyType = type.getOakType().tag();

        if (referenceQuestion.hasProperty(PROP_FALLBACK)) {
            final RepositoryFunction<Value, Value> valueHandler = getValueHandler(propertyType, valueFactory);

            Property fallback = referenceQuestion.getProperty(PROP_FALLBACK);
            if (fallback.isMultiple()) {
                Value[] fallbackValues = fallback.getValues();
                Value[] values = new Value[fallbackValues.length];
                for (int i = 0; i < fallbackValues.length; i++) {
                    values[i] = valueHandler.apply(fallbackValues[i]);
                }
                return values;
            } else {
                return valueHandler.apply(fallback.getValue());
            }
        } else {
            return null;
        }
    }

    /**
     * Retrieve a function that can be run on an existing value to create a new value of a specified type.
     * @param propertyType the {@code PropertyType} that the result of the returned function should have
     * @param valueFactory a ValueFactory that can be used by the returned function to create the new value
     * @return a function that converts an existing {@code Value} to a new {@code value} with a specified type
     * @throws RepositoryException if an unexpected error occurs
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity"})
    private static RepositoryFunction<Value, Value> getValueHandler(int propertyType, ValueFactory valueFactory)
        throws RepositoryException
    {
        final RepositoryFunction<Value, Value> valueHandler;
        switch (propertyType) {
            case PropertyType.BINARY:
                valueHandler = v -> valueFactory.createValue(v.getBinary());
                break;
            case PropertyType.BOOLEAN:
                valueHandler = v -> valueFactory.createValue(v.getBoolean());
                break;
            case PropertyType.DATE:
                valueHandler = v -> valueFactory.createValue(v.getDate());
                break;
            case PropertyType.DECIMAL:
                valueHandler = v -> valueFactory.createValue(v.getDecimal());
                break;
            case PropertyType.DOUBLE:
                valueHandler = v -> valueFactory.createValue(v.getDouble());
                break;
            case PropertyType.LONG:
                valueHandler = v -> valueFactory.createValue(v.getLong());
                break;
            case PropertyType.UNDEFINED:
                valueHandler = v -> v;
                break;
            case PropertyType.NAME:
            case PropertyType.PATH:
            case PropertyType.REFERENCE:
            case PropertyType.STRING:
            case PropertyType.URI:
            case PropertyType.WEAKREFERENCE:
                valueHandler = v -> valueFactory.createValue(v.getString(), propertyType);
                break;
            default:
                throw new ValueFormatException(String.format("Unknown PropertyType {}", propertyType));
        }
        return valueHandler;
    }

    /**
     * Get the type of the operands of a conditional reference question.
     * @param referenceQuestion The reference question to retrieve the type from
     * @return The type of the properties that the reference question is conditional on
     * @throws RepositoryException if an unexpected error occurs
     */
    private static ConditionalUtils.OperandType getType(final Node referenceQuestion)
        throws RepositoryException
    {
        return referenceQuestion.hasProperty(PROP_REFERENCE_TYPE)
            ? ConditionalUtils.OperandType.parse(
                referenceQuestion.getProperty(PROP_REFERENCE_TYPE).getValue().getString())
            : ConditionalUtils.OperandType.TEXT;
    }
}
