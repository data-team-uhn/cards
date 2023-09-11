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
package io.uhndata.cards.versioning;

import java.util.Calendar;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.version.OnParentVersionAction;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeBuilder;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;

/**
 * An {@link Editor} that updates the jcr:lastModified and
 * jcr:lastModifiedBy properties with the date/time and the username
 * of the last check-in operation.
 *
 * @version $Id$
 */
public class LastModifiedEditor extends DefaultEditor
{
    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;
    private final ResourceResolver currentResourceResolver;
    private final NodeBuilder versionableAncestor;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param resourceResolver a ResourceResolver object used to ultimately determine the logged-in user
     * @param versionableAncestor a NodeBuilder for the ancestor object that is of type mix:lastModified
     */
    public LastModifiedEditor(NodeBuilder nodeBuilder, ResourceResolver resourceResolver,
        NodeBuilder versionableAncestor)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.currentResourceResolver = resourceResolver;
        if (isMixLastModified(nodeBuilder)) {
            this.versionableAncestor = nodeBuilder;
        } else {
            this.versionableAncestor = versionableAncestor;
        }
    }

    // Called when the value of an existing property gets changed
    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
        if ("jcr:isCheckedOut".equals(after.getName()) && after.getValue(Type.BOOLEAN)) {
            this.currentNodeBuilder.setProperty("jcr:lastCheckedOut", Calendar.getInstance());
        } else {
            propertyAdded(after);
        }
    }

    @Override
    public void propertyDeleted(PropertyState before) throws CommitFailedException
    {
        propertyAdded(before);
    }

    // Called when a new property is added
    @Override
    public void propertyAdded(final PropertyState after)
        throws CommitFailedException
    {
        if (!isPropertyNonVersionable(after.getName())) {
            handleAnswerChange();
        }
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        if ("jcr:system".equals(name) || this.isChildNodeNonVersionable(name)) {
            return null;
        }
        return new LastModifiedEditor(this.currentNodeBuilder.getChildNode(name),
            this.currentResourceResolver,
            this.versionableAncestor);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        if ("jcr:system".equals(name) || this.isChildNodeNonVersionable(name)) {
            return null;
        }
        return new LastModifiedEditor(this.currentNodeBuilder.getChildNode(name),
            this.currentResourceResolver,
            this.versionableAncestor);
    }

    @Override
    public Editor childNodeDeleted(String name, NodeState before) throws CommitFailedException
    {
        if (this.isDeletedChildNodeNonVersionable(name)) {
            return null;
        }
        handleAnswerChange();
        return null;
    }

    private boolean isMixLastModified(NodeBuilder nodeBuilder)
    {
        if (nodeBuilder instanceof MemoryNodeBuilder) {
            final String nodePath = ((MemoryNodeBuilder) nodeBuilder).getPath();
            final Session thisSession = this.currentResourceResolver.adaptTo(Session.class);
            try {
                final Node thisNode = thisSession.getNode(nodePath);
                if (thisNode.isNodeType("mix:lastModified")) {
                    return true;
                }
            } catch (RepositoryException e) {
                return false;
            }
        }
        return false;
    }

    private boolean isPropertyNonVersionable(final String propertyName)
    {
        if (isSystemProperty(propertyName)) {
            return true;
        }
        try {
            Node thisNode = getCurrentNode();
            if (thisNode != null) {
                int opv = -1;
                for (PropertyDefinition pd : thisNode.getPrimaryNodeType().getPropertyDefinitions()) {
                    if (pd.getName().equals(propertyName)) {
                        opv = pd.getOnParentVersion();
                        break;
                    } else if ("*".equals(pd.getName())) {
                        opv = pd.getOnParentVersion();
                    }
                }
                if (opv == -1 && thisNode.hasProperty(propertyName)) {
                    opv = thisNode.getProperty(propertyName).getDefinition().getOnParentVersion();
                }
                return opv == OnParentVersionAction.IGNORE;
            }
        } catch (RepositoryException e) {
            return false;
        }
        return false;
    }

    private boolean isChildNodeNonVersionable(String childName)
    {
        try {
            Node childNode = getCurrentNode().getNode(childName);
            if (childNode != null) {
                boolean result = childNode.getDefinition().getOnParentVersion() == OnParentVersionAction.IGNORE;
                return result;
            }
        } catch (RepositoryException e) {
            return false;
        }
        return false;
    }

    private boolean isDeletedChildNodeNonVersionable(String childName)
    {
        // Attempt to determine if the deleted child node does not need versioning
        try {
            NodeDefinition[] childNodeDefinitions = getCurrentNode().getPrimaryNodeType().getChildNodeDefinitions();
            for (NodeDefinition definition : childNodeDefinitions) {
                if (childName.equals(definition.getName())
                    && definition.getOnParentVersion() == OnParentVersionAction.IGNORE) {
                    // Found a non versionable child node definition with a matching name to the deleted node.
                    return true;
                }
            }
        } catch (RepositoryException e) {
            return false;
        }
        // Default to false if no matching child node type was found
        return false;
    }

    private Node getCurrentNode() throws RepositoryException
    {
        if (this.currentNodeBuilder instanceof MemoryNodeBuilder) {
            final String nodePath = ((MemoryNodeBuilder) this.currentNodeBuilder).getPath();
            final Session thisSession = this.currentResourceResolver.adaptTo(Session.class);
            return thisSession.getNode(nodePath);
        }
        return null;
    }

    private boolean isSystemProperty(final String propertyName)
    {
        return propertyName.startsWith("jcr:") || propertyName.startsWith("sling:") || propertyName.startsWith(":");
    }

    private void handleAnswerChange()
    {
        if (this.versionableAncestor != null) {
            this.versionableAncestor.setProperty("jcr:lastModified", Calendar.getInstance());
            final Session thisSession = this.currentResourceResolver.adaptTo(Session.class);
            this.versionableAncestor.setProperty("jcr:lastModifiedBy", thisSession.getUserID());
        }
    }
}
