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
package io.uhndata.cards.forms.internal;

import java.lang.reflect.Field;
import java.util.Deque;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Unit tests for {@link SubjectHierarchyValidator}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectHierarchyValidatorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String TEST_ROOT_SUBJECT_TYPE_PATH = "/SubjectTypes/Root";
    private static final String TEST_BRANCH_SUBJECT_TYPE_PATH = "/SubjectTypes/Root/Branch";
    private static final String TEST_LEAF_SUBJECT_TYPE_PATH = "/SubjectTypes/Root/Branch/Leaf";
    private static final String TYPE_PROPERTY = "type";
    private static final String PARENTS_PROPERTY = "parents";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Mock
    private SubjectTypeUtils subjectTypeUtils;

    @Mock
    private SubjectUtils subjectUtils;

    private Deque<NodeState> parentNodes;

    private SubjectHierarchyValidator subjectHierarchyValidator;

    @Test
    public void constructorTest()
    {
        Assert.assertNotNull(this.subjectHierarchyValidator);
    }

    @Test
    public void enterAddsNodeState() throws CommitFailedException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        NodeState leafSubject = createSubjectNodeState(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH).getIdentifier());

        this.subjectHierarchyValidator.enter(Mockito.mock(NodeState.class), leafSubject);
        Assert.assertEquals(3, this.parentNodes.size());
        Assert.assertEquals(leafSubject, this.parentNodes.getLast());
    }

    @Test
    public void leaveRemovesLastNodeState() throws CommitFailedException
    {
        this.subjectHierarchyValidator.leave(Mockito.mock(NodeState.class), Mockito.mock(NodeState.class));
        Assert.assertEquals(1, this.parentNodes.size());
    }

    @Test
    public void childNodeChangedReturnsThisValidator() throws CommitFailedException
    {
        Validator validator = this.subjectHierarchyValidator.childNodeChanged(UUID.randomUUID().toString(),
                Mockito.mock(NodeState.class), Mockito.mock(NodeState.class));
        Assert.assertNotNull(validator);
        Assert.assertTrue(validator instanceof SubjectHierarchyValidator);
        Assert.assertEquals(this.subjectHierarchyValidator, validator);
    }

    @Test
    public void childNodeAddedForSubjectNodeReturnsThisValidator() throws CommitFailedException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        NodeState subject = createSubjectNodeState(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH).getIdentifier());
        Mockito.when(this.subjectUtils.isSubject(Mockito.any(NodeState.class))).thenReturn(true);
        Mockito.when(this.subjectTypeUtils.getSubjectType(subject.getProperty(TYPE_PROPERTY).getValue(Type.REFERENCE)))
                .thenReturn(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH));
        Mockito.when(this.subjectTypeUtils.isSubjectType(Mockito.any(Node.class))).thenReturn(true);

        Validator validator = this.subjectHierarchyValidator.childNodeAdded(UUID.randomUUID().toString(), subject);
        Assert.assertNotNull(validator);
        Assert.assertTrue(validator instanceof SubjectHierarchyValidator);
        Assert.assertEquals(this.subjectHierarchyValidator, validator);
    }

    @Test
    public void childNodeAddedForNotSubjectNodeReturnsThisValidator() throws CommitFailedException
    {
        Mockito.when(this.subjectUtils.isSubject(Mockito.any(NodeState.class))).thenReturn(false);

        Validator validator = this.subjectHierarchyValidator.childNodeAdded(UUID.randomUUID().toString(),
                Mockito.mock(NodeState.class));
        Assert.assertNotNull(validator);
        Assert.assertTrue(validator instanceof SubjectHierarchyValidator);
        Assert.assertEquals(this.subjectHierarchyValidator, validator);
    }

    @Test
    public void childNodeAddedForSubjectNodeThrowsException() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        NodeState subject = createSubjectNodeState(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH).getIdentifier());
        Mockito.when(this.subjectUtils.isSubject(Mockito.any(NodeState.class))).thenReturn(true);
        Mockito.when(this.subjectTypeUtils.getSubjectType(subject.getProperty(TYPE_PROPERTY).getValue(Type.REFERENCE)))
                .thenReturn(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH));
        Mockito.when(this.subjectTypeUtils.isSubjectType(Mockito.any(Node.class))).thenReturn(true);

        this.parentNodes.removeLast();
        Assert.assertThrows(CommitFailedException.class,
            () -> this.subjectHierarchyValidator.childNodeAdded(UUID.randomUUID().toString(), subject));

    }

    @Before
    public void setupRepo() throws RepositoryException, IllegalAccessException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);

        this.context.build()
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .commit();
        this.context.load().json("/SubjectTypes.json", TEST_ROOT_SUBJECT_TYPE_PATH);

        Node root = session.getNode(TEST_ROOT_SUBJECT_TYPE_PATH);
        Node branch = session.getNode(TEST_BRANCH_SUBJECT_TYPE_PATH);
        branch.setProperty(PARENTS_PROPERTY, root);
        Node leaf = session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH);
        leaf.setProperty(PARENTS_PROPERTY, branch);

        this.subjectHierarchyValidator = new SubjectHierarchyValidator(this.subjectTypeUtils, this.subjectUtils);
        this.parentNodes = getParentNodesTemporaryPublic();

        NodeState rootState = createSubjectNodeState(root.getIdentifier());
        NodeState branchState = createSubjectNodeState(branch.getIdentifier());
        this.parentNodes.add(rootState);
        this.parentNodes.add(branchState);
    }

    private Deque<NodeState> getParentNodesTemporaryPublic() throws IllegalAccessException
    {
        for (Field field : this.subjectHierarchyValidator.getClass().getDeclaredFields()) {
            if (field.getType().equals(Deque.class)) {
                field.setAccessible(true);
                return ((Deque<NodeState>) field.get(this.subjectHierarchyValidator));
            }
        }
        return null;
    }

    private NodeState createSubjectNodeState(String subjectTypeId)
    {
        NodeBuilder subject = EmptyNodeState.EMPTY_NODE.builder();
        subject.setProperty(NODE_TYPE, SUBJECT_TYPE, Type.NAME);
        subject.setProperty(TYPE_PROPERTY, subjectTypeId, Type.REFERENCE);
        return subject.getNodeState();
    }
}
