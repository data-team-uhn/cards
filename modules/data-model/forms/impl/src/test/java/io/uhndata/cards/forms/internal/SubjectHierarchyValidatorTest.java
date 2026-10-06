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
import org.mockito.junit.MockitoJUnitRunner;

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

    private SubjectHierarchyValidator subjectHierarchyValidator;

    @Test
    public void enterAddsNodeState() throws CommitFailedException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        NodeState leafSubject = createSubjectNodeState(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH).getIdentifier());

        // Push an extra leaf subject on the stack of parents; a new leaf subject requires its immediate parent to
        // be a branch subject, but the top of the stack is now a leaf subject, so the validation must fail
        this.subjectHierarchyValidator.enter(Mockito.mock(NodeState.class), leafSubject);

        stubSubjectTypeHierarchy(session);
        NodeState newLeafSubject =
            createSubjectNodeState(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH).getIdentifier());
        Assert.assertThrows(CommitFailedException.class,
            () -> this.subjectHierarchyValidator.childNodeAdded(UUID.randomUUID().toString(), newLeafSubject));
    }

    @Test
    public void leaveRemovesLastNodeState() throws CommitFailedException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        NodeState leafSubject = createSubjectNodeState(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH).getIdentifier());

        // Push an extra leaf subject on the stack of parents, then pop it; the stack must be back to the
        // root/branch hierarchy, so a new leaf subject must pass the validation again
        this.subjectHierarchyValidator.enter(Mockito.mock(NodeState.class), leafSubject);
        this.subjectHierarchyValidator.leave(Mockito.mock(NodeState.class), Mockito.mock(NodeState.class));

        stubSubjectTypeHierarchy(session);
        NodeState newLeafSubject =
            createSubjectNodeState(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH).getIdentifier());
        Validator validator =
            this.subjectHierarchyValidator.childNodeAdded(UUID.randomUUID().toString(), newLeafSubject);
        Assert.assertEquals(this.subjectHierarchyValidator, validator);
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
        stubSubjectTypeHierarchy(session);

        Validator validator = this.subjectHierarchyValidator.childNodeAdded(UUID.randomUUID().toString(), subject);
        Assert.assertNotNull(validator);
        Assert.assertTrue(validator instanceof SubjectHierarchyValidator);
        Assert.assertEquals(this.subjectHierarchyValidator, validator);
    }

    @Test
    public void childNodeAddedForNotSubjectNodeReturnsThisValidator() throws CommitFailedException
    {
        Validator validator = this.subjectHierarchyValidator.childNodeAdded(UUID.randomUUID().toString(),
            Mockito.mock(NodeState.class));
        Assert.assertNotNull(validator);
        Assert.assertTrue(validator instanceof SubjectHierarchyValidator);
        Assert.assertEquals(this.subjectHierarchyValidator, validator);
    }

    @Test
    public void childNodeAddedForSubjectNodeThrowsException() throws RepositoryException, CommitFailedException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        NodeState subject = createSubjectNodeState(session.getNode(TEST_LEAF_SUBJECT_TYPE_PATH).getIdentifier());
        stubSubjectTypeHierarchy(session);

        // Pop the branch subject off the stack of parents; the leaf subject can no longer find its required parent
        this.subjectHierarchyValidator.leave(Mockito.mock(NodeState.class), Mockito.mock(NodeState.class));
        Assert.assertThrows(CommitFailedException.class,
            () -> this.subjectHierarchyValidator.childNodeAdded(UUID.randomUUID().toString(), subject));

    }

    @Before
    public void setupRepo() throws RepositoryException, CommitFailedException
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

        // Seed the validator's stack of parent nodes through its public API, simulating the descent from the root
        // subject through the branch subject
        NodeState rootState = createSubjectNodeState(root.getIdentifier());
        NodeState branchState = createSubjectNodeState(branch.getIdentifier());
        this.subjectHierarchyValidator.enter(Mockito.mock(NodeState.class), rootState);
        this.subjectHierarchyValidator.enter(Mockito.mock(NodeState.class), branchState);
    }

    private void stubSubjectTypeHierarchy(Session session) throws RepositoryException
    {
        Mockito.when(this.subjectUtils.isSubject(Mockito.any(NodeState.class))).thenReturn(true);
        Mockito.when(this.subjectTypeUtils.getSubjectType(Mockito.anyString()))
            .thenAnswer(invocation -> session.getNodeByIdentifier(invocation.getArgument(0)));
        // A subject type only requires a parent if it has a "parents" property; Root has none, so the hierarchy
        // walk stops above Root through this condition instead of a swallowed exception
        Mockito.when(this.subjectTypeUtils.isSubjectType(Mockito.any(Node.class))).thenAnswer(invocation -> {
            Node type = invocation.getArgument(0);
            return type != null && type.hasProperty(PARENTS_PROPERTY);
        });
    }

    private NodeState createSubjectNodeState(String subjectTypeId)
    {
        NodeBuilder subject = EmptyNodeState.EMPTY_NODE.builder();
        subject.setProperty(NODE_TYPE, SUBJECT_TYPE, Type.NAME);
        subject.setProperty(TYPE_PROPERTY, subjectTypeId, Type.REFERENCE);
        return subject.getNodeState();
    }
}
