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
import javax.jcr.Property;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectRestrictionPattern}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectRestrictionPatternTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String PARENTS_PROPERTY = "parents";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private SubjectRestrictionPattern subjectRestrictionPattern;
    private String targetSubject;

    @Test
    public void constructorTest()
    {
        assertNotNull(this.subjectRestrictionPattern);
    }

    @Test
    public void matchesForPathReturnsFalse()
    {
        assertFalse(this.subjectRestrictionPattern.matches("/Subjects/r1"));
    }

    @Test
    public void matchesReturnsFalse()
    {
        assertFalse(this.subjectRestrictionPattern.matches());
    }

    @Test
    public void matchesForRootTreeReturnsFalse()
    {
        Tree tree = mock(Tree.class);
        when(tree.getProperty(NODE_TYPE)).thenReturn(null);
        when(tree.isRoot()).thenReturn(true);
        assertFalse(this.subjectRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeAndSubjectPropertyInTargetSectionsReturnsTrue()
    {
        String childName = "a1";
        NodeBuilder childBuilder = EmptyNodeState.EMPTY_NODE.builder();

        String formName = "f1";
        NodeBuilder formBuilder = createFormNodeBuilder(this.targetSubject, childName, childBuilder.getNodeState());

        String formsHomepageName = "Forms";
        NodeBuilder formsHomepageBuilder =
                createNodeBuilder("cards:FormsHomepage", formName, formBuilder.getNodeState());

        NodeBuilderTree rootTree = new NodeBuilderTree("",
                createNodeBuilder("jcr:root", formsHomepageName, formsHomepageBuilder.getNodeState()));
        Tree tree = rootTree.addChild(formsHomepageName).addChild(formName).addChild(childName);

        assertTrue(this.subjectRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeAndNullSessionReturnsFalse()
    {
        this.subjectRestrictionPattern = new SubjectRestrictionPattern(this.targetSubject, null);
        String answerName = "a1";
        NodeBuilder answerBuilder = EmptyNodeState.EMPTY_NODE.builder();

        String formName = "f1";
        NodeBuilder formBuilder =
                createFormNodeBuilder(UUID.randomUUID().toString(), answerName, answerBuilder.getNodeState());

        String formsHomepageName = "Forms";
        NodeBuilder formsHomepageBuilder =
                createNodeBuilder("cards:FormsHomepage", formName, formBuilder.getNodeState());

        NodeBuilderTree rootTree = new NodeBuilderTree("",
                createNodeBuilder("jcr:root", formsHomepageName, formsHomepageBuilder.getNodeState()));
        Tree tree = rootTree.addChild(formsHomepageName).addChild(formName).addChild(answerName);

        assertFalse(this.subjectRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeAndChildSubjectReturnsTrue() throws RepositoryException
    {
        Session mockedSession = mock(Session.class);
        this.subjectRestrictionPattern = new SubjectRestrictionPattern(this.targetSubject, mockedSession);

        Node leafSubject = mock(Node.class);
        String leafSubjectUUID = UUID.randomUUID().toString();
        Property leafParentProperty = mock(Property.class);
        Node branchSubject = mock(Node.class);
        String branchSubjectUUID = UUID.randomUUID().toString();
        Property branchParentProperty = mock(Property.class);

        String childName = "a1";
        NodeBuilder childBuilder = EmptyNodeState.EMPTY_NODE.builder();

        String formName = "f1";
        NodeBuilder formBuilder =
                createFormNodeBuilder(leafSubjectUUID, childName, childBuilder.getNodeState());

        String formsHomepageName = "Forms";
        NodeBuilder formsHomepageBuilder =
                createNodeBuilder("cards:FormsHomepage", formName, formBuilder.getNodeState());

        NodeBuilderTree rootTree = new NodeBuilderTree("",
                createNodeBuilder("jcr:root", formsHomepageName, formsHomepageBuilder.getNodeState()));
        Tree tree = rootTree.addChild(formsHomepageName).addChild(formName).addChild(childName);

        when(mockedSession.getNodeByIdentifier(leafSubjectUUID)).thenReturn(leafSubject);
        when(leafSubject.hasProperty(PARENTS_PROPERTY)).thenReturn(true);
        when(leafSubject.getProperty(PARENTS_PROPERTY)).thenReturn(leafParentProperty);
        when(leafParentProperty.getString()).thenReturn(branchSubjectUUID);

        when(mockedSession.getNodeByIdentifier(branchSubjectUUID)).thenReturn(branchSubject);
        when(branchSubject.hasProperty(PARENTS_PROPERTY)).thenReturn(true);
        when(branchSubject.getProperty(PARENTS_PROPERTY)).thenReturn(branchParentProperty);
        when(branchParentProperty.getString()).thenReturn(this.targetSubject);

        assertTrue(this.subjectRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeCatchesRepositoryExceptionReturnsFalse() throws RepositoryException
    {
        Session mockedSession = mock(Session.class);
        String subjectUuid = UUID.randomUUID().toString();
        this.subjectRestrictionPattern = new SubjectRestrictionPattern(this.targetSubject, mockedSession);

        String childName = "a1";
        NodeBuilder childBuilder = EmptyNodeState.EMPTY_NODE.builder();

        String formName = "f1";
        NodeBuilder formBuilder = createFormNodeBuilder(subjectUuid, childName, childBuilder.getNodeState());

        String formsHomepageName = "Forms";
        NodeBuilder formsHomepageBuilder =
                createNodeBuilder("cards:FormsHomepage", formName, formBuilder.getNodeState());

        NodeBuilderTree rootTree = new NodeBuilderTree("",
                createNodeBuilder("jcr:root", formsHomepageName, formsHomepageBuilder.getNodeState()));
        Tree tree = rootTree.addChild(formsHomepageName).addChild(formName).addChild(childName);

        when(mockedSession.getNodeByIdentifier(subjectUuid)).thenThrow(new RepositoryException());
        assertFalse(this.subjectRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForTreeCatchesItemNotFoundExceptionReturnsFalse()
    {
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

        assertFalse(this.subjectRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Before
    public void setUp()
    {
        this.targetSubject = UUID.randomUUID().toString();
        this.subjectRestrictionPattern = new SubjectRestrictionPattern(this.targetSubject,
                this.context.resourceResolver().adaptTo(Session.class));
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
