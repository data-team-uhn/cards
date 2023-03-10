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
package io.uhndata.cards.permissions.internal;

import java.util.UUID;

import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.plugins.tree.impl.NodeBuilderTree;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link CreatedByRestrictionPattern}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class CreatedByRestrictionPatternTest
{
    private static final String CREATED_BY_PROPERTY = "jcr:createdBy";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private CreatedByRestrictionPattern createdByRestrictionPattern;

    @Test
    public void constructorTest()
    {
        assertNotNull(this.createdByRestrictionPattern);
    }

    @Test
    public void matchesForTreeWithoutCreatedByPropertyReturnsFalse()
    {
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(), EmptyNodeState.EMPTY_NODE.builder());
        assertFalse(this.createdByRestrictionPattern.matches(tree, null));
    }

    @Test
    public void matchesForNotNullPropertyStateReturnsFalse()
    {
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(), createNodeBuilder("user"));
        assertFalse(this.createdByRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeWithCreatedByPropertyReturnsTrue()
    {
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(), createNodeBuilder("admin"));
        assertTrue(this.createdByRestrictionPattern.matches(tree, null));
    }

    @Test
    public void matchesForPathReturnsFalse()
    {
        assertFalse(this.createdByRestrictionPattern.matches(UUID.randomUUID().toString()));
    }

    @Test
    public void matchesReturnsFalse()
    {
        assertFalse(this.createdByRestrictionPattern.matches());
    }

    @Before
    public void setUp()
    {
        this.createdByRestrictionPattern = new CreatedByRestrictionPattern(this.context.resourceResolver()
                .adaptTo(Session.class));
    }


    private NodeBuilder createNodeBuilder(String createdBy)
    {
        NodeBuilder builder = EmptyNodeState.EMPTY_NODE.builder();
        builder.setProperty(CREATED_BY_PROPERTY, createdBy);
        return builder;
    }
}
