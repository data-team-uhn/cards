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
package ca.sickkids.ccm.lfs.subjects.internal;

import java.util.ArrayList;
import java.util.List;

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

/**
 * An {@link Editor} that sets the {@code ancestry} property for every {@code lfs:Subject}
 * or {@code lfs:SubjectType} with the parents of that node.
 *
 * @version $Id$
 */
public class SubjectAncestryEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectAncestryEditor.class);

    private static final String PROP_ANCESTORS = "ancestors";

    private static final String PROP_PARENTS = "parents";

    private final Session session;

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the current node
     * @param session the session used to retrieve subjects by UUID
     */
    public SubjectAncestryEditor(final NodeBuilder nodeBuilder, final Session session)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.session = session;
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
        throws CommitFailedException
    {
        return new SubjectAncestryEditor(this.currentNodeBuilder.getChildNode(name), this.session);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return new SubjectAncestryEditor(this.currentNodeBuilder.getChildNode(name), this.session);
    }

    @Override
    public void leave(NodeState before, NodeState after) throws CommitFailedException
    {
        if (isSubject(this.currentNodeBuilder)) {
            try {
                computeAncestry();
            } catch (RepositoryException e) {
                // This is not a fatal error, the subject ancestry is not required for a functional application
                LOGGER.warn("Unexpected exception while computing the ancestry of subject {}",
                    this.currentNodeBuilder.getString("jcr:uuid"));
            }
        }
    }

    /**
     * Gather all parents of a given node and store them in the {@code parents} property.
     *
     * @throws RepositoryException if accessing the repository fails
     */
    private void computeAncestry() throws RepositoryException
    {
        final List<String> identifiers = new ArrayList<>();
        Node parentNode = this.session.getNodeByIdentifier(this.currentNodeBuilder.getString("jcr:uuid"));

        // Iterate through all parents of this node
        while (parentNode != null) {
            parentNode = parentNode.getParent();
            if (parentNode.hasProperty("jcr:primaryType") && isSubject(parentNode)) {
                identifiers.add(parentNode.getProperty("jcr:uuid").getString());
            } else {
                parentNode = null;
            }
        }

        // Write the parents to the JCR repo (if > 0)
        if (identifiers.size() > 0) {
            this.currentNodeBuilder.setProperty(PROP_PARENTS, identifiers.get(0), Type.WEAKREFERENCE);
            this.currentNodeBuilder.setProperty(PROP_ANCESTORS, identifiers, Type.WEAKREFERENCES);
        } else {
            // Remove the old properties if they exist
            if (this.currentNodeBuilder.hasProperty(PROP_ANCESTORS)) {
                this.currentNodeBuilder.removeProperty(PROP_ANCESTORS);
            }
            if (this.currentNodeBuilder.hasProperty(PROP_PARENTS)) {
                this.currentNodeBuilder.removeProperty(PROP_PARENTS);
            }
        }
    }

    /**
     * Checks if the given node is a Subject or SubjectType node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a Subject/SubjectType node, {@code false} otherwise
     */
    private boolean isSubject(NodeBuilder node)
    {
        return "lfs:Subject".equals(getNodeType(node)) || "lfs:SubjectType".equals(getNodeType(node));
    }

    /**
     * Checks if the given node is a Subject or SubjectType node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a Subject/SubjectType node, {@code false} otherwise
     */
    private boolean isSubject(Node node) throws RepositoryException
    {
        return "lfs:Subject".equals(getNodeType(node)) || "lfs:SubjectType".equals(getNodeType(node));
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

    /**
     * Retrieves the primary node type of a node, as a String.
     *
     * @param node the node whose type to retrieve
     * @return a string
     */
    private String getNodeType(Node node) throws RepositoryException
    {
        return node.getProperty("jcr:primaryType").getString();
    }
}
