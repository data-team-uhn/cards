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
 * An {@link Editor} that computes and sets the fullIdentifier property for every changed Subject. The full identifier
 * is a concatenation of the subject's hierarchy, in the format {@code parent's full identifier / subject's identifier}.
 *
 * @version $Id$
 */
public class SubjectProgenyEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectProgenyEditor.class);

    private static final String PROP_ANCESTORS = "ancestors";

    private static final String PROP_PARENTS = "parents";

    private static final String PROP_PROGENY = "progeny";

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
    public SubjectProgenyEditor(final NodeBuilder nodeBuilder, final Session session)
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
        return new SubjectProgenyEditor(this.currentNodeBuilder.getChildNode(name), this.session);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return new SubjectProgenyEditor(this.currentNodeBuilder.getChildNode(name), this.session);
    }

    @Override
    public void leave(NodeState before, NodeState after) throws CommitFailedException
    {
        //if (isRoot(this.currentNodeBuilder)) {
        if (isSubject(this.currentNodeBuilder)) {
            try {
                computeParents();
                computeProgeny();
            } catch (RepositoryException e) {
                // This is not a fatal error, the subject status is not required for a functional application
                LOGGER.warn("Unexpected exception while computing the full identifier of subject {}",
                    this.currentNodeBuilder.getString("jcr:uuid"));
            }
        }
    }

    /**
     * Gather all identifiers from all the Subject parents of the current node and store them as the ' / ' separated
     * fullIdentifier property.
     *
     * @throws RepositoryException if accessing the repository fails
     */
    private void computeParents() throws RepositoryException
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
        }
    }

    /**
     * Gather all identifiers from all the Subject/SubjectType children of the current node
     * and store them as a list in Progeny.
     *
     * @throws RepositoryException if accessing the repository fails
     */
    private void computeProgeny() throws RepositoryException
    {
        final List<String> identifiers = getProgeny(this.currentNodeBuilder);

        // Get&write progeny to the JCR repo
        this.currentNodeBuilder.setProperty(PROP_PROGENY, identifiers, Type.WEAKREFERENCES);
    }

    /**
     * Gather all identifiers from all the Subject/SubjectType children of the current node
     * and store them as a string list.
     *
     * @throws RepositoryException if accessing the repository fails
     */
    private List<String> getProgeny(NodeBuilder node) throws RepositoryException
    {
        List<String> progeny = new ArrayList<>();

        // Iterate through all children of this node
        for (String childName : node.getChildNodeNames()) {
            NodeBuilder childNode = node.getChildNode(childName);
            progeny.add(childNode.getProperty("jcr:uuid").getValue(Type.STRING));
            progeny.addAll(getProgeny(childNode));
        }

        return progeny;
    }

    /**
     * Checks if the given node is a Subject node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a Subject node, {@code false} otherwise
     */
    private boolean isRoot(NodeBuilder node)
    {
        return "lfs:SubjectHomepage".equals(getNodeType(node)) || "lfs:SubjectTypeHomepage".equals(getNodeType(node));
    }

    /**
     * Checks if the given node is a Subject node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a Subject node, {@code false} otherwise
     */
    private boolean isSubject(NodeBuilder node)
    {
        return "lfs:Subject".equals(getNodeType(node)) || "lfs:SubjectType".equals(getNodeType(node));
    }

    /**
     * Checks if the given node is a Subject node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a Subject node, {@code false} otherwise
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
