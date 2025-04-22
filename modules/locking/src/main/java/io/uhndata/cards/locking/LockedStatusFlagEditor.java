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
package io.uhndata.cards.locking;

import java.util.HashSet;
import java.util.Set;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * An {@link Editor} that adds or removes the {@code LOCKED} status flag when a form or subject is updated.
 *
 * @version $Id$
 */
public class LockedStatusFlagEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LockedStatusFlagEditor.class);

    // The property that contains a node's status flags
    private static final String STATUS_FLAGS = "statusFlags";
    // The property that contains a reference to a lock node if a node is locked
    private static final String LOCK_PROPERTY = "cards:Lock";
    // The status flag that indicates that a node is locked.
    private static final String LOCKED_FLAG = "LOCKED";

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    private final ResourceResolverFactory rrf;

    private final FormUtils formUtils;

    private final SubjectUtils subjectUtils;

    private final boolean isLockableNode;

    /**
     * Simple constructor.
     *
     * @param currentNodeBuilder the NodeBuilder to process
     * @param rrf is the factory used to get a service user
     * @param formUtils for working with form data
     * @param subjectUtils for working with subject data
     */
    public LockedStatusFlagEditor(final NodeBuilder currentNodeBuilder, final ResourceResolverFactory rrf,
        final FormUtils formUtils, final SubjectUtils subjectUtils)
    {
        this.currentNodeBuilder = currentNodeBuilder;
        this.formUtils = formUtils;
        this.subjectUtils = subjectUtils;
        this.isLockableNode = this.formUtils.isForm(currentNodeBuilder)
            || this.subjectUtils.isSubject(currentNodeBuilder);
        this.rrf = rrf;
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
        throws CommitFailedException
    {
        return new LockedStatusFlagEditor(this.currentNodeBuilder.getChildNode(name), this.rrf, this.formUtils,
            this.subjectUtils);
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        return new LockedStatusFlagEditor(this.currentNodeBuilder.getChildNode(name), this.rrf, this.formUtils,
            this.subjectUtils);
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        if (this.isLockableNode) {
            final Set<String> statusFlags = new HashSet<>();
            if (this.currentNodeBuilder.hasProperty(STATUS_FLAGS)) {
                Iterable<String> flags = this.currentNodeBuilder.getProperty(STATUS_FLAGS).getValue(Type.STRINGS);
                flags.forEach(flag -> statusFlags.add(flag));
            }
            final String lockValue = this.currentNodeBuilder.hasProperty(LOCK_PROPERTY)
                ? this.currentNodeBuilder.getProperty(LOCK_PROPERTY).toString()
                : "";

            boolean flagsChanged = false;
            if (lockValue.length() > 0 && !statusFlags.contains(LOCKED_FLAG)) {
                // Node is locked but does not contain the lock status flag
                statusFlags.add(LOCKED_FLAG);
                flagsChanged = true;
            } else if (lockValue.length() == 0 && statusFlags.contains(LOCKED_FLAG)) {
                // Node is not locked but contains the locked flag
                statusFlags.remove(LOCKED_FLAG);
                flagsChanged = true;
            }

            if (flagsChanged) {
                this.currentNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
            }
        }
    }
}
