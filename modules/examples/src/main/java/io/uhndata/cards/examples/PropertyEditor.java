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
package io.uhndata.cards.examples;

import java.util.ArrayList;
import java.util.List;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.PropertyStates;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * A sample {@link Editor} that removes any leading or trailing whitespace from string values, single- or multi-valued.
 *
 * @version $Id$
 */
public class PropertyEditor extends DefaultEditor
{
    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     */
    public PropertyEditor(NodeBuilder nodeBuilder)
    {
        this.currentNodeBuilder = nodeBuilder;
    }

    // Called when a new property is added
    @Override
    public void propertyAdded(PropertyState after) throws CommitFailedException
    {
        trim(after);
    }

    // Called when the value of an existing property gets changed
    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
        trim(after);
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        return new PropertyEditor(this.currentNodeBuilder.getChildNode(name));
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return new PropertyEditor(this.currentNodeBuilder.getChildNode(name));
    }

    private void trim(PropertyState state) throws CommitFailedException
    {
        if (state.isArray() && state.getType() == Type.STRINGS) {
            List<String> newValues = new ArrayList<>(state.count());
            for (int i = 0; i < state.count(); ++i) {
                String value = state.getValue(Type.STRING, i);
                newValues.add(value == null ? null : value.trim());
            }
            this.currentNodeBuilder
                .setProperty(PropertyStates.createProperty(state.getName(), newValues, Type.STRINGS));
        } else if (state.getType() == Type.STRING) {
            this.currentNodeBuilder.setProperty(state.getName(), state.getValue(Type.STRING).trim());
        }
    }
}
