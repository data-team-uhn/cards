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
 * Unit tests for {@link QuestionRestrictionPattern}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class QuestionRestrictionPatternTest
{
    private static final String RESOURCE_SUPER_TYPE = "sling:resourceSuperType";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/q1";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private QuestionRestrictionPattern questionRestrictionPattern;
    private Iterable<String> targetQuestions;

    @Test
    public void constructorTest()
    {
        assertNotNull(this.questionRestrictionPattern);
    }

    @Test
    public void matchesForTreeAndQuestionPropertyInTargetQuestionsReturnsTrue() throws RepositoryException
    {
        Session mockedSession = mock(Session.class);
        this.questionRestrictionPattern = new QuestionRestrictionPattern(this.targetQuestions, mockedSession);
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(),
                createNodeBuilder("cards/Answer", QUESTION_PROPERTY, UUID.randomUUID().toString()));
        Node questionnaire = mock(Node.class);

        when(mockedSession.getNodeByIdentifier(anyString())).thenReturn(questionnaire);
        when(questionnaire.getPath()).thenReturn(TEST_QUESTION_PATH);

        assertTrue(this.questionRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForNotAnswerTreeReturnsFalse() throws RepositoryException
    {
        Session mockedSession = mock(Session.class);
        this.questionRestrictionPattern = new QuestionRestrictionPattern(this.targetQuestions, mockedSession);
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(),
                createNodeBuilder("cards/AnswerSection", SECTION_PROPERTY, UUID.randomUUID().toString()));

        assertFalse(this.questionRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeCatchesRepositoryExceptionReturnsFalse() throws RepositoryException
    {
        Session mockedSession = mock(Session.class);
        this.questionRestrictionPattern = new QuestionRestrictionPattern(this.targetQuestions, mockedSession);
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(),
                createNodeBuilder("cards/Answer", QUESTION_PROPERTY, UUID.randomUUID().toString()));
        when(mockedSession.getNodeByIdentifier(anyString())).thenThrow(new RepositoryException());

        assertFalse(this.questionRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeWithoutResourceSuperTypePropertyReturnsFalse()
    {
        NodeBuilderTree tree = new NodeBuilderTree(UUID.randomUUID().toString(), EmptyNodeState.EMPTY_NODE.builder());
        assertFalse(this.questionRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForPathReturnsFalse()
    {
        assertFalse(this.questionRestrictionPattern.matches(TEST_QUESTION_PATH));
    }

    @Test
    public void matchesReturnsFalse()
    {
        assertFalse(this.questionRestrictionPattern.matches());
    }

    @Before
    public void setUp()
    {
        this.targetQuestions = List.of(TEST_QUESTION_PATH);
        this.questionRestrictionPattern = new QuestionRestrictionPattern(this.targetQuestions,
                this.context.resourceResolver().adaptTo(Session.class));
    }

    private NodeBuilder createNodeBuilder(String resourceSuperType, String referenceProperty,
                                          String referencePropertyValue)
    {
        NodeBuilder builder = EmptyNodeState.EMPTY_NODE.builder();
        builder.setProperty(RESOURCE_SUPER_TYPE, resourceSuperType);
        builder.setProperty(referenceProperty, referencePropertyValue, Type.REFERENCE);
        return builder;
    }
}
