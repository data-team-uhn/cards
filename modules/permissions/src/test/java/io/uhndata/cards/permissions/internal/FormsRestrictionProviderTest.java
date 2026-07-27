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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.plugins.tree.impl.NodeBuilderTree;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.Restriction;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionDefinition;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.permissions.spi.RestrictionFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FormsRestrictionProvider}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class FormsRestrictionProviderTest
{

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private FormsRestrictionProvider formsRestrictionProvider;

    @Test
    public void constructorTest()
    {
        assertNotNull(this.formsRestrictionProvider);
    }

    @Test
    public void getPatternForNullOakPathAndNotEmptyRestrictionsSetReturnsEmptyRestrictionPattern()
    {
        RestrictionPattern pattern = this.formsRestrictionProvider.getPattern(null, Set.of(mock(Restriction.class)));
        assertNotNull(pattern);
        assertEquals(RestrictionPattern.EMPTY, pattern);
    }

    @Test
    public void getPatternForOakPathAndNotEmptyRestrictionsSetReturnsAnswerRestrictionPattern()
    {
        Restriction restriction = mock(Restriction.class);

        RestrictionDefinition restrictionDefinition = mock(RestrictionDefinition.class);
        when(restriction.getDefinition()).thenReturn(restrictionDefinition);
        when(restrictionDefinition.getName()).thenReturn("cards:answer");

        PropertyState value = mock(PropertyState.class);
        when(restriction.getProperty()).thenReturn(value);
        when(value.getValue(Type.STRING)).thenReturn(UUID.randomUUID().toString());

        RestrictionPattern pattern = this.formsRestrictionProvider.getPattern(UUID.randomUUID().toString(),
                Set.of(restriction));
        assertNotNull(pattern);
        assertTrue(pattern instanceof AnswerRestrictionPattern);
    }

    @Test
    public void getPatternForNullOakPathAndTreeReturnsEmptyRestrictionPattern()
    {
        RestrictionPattern pattern = this.formsRestrictionProvider.getPattern(null, mock(Tree.class));
        assertNotNull(pattern);
        assertEquals(RestrictionPattern.EMPTY, pattern);
    }

    @Test
    public void getPatternForOakPathAndTreeReturnsAnswerRestrictionPattern()
    {
        NodeBuilder builder = EmptyNodeState.EMPTY_NODE.builder();
        builder.setProperty("cards:answer", UUID.randomUUID().toString());
        NodeBuilderTree treeNode = new NodeBuilderTree("answerRestriction", builder);
        RestrictionPattern pattern = this.formsRestrictionProvider.getPattern(UUID.randomUUID().toString(), treeNode);
        assertNotNull(pattern);
        assertTrue(pattern instanceof AnswerRestrictionPattern);
    }

    @Test
    public void getPatternForOakPathAndTreeWithNonexistentFactoryNameReturnsEmptyRestrictionPattern()
    {
        NodeBuilder builder = EmptyNodeState.EMPTY_NODE.builder();
        builder.setProperty("cards:question", UUID.randomUUID().toString());
        NodeBuilderTree treeNode = new NodeBuilderTree("answerRestriction", builder);
        RestrictionPattern pattern = this.formsRestrictionProvider.getPattern(UUID.randomUUID().toString(), treeNode);
        assertNotNull(pattern);
        assertEquals(RestrictionPattern.EMPTY, pattern);
    }

    @Before
    public void setUp()
    {
        List<RestrictionFactory> factories = List.of(new AnswerRestrictionFactory(),
                new QuestionnaireRestrictionFactory());
        this.formsRestrictionProvider = new FormsRestrictionProvider(factories);
    }

}
