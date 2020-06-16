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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link Editor} that verifies the correctness and completeness of submitted questionnaire answers and sets the
 * INVALID and INCOMPLETE status flags accordingly.
 *
 * @version $Id$
 */
public class AnswerCompletionStatusEditor extends DefaultEditor
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AnswerCompletionStatusEditor.class);

    private static final String PROP_VALUE = "value";

    private static final String PROP_QUESTION = "question";

    private static final String STATUS_FLAGS = "statusFlags";

    private static final String STATUS_FLAG_INCOMPLETE = "INCOMPLETE";

    private static final String STATUS_FLAG_INVALID = "INVALID";

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    // A ResourceResolver object is passed in during the initialization of this object. This ResourceResolver
    // is later used for obtaining the constraints on the answers submitted to a question.
    private final ResourceResolver currentResourceResolver;

    // This holds a list of NodeBuilders with the first item corresponding to the root of the JCR tree
    // and the last item corresponding to the current node. By keeping this list, one is capable of
    // moving up the tree and setting status flags of ancestor nodes based on the status flags of a
    // descendant node.
    private final List<NodeBuilder> currentNodeBuilderPath;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder a list of NodeBuilder objects starting from the root of the JCR tree and moving down towards
     *            the current node.
     * @param resourceResolver a ResourceResolver object used to obtain answer constraints
     */
    public AnswerCompletionStatusEditor(final List<NodeBuilder> nodeBuilder, final ResourceResolver resourceResolver)
    {
        this.currentNodeBuilderPath = nodeBuilder;
        this.currentNodeBuilder = nodeBuilder.get(nodeBuilder.size() - 1);
        this.currentResourceResolver = resourceResolver;
    }

    // Called when a new property is added
    @Override
    public void propertyAdded(final PropertyState after)
        throws CommitFailedException
    {
        propertyChanged(null, after);
        // Summarize all parents
        try {
            summarizeBuilders(this.currentNodeBuilderPath);
        } catch (final RepositoryException e) {
            return;
        }
    }

    // Called when the value of an existing property gets changed
    @Override
    public void propertyChanged(final PropertyState before, final PropertyState after)
        throws CommitFailedException
    {
        final Node questionNode = getQuestionNode(this.currentNodeBuilder);
        if (questionNode != null && PROP_VALUE.equals(after.getName())) {
            final Iterable<String> nodeAnswers = after.getValue(Type.STRINGS);
            final int numAnswers = iterableLength(nodeAnswers);
            final List<String> statusFlags = new ArrayList<>();
            if (checkInvalidAnswer(questionNode, numAnswers)) {
                statusFlags.add(STATUS_FLAG_INVALID);
                statusFlags.add(STATUS_FLAG_INCOMPLETE);
            } else {
                /*
                 * We are here because:
                 *     - minAnswers == 0 && maxAnswers == 0
                 *     - minAnswers == 0 && maxAnswers == 1 && numAnswers in range [0,1] (eg. optional radio button)
                 *     - minAnswers == 1 && maxAnswers == 0 && numAnswers in range [1,+INF) (eg. mandatory checkboxes)
                 *     - minAnswers == 1 && maxAnswers == 1 && numAnswers == 1 (eg. mandatory radio button)
                 *     - minAnswers == N && maxAnswers == 0 && numAnswers in range [N,+INF)
                 *         (eg. at least N (inclusive) checkboxes must be selected)
                 *     - minAnswers == 0 && maxAnswers == M && numAnswers in range [0, M]
                 *         (eg. at most M (inclusive) checkboxes must be selected)
                 *     - minAnswers == N && maxAnswers == M && numAnswers in range [N,M]
                 *         (eg. between N (inclusive) and M (inclusive) checkboxes must be selected)
                 */

                /*
                 * TODO: Implement validation rules and check them here
                 * Remove INVALID and INCOMPLETE flags if all validation rules pass
                 */
            }
            this.currentNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
            // Summarize all parents
            try {
                summarizeBuilders(this.currentNodeBuilderPath);
            } catch (final RepositoryException e) {
                LOGGER.warn("Could not run summarize(): {}", e.getMessage(), e);
            }
        }
    }

    // Called when a property is deleted
    @Override
    public void propertyDeleted(final PropertyState before)
        throws CommitFailedException
    {
        final Node questionNode = getQuestionNode(this.currentNodeBuilder);
        if (questionNode != null) {
            if (PROP_VALUE.equals(before.getName())) {
                final List<String> statusFlags = new ArrayList<>();
                // Only add the INVALID,INCOMPLETE flags if the given question requires more than zero answers
                if (checkInvalidAnswer(questionNode, 0)) {
                    statusFlags.add(STATUS_FLAG_INVALID);
                    statusFlags.add(STATUS_FLAG_INCOMPLETE);
                }
                this.currentNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);

                // Summarize all parents
                try {
                    summarizeBuilders(this.currentNodeBuilderPath);
                } catch (final RepositoryException e) {
                    LOGGER.warn("Could not run summarizeBuilders(): {}", e.getMessage(), e);
                }
            }
        }
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
        throws CommitFailedException
    {
        final Node questionNode = getQuestionNode(this.currentNodeBuilder.getChildNode(name));
        if (questionNode != null) {
            if (this.currentNodeBuilder.getChildNode(name).hasProperty(PROP_QUESTION)) {
                final List<String> statusFlags = new ArrayList<>();
                // Only add the INCOMPLETE flag if the given question requires more than zero answers
                if (checkInvalidAnswer(questionNode, 0)) {
                    statusFlags.add(STATUS_FLAG_INCOMPLETE);
                }
                this.currentNodeBuilder.getChildNode(name).setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
            }
        }
        final List<NodeBuilder> tmpList = new ArrayList<>(this.currentNodeBuilderPath);
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.currentResourceResolver);
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        final List<NodeBuilder> tmpList = new ArrayList<>(this.currentNodeBuilderPath);
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.currentResourceResolver);
    }

    /**
     * Gets the question node associated with the answer for which this AnswerCompletionStatusEditor is an editor
     * thereof.
     *
     * @return the question Node object associated with this answer
     */
    private Node getQuestionNode(final NodeBuilder nb)
    {
        try {
            if (nb.hasProperty(PROP_QUESTION)) {
                final Session resourceSession = this.currentResourceResolver.adaptTo(Session.class);
                final String questionNodeReference = nb.getProperty(PROP_QUESTION).getValue(Type.REFERENCE);
                final Node questionNode = resourceSession.getNodeByIdentifier(questionNodeReference);
                return questionNode;
            }
        } catch (final RepositoryException ex) {
            return null;
        }
        return null;
    }

    /**
     * Gets the questionnaire section node referenced by the AnswerSection NodeBuilder nb.
     *
     * @return the section Node object referenced by NodeBuilder nb
     */
    private Node getSectionNode(final NodeBuilder nb)
    {
        try {
            if (nb.hasProperty("section")) {
                final Session resourceSession = this.currentResourceResolver.adaptTo(Session.class);
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
     * Counts the number of items in an Iterable.
     *
     * @param iterable the Iterable object to be counted
     * @return the number of objects in the Iterable
     */
    private int iterableLength(final Iterable<?> iterable)
    {
        int len = 0;
        final Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            len++;
        }
        return len;
    }

    /**
     * Reports if a given number of answers is invalid for a given question.
     *
     * @param questionNode the Node to provide the minAnswers and maxAnswers properties
     * @return true if the number of answers is valid, false if it is not
     */
    private boolean checkInvalidAnswer(final Node questionNode, final int numAnswers)
    {
        try {
            final long minAnswers = questionNode.getProperty("minAnswers").getLong();
            final long maxAnswers = questionNode.getProperty("maxAnswers").getLong();
            if ((numAnswers < minAnswers && minAnswers != 0) || (numAnswers > maxAnswers && maxAnswers != 0)) {
                return true;
            }
        } catch (final RepositoryException ex) {
            // If something goes wrong then we definitely cannot have a valid answer
            return true;
        }
        return false;
    }

    private void summarizeBuilders(final List<NodeBuilder> nodeBuilders)
        throws RepositoryException
    {
        /*
         * i == 0 --> jcr:root
         * i == 1 --> jcr:root/Forms
         * i == 2 --> jcr:root/Forms/<some form object>
         */
        for (int i = nodeBuilders.size() - 2; i >= 2; i--) {
            summarizeBuilder(nodeBuilders.get(i), nodeBuilders.get(i - 1));
        }
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
    private NodeBuilder getChildNodeWithQuestion(final NodeBuilder nb, final String uuid)
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
    private Calendar parseDate(final String str)
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

    private int getPropertyStateType(final PropertyState ps)
    {
        int ret = PropertyType.STRING;
        if (Type.LONG.equals(ps.getType())) {
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

    private boolean evalSectionEq(final PropertyState propA, final Value valB)
        throws RepositoryException, ValueFormatException
    {
        boolean testResult = false;
        switch (getPropertyStateType(propA)) {
            case PropertyType.STRING:
                testResult = propA.getValue(Type.STRING).equals(valB.getString());
                break;
            case PropertyType.LONG:
                testResult = (propA.getValue(Type.LONG) == valB.getLong());
                break;
            case PropertyType.DOUBLE:
                testResult = (propA.getValue(Type.DOUBLE) == valB.getDouble());
                break;
            case PropertyType.DECIMAL:
                testResult = (propA.getValue(Type.DECIMAL) == valB.getDecimal());
                break;
            case PropertyType.BOOLEAN:
                testResult = (propA.getValue(Type.BOOLEAN) == valB.getBoolean());
                break;
            case PropertyType.DATE:
                testResult = parseDate(propA.getValue(Type.DATE)).equals(parseDate(valB.getString()));
                break;
            default:
                break;
        }
        return testResult;
    }

    private boolean evalSectionLt(final PropertyState propA, final Value valB)
        throws RepositoryException, ValueFormatException
    {
        boolean testResult = false;
        switch (getPropertyStateType(propA)) {
            case PropertyType.LONG:
                testResult = (propA.getValue(Type.LONG) < valB.getLong());
                break;
            case PropertyType.DOUBLE:
                testResult = (propA.getValue(Type.DOUBLE) < valB.getDouble());
                break;
            case PropertyType.DATE:
                testResult = parseDate(propA.getValue(Type.DATE)).before(parseDate(valB.getString()));
                break;
            default:
                break;
        }
        return testResult;
    }

    private boolean evalSectionGt(final PropertyState propA, final Value valB)
        throws RepositoryException, ValueFormatException
    {
        boolean testResult = false;
        switch (getPropertyStateType(propA)) {
            case PropertyType.LONG:
                testResult = (propA.getValue(Type.LONG) > valB.getLong());
                break;
            case PropertyType.DOUBLE:
                testResult = (propA.getValue(Type.DOUBLE) > valB.getDouble());
                break;
            case PropertyType.DATE:
                testResult = parseDate(propA.getValue(Type.DATE)).after(parseDate(valB.getString()));
                break;
            default:
                break;
        }
        return testResult;
    }

    /**
     * Evaluates the boolean expression {propA} {operator} {propB}.
     */
    private boolean evalSectionCondition(final PropertyState propA, final Property propB, final String operator)
        throws RepositoryException, ValueFormatException
    {
        Value valB;
        if (propB.isMultiple()) {
            valB = propB.getValues()[0];
        } else {
            valB = propB.getValue();
        }
        if ("=".equals(operator) || "<>".equals(operator)) {
            /*
             * Type.STRING uses .equals()
             * Everything else uses ==
             */
            boolean testResult = evalSectionEq(propA, valB);
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
            return evalSectionLt(propA, valB);
        } else if (">".equals(operator)) {
            return evalSectionGt(propA, valB);
        }
        // If we can't evaluate it, assume it to be false
        return false;
    }

    /*
     * Read in a string, inStr, and return it with any non-allowed chars removed.
     */
    private String sanitizeNodeName(final String inStr)
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

    /*
     * Given a "condition" node from the "Questionnaires" and a "lfs:AnswerSection" node from the "Forms" and a
     * NodeBuilder type object referring to the parent of the "lfs:AnswerSection" node, evaluate the boolean expression
     * defined by the descendants of the "condition" Node.
     */
    private boolean evaluateConditionNodeRecursive(final Node conditionNode, final Node sectionNode,
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
            String keyA = operandA.getProperty(PROP_VALUE).getValues()[0].getString();
            // Sanitize
            keyA = sanitizeNodeName(keyA);
            // Get the node from the Questionnaire corresponding to keyA
            final Node sectionNodeParent = sectionNode.getParent();
            final Node keyANode = sectionNodeParent.getNode(keyA);
            final String keyANodeUUID = keyANode.getIdentifier();
            // Get the node from the Form containing the answer to keyANode
            final NodeBuilder conditionalFormNode = getChildNodeWithQuestion(prevNb, keyANodeUUID);
            final PropertyState comparedProp = conditionalFormNode.getProperty(PROP_VALUE);
            final Property referenceProp = operandB.getProperty(PROP_VALUE);
            return evalSectionCondition(comparedProp, referenceProp, comparator);
        }
        // If all goes wrong
        return false;
    }

    private boolean isConditionSatisfied(final NodeBuilder nb, final NodeBuilder prevNb)
        throws RepositoryException
    {
        final Node sectionNode = getSectionNode(nb);
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

    private void summarizeBuilder(final NodeBuilder selectedNodeBuilder, final NodeBuilder prevNb)
        throws RepositoryException
    {
        // Iterate through all children of this node
        final Iterable<String> childrenNames = selectedNodeBuilder.getChildNodeNames();
        final Iterator<String> childrenNamesIter = childrenNames.iterator();
        boolean isInvalid = false;
        boolean isIncomplete = false;
        while (childrenNamesIter.hasNext()) {
            final String selectedChildName = childrenNamesIter.next();
            final NodeBuilder selectedChild = selectedNodeBuilder.getChildNode(selectedChildName);
            if ("lfs:AnswerSection".equals(selectedChild.getProperty("jcr:primaryType").getValue(Type.STRING))) {
                if (!isConditionSatisfied(selectedChild, selectedNodeBuilder)) {
                    continue;
                }
            }
            // Is selectedChild - invalid? , incomplete?
            if (selectedChild.hasProperty(STATUS_FLAGS)) {
                final Iterable<String> selectedProps = selectedChild.getProperty(STATUS_FLAGS).getValue(Type.STRINGS);
                final Iterator<String> selectedPropsIter = selectedProps.iterator();
                while (selectedPropsIter.hasNext()) {
                    final String thisStr = selectedPropsIter.next();
                    if (STATUS_FLAG_INVALID.equals(thisStr)) {
                        isInvalid = true;
                    }
                    if (STATUS_FLAG_INCOMPLETE.equals(thisStr)) {
                        isIncomplete = true;
                    }
                }
            }
        }
        // Set the flags in selectedNodeBuilder accordingly
        final List<String> statusFlags = new ArrayList<>();
        if (isInvalid) {
            statusFlags.add(STATUS_FLAG_INVALID);
        }
        if (isIncomplete) {
            statusFlags.add(STATUS_FLAG_INCOMPLETE);
        }
        // Write these statusFlags to the JCR repo
        selectedNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
    }
}
