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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.utils.ConditionalUtils;

public final class ConditionalSectionUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionalSectionUtils.class);

    private static final String PROP_QUESTION = "question";

    private static final String PROP_IS_REFERENCE = "isReference";

    private static final String PROP_TYPE = "dataType";

    private static final String PROP_VALUE = "value";

    private static final String PROP_REQUIRE_ALL = "requireAll";


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
     * Get an operand of a specified type from the specified nodes.
     * @param node The conditional operand node;
     *             used to determine the operand value for non-reference operands
     *             or where the operand value should be retrieved from for reference operands
     * @param type The type of the operand value
     * @param sectionNode the {@code cards:Section} node under which the conditional resides
     * @param form the {@code cards:Form} node being evaluated
     * @return a new operand containing the retrieved values
     * @throws RepositoryException if an unexpected error occurs
     */
    private static ConditionalUtils.Operand getOperand(final Node node, final ConditionalUtils.OperandType type,
        final Node sectionNode, final NodeBuilder form)
        throws RepositoryException
    {
        if (node == null || !node.hasProperty(PROP_VALUE) || !node.hasProperty(PROP_IS_REFERENCE)) {
            return new ConditionalOperand(type, null, false);
        }
        boolean reference = node.getProperty(PROP_IS_REFERENCE).getValue().getBoolean();
        if (reference) {
            PropertyState answerProperty = getPropertyStateFromRef(node, sectionNode, form);

            if (answerProperty != null) {
                @SuppressWarnings("unchecked")
                Iterable<Comparable<Object>> answerValues =
                    (Iterable<Comparable<Object>>) answerProperty.getValue(type.getOakType());
                return new ConditionalOperand(answerValues, reference);
            } else {
                return new ConditionalOperand(reference);
            }
        } else {
            return new ConditionalOperand(type, node.getProperty(PROP_VALUE), reference);
        }
    }

    /**
     * An operand of a conditional, either {@code operandA} or {@code operandB}, exposes either the actual values stored
     * in the form or the constants specified in the operand node.
     */
    private static class ConditionalOperand extends ConditionalUtils.Operand
    {
        protected boolean reference;

        ConditionalOperand(final ConditionalUtils.OperandType type, final Property property, final boolean reference)
            throws RepositoryException
        {
            super(type, property);
            this.reference = reference;
        }

        ConditionalOperand(final Iterable<Comparable<Object>> values, final boolean reference)
            throws RepositoryException
        {
            super(values);
            this.reference = reference;
        }

        ConditionalOperand(final boolean reference)
        {
            super();
            this.reference = reference;
        }

        @Override
        public String toString()
        {
            return (this.reference ? "@" : "") + this.values.toString();
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
        private final ConditionalUtils.Operand left;

        private final ConditionalUtils.Operand right;

        private final ConditionalUtils.Operator operator;

        Condition(final Node node, final Node sectionNode, final NodeBuilder form) throws RepositoryException
        {
            this.operator = ConditionalUtils.Operator.parse(node.getProperty("comparator").getString());
            final ConditionalUtils.OperandType type = node.hasProperty(PROP_TYPE)
                ? ConditionalUtils.OperandType.parse(node.getProperty(PROP_TYPE).getValue().getString())
                : ConditionalUtils.OperandType.TEXT;
            this.left =
                getOperand(node.hasNode("operandA") ? node.getNode("operandA") : null, type, sectionNode,
                    form);
            this.right =
                getOperand(node.hasNode("operandB") ? node.getNode("operandB") : null, type, sectionNode,
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
