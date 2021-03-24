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
 * An {@link Editor} that sets the {@code progeny} property for every {@code lfs:Subject}
 * or {@code lfs:SubjectType} with the children of that node.
 *
 * @version $Id$
 */
public class SubjectProgenyEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectProgenyEditor.class);

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
        if (isSubject(this.currentNodeBuilder)) {
            try {
                computeProgeny();
            } catch (RepositoryException e) {
                // This is not a fatal error, the subject progeny is not required for a functional application
                LOGGER.warn("Unexpected exception while computing the progeny of subject {}",
                    this.currentNodeBuilder.getString("jcr:uuid"));
            }
        }
    }

    /**
     * Gather all children of a given node and store them in the {@code progeny} property.
     *
     * @throws RepositoryException if accessing the repository fails
     */
    private void computeProgeny() throws RepositoryException
    {
        final List<String> identifiers = getProgeny(this.currentNodeBuilder);

        // Get&write progeny to the JCR repo
        // NB: Empty properties cause errors in JCR-SQL2 queries, so we remove the property altogether if it is empty
        if (identifiers.size() > 0) {
            this.currentNodeBuilder.setProperty(PROP_PROGENY, identifiers, Type.WEAKREFERENCES);
        } else if (this.currentNodeBuilder.hasProperty(PROP_PROGENY)) {
            this.currentNodeBuilder.removeProperty(PROP_PROGENY);
        }
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
