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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.StreamSupport;

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

    private static final String STATUS_FLAGS = "statusFlags";

    private static final String STATUS_FLAG_INCOMPLETE = "INCOMPLETE";

    private static final String STATUS_FLAG_INVALID = "INVALID";

    private static final String STATUS_FLAG_DRAFT = "DRAFT";

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    private final Session session;

    private final FormUtils formUtils;

    private final boolean isFormNode;

    // Validators list to be called in sequence, in ascending order of their priority, and each can add or remove flags.
    private final List<AnswerValidator> allValidators;

    private final boolean newForm;

    /**
     * Simple constructor.
     *
     * @param currentNodeBuilder the NodeBuilder to process
     * @param newNode is this a newly created node, or an existing node being updated
     * @param session the current JCR session
     * @param formUtils for working with form data
     * @param allValidators all available AnswerValidator services
     */
    public AnswerCompletionStatusEditor(final NodeBuilder currentNodeBuilder, final boolean newNode,
        final Session session, final FormUtils formUtils, final List<AnswerValidator> allValidators)
    {
        this.currentNodeBuilder = currentNodeBuilder;
        this.newForm = newNode;
        this.session = session;
        this.formUtils = formUtils;
        this.allValidators = allValidators;
        this.isFormNode = this.formUtils.isForm(currentNodeBuilder);
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
        throws CommitFailedException
    {
        if (this.isFormNode) {
            return null;
        }
        return new AnswerCompletionStatusEditor(this.currentNodeBuilder.getChildNode(name), true, this.session,
            this.formUtils, this.allValidators);
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        if (this.isFormNode) {
            return null;
        }
        return new AnswerCompletionStatusEditor(this.currentNodeBuilder.getChildNode(name), false, this.session,
            this.formUtils, this.allValidators);
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        if (this.isFormNode) {
            processNode(this.currentNodeBuilder);
        }
    }

    private void processNode(final NodeBuilder node)
    {
        summarizeChildren(node);
        summarizeNode(node);
    }

    private void summarizeChildren(final NodeBuilder node)
    {
        node.getChildNodeNames().iterator().forEachRemaining(name -> processNode(node.getChildNode(name)));
    }

    private void summarizeNode(final NodeBuilder node)
    {
        if (this.formUtils.isAnswer(node)) {
            validateAnswer(node);
        } else if (this.formUtils.isForm(node)
            || this.formUtils.isAnswerSection(node)) {
            try {
                summarize(node);
            } catch (final RepositoryException e) {
                // This is not a fatal error, the form status is not required for a functional application
                LOGGER.warn("Unexpected exception while checking the completion status of form {}",
                    this.currentNodeBuilder.getString("jcr:uuid"), e);
            }
        }
    }

    /**
     * Validate an answer node.
     *
     * @param answerNode the {@code cards:Answer} node to validate
     */
    public void validateAnswer(final NodeBuilder answerNode)
    {
        final Node questionNode = this.formUtils.getQuestion(answerNode);

        if (questionNode != null) {
            // populate the flags map with the old flags all set to false
            final Map<String, Boolean> flags = new HashMap<>();
            if (answerNode.hasProperty(STATUS_FLAGS)) {
                answerNode.getProperty(STATUS_FLAGS).getValue(Type.STRINGS)
                    .forEach(flag -> flags.put(flag, Boolean.FALSE));
            }
            // call each validator
            this.allValidators.forEach(validator -> {
                validator.validate(answerNode, questionNode, this.newForm, flags);
            });
            // Write these statusFlags to the JCR repo
            answerNode.setProperty(STATUS_FLAGS, flags.keySet(), Type.STRINGS);
        }
    }

    /**
     * Gather all status flags from all the (satisfied) descendants of a node and store them as the status flags of the
     * node.
     *
     * @param node the node to summarize, either a {@code cards:Form} or a {@code cards:AnswerSection} node
     * @throws RepositoryException if accessing the repository fails
     */
    private void summarize(final NodeBuilder node) throws RepositoryException
    {
        final Set<String> flags = StreamSupport.stream(node.getChildNodeNames().spliterator(), false)
            .map(childName -> node.getChildNode(childName))
            .filter(child -> {
                try {
                    return !(this.formUtils.isAnswerSection(child)
                        && !ConditionalSectionUtils.isConditionSatisfied(this.session, child, this.currentNodeBuilder));
                } catch (final RepositoryException e) {
                    return true;
                }
            })
            .filter(child -> child.hasProperty(STATUS_FLAGS))
            .map(child -> child.getProperty(STATUS_FLAGS).getValue(Type.STRINGS))
            .<Set<String>>reduce(new HashSet<>(), (oldFlags, newFlags) -> {
                newFlags.forEach(flag -> oldFlags.add(flag));
                return oldFlags;
            },
                (l, r) -> {
                    final Set<String> u = new HashSet<>();
                    u.addAll(l);
                    u.addAll(r);
                    return u;
                });
        // Set the flags in selectedNodeBuilder accordingly
        final Set<String> statusFlags = new TreeSet<>();
        if (node.hasProperty(STATUS_FLAGS)) {
            node.getProperty(STATUS_FLAGS).getValue(Type.STRINGS).forEach(statusFlags::add);
        }
        if (isEmptyForm()) {
            flags.add(STATUS_FLAG_INCOMPLETE);
        }
        if (flags.contains(STATUS_FLAG_INVALID)) {
            statusFlags.add(STATUS_FLAG_INVALID);
        } else {
            statusFlags.remove(STATUS_FLAG_INVALID);
        }

        if (flags.contains(STATUS_FLAG_INCOMPLETE)) {
            statusFlags.add(STATUS_FLAG_INCOMPLETE);
        } else {
            statusFlags.remove(STATUS_FLAG_INCOMPLETE);
        }
        if (statusFlags.contains(STATUS_FLAG_INCOMPLETE) || statusFlags.contains(STATUS_FLAG_INVALID)) {
            statusFlags.add(STATUS_FLAG_DRAFT);
        } else {
            statusFlags.remove(STATUS_FLAG_DRAFT);
        }
        // Write these statusFlags to the JCR repo
        node.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
    }

    /**
     * Checks if a NodeBuilder represents an empty Form and returns true if that is the case. Otherwise, this method
     * returns false.
     */
    private boolean isEmptyForm()
    {
        if (!(this.currentNodeBuilder.getChildNodeNames().iterator().hasNext())) {
            return true;
        }
        return false;
    }
}
