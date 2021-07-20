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
package io.uhndata.cards.permissions.internal.ownership;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeBuilder;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link Editor} that automatically sets the current user as the "owner" of a new Form or Subject.
 *
 * @version $Id$
 */
public class OwnerSetterEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OwnerSetterEditor.class);

    /**
     * This holds the builder for the current node. The methods called for editing specific properties don't receive the
     * actual parent node of those properties, so we must manually keep track of the current node.
     */
    private final NodeBuilder currentNodeBuilder;

    /**
     * The author of the change that triggered this editor.
     */
    private final String author;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node, allows read+write access to the changed nodes
     * @param author the author of the change
     */
    public OwnerSetterEditor(final NodeBuilder nodeBuilder, final String author)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.author = author;
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        return new OwnerSetterEditor(this.currentNodeBuilder.getChildNode(name), this.author);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return new OwnerSetterEditor(this.currentNodeBuilder.getChildNode(name), this.author);
    }

    @Override
    public void propertyAdded(PropertyState after) throws CommitFailedException
    {
        // FIXME The list of nodetypes to be changed should be configurable
        if ("jcr:primaryType".equals(after.getName())
            && StringUtils.equalsAny(after.getValue(Type.STRING), "cards:Form", "cards:Subject")) {
            this.currentNodeBuilder.setProperty("owner", this.author);
            LOGGER.debug("Set {} as the owner of {} {}", this.author, after.getValue(Type.STRING),
                this.currentNodeBuilder instanceof MemoryNodeBuilder
                    ? ((MemoryNodeBuilder) this.currentNodeBuilder).getPath() : this.currentNodeBuilder);
        }
    }
}
