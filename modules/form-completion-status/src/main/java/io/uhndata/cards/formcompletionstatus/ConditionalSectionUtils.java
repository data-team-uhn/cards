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
package io.uhndata.cards.formcompletionstatus;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConditionalSectionUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionalSectionUtils.class);

    private static final String PROP_QUESTION = "question";

    private static final String PROP_IS_REFERENCE = "isReference";

    private static final String PROP_VALUE = "value";

    private static final String PROP_REQUIRE_ALL = "requireAll";

    @SuppressWarnings("serial")
    private static final Map<Integer, java.util.function.BiFunction<Object, Object, Boolean>> EQ_OPERATORS =
        new HashMap<Integer, java.util.function.BiFunction<Object, Object, Boolean>>()
        {
            {
                put(PropertyType.STRING, Object::equals);
                put(PropertyType.LONG, (a, b) -> toLong(a) == toLong(b));
                put(PropertyType.DOUBLE, (a, b) -> toDouble(a) == toDouble(b));
                put(PropertyType.DECIMAL, (a, b) -> {
                    BigDecimal decimalA = toDecimal(a);
                    BigDecimal decimalB = toDecimal(b);
                    return decimalA != null && decimalB != null && decimalA.compareTo(decimalB) == 0;
                });
                put(PropertyType.BOOLEAN, (a, b) -> toBoolean(a) == toBoolean(b));
                put(PropertyType.DATE, (a, b) -> {
                    Calendar dateA = toDate(a);
                    Calendar dateB = toDate(b);
                    return dateA != null && dateB != null && dateA.compareTo(dateB) == 0;
                });
            }
        };

    @SuppressWarnings("serial")
    private static final Map<Integer, java.util.function.BiFunction<Object, Object, Boolean>> LT_OPERATORS =
        new HashMap<Integer, java.util.function.BiFunction<Object, Object, Boolean>>()
        {
            {
                put(PropertyType.LONG, (a, b) -> toLong(a) < toLong(b));
                put(PropertyType.DOUBLE, (a, b) -> toDouble(a) < toDouble(b));
                put(PropertyType.DECIMAL, (a, b) -> {
                    BigDecimal decimalA = toDecimal(a);
                    BigDecimal decimalB = toDecimal(b);
                    return decimalA != null && decimalB != null && decimalA.compareTo(decimalB) < 0;
                });
                put(PropertyType.DATE, (a, b) -> {
                    Calendar dateA = toDate(a);
                    Calendar dateB = toDate(b);
                    return dateA != null && dateB != null && dateA.before(dateB);
                });
            }
        };

    @SuppressWarnings("serial")
    private static final Map<Integer, java.util.function.BiFunction<Object, Object, Boolean>> GT_OPERATORS =
        new HashMap<Integer, java.util.function.BiFunction<Object, Object, Boolean>>()
        {
            {
                put(PropertyType.LONG, (a, b) -> toLong(a) > toLong(b));
                put(PropertyType.DOUBLE, (a, b) -> toDouble(a) > toDouble(b));
                put(PropertyType.DECIMAL, (a, b) -> {
                    BigDecimal decimalA = toDecimal(a);
                    BigDecimal decimalB = toDecimal(b);
                    return decimalA != null && decimalB != null && decimalA.compareTo(decimalB) > 0;
                });
                put(PropertyType.DATE, (a, b) -> {
                    Calendar dateA = toDate(a);
                    Calendar dateB = toDate(b);
                    return dateA != null && dateB != null && dateA.after(dateB);
                });
            }
        };

    @SuppressWarnings("serial")
    private static final Map<Integer, java.util.function.BiFunction<Object, Object, Boolean>> LTE_OPERATORS =
        new HashMap<Integer, java.util.function.BiFunction<Object, Object, Boolean>>()
        {
            {
                put(PropertyType.LONG, (a, b) -> toLong(a) <= toLong(b));
                put(PropertyType.DOUBLE, (a, b) -> toDouble(a) <= toDouble(b));
                put(PropertyType.DECIMAL, (a, b) -> {
                    BigDecimal decimalA = toDecimal(a);
                    BigDecimal decimalB = toDecimal(b);
                    return decimalA != null && decimalB != null && decimalA.compareTo(decimalB) <= 0;
                });
                put(PropertyType.DATE, (a, b) -> {
                    Calendar dateA = toDate(a);
                    Calendar dateB = toDate(b);
                    return dateA != null && dateB != null && !dateA.after(dateB);
                });
            }
        };

    @SuppressWarnings("serial")
    private static final Map<Integer, java.util.function.BiFunction<Object, Object, Boolean>> GTE_OPERATORS =
        new HashMap<Integer, java.util.function.BiFunction<Object, Object, Boolean>>()
        {
            {
                put(PropertyType.LONG, (a, b) -> toLong(a) >= toLong(b));
                put(PropertyType.DOUBLE, (a, b) -> toDouble(a) >= toDouble(b));
                put(PropertyType.DECIMAL, (a, b) -> {
                    BigDecimal decimalA = toDecimal(a);
                    BigDecimal decimalB = toDecimal(b);
                    return decimalA != null && decimalB != null && decimalA.compareTo(decimalB) >= 0;
                });
                put(PropertyType.DATE, (a, b) -> {
                    Calendar dateA = toDate(a);
                    Calendar dateB = toDate(b);
                    return dateA != null && dateB != null && !dateA.before(dateB);
                });
            }
        };

    /**
     * Hide the utility class constructor.
     */
    private ConditionalSectionUtils()
    {
    }

    /**
     * Gets the questionnaire section node referenced by the AnswerSection NodeBuilder nb.
     *
     * @param resourceSession the current session
     * @param answerSection the answer section whose section should be retrieved
     * @return the section Node object referenced by answerSection
     */
    private static Node getSectionNode(final Session resourceSession, final NodeBuilder answerSection)
    {
        try {
            if (answerSection.hasProperty("section")) {
                final String sectionNodeReference = answerSection.getProperty("section").getValue(Type.REFERENCE);
                return resourceSession.getNodeByIdentifier(sectionNodeReference);
            }
        } catch (final RepositoryException ex) {
            return null;
        }
        return null;
    }

    /**
     * Returns the first NodeBuilder with a question value equal to questionUUID that is a descendant of the parent
     * node. If no such node can be found, null is returned.
     *
     * @param parent the NodeBuilder to search through its children
     * @param questionUUID the UUID String for which the child's question property must be equal to
     * @return the first NodeBuilder with a question value equal to questionUUID that is a descendant of the parent
     *         NodeBuilder, or null if such a node does not exist.
     */
    private static NodeBuilder getAnswerForQuestion(final NodeBuilder parent, final String questionUUID)
    {
        final Iterable<String> childrenNames = parent.getChildNodeNames();
        for (final String childName : childrenNames) {
            final NodeBuilder child = parent.getChildNode(childName);
            // Check if this is the answer we're looking for
            if (child.hasProperty(PROP_QUESTION)
                && questionUUID.equals(child.getProperty(PROP_QUESTION).getValue(Type.STRING))) {
                return child;
            }
            // If this is an answer section, look for the answer in it, and return if found
            if ("cards:AnswerSection".equals(child.getName("jcr:primaryType"))) {
                NodeBuilder sectionResult = getAnswerForQuestion(child, questionUUID);
                if (sectionResult != null) {
                    return sectionResult;
                }
            }
        }
        return null;
    }

    /**
     * Parses a date from the given input string.
     *
     * @param str the serialized date to parse
     * @return the parsed date, or {@code null} if the date cannot be parsed
     */
    private static Calendar parseDate(final String str)
    {
        try {
            final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            final Date date = fmt.parse(str.split("T")[0]);
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return calendar;
        } catch (final ParseException e) {
            LOGGER.warn("PARSING DATE FAILED: Invalid date {}", str);
            return null;
        }
    }

    private static boolean isLikeString(final PropertyState ps)
    {
        return (Type.STRING.equals(ps.getType()) || Type.STRINGS.equals(ps.getType()));
    }

    private static boolean isLikeLong(final PropertyState ps)
    {
        return (Type.LONG.equals(ps.getType()) || Type.LONGS.equals(ps.getType()));
    }

    private static boolean isLikeDouble(final PropertyState ps)
    {
        return (Type.DOUBLE.equals(ps.getType()) || Type.DOUBLES.equals(ps.getType()));
    }

    private static boolean isLikeDecimal(final PropertyState ps)
    {
        return (Type.DECIMAL.equals(ps.getType()) || Type.DECIMALS.equals(ps.getType()));
    }

    private static boolean isLikeBoolean(final PropertyState ps)
    {
        return (Type.BOOLEAN.equals(ps.getType()) || Type.BOOLEANS.equals(ps.getType()));
    }

    private static boolean isLikeDate(final PropertyState ps)
    {
        return (Type.DATE.equals(ps.getType()) || Type.DATES.equals(ps.getType()));
    }

    private static int getPropertyStateType(final PropertyState ps)
    {
        int ret = PropertyType.UNDEFINED;
        if (isLikeString(ps)) {
            ret = PropertyType.STRING;
        } else if (isLikeLong(ps)) {
            ret = PropertyType.LONG;
        } else if (isLikeDouble(ps)) {
            ret = PropertyType.DOUBLE;
        } else if (isLikeDecimal(ps)) {
            ret = PropertyType.DECIMAL;
        } else if (isLikeBoolean(ps)) {
            ret = PropertyType.BOOLEAN;
        } else if (isLikeDate(ps)) {
            ret = PropertyType.DATE;
        }
        return ret;
    }

    private static int getPropertyObjectType(final Object obj)
    {
        int ret = PropertyType.UNDEFINED;
        if (obj instanceof String) {
            ret = PropertyType.STRING;
        } else if (obj instanceof Long) {
            ret = PropertyType.LONG;
        } else if (obj instanceof Double) {
            ret = PropertyType.DOUBLE;
        } else if (obj instanceof BigDecimal) {
            ret = PropertyType.DECIMAL;
        } else if (obj instanceof Boolean) {
            ret = PropertyType.BOOLEAN;
        } else if (obj instanceof Calendar) {
            ret = PropertyType.DATE;
        }
        return ret;
    }

    private static boolean evalSection(final Object propA, final Object propB,
        final Map<Integer, java.util.function.BiFunction<Object, Object, Boolean>> operators)
        throws RepositoryException, ValueFormatException
    {
        if (propA == null || propB == null) {
            return false;
        }
        Integer type = getPropertyObjectType(propA);
        if (!operators.containsKey(type)) {
            return false;
        }
        return operators.get(type).apply(propA, propB);
    }

    /**
     * Evaluates the boolean expression {propA} {operator} {propB}.
     */
    private static boolean evalSectionCondition(final Object propA, final Object propB, final String operator)
        throws RepositoryException, ValueFormatException
    {
        // If we can't evaluate it, default to false
        boolean result = false;
        if ("=".equals(operator) || "<>".equals(operator)) {
            boolean testResult = evalSection(propA, propB, EQ_OPERATORS);
            if ("<>".equals(operator)) {
                testResult = !testResult;
            }
            result = testResult;
        } else if ("<".equals(operator)) {
            result = evalSection(propA, propB, LT_OPERATORS);
        } else if (">".equals(operator)) {
            result = evalSection(propA, propB, GT_OPERATORS);
        } else if ("<=".equals(operator)) {
            result = evalSection(propA, propB, LTE_OPERATORS);
        } else if (">=".equals(operator)) {
            result = evalSection(propA, propB, GTE_OPERATORS);
        }
        return result;
    }

    /*
     * Read in a string, inStr, and return it with any non-allowed chars removed.
     */
    private static String sanitizeNodeName(final String inStr)
    {
        final String inStrLower = inStr.toLowerCase();
        String outStr = "";
        for (int i = 0; i < inStr.length(); i++) {
            if ("abcdefghijklmnopqrstuvwxyz 0123456789_-".indexOf(inStrLower.charAt(i)) > -1) {
                outStr += inStr.charAt(i);
            }
        }
        return outStr;
    }

    private static Object getObjectFromPropertyState(PropertyState ps, int index)
    {
        Object ret = null;
        switch (getPropertyStateType(ps)) {
            case PropertyType.STRING:
                ret = ps.getValue(Type.STRING, index);
                break;
            case PropertyType.LONG:
                ret = ps.getValue(Type.LONG, index);
                break;
            case PropertyType.DOUBLE:
                ret = ps.getValue(Type.DOUBLE, index);
                break;
            case PropertyType.DECIMAL:
                ret = ps.getValue(Type.DECIMAL, index);
                break;
            case PropertyType.BOOLEAN:
                ret = ps.getValue(Type.BOOLEAN, index);
                break;
            case PropertyType.DATE:
                ret = parseDate(ps.getValue(Type.DATE, index));
                break;
            default:
                break;
        }
        return ret;
    }

    private static Object getObjectFromValue(Value val) throws RepositoryException, ValueFormatException
    {
        Object ret = null;
        switch (val.getType()) {
            case PropertyType.STRING:
                ret = val.getString();
                break;
            case PropertyType.LONG:
                ret = val.getLong();
                break;
            case PropertyType.DOUBLE:
                ret = val.getDouble();
                break;
            case PropertyType.DECIMAL:
                ret = val.getDecimal();
                break;
            case PropertyType.BOOLEAN:
                ret = val.getBoolean();
                break;
            case PropertyType.DATE:
                Calendar calendar = parseDate(Integer.toString(val.getDate().get(Calendar.YEAR))
                    + "-"
                    + Integer.toString(val.getDate().get(Calendar.MONTH) + 1)
                    + "-"
                    + Integer.toString(val.getDate().get(Calendar.DAY_OF_MONTH))
                    + "T00:00");
                ret = calendar;
                break;
            default:
                break;
        }
        return ret;
    }

    private static PropertyState getPropertyStateFromRef(final Node operand,
        final Node sectionNode, final NodeBuilder form) throws RepositoryException
    {
        String key = sanitizeNodeName(operand.getProperty(PROP_VALUE).getValues()[0].getString());
        final Node questionnaire = getQuestionnaireForSection(sectionNode);
        final Node question = getQuestionWithName(questionnaire, key);
        if (question == null) {
            return null;
        }
        final String questionUUID = question.getIdentifier();
        // Get the node from the Form containing the answer to keyNode
        final NodeBuilder answer = getAnswerForQuestion(form, questionUUID);
        if (answer == null) {
            return null;
        }
        return answer.getProperty(PROP_VALUE);
    }

    /**
     * Retrieves for the Questionnaire that a Section belongs to. This is usually the parent node, but in the case of a
     * nested section, it may be higher up the ancestors chain.
     *
     * @param section the Section whose Questionnaire to retrieve
     * @return a Questionnaire node, or {@code null} if navigating the tree fails
     */
    private static Node getQuestionnaireForSection(final Node section)
    {
        Node result = section;
        try {
            while (section != null && !"cards:Questionnaire".equals(result.getPrimaryNodeType().getName())) {
                result = result.getParent();
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Unexpected error looking up the questionnaire: {}", e.getMessage(), e);
            return null;
        }
        return result;
    }

    /**
     * Retrieves the Question node with the given name. This is needed to get from a simple question name, like
     * {@code "gender"}, to the JCR UUID used in the actual reference from the answer to the question.
     *
     * @param parent the node to (recursively) look in, starting with the Questionnaire
     * @param questionName the simple question name
     * @return the Question node, or {@code null} if the question cannot be found
     */
    private static Node getQuestionWithName(final Node parent, final String questionName)
    {
        try {
            final NodeIterator children = parent.getNodes();
            while (children.hasNext()) {
                final Node childNode = children.nextNode();
                if (questionName.equals(childNode.getName())) {
                    return childNode;
                }
                if ("cards:Section".equals(childNode.getPrimaryNodeType().getName())) {
                    Node sectionResult = getQuestionWithName(childNode, questionName);
                    if (sectionResult != null) {
                        return sectionResult;
                    }
                }
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to look up question: {}", e.getMessage(), e);
        }
        return null;
    }

    private static Object getOperandValue(final Node operand, final Node sectionNode, final NodeBuilder form,
        int index) throws RepositoryException
    {
        Object returnedValue = null;
        if (operand.getProperty(PROP_IS_REFERENCE).getValue().getBoolean()) {
            PropertyState operandProp = getPropertyStateFromRef(operand, sectionNode, form);
            if (operandProp != null) {
                returnedValue = getObjectFromPropertyState(operandProp, index);
            }
        } else {
            Property nodeProp = operand.getProperty(PROP_VALUE);
            Value nodeVal;
            if (nodeProp.isMultiple()) {
                nodeVal = nodeProp.getValues()[index];
            } else {
                nodeVal = nodeProp.getValue();
            }
            returnedValue = getObjectFromValue(nodeVal);
        }
        return returnedValue;
    }

    private static int getOperandLength(final Node operand, final Node sectionNode, final NodeBuilder form)
        throws RepositoryException
    {
        int returnedValue = -1;
        if (operand.getProperty(PROP_IS_REFERENCE).getValue().getBoolean()) {
            PropertyState operandProp = getPropertyStateFromRef(operand, sectionNode, form);
            if (operandProp != null) {
                returnedValue = operandProp.count();
            }
        } else {
            Property nodeProp = operand.getProperty(PROP_VALUE);
            if (nodeProp.isMultiple()) {
                Value[] nodeVals = nodeProp.getValues();
                returnedValue = nodeVals.length;
            } else {
                returnedValue = 1;
            }
        }
        return returnedValue;
    }

    private static boolean evalOperands(final Node operandA, final Node operandB,
        final String comparator, final Node sectionNode, final NodeBuilder form)
            throws RepositoryException
    {
        final int lengthB = getOperandLength(operandB, sectionNode, form);
        final int lengthA = getOperandLength(operandA, sectionNode, form);
        final boolean requireAllB = operandB.getProperty(PROP_REQUIRE_ALL).getBoolean();
        final boolean requireAllA = operandA.getProperty(PROP_REQUIRE_ALL).getBoolean();

        /*
         * Check for "is empty" / "is not empty" as requireAll is not relevant for these operators
         */
        if ("is empty".equals(comparator) || "is not empty".equals(comparator)) {
            boolean testResult = (lengthB == -1 || lengthA == -1);
            if ("is not empty".equals(comparator)) {
                testResult = !testResult;
            }
            return testResult;
        }

        // If at least one operand requires all to match
        final boolean requireAllMulti = requireAllB | requireAllA;

        boolean res = requireAllMulti;
        for (int bi = 0; bi < lengthB; bi++) {
            for (int ai = 0; ai < lengthA; ai++) {
                final Object valueA = getOperandValue(operandA, sectionNode, form, ai);
                final Object valueB = getOperandValue(operandB, sectionNode, form, bi);
                if (evalSectionCondition(valueA, valueB, comparator) == !requireAllMulti) {
                    res = !requireAllMulti;
                }
            }
        }
        return res;
    }

    /*
     * Given a "condition" node from the "Questionnaires" and a "cards:AnswerSection" node from the "Forms" and a
     * NodeBuilder type object referring to the parent of the "cards:AnswerSection" node, evaluate the boolean
     * expression defined by the descendants of the "condition" Node.
     */
    private static boolean evaluateConditionNodeRecursive(final Node conditionNode, final Node sectionNode,
        final NodeBuilder form) throws RepositoryException
    {
        if ("cards:ConditionalGroup".equals(conditionNode.getPrimaryNodeType().getName())) {
            // Is this an OR or an AND
            final boolean requireAll = conditionNode.getProperty(PROP_REQUIRE_ALL).getBoolean();
            // Evaluate recursively
            @SuppressWarnings("unchecked")
            final Iterator<Node> conditionChildren = conditionNode.getNodes();
            boolean downstreamResult = false;
            if (requireAll) {
                downstreamResult = true;
            }
            while (conditionChildren.hasNext()) {
                final boolean partialRes =
                    evaluateConditionNodeRecursive(conditionChildren.next(), sectionNode, form);
                if (requireAll) {
                    downstreamResult = downstreamResult && partialRes;
                } else {
                    downstreamResult = downstreamResult || partialRes;
                }
            }
            return downstreamResult;
        } else if ("cards:Conditional".equals(conditionNode.getPrimaryNodeType().getName())) {
            final String comparator = conditionNode.getProperty("comparator").getString();
            final Node operandB = conditionNode.getNode("operandB");
            final Node operandA = conditionNode.getNode("operandA");

            return evalOperands(operandA, operandB, comparator, sectionNode, form);
        }
        // If all goes wrong
        return false;
    }

    public static boolean isConditionSatisfied(final Session resourceSession,
        final NodeBuilder answerSection, final NodeBuilder form) throws RepositoryException
    {
        final Node sectionNode = getSectionNode(resourceSession, answerSection);
        if (sectionNode != null) {
            final Node conditionNode = findCondition(sectionNode);
            /*
             * Recursively go through all children of the condition node and determine if this condition node evaluates
             * to True or False.
             */
            if (conditionNode != null && !evaluateConditionNodeRecursive(conditionNode, sectionNode, form)) {
                return false;
            }
        }
        return true;
    }

    private static Node findCondition(final Node section)
    {
        try {
            final NodeIterator children = section.getNodes();
            while (children.hasNext()) {
                final Node child = children.nextNode();
                if (child.isNodeType("cards:Conditional") || child.isNodeType("cards:ConditionalGroup")) {
                    return child;
                }
            }
        } catch (final RepositoryException e) {
            // Not expected
        }
        return null;
    }

    private static long toLong(Object o)
    {
        long result = 0;
        if (o instanceof Number) {
            result = ((Number) o).longValue();
        } else if (o instanceof String) {
            result = Long.valueOf((String) o);
        }
        return result;
    }

    private static double toDouble(Object o)
    {
        double result = Double.NaN;
        if (o instanceof Number) {
            result = ((Number) o).doubleValue();
        } else if (o instanceof String) {
            result = Double.valueOf((String) o);
        }
        return result;
    }

    private static BigDecimal toDecimal(Object o)
    {
        BigDecimal result = null;
        if (o instanceof BigDecimal) {
            result = (BigDecimal) o;
        } else if (o instanceof Long) {
            result = BigDecimal.valueOf((Long) o);
        } else if (o instanceof Integer) {
            result = BigDecimal.valueOf((Integer) o);
        } else if (o instanceof Double) {
            result = BigDecimal.valueOf((Double) o);
        } else if (o instanceof Float) {
            result = BigDecimal.valueOf((Float) o);
        } else if (o instanceof String) {
            result = new BigDecimal((String) o);
        }
        return result;
    }

    private static boolean toBoolean(Object o)
    {
        boolean result = false;
        if (o instanceof Boolean) {
            result = (Boolean) o;
        } else if (o instanceof String) {
            result = Boolean.getBoolean((String) o);
        }
        return result;
    }

    private static Calendar toDate(Object o)
    {
        Calendar result = null;
        if (o instanceof Calendar) {
            result = (Calendar) o;
        } else if (o instanceof Date) {
            result = Calendar.getInstance();
            result.setTime((Date) o);
        } else if (o instanceof String) {
            result = GregorianCalendar.from(ZonedDateTime.parse((String) o, DateTimeFormatter.ISO_DATE_TIME));
        }
        return result;
    }
}
