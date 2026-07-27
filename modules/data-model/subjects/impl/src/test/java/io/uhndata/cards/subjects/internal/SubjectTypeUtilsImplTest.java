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
package io.uhndata.cards.subjects.internal;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectTypeUtilsImpl}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectTypeUtilsImplTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String SUBJECT_TYPE_TYPE = "cards:SubjectType";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String ROOT_SUBJECT_TYPE_PATH = "/SubjectTypes/Root";
    private static final String TYPE_PROPERTY = "type";
    private static final String LABEL_PROPERTY = "label";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SubjectTypeUtilsImpl subjectTypeUtils;

    @Mock
    private ThreadResourceResolverProvider rrp;

    @Test
    public void getSubjectTypeForActualSubjectTypeIdentifierReturnsSubjectTypeNode() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        String identifier = resourceResolver.getResource(ROOT_SUBJECT_TYPE_PATH).adaptTo(Node.class).getIdentifier();
        when(this.rrp.getThreadResourceResolver()).thenReturn(resourceResolver);

        Node actualSubjectTypeNode = this.subjectTypeUtils.getSubjectType(identifier);
        assertNotNull(actualSubjectTypeNode);
        assertEquals(ROOT_SUBJECT_TYPE_PATH, actualSubjectTypeNode.getPath());
    }

    @Test
    public void getSubjectTypeForNotSubjectTypeIdentifierReturnsNull() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        String subjectIdentifier = resourceResolver.getResource(TEST_SUBJECT_PATH).adaptTo(Node.class).getIdentifier();
        when(this.rrp.getThreadResourceResolver()).thenReturn(resourceResolver);

        Node actualSubjectTypeNode = this.subjectTypeUtils.getSubjectType(subjectIdentifier);
        assertNull(actualSubjectTypeNode);
    }

    @Test
    public void isSubjectTypeForNodeWithNullArgumentReturnsFalse()
    {
        assertFalse(this.subjectTypeUtils.isSubjectType((Node) null));
    }

    @Test
    public void isSubjectTypeForNodeThrowsExceptionReturnsFalse() throws RepositoryException
    {
        Node node = mock(Node.class);
        when(node.isNodeType(ROOT_SUBJECT_TYPE_PATH)).thenThrow(new RepositoryException());
        assertFalse(this.subjectTypeUtils.isSubjectType(node));
    }

    @Test
    public void isSubjectTypeForActualSubjectTypeNodeReturnsTrue()
    {
        Node node = this.context.resourceResolver().getResource(ROOT_SUBJECT_TYPE_PATH).adaptTo(Node.class);
        assertTrue(this.subjectTypeUtils.isSubjectType(node));
    }

    @Test
    public void isSubjectTypeForNotSubjectTypeNodeReturnsFalse()
    {
        Node node = this.context.resourceResolver().getResource(TEST_SUBJECT_PATH).adaptTo(Node.class);
        assertFalse(this.subjectTypeUtils.isSubjectType(node));
    }

    @Test
    public void isSubjectTypeForNodeBuilderWithNullArgumentReturnsFalse()
    {
        assertFalse(this.subjectTypeUtils.isSubjectType((NodeBuilder) null));
    }

    @Test
    public void isSubjectTypeForActualSubjectTypeNodeBuilderReturnsTrue()
    {
        when(this.rrp.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE_TYPE);
        assertTrue(this.subjectTypeUtils.isSubjectType(nodeBuilder));
    }

    @Test
    public void isSubjectTypeForNotSubjectTypeNodeBuilderReturnsFalse()
    {
        when(this.rrp.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE);
        assertFalse(this.subjectTypeUtils.isSubjectType(nodeBuilder));
    }

    @Test
    public void isSubjectTypeForNodeStateWithNullArgumentReturnsFalse()
    {
        assertFalse(this.subjectTypeUtils.isSubjectType((NodeState) null));
    }

    @Test
    public void isSubjectTypeForActualSubjectTypeNodeStateReturnsTrue()
    {
        when(this.rrp.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE_TYPE);
        assertTrue(this.subjectTypeUtils.isSubjectType(nodeBuilder.getNodeState()));
    }

    @Test
    public void isSubjectTypeForNotSubjectTypeNodeStateReturnsFalse()
    {
        when(this.rrp.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE);
        assertFalse(this.subjectTypeUtils.isSubjectType(nodeBuilder.getNodeState()));
    }

    @Test
    public void getLabelForActualSubjectTypeNodeWithLabelProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subjectType = session.getNode(ROOT_SUBJECT_TYPE_PATH);
        String label = this.subjectTypeUtils.getLabel(subjectType);
        assertNotNull(label);
        assertEquals("Root", label);
    }

    @Test
    public void getLabelForActualSubjectTypeNodeWithoutLabelPropertyReturnsNull() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subjectType = session.getNode(ROOT_SUBJECT_TYPE_PATH);
        subjectType.getProperty(LABEL_PROPERTY).remove();

        String label = this.subjectTypeUtils.getLabel(subjectType);
        assertNull(label);
    }

    @Test
    public void getLabelForNotSubjectTypeNodeReturnsNull() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node fakeSubjectType = session.getNode(TEST_SUBJECT_PATH);

        String label = this.subjectTypeUtils.getLabel(fakeSubjectType);
        assertNull(label);
    }

    @Test
    public void getLabelForSubjectTypeNodeThrowsExceptionReturnsNull() throws RepositoryException
    {
        Node subjectType = mock(Node.class);
        when(subjectType.isNodeType(SUBJECT_TYPE_TYPE)).thenReturn(true);
        when(subjectType.hasProperty(LABEL_PROPERTY)).thenThrow(new RepositoryException());

        String label = this.subjectTypeUtils.getLabel(subjectType);
        assertNull(label);
    }

    @Before
    public void setupRepo()
    {
        this.context.build()
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .commit();
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();
    }

}
