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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.plugins.tree.impl.NodeBuilderTree;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
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
 * Unit tests for {@link SessionSubjectRestrictionPattern}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SessionSubjectRestrictionPatternTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String SESSION_SUBJECT_ATTRIBUTE = "cards:sessionSubject";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private SessionSubjectRestrictionPattern sessionSubjectRestrictionPattern;

    @Test
    public void constructorTest()
    {
        assertNotNull(this.sessionSubjectRestrictionPattern);
    }

    @Test
    public void matchesForTreeAndNullSessionReturnsFalse()
    {
        this.sessionSubjectRestrictionPattern = new SessionSubjectRestrictionPattern(null);
        assertFalse(this.sessionSubjectRestrictionPattern.matches(mock(Tree.class), mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeAndBlankSessionAttributeReturnsFalse()
    {
        Session mockedSession = mock(Session.class);
        this.sessionSubjectRestrictionPattern = new SessionSubjectRestrictionPattern(mockedSession);
        when(mockedSession.getAttribute(SESSION_SUBJECT_ATTRIBUTE)).thenReturn("");
        assertFalse(this.sessionSubjectRestrictionPattern.matches(mock(Tree.class), mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeFormForSubjectReturnsTrue() throws RepositoryException
    {
        Session mockedSession = mock(Session.class);
        String sessionSubject = "/Subjects/r1";
        Node sessionSubjectNode = mock(Node.class);
        this.sessionSubjectRestrictionPattern = new SessionSubjectRestrictionPattern(mockedSession);
        when(mockedSession.getAttribute(SESSION_SUBJECT_ATTRIBUTE)).thenReturn(sessionSubject);

        String childName = "a1";
        NodeBuilder childBuilder = EmptyNodeState.EMPTY_NODE.builder();

        String formName = "f1";
        NodeBuilder formBuilder =
                createFormNodeBuilder(UUID.randomUUID().toString(), childName, childBuilder.getNodeState());

        String formsHomepageName = "Forms";
        NodeBuilder formsHomepageBuilder =
                createNodeBuilder("cards:FormsHomepage", formName, formBuilder.getNodeState());

        NodeBuilderTree rootTree = new NodeBuilderTree("",
                createNodeBuilder("jcr:root", formsHomepageName, formsHomepageBuilder.getNodeState()));
        Tree tree = rootTree.addChild(formsHomepageName).addChild(formName).addChild(childName);

        when(mockedSession.getNodeByIdentifier(anyString())).thenReturn(sessionSubjectNode);
        when(sessionSubjectNode.getPath()).thenReturn(sessionSubject);

        assertTrue(this.sessionSubjectRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeSubjectReturnsTrue()
    {
        Session mockedSession = mock(Session.class);
        String sessionSubject = "/Subjects/r1";
        this.sessionSubjectRestrictionPattern = new SessionSubjectRestrictionPattern(mockedSession);
        when(mockedSession.getAttribute(SESSION_SUBJECT_ATTRIBUTE)).thenReturn(sessionSubject);

        String childName = "b1";
        NodeBuilder childBuilder = EmptyNodeState.EMPTY_NODE.builder();

        String subjectName = "r1";
        NodeBuilder subjectBuilder = createNodeBuilder("cards:Subject", childName, childBuilder.getNodeState());

        String subjectsHomepageName = "Subjects";
        NodeBuilder subjectsHomepageBuilder =
                createNodeBuilder("cards:SubjectsHomepage", subjectName, subjectBuilder.getNodeState());

        NodeBuilderTree rootTree = new NodeBuilderTree("",
                createNodeBuilder("jcr:root", subjectsHomepageName, subjectsHomepageBuilder.getNodeState()));
        Tree tree = rootTree.addChild(subjectsHomepageName).addChild(subjectName).addChild(childName);

        assertTrue(this.sessionSubjectRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }


    @Test
    public void matchesForTreeNeitherFormForSubjectNorSubjectReturnsFalse() throws RepositoryException
    {
        Session mockedSession = mock(Session.class);
        String sessionSubject = "/Subjects/r1";
        Node sessionSubjectNode = mock(Node.class);
        this.sessionSubjectRestrictionPattern = new SessionSubjectRestrictionPattern(mockedSession);
        when(mockedSession.getAttribute(SESSION_SUBJECT_ATTRIBUTE)).thenReturn(sessionSubject);

        String childName = "a1";
        NodeBuilder childBuilder = EmptyNodeState.EMPTY_NODE.builder();

        String formName = "f1";
        NodeBuilder formBuilder =
                createFormNodeBuilder(UUID.randomUUID().toString(), childName, childBuilder.getNodeState());

        String formsHomepageName = "Forms";
        NodeBuilder formsHomepageBuilder =
                createNodeBuilder("cards:FormsHomepage", formName, formBuilder.getNodeState());

        NodeBuilderTree rootTree = new NodeBuilderTree("",
                createNodeBuilder("jcr:root", formsHomepageName, formsHomepageBuilder.getNodeState()));
        Tree tree = rootTree.addChild(formsHomepageName).addChild(formName).addChild(childName);

        when(mockedSession.getNodeByIdentifier(anyString())).thenThrow(new RepositoryException());
        when(sessionSubjectNode.getPath()).thenReturn(sessionSubject);

        assertFalse(this.sessionSubjectRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForPathReturnsTrue()
    {
        assertTrue(this.sessionSubjectRestrictionPattern.matches(UUID.randomUUID().toString()));
    }

    @Test
    public void matchesReturnsFalse()
    {
        assertFalse(this.sessionSubjectRestrictionPattern.matches());
    }

    @Before
    public void setUp()
    {
        this.sessionSubjectRestrictionPattern = new SessionSubjectRestrictionPattern(this.context.resourceResolver()
                .adaptTo(Session.class));
    }

    private NodeBuilder createNodeBuilder(String nodeType, String childName, NodeState childNodeState)
    {
        NodeBuilder builder = EmptyNodeState.EMPTY_NODE.builder();
        builder.setProperty(NODE_TYPE, nodeType);
        builder.setChildNode(childName, childNodeState);
        return builder;
    }

    private NodeBuilder createFormNodeBuilder(String subjectPropertyUuid, String childName, NodeState childNodeState)
    {
        NodeBuilder builder = createNodeBuilder("cards:Form", childName, childNodeState);
        builder.setProperty(SUBJECT_PROPERTY, subjectPropertyUuid, Type.REFERENCE);
        return builder;
    }
}
