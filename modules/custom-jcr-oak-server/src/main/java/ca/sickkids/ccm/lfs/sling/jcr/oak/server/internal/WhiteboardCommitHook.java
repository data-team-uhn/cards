/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ca.sickkids.ccm.lfs.sling.jcr.oak.server.internal;

import java.util.stream.Collectors;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitHook;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.CompositeHook;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.whiteboard.AbstractServiceTracker;
import org.apache.jackrabbit.oak.spi.whiteboard.Whiteboard;

/**
 * An aggregator {@link CommitHook} which combines all the {@link CommitHook} components registered in the
 * {@link Whiteboard}. This is used automatically by {@link OakSlingRepositoryManager} whenever
 * {@link OakSlingRepositoryManagerConfiguration#dynamic()} is {@code true}.
 * <p>
 * This should be part of org.apache.jackrabbit.oak.spi.commit instead.
 * </p>
 *
 * @version $Id$
 */
final class WhiteboardCommitHook extends AbstractServiceTracker<CommitHook> implements CommitHook
{
    WhiteboardCommitHook()
    {
        super(CommitHook.class);
    }

    @Override
    public NodeState processCommit(NodeState before, NodeState after, CommitInfo info) throws CommitFailedException
    {
        return CompositeHook.compose(
            // All registered CommitHook components
            getServices().stream()
                // Excluding composite hooks, such as this one
                .filter(i -> !(i instanceof CompositeHook)).collect(Collectors.toList()))
            // Forward the call
            .processCommit(before, after, info);
    }
}
