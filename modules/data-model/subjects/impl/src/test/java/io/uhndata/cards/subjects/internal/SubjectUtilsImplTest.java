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
 * Unit tests for {@link SubjectUtilsImpl}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectUtilsImplTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String SUBJECT_TYPE_TYPE = "cards:SubjectType";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String ROOT_SUBJECT_TYPE_PATH = "/SubjectTypes/Root";
    private static final String TYPE_PROPERTY = "type";
    private static final String LABEL_PROPERTY = "identifier";


    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SubjectUtilsImpl subjectUtils;

    @Mock
    private ThreadResourceResolverProvider rrp;

    @Test
    public void getSubjectForActualSubjectIdentifierReturnsSubjectNode() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        String identifier = resourceResolver.getResource(TEST_SUBJECT_PATH).adaptTo(Node.class).getIdentifier();
        when(this.rrp.getThreadResourceResolver()).thenReturn(resourceResolver);

        Node actualSubjectNode = this.subjectUtils.getSubject(identifier);
        assertNotNull(actualSubjectNode);
        assertEquals(TEST_SUBJECT_PATH, actualSubjectNode.getPath());
    }

    @Test
    public void getSubjectForNotSubjectIdentifierReturnsNull() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        String subjectTypeIdentifier = resourceResolver.getResource(ROOT_SUBJECT_TYPE_PATH).adaptTo(Node.class)
                .getIdentifier();
        when(this.rrp.getThreadResourceResolver()).thenReturn(resourceResolver);

        Node actualSubjectNode = this.subjectUtils.getSubject(subjectTypeIdentifier);
        assertNull(actualSubjectNode);
    }

    @Test
    public void isSubjectForNodeWithNullArgumentReturnsFalse()
    {
        assertFalse(this.subjectUtils.isSubject((Node) null));
    }

    @Test
    public void isSubjectForNodeThrowsExceptionReturnsFalse() throws RepositoryException
    {
        Node node = mock(Node.class);
        when(node.isNodeType(SUBJECT_TYPE)).thenThrow(new RepositoryException());
        assertFalse(this.subjectUtils.isSubject(node));
    }

    @Test
    public void isSubjectForActualSubjectNodeReturnsTrue()
    {
        Node node = this.context.resourceResolver().getResource(TEST_SUBJECT_PATH).adaptTo(Node.class);
        assertTrue(this.subjectUtils.isSubject(node));
    }

    @Test
    public void isSubjectForNotSubjectNodeReturnsFalse()
    {
        Node node = this.context.resourceResolver().getResource(ROOT_SUBJECT_TYPE_PATH).adaptTo(Node.class);
        assertFalse(this.subjectUtils.isSubject(node));
    }

    @Test
    public void isSubjectForNodeBuilderWithNullArgumentReturnsFalse()
    {
        assertFalse(this.subjectUtils.isSubject((NodeBuilder) null));
    }

    @Test
    public void isSubjectForActualSubjectNodeBuilderReturnsTrue()
    {
        when(this.rrp.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE);
        assertTrue(this.subjectUtils.isSubject(nodeBuilder));
    }

    @Test
    public void isSubjectForNotSubjectNodeBuilderReturnsFalse()
    {
        when(this.rrp.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE_TYPE);
        assertFalse(this.subjectUtils.isSubject(nodeBuilder));
    }

    @Test
    public void isSubjectForNodeStateWithNullArgumentReturnsFalse()
    {
        assertFalse(this.subjectUtils.isSubject((NodeState) null));
    }

    @Test
    public void isSubjectForActualSubjectNodeStateReturnsTrue()
    {
        when(this.rrp.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE);
        assertTrue(this.subjectUtils.isSubject(nodeBuilder.getNodeState()));
    }

    @Test
    public void isSubjectForNotSubjectNodeStateReturnsFalse()
    {
        when(this.rrp.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE_TYPE);
        assertFalse(this.subjectUtils.isSubject(nodeBuilder.getNodeState()));
    }

    @Test
    public void getTypeForActualSubjectNodeWithTypeProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node type = this.subjectUtils.getType(subject);
        assertNotNull(type);
        assertEquals(ROOT_SUBJECT_TYPE_PATH, type.getPath());
    }

    @Test
    public void getTypeForActualSubjectNodeWithoutTypePropertyReturnsNull() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        subject.getProperty(TYPE_PROPERTY).remove();

        Node type = this.subjectUtils.getType(subject);
        assertNull(type);
    }

    @Test
    public void getTypeForNotSubjectNodeReturnsNull() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node fakeSubject = session.getNode(ROOT_SUBJECT_TYPE_PATH);

        Node type = this.subjectUtils.getType(fakeSubject);
        assertNull(type);
    }

    @Test
    public void getTypeForSubjectNodeThrowsExceptionReturnsNull() throws RepositoryException
    {
        Node subject = mock(Node.class);
        when(subject.isNodeType(SUBJECT_TYPE)).thenReturn(true);
        when(subject.hasProperty(TYPE_PROPERTY)).thenThrow(new RepositoryException());

        Node type = this.subjectUtils.getType(subject);
        assertNull(type);
    }

    @Test
    public void getLabelForActualSubjectNodeWithLabelProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        String label = this.subjectUtils.getLabel(subject);
        assertNotNull(label);
        assertEquals("root_subject", label);
    }

    @Test
    public void getLabelForActualSubjectNodeWithoutLabelPropertyReturnsNull() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        subject.getProperty(LABEL_PROPERTY).remove();

        String label = this.subjectUtils.getLabel(subject);
        assertNull(label);
    }

    @Test
    public void getLabelForNotSubjectNodeReturnsNull() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node fakeSubject = session.getNode(ROOT_SUBJECT_TYPE_PATH);

        String label = this.subjectUtils.getLabel(fakeSubject);
        assertNull(label);
    }

    @Test
    public void getLabelForSubjectNodeThrowsExceptionReturnsNull() throws RepositoryException
    {
        Node subject = mock(Node.class);
        when(subject.isNodeType(SUBJECT_TYPE)).thenReturn(true);
        when(subject.hasProperty(LABEL_PROPERTY)).thenThrow(new RepositoryException());

        String label = this.subjectUtils.getLabel(subject);
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
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        LABEL_PROPERTY, "root_subject")
                .commit();
    }

}
