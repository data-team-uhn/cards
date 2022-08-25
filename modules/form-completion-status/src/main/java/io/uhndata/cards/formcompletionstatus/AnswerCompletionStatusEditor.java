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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.formcompletionstatus.spi.AnswerValidator;
import io.uhndata.cards.forms.api.FormUtils;

/**
 * An {@link Editor} that verifies the correctness and completeness of submitted questionnaire answers and sets the
 * {@code INVALID} and {@code INCOMPLETE} status flags accordingly.
 *
 * @version $Id$
 */
public class AnswerCompletionStatusEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AnswerCompletionStatusEditor.class);

    private static final String PROP_QUESTION = "question";

    private static final String STATUS_FLAGS = "statusFlags";

    private static final String STATUS_FLAG_INCOMPLETE = "INCOMPLETE";

    private static final String STATUS_FLAG_INVALID = "INVALID";

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    private final NodeBuilder form;

    private final Session session;

    private final FormUtils formUtils;

    // Validators list to be called in sequence, in ascending order of their priority, and each can add or remove flags.
    private final List<AnswerValidator> allValidators;

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
     * @param formUtils for working with form data
     * @param allValidators all available AnswerValidator services
     */
    public AnswerCompletionStatusEditor(final List<NodeBuilder> nodeBuilder, final NodeBuilder form,
        final Session session, final FormUtils formUtils, List<AnswerValidator> allValidators)
    {
        this.currentNodeBuilderPath = nodeBuilder;
        this.currentNodeBuilder = nodeBuilder.get(nodeBuilder.size() - 1);
        this.session = session;
        this.formUtils = formUtils;
        this.allValidators = allValidators;
        if (this.formUtils.isForm(this.currentNodeBuilder)) {
            this.form = this.currentNodeBuilder;
        } else {
            this.form = form;
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
        validateAnswer(after, true);

        final List<NodeBuilder> tmpList = new ArrayList<>(this.currentNodeBuilderPath);
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.form, this.session, this.formUtils, this.allValidators);
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        validateAnswer(after, false);

        final List<NodeBuilder> tmpList = new ArrayList<>(this.currentNodeBuilderPath);
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.form, this.session, this.formUtils, this.allValidators);
    }

    @Override
    public void leave(NodeState before, NodeState after)
        throws CommitFailedException
    {
        if (this.formUtils.isForm(this.currentNodeBuilder) || this.formUtils.isAnswerSection(this.currentNodeBuilder)) {
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
     * If the node is indeed an answer node,
     * - populate the flags map with the old flags all set to false,
     * - get the question node from answer, and call each validator;
     * - then gather all the keys from the flags map and set the statusFlags property on the answer node.
     * @param answer answer
     * @param initialAnswer initialAnswer
     */
    public void validateAnswer(NodeState answer, boolean initialAnswer)
    {
        if (!this.formUtils.isAnswer(answer)) {
            return;
        }

        final Node questionNode = getQuestionNode(this.currentNodeBuilder);

        if (questionNode != null) {
            final Set<String> statusFlags = new TreeSet<>();
            if (this.currentNodeBuilder.hasProperty(STATUS_FLAGS)) {
                this.currentNodeBuilder.getProperty(STATUS_FLAGS).getValue(Type.STRINGS).forEach(statusFlags::add);
            }
            Map<String, Boolean> flags = new HashMap<>();
            // populate the flags map with the old flags all set to false
            statusFlags.forEach(flag -> flags.remove(flag));

            // call each validator
            this.allValidators.forEach(validator -> {
                validator.validate(this.currentNodeBuilder, questionNode, initialAnswer, flags);
            });

            // then gather all the keys from the flags map and set the statusFlags property on the answer node
            final Set<String> newFlags = flags.entrySet().stream()
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());
            // Write these statusFlags to the JCR repo
            this.currentNodeBuilder.setProperty(STATUS_FLAGS, newFlags, Type.STRINGS);
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
     * Checks if a NodeBuilder represents an empty Form and returns true if that is the case. Otherwise, this method
     * returns false.
     */
    private boolean isEmptyForm(NodeBuilder n)
    {
        if (this.formUtils.isForm(n)) {
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
            if (this.formUtils.isAnswerSection(selectedChild)) {
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
        } else {
            statusFlags.remove(STATUS_FLAG_INVALID);
        }

        if (isIncomplete) {
            statusFlags.add(STATUS_FLAG_INCOMPLETE);
        } else {
            statusFlags.remove(STATUS_FLAG_INCOMPLETE);
        }
        // Write these statusFlags to the JCR repo
        this.currentNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
    }
}
