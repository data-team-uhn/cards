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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
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

    private final NodeBuilder form;

    private final Session session;

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
     * @param form the form node found up the tree, if any; may be {@code null} if no form node has been encountered so
     *            far
     * @param session the current JCR session
     */
    public AnswerCompletionStatusEditor(final List<NodeBuilder> nodeBuilder, final NodeBuilder form,
        final Session session)
    {
        this.currentNodeBuilderPath = nodeBuilder;
        this.currentNodeBuilder = nodeBuilder.get(nodeBuilder.size() - 1);
        this.session = session;
        if (isForm(this.currentNodeBuilder)) {
            this.form = this.currentNodeBuilder;
        } else {
            this.form = form;
        }
    }

    // Called when a new property is added
    @Override
    public void propertyAdded(final PropertyState after)
        throws CommitFailedException
    {
        propertyChanged(null, after);
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
            final Set<String> statusFlags = new TreeSet<>();
            this.currentNodeBuilder.getProperty(STATUS_FLAGS).getValue(Type.STRINGS).forEach(statusFlags::add);
            if (checkInvalidAnswer(questionNode, numAnswers)) {
                statusFlags.add(STATUS_FLAG_INVALID);
                statusFlags.add(STATUS_FLAG_INCOMPLETE);
            } else {
                statusFlags.remove(STATUS_FLAG_INVALID);
                statusFlags.remove(STATUS_FLAG_INCOMPLETE);
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
            }
            this.currentNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
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
                final Set<String> statusFlags = new TreeSet<>();
                this.currentNodeBuilder.getProperty(STATUS_FLAGS).getValue(Type.STRINGS).forEach(statusFlags::add);
                // Only add the INVALID,INCOMPLETE flags if the given question requires more than zero answers
                if (checkInvalidAnswer(questionNode, 0)) {
                    statusFlags.add(STATUS_FLAG_INVALID);
                    statusFlags.add(STATUS_FLAG_INCOMPLETE);
                } else {
                    statusFlags.remove(STATUS_FLAG_INVALID);
                    statusFlags.remove(STATUS_FLAG_INCOMPLETE);
                }
                this.currentNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
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
            final Set<String> statusFlags = new TreeSet<>();
            if (after.hasProperty(STATUS_FLAGS)) {
                after.getProperty(STATUS_FLAGS).getValue(Type.STRINGS).forEach(statusFlags::add);
            }
            // Only add the INCOMPLETE flag if the given question requires more than zero answers
            if (checkInvalidAnswer(questionNode, 0)) {
                statusFlags.add(STATUS_FLAG_INCOMPLETE);
            } else {
                statusFlags.remove(STATUS_FLAG_INCOMPLETE);
            }
            this.currentNodeBuilder.getChildNode(name).setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
        }
        final List<NodeBuilder> tmpList = new ArrayList<>(this.currentNodeBuilderPath);
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.form, this.session);
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        final List<NodeBuilder> tmpList = new ArrayList<>(this.currentNodeBuilderPath);
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.form, this.session);
    }

    @Override
    public void leave(NodeState before, NodeState after)
        throws CommitFailedException
    {
        if (isForm(this.currentNodeBuilder) || isSection(this.currentNodeBuilder)) {
            try {
                summarize();
            } catch (RepositoryException e) {
                // This is not a fatal error, the form status is not required for a functional application
                LOGGER.warn("Unexpected exception while checking the completion status of form {}",
                    this.currentNodeBuilder.getString("jcr:uuid"));
            }
        }
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
                final String questionNodeReference = nb.getProperty(PROP_QUESTION).getValue(Type.REFERENCE);
                final Node questionNode = this.session.getNodeByIdentifier(questionNodeReference);
                return questionNode;
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
            final long minAnswers =
                questionNode.hasProperty("minAnswers") ? questionNode.getProperty("minAnswers").getLong() : 0;
            final long maxAnswers =
                questionNode.hasProperty("maxAnswers") ? questionNode.getProperty("maxAnswers").getLong() : 0;
            if ((numAnswers < minAnswers && minAnswers != 0) || (numAnswers > maxAnswers && maxAnswers != 0)) {
                return true;
            }
        } catch (final RepositoryException ex) {
            // If something goes wrong then we definitely cannot have a valid answer
            return true;
        }
        return false;
    }

    /**
     * Checks if a NodeBuilder represents an empty Form and returns true if that is the case. Otherwise, this method
     * returns false.
     */
    private boolean isEmptyForm(NodeBuilder n)
    {
        if (isForm(n)) {
            if (!(n.getChildNodeNames().iterator().hasNext())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gather all status flags from all the (satisfied) descendants of the current node and store them as the status
     * flags of the current node.
     *
     * @throws RepositoryException if accessing the repository fails
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private void summarize() throws RepositoryException
    {
        // Iterate through all children of this node
        final Iterator<String> childrenNames = this.currentNodeBuilder.getChildNodeNames().iterator();
        boolean isInvalid = false;
        // If this Form has no children yet, the form is brand new, thus incomplete
        boolean isIncomplete = isEmptyForm(this.currentNodeBuilder);
        while (childrenNames.hasNext()) {
            final String selectedChildName = childrenNames.next();
            final NodeBuilder selectedChild = this.currentNodeBuilder.getChildNode(selectedChildName);
            if (isSection(selectedChild)) {
                if (!ConditionalSectionUtils.isConditionSatisfied(this.session, selectedChild, this.form)) {
                    continue;
                }
            }
            // Is selectedChild - invalid? , incomplete?
            if (selectedChild.hasProperty(STATUS_FLAGS)) {
                final Iterable<String> selectedProps =
                    selectedChild.getProperty(STATUS_FLAGS).getValue(Type.STRINGS);
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
        final Set<String> statusFlags = new TreeSet<>();
        if (this.currentNodeBuilder.hasProperty(STATUS_FLAGS)) {
            this.currentNodeBuilder.getProperty(STATUS_FLAGS).getValue(Type.STRINGS).forEach(statusFlags::add);
        }
        if (isInvalid) {
            statusFlags.add(STATUS_FLAG_INVALID);
        }
        if (isIncomplete) {
            statusFlags.add(STATUS_FLAG_INCOMPLETE);
        }
        // Write these statusFlags to the JCR repo
        this.currentNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
    }

    /**
     * Checks if the given node is a Form node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a Form node, {@code false} otherwise
     */
    private boolean isForm(NodeBuilder node)
    {
        return "cards:Form".equals(getNodeType(node));
    }

    /**
     * Checks if the given node is an AnswerSection node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is an AnswerSection node, {@code false} otherwise
     */
    private boolean isSection(NodeBuilder node)
    {
        return "cards:AnswerSection".equals(getNodeType(node));
    }

    /**
     * Retrieves the primary node type of a node, as a String.
     *
     * @param node the node whose type to retrieve
     * @return a string
     */
    private String getNodeType(NodeBuilder node)
    {
        return node.getProperty("jcr:primaryType").getValue(Type.STRING);
    }
}
