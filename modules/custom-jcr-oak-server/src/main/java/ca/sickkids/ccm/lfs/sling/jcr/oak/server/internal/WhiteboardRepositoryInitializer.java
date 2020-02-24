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

import org.apache.jackrabbit.oak.spi.lifecycle.RepositoryInitializer;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.whiteboard.AbstractServiceTracker;
import org.apache.jackrabbit.oak.spi.whiteboard.Whiteboard;

/**
 * An aggregator {@link RepositoryInitializer} which combines all the {@link RepositoryInitializer} components
 * registered in the {@link Whiteboard}. This is used automatically by {@link OakSlingRepositoryManager} whenever
 * {@link OakSlingRepositoryManagerConfiguration#dynamic()} is {@code true}.
 * <p>
 * This should be part of org.apache.jackrabbit.oak.spi.lifecycle instead.
 * </p>
 *
 * @version $Id$
 */
final class WhiteboardRepositoryInitializer extends AbstractServiceTracker<RepositoryInitializer>
    implements RepositoryInitializer
{
    WhiteboardRepositoryInitializer()
    {
        super(RepositoryInitializer.class);
    }

    @Override
    public void initialize(NodeBuilder builder)
    {
        getServices().stream().forEach(i -> i.initialize(builder));
    }
}
