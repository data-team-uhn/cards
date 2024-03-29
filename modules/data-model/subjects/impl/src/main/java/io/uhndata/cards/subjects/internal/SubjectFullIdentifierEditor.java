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
package io.uhndata.cards.subjects.internal;

import java.util.Stack;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link Editor} that computes and sets the fullIdentifier property for every changed Subject. The full identifier
 * is a concatenation of the subject's hierarchy, in the format {@code parent's full identifier / subject's identifier}.
 *
 * @version $Id$
 */
public class SubjectFullIdentifierEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectFullIdentifierEditor.class);

    private static final String PROP_FULLID_NAME = "fullIdentifier";

    private final Stack<String> identifiers;

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the current node
     * @param ancestors a stack of subject identifiers encountered from the root to the current node, empty if no other
     *            subjects have been encountered so far
     */
    public SubjectFullIdentifierEditor(final NodeBuilder nodeBuilder, final Stack<String> ancestors)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.identifiers = ancestors;
        if (isSubject(nodeBuilder)) {
            this.identifiers.push(nodeBuilder.getString("identifier"));
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
        return new SubjectFullIdentifierEditor(this.currentNodeBuilder.getChildNode(name), this.identifiers);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return new SubjectFullIdentifierEditor(this.currentNodeBuilder.getChildNode(name), this.identifiers);
    }

    @Override
    public void leave(NodeState before, NodeState after) throws CommitFailedException
    {
        if (isSubject(this.currentNodeBuilder)) {
            try {
                computeFullIdentifier();
            } catch (RepositoryException e) {
                // This is not a fatal error, the subject status is not required for a functional application
                LOGGER.warn("Unexpected exception while computing the full identifier of subject {}",
                    this.currentNodeBuilder.getString("jcr:uuid"));
            }
            this.identifiers.pop();
        }
    }

    /**
     * Gather all identifiers from all the Subject parents of the current node and store them as the ' / ' separated
     * fullIdentifier property.
     *
     * @throws RepositoryException if accessing the repository fails
     */
    private void computeFullIdentifier() throws RepositoryException
    {
        // Write fullIdentifier to the JCR repo
        this.currentNodeBuilder.setProperty(PROP_FULLID_NAME,
            this.identifiers.stream().reduce((result, child) -> result + " / " + child).get(), Type.STRING);
    }

    /**
     * Checks if the given node is a Subject node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a Subject node, {@code false} otherwise
     */
    private boolean isSubject(NodeBuilder node)
    {
        return "cards:Subject".equals(getNodeType(node));
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
