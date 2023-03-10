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
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
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
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SectionRestrictionPattern}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SectionRestrictionPatternTest
{
    private static final String RESOURCE_TYPE = "sling:resourceType";
    private static final String TEST_SECTION_PATH = "/Questionnaires/TestQuestionnaire/s1";
    private static final String SECTION_PROPERTY = "section";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private SectionRestrictionPattern sectionRestrictionPattern;
    private Iterable<String> targetSections;

    @Test
    public void constructorTest()
    {
        assertNotNull(this.sectionRestrictionPattern);
    }

    @Test
    public void matchesForTreeAndQuestionPropertyInTargetSectionsReturnsTrue() throws RepositoryException
    {
        Session mockedSession = mock(Session.class);
        this.sectionRestrictionPattern = new SectionRestrictionPattern(this.targetSections, mockedSession);
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(),
                createNodeBuilder("cards/AnswerSection"));
        Node questionnaire = mock(Node.class);

        when(mockedSession.getNodeByIdentifier(anyString())).thenReturn(questionnaire);
        when(questionnaire.getPath()).thenReturn(TEST_SECTION_PATH);

        assertTrue(this.sectionRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeCatchesRepositoryExceptionReturnsFalse() throws RepositoryException
    {
        Session mockedSession = mock(Session.class);
        this.sectionRestrictionPattern = new SectionRestrictionPattern(this.targetSections, mockedSession);
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(),
                createNodeBuilder("cards/AnswerSection"));

        when(mockedSession.getNodeByIdentifier(anyString())).thenThrow(new RepositoryException());
        assertFalse(this.sectionRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeWithoutResourceSuperTypePropertyReturnsFalse()
    {
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(), EmptyNodeState.EMPTY_NODE.builder());
        assertFalse(this.sectionRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForPathReturnsFalse()
    {
        assertFalse(this.sectionRestrictionPattern.matches(TEST_SECTION_PATH));
    }

    @Test
    public void matchesReturnsFalse()
    {
        assertFalse(this.sectionRestrictionPattern.matches());
    }

    @Before
    public void setUp()
    {
        this.targetSections = List.of(TEST_SECTION_PATH);
        this.sectionRestrictionPattern = new SectionRestrictionPattern(this.targetSections,
                this.context.resourceResolver().adaptTo(Session.class));
    }

    private NodeBuilder createNodeBuilder(String resourceType)
    {
        NodeBuilder builder = EmptyNodeState.EMPTY_NODE.builder();
        builder.setProperty(RESOURCE_TYPE, resourceType);
        builder.setProperty(SECTION_PROPERTY, UUID.randomUUID().toString(), Type.REFERENCE);
        return builder;
    }
}
