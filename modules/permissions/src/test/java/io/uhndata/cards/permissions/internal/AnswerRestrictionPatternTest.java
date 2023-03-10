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
 * Unit tests for {@link AnswerRestrictionPattern}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class AnswerRestrictionPatternTest
{
    private static final String RESOURCE_SUPER_TYPE = "sling:resourceSuperType";
    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private AnswerRestrictionPattern answerRestrictionPattern;
    private String targetAnswerPath;

    @Test
    public void constructorTest()
    {
        assertNotNull(this.answerRestrictionPattern);
    }

    @Test
    public void matchesForNotAnswerSuperTypeTreeReturnsFalse()
    {
        NodeBuilderTree tree = new NodeBuilderTree(this.targetAnswerPath, createNodeBuilder("cards/Question"));
        assertFalse(this.answerRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForAnswerSuperTypeTreeReturnsTrue()
    {
        NodeBuilderTree tree = new NodeBuilderTree(this.targetAnswerPath, createNodeBuilder("cards/Answer"));
        assertTrue(this.answerRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForAnswerSuperTypeTreeReturnsFalse()
    {
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(), createNodeBuilder("cards/Answer"));
        assertFalse(this.answerRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForPathReturnsFalse()
    {
        assertFalse(this.answerRestrictionPattern.matches("/Forms/f1/a1"));
    }

    @Test
    public void matchesReturnsFalse()
    {
        assertFalse(this.answerRestrictionPattern.matches());
    }

    @Before
    public void setUp()
    {
        this.targetAnswerPath = UUID.randomUUID().toString();
        this.answerRestrictionPattern = new AnswerRestrictionPattern(this.targetAnswerPath);
    }

    private NodeBuilder createNodeBuilder(String resourceSuperType)
    {
        NodeBuilder builder = EmptyNodeState.EMPTY_NODE.builder();
        builder.setProperty(RESOURCE_SUPER_TYPE, resourceSuperType);
        return builder;
    }
}
