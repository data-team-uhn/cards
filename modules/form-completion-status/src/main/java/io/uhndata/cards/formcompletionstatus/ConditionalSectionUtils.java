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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

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

    private static final String PROP_TYPE = "dataType";

    private static final String PROP_VALUE = "value";

    private static final String PROP_REQUIRE_ALL = "requireAll";

    private static final BiPredicate<Operand, Operand> SINGLE_LISTS =
        (a, b) -> (a.getValues().size() == 1 && b.getValues().size() == 1);

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

    public static boolean isConditionSatisfied(final Session resourceSession,
        final NodeBuilder answerSection, final NodeBuilder form) throws RepositoryException
    {
        final Node sectionNode = getSectionNode(resourceSession, answerSection);
        if (sectionNode != null) {
            final Conditional conditional = Conditional.findConditional(sectionNode, form);
            if (conditional != null) {
                return conditional.isSatisfied();
            }
        }
        return true;
    }

    /**
     * The data type of the operand values, as indicated in the {@code dataType} property of the
     * {@code cards:Conditional} node.
     */
    @SuppressWarnings("unchecked")
    private enum OperandType
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
     * An operand of a conditional, either {@code operandA} or {@code operandB}, exposes either the actual values stored
     * in the form or the constants specified in the operand node.
     */
    private static class Operand
    {
        private final List<Comparable<Object>> values;

        private final boolean reference;

        Operand(final Node node, final OperandType type, final Node sectionNode, final NodeBuilder form)
            throws RepositoryException
        {
            this.values = new ArrayList<>();
            if (node == null || !node.hasProperty(PROP_VALUE) || !node.hasProperty(PROP_IS_REFERENCE)) {
                this.reference = false;
                return;
            }
            this.reference = node.getProperty(PROP_IS_REFERENCE).getValue().getBoolean();
            if (this.reference) {
                PropertyState answerProperty = getPropertyStateFromRef(node, sectionNode, form);

                if (answerProperty != null) {
                    @SuppressWarnings("unchecked")
                    Iterable<Comparable<Object>> answerValues =
                        (Iterable<Comparable<Object>>) answerProperty.getValue(type.getOakType());
                    answerValues.forEach(v -> this.values.add(v));
                }
            } else {
                Property valueProp = node.getProperty(PROP_VALUE);
                if (valueProp.isMultiple()) {
                    for (Value v : valueProp.getValues()) {
                        this.values.add(type.getValue(v));
                    }
                } else {
                    this.values.add(type.getValue(valueProp.getValue()));
                }
            }
        }

        /**
         * Retrieve the values for this operand, either the answers from the form or the constants specified in the
         * operand node.
         *
         * @return a list of values, empty if the operand doesn't actually exist or if there are no actual values in the
         *         form
         */
        public List<Comparable<Object>> getValues()
        {
            return this.values;
        }

        @Override
        public String toString()
        {
            return (this.reference ? "@" : "") + this.values.toString();
        }
    }

    /**
     * The operator of a conditional, includes a method to evaluate it on actual operand values.
     */
    private enum Operator
    {
        EQ("=", (left, right) -> left.getValues().stream()
            .allMatch(vl -> right.getValues().stream().anyMatch(vr -> (vl.compareTo(vr) == 0)))
            && right.getValues().stream()
                .allMatch(vr -> left.getValues().stream().anyMatch(vl -> (vl.compareTo(vr) == 0))),
            true),
        NEQ("<>", (left, right) -> !EQ.evaluate(left, right), true),
        LT("<", (left, right) -> SINGLE_LISTS.test(left, right)
            && (left.getValues().get(0).compareTo(right.getValues().get(0)) < 0),
            false),
        LTE("<=", (left, right) -> SINGLE_LISTS.test(left, right)
            && (left.getValues().get(0).compareTo(right.getValues().get(0)) <= 0), false),
        GT(">", (left, right) -> SINGLE_LISTS.test(left, right)
            && (left.getValues().get(0).compareTo(right.getValues().get(0)) > 0), false),
        GTE(">=", (left, right) -> SINGLE_LISTS.test(left, right)
            && (left.getValues().get(0).compareTo(right.getValues().get(0)) >= 0), false),
        EMPTY("is empty", (left, right) -> left.getValues().size() == 0 && right.getValues().size() == 0, true),
        NOT_EMPTY("is not empty", (left, right) -> !EMPTY.evaluate(left, right), true),
        INCLUDES("includes",
            (left, right) -> right.values.stream().allMatch(
                vl -> left.getValues().stream().anyMatch(vr -> (vr.compareTo(vl) == 0))),
            true),
        INCLUDES_ANY("includes any",
            (left, right) -> right.values.stream().anyMatch(
                vl -> left.getValues().stream().anyMatch(vr -> (vr.compareTo(vl) == 0))),
            true),
        EXCLUDES("excludes",
            (left, right) -> right.values.stream().noneMatch(
                vl -> left.getValues().stream().anyMatch(vr -> (vr.compareTo(vl) == 0))),
            true),
        EXCLUDES_ANY("excludes any",
            (left, right) -> right.values.stream().anyMatch(
                vl -> left.getValues().stream().noneMatch(vr -> (vr.compareTo(vl) == 0))),
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
            if (!this.supportsMultivalue && (left.getValues().size() > 1 || right.getValues().size() > 1)) {
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

    /**
     * Generic interface for both single conditionals and conditional groups.
     */
    private interface Conditional
    {
        /**
         * Check if the actual form values pass this conditional.
         *
         * @return {@code true} if the current state of the form passes the conditional
         */
        boolean isSatisfied();

        /**
         * Look for a conditional child node of the passed section node.
         *
         * @param section a {@code cards:Section} node
         * @param form a {@code cards:Form} node
         * @return the section's conditional node, or {@code null} if no conditional is specified
         */
        static Conditional findConditional(final Node section, final NodeBuilder form)
        {
            Conditional result = null;
            try {
                final NodeIterator children = section.getNodes();
                while (children.hasNext()) {
                    result = parse(children.nextNode(), section, form);
                    if (result != null) {
                        return result;
                    }
                }
            } catch (final RepositoryException e) {
                // Not expected
            }
            return result;
        }

        /**
         * Convert a conditional node into the equivalent Java object.
         *
         * @param node either a {@code cards:Conditional} or {@code cards:ConditionalGroup} node
         * @param sectionNode the {@code cards:Section} node under which the conditional resides
         * @param form the {@code cards:Form} node being evaluated
         * @return either a {@link Condition} or {@code ConditionGroup} object, or {@code null} if the passed node is
         *         not a conditional
         */
        static Conditional parse(final Node node, final Node sectionNode, final NodeBuilder form)
        {
            try {
                if (node.isNodeType("cards:Conditional")) {
                    return new Condition(node, sectionNode, form);
                } else if (node.isNodeType("cards:ConditionalGroup")) {
                    return new ConditionGroup(node, sectionNode, form);
                }
            } catch (RepositoryException e) {
                // Not expected
            }
            return null;
        }
    }

    /**
     * A single condition stored in a {@code cards:Conditional} node.
     */
    private static class Condition implements Conditional
    {
        private final Operand left;

        private final Operand right;

        private final Operator operator;

        Condition(final Node node, final Node sectionNode, final NodeBuilder form) throws RepositoryException
        {
            this.operator = Operator.parse(node.getProperty("comparator").getString());
            final OperandType type = node.hasProperty(PROP_TYPE)
                ? OperandType.parse(node.getProperty(PROP_TYPE).getValue().getString()) : OperandType.TEXT;
            this.left =
                new Operand(node.hasNode("operandA") ? node.getNode("operandA") : null, type, sectionNode,
                    form);
            this.right =
                new Operand(node.hasNode("operandB") ? node.getNode("operandB") : null, type, sectionNode,
                    form);
        }

        @Override
        public boolean isSatisfied()
        {
            return this.operator.evaluate(this.left, this.right);
        }

        @Override
        public String toString()
        {
            return this.left + " " + this.operator + " " + this.right;
        }
    }

    /**
     * A group of conditions stored in a {@code cards:ConditionalGroup} node.
     */
    private static class ConditionGroup implements Conditional
    {
        private final List<Conditional> children;

        private final boolean requireAll;

        ConditionGroup(final Node node, final Node sectionNode, final NodeBuilder form)
            throws RepositoryException
        {
            this.requireAll = node.getProperty(PROP_REQUIRE_ALL).getBoolean();
            this.children = new ArrayList<>();
            final NodeIterator childNodes = node.getNodes();
            while (childNodes.hasNext()) {
                Conditional child = Conditional.parse(childNodes.nextNode(), sectionNode, form);
                if (child != null) {
                    this.children.add(child);
                }
            }
        }

        @Override
        public boolean isSatisfied()
        {
            return this.requireAll ? this.children.stream().allMatch(Conditional::isSatisfied)
                : this.children.stream().anyMatch(Conditional::isSatisfied);
        }

        @Override
        public String toString()
        {
            return this.children.toString();
        }
    }
}
