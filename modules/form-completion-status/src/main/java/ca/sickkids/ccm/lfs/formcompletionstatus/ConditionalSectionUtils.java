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
package ca.sickkids.ccm.lfs.formcompletionstatus;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import javax.jcr.Node;
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

    /**
     * Hide the utility class constructor.
     */
    private ConditionalSectionUtils()
    {
    }

    /**
     * Gets the questionnaire section node referenced by the AnswerSection NodeBuilder nb.
     *
     * @return the section Node object referenced by NodeBuilder nb
     */
    private static Node getSectionNode(final Session resourceSession, final NodeBuilder nb)
    {
        try {
            if (nb.hasProperty("section")) {
                //final Session resourceSession = this.currentResourceResolver.adaptTo(Session.class);
                final String sectionNodeReference = nb.getProperty("section").getValue(Type.REFERENCE);
                final Node sectionNode = resourceSession.getNodeByIdentifier(sectionNodeReference);
                return sectionNode;
            }
        } catch (final RepositoryException ex) {
            return null;
        }
        return null;
    }

    /**
     * Returns the first NodeBuilder with a question value equal to the String uuid that is a child of the NodeBuilder
     * nb. If no such child can be found, null is returned
     *
     * @param nb the NodeBuilder to search through its children
     * @param uuid the UUID String for which the child's question property must be equal to
     * @return the first NodeBuilder with a question value equal to the String uuid that is a child of the NodeBuilder
     *         nb, or null if such node does not exist.
     */
    private static NodeBuilder getChildNodeWithQuestion(final NodeBuilder nb, final String uuid)
    {
        final Iterable<String> childrenNames = nb.getChildNodeNames();
        final Iterator<String> childrenNamesIter = childrenNames.iterator();
        while (childrenNamesIter.hasNext()) {
            final String selectedChildName = childrenNamesIter.next();
            final NodeBuilder selectedChild = nb.getChildNode(selectedChildName);
            if (selectedChild.hasProperty(PROP_QUESTION)) {
                if (uuid.equals(selectedChild.getProperty(PROP_QUESTION).getValue(Type.STRING))) {
                    return selectedChild;
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
            //calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
            calendar.setTime(date);
            //calendar.set(Calendar.HOUR, 0);
            //calendar.set(Calendar.MINUTE, 0);
            //calendar.set(Calendar.SECOND, 0);
            //calendar.set(Calendar.MILLISECOND, 0);
            return calendar;
        } catch (final ParseException e) {
            LOGGER.warn("PARSING DATE FAILED: Invalid date {}", str);
            return null;
        }
    }

    private static int getPropertyStateType(final PropertyState ps)
    {
        int ret = PropertyType.UNDEFINED;
        if (Type.STRING.equals(ps.getType())) {
            ret = PropertyType.STRING;
        } else if (Type.LONG.equals(ps.getType())) {
            ret = PropertyType.LONG;
        } else if (Type.DOUBLE.equals(ps.getType())) {
            ret = PropertyType.DOUBLE;
        } else if (Type.DECIMAL.equals(ps.getType())) {
            ret = PropertyType.DECIMAL;
        } else if (Type.BOOLEAN.equals(ps.getType())) {
            ret = PropertyType.BOOLEAN;
        } else if (Type.DATE.equals(ps.getType())) {
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

    private static boolean evalSectionEq(final Object propA, final Object propB)
        throws RepositoryException, ValueFormatException
    {
        boolean testResult = false;
        switch (getPropertyObjectType(propA)) {
            case PropertyType.STRING:
                testResult = propA.equals(propB);
                break;
            case PropertyType.LONG:
                testResult = (propA == propB);
                break;
            case PropertyType.DOUBLE:
                testResult = (propA.equals(propB));
                break;
            case PropertyType.DECIMAL:
                testResult = (((BigDecimal) propA).compareTo((BigDecimal) propB) == 0);
                break;
            case PropertyType.BOOLEAN:
                testResult = (propA == propB);
                break;
            case PropertyType.DATE:
                testResult = propA.equals(propB);
                break;
            default:
                break;
        }
        return testResult;
    }

    private static boolean evalSectionLt(final Object propA, final Object propB)
        throws RepositoryException, ValueFormatException
    {
        boolean testResult = false;
        switch (getPropertyObjectType(propA)) {
            case PropertyType.LONG:
                testResult = ((long) propA < (long) propB);
                break;
            case PropertyType.DOUBLE:
                testResult = ((double) propA < (double) propB);
                break;
            case PropertyType.DATE:
                testResult = ((Calendar) propA).before((Calendar) propB);
                break;
            default:
                break;
        }
        return testResult;
    }

    private static boolean evalSectionGt(final Object propA, final Object propB)
        throws RepositoryException, ValueFormatException
    {
        boolean testResult = false;
        switch (getPropertyObjectType(propA)) {
            case PropertyType.LONG:
                testResult = ((long) propA > (long) propB);
                break;
            case PropertyType.DOUBLE:
                testResult = ((double) propA > (double) propB);
                break;
            case PropertyType.DATE:
                testResult = ((Calendar) propA).after((Calendar) propB);
                break;
            default:
                break;
        }
        return testResult;
    }

    /**
     * Evaluates the boolean expression {propA} {operator} {propB}.
     */
    private static boolean evalSectionCondition(final Object propA, final Object propB, final String operator)
        throws RepositoryException, ValueFormatException
    {
        if ("=".equals(operator) || "<>".equals(operator)) {
            /*
             * Type.STRING uses .equals()
             * Everything else uses ==
             */
            boolean testResult = evalSectionEq(propA, propB);
            if ("<>".equals(operator)) {
                testResult = !testResult;
            }
            return testResult;
        } else if ("is empty".equals(operator) || "is not empty".equals(operator)) {
            boolean testResult = (propA == null);
            if ("is not empty".equals(operator)) {
                testResult = !testResult;
            }
            return testResult;
        } else if ("<".equals(operator)) {
            return evalSectionLt(propA, propB);
        } else if (">".equals(operator)) {
            return evalSectionGt(propA, propB);
        }
        // If we can't evaluate it, assume it to be false
        return false;
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

    private static Object getObjectFromPropertyState(PropertyState ps)
    {
        Object ret = null;
        switch (getPropertyStateType(ps))
        {
            case PropertyType.STRING:
                ret = ps.getValue(Type.STRING);
                break;
            case PropertyType.LONG:
                ret = ps.getValue(Type.LONG);
                break;
            case PropertyType.DOUBLE:
                ret = ps.getValue(Type.DOUBLE);
                break;
            case PropertyType.DECIMAL:
                ret = ps.getValue(Type.DECIMAL);
                break;
            case PropertyType.BOOLEAN:
                ret = ps.getValue(Type.BOOLEAN);
                break;
            case PropertyType.DATE:
                ret = parseDate(ps.getValue(Type.DATE));
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
                //Calendar calendar = Calendar.getInstance();
                //calendar.set(val.getDate().get(Calendar.YEAR),
                //    val.getDate().get(Calendar.MONTH),
                //    val.getDate().get(Calendar.DAY_OF_MONTH));
                //ret = val.getDate();
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

    private static Object getOperandValue(final Node operand, final Node sectionNode, final NodeBuilder prevNb)
        throws RepositoryException
    {
        Object returnedValue = null;
        if (operand.getProperty(PROP_IS_REFERENCE).getValue().getBoolean()) {
            String key = operand.getProperty(PROP_VALUE).getValues()[0].getString();
            // Sanitize
            key = sanitizeNodeName(key);
            final Node sectionNodeParent = sectionNode.getParent();
            final Node keyNode = sectionNodeParent.getNode(key);
            final String keyNodeUUID = keyNode.getIdentifier();
            // Get the node from the Form containing the answer to keyNode
            final NodeBuilder conditionalFormNode = getChildNodeWithQuestion(prevNb, keyNodeUUID);
            if (conditionalFormNode == null) {
                return null;
            }
            final PropertyState operandProp = conditionalFormNode.getProperty(PROP_VALUE);
            if (operandProp != null) {
                returnedValue = getObjectFromPropertyState(operandProp);
            }
        } else {
            Property nodeProp = operand.getProperty(PROP_VALUE);
            Value nodeVal;
            if (nodeProp.isMultiple()) {
                nodeVal = nodeProp.getValues()[0];
            } else {
                nodeVal = nodeProp.getValue();
            }
            returnedValue = getObjectFromValue(nodeVal);
        }
        return returnedValue;
    }

    /*
     * Given a "condition" node from the "Questionnaires" and a "lfs:AnswerSection" node from the "Forms" and a
     * NodeBuilder type object referring to the parent of the "lfs:AnswerSection" node, evaluate the boolean expression
     * defined by the descendants of the "condition" Node.
     */
    private static boolean evaluateConditionNodeRecursive(final Node conditionNode, final Node sectionNode,
        final NodeBuilder prevNb)
        throws RepositoryException
    {
        if ("lfs:ConditionalGroup".equals(conditionNode.getProperty("jcr:primaryType").getString())) {
            // Is this an OR or an AND
            final boolean requireAll = conditionNode.getProperty("requireAll").getBoolean();
            // Evaluate recursively
            final Iterator<Node> conditionChildren = conditionNode.getNodes();
            boolean downstreamResult = false;
            if (requireAll) {
                downstreamResult = true;
            }
            while (conditionChildren.hasNext()) {
                final boolean partialRes =
                    evaluateConditionNodeRecursive(conditionChildren.next(), sectionNode, prevNb);
                if (requireAll) {
                    downstreamResult = downstreamResult && partialRes;
                } else {
                    downstreamResult = downstreamResult || partialRes;
                }
            }
            return downstreamResult;
        } else if ("lfs:Conditional".equals(conditionNode.getProperty("jcr:primaryType").getString())) {
            final String comparator = conditionNode.getProperty("comparator").getString();
            final Node operandB = conditionNode.getNode("operandB");
            final Node operandA = conditionNode.getNode("operandA");

            final Object valueA = getOperandValue(operandA, sectionNode, prevNb);
            final Object valueB = getOperandValue(operandB, sectionNode, prevNb);
            return evalSectionCondition(valueA, valueB, comparator);
        }
        // If all goes wrong
        return false;
    }

    public static boolean isConditionSatisfied(final Session resourceSession,
        final NodeBuilder nb, final NodeBuilder prevNb) throws RepositoryException
    {
        final Node sectionNode = getSectionNode(resourceSession, nb);
        if (sectionNode.hasNode("condition")) {
            final Node conditionNode = sectionNode.getNode("condition");
            /*
             * Recursively go through all children of the condition node and determine if this condition node evaluates
             * to True or False.
             */
            if (evaluateConditionNodeRecursive(conditionNode, sectionNode, prevNb)) {
                return true;
            }
        }
        return false;
    }
}
