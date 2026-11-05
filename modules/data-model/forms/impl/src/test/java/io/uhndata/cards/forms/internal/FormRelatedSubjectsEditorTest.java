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

import java.util.Iterator;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link FormRelatedSubjectsEditor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class FormRelatedSubjectsEditorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";

    private static final String FORM_TYPE = "cards:Form";

    private static final String SUBJECT_TYPE = "cards:Subject";

    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";

    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";

    private static final String TEST_SUBJECT_CHILD_PATH = "/Subjects/Test/TestTumor";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private FormRelatedSubjectsEditor formRelatedSubjectsEditor;

    private NodeBuilder currentNodeBuilder;

    private Session session;

    @Test
    public void childNodeAddedTestForFormNode() throws CommitFailedException
    {
        String name = this.currentNodeBuilder.getProperty("jcr:uuid").getValue(Type.STRING);
        Editor editor = this.formRelatedSubjectsEditor.childNodeAdded(name, Mockito.mock(NodeState.class));
        Assert.assertNull(editor);
    }

    @Test
    public void childNodeAddedTestForAnswerSectionNode() throws CommitFailedException
    {
        this.currentNodeBuilder = getFormSection();
        this.formRelatedSubjectsEditor = new FormRelatedSubjectsEditor(this.currentNodeBuilder, this.session);
        String name = this.currentNodeBuilder.getProperty("jcr:uuid").getValue(Type.STRING);
        Editor editor = this.formRelatedSubjectsEditor.childNodeAdded(name, Mockito.mock(NodeState.class));
        Assert.assertNotNull(editor);
        Assert.assertTrue(editor instanceof FormRelatedSubjectsEditor);
    }

    @Test
    public void childNodeChangedTestForFormNode() throws CommitFailedException
    {
        String name = this.currentNodeBuilder.getProperty("jcr:uuid").getValue(Type.STRING);
        Editor editor = this.formRelatedSubjectsEditor.childNodeChanged(name, Mockito.mock(NodeState.class),
            Mockito.mock(NodeState.class));
        Assert.assertNull(editor);

    }

    @Test
    public void childNodeChangedTestForAnswerSectionNode() throws CommitFailedException
    {
        this.currentNodeBuilder = getFormSection();
        this.formRelatedSubjectsEditor = new FormRelatedSubjectsEditor(this.currentNodeBuilder, this.session);
        String name = this.currentNodeBuilder.getProperty("jcr:uuid").getValue(Type.STRING);
        Editor editor = this.formRelatedSubjectsEditor.childNodeChanged(name, Mockito.mock(NodeState.class),
            Mockito.mock(NodeState.class));
        Assert.assertNotNull(editor);
        Assert.assertTrue(editor instanceof FormRelatedSubjectsEditor);
    }

    @Test
    public void leaveTest() throws CommitFailedException, RepositoryException
    {
        this.formRelatedSubjectsEditor.leave(Mockito.mock(NodeState.class), Mockito.mock(NodeState.class));
        Iterator<String> relatedSubjects = this.currentNodeBuilder.getProperty("relatedSubjects")
            .getValue(Type.WEAKREFERENCES).iterator();
        Assert.assertTrue(relatedSubjects.hasNext());
        Assert.assertEquals(this.session.getNode(TEST_SUBJECT_CHILD_PATH).getIdentifier(), relatedSubjects.next());
        Assert.assertTrue(relatedSubjects.hasNext());
        Assert.assertEquals(this.session.getNode(TEST_SUBJECT_PATH).getIdentifier(), relatedSubjects.next());
    }

    @Test
    public void leaveTestThrowsException() throws CommitFailedException
    {
        this.currentNodeBuilder = this.currentNodeBuilder.setProperty("subject", "not_existing_node_uuid");
        this.formRelatedSubjectsEditor = new FormRelatedSubjectsEditor(this.currentNodeBuilder, this.session);
        this.formRelatedSubjectsEditor.leave(Mockito.mock(NodeState.class), Mockito.mock(NodeState.class));
        Assert.assertNull(this.currentNodeBuilder.getProperty("relatedSubjects"));
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        this.session = this.context.resourceResolver().adaptTo(Session.class);

        this.context.build()
            .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
            .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
            .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
            .commit();
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
            .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
            .commit();
        this.context.build()
            .resource(TEST_SUBJECT_CHILD_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class),
                "parents", this.session.getNode(TEST_SUBJECT_PATH))
            .commit();

        String subjectChildUuid = this.session.getNode(TEST_SUBJECT_CHILD_PATH).getIdentifier();

        // Create NodeBuilder/NodeState instances of New Test Form
        String answerSectionUuid = UUID.randomUUID().toString();
        NodeBuilder answerSectionBuilder = createTestAnswerSection(answerSectionUuid);

        String formUuid = UUID.randomUUID().toString();
        this.currentNodeBuilder = createTestForm(formUuid, subjectChildUuid, answerSectionUuid,
            answerSectionBuilder.getNodeState());
        this.formRelatedSubjectsEditor = new FormRelatedSubjectsEditor(this.currentNodeBuilder, this.session);

    }

    private NodeBuilder createTestForm(String uuid, String subjectUuid, String answerSectionUuid,
        NodeState answerSection)
    {
        NodeBuilder formBuilder = EmptyNodeState.EMPTY_NODE.builder();
        formBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        formBuilder.setProperty("subject", subjectUuid);
        formBuilder.setProperty("jcr:uuid", uuid);
        formBuilder.setChildNode(answerSectionUuid, answerSection);
        return formBuilder;
    }

    private NodeBuilder createTestAnswerSection(String uuid)
    {
        NodeBuilder answerSectionBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerSectionBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        answerSectionBuilder.setProperty("jcr:uuid", uuid);
        return answerSectionBuilder;
    }

    private NodeBuilder getFormSection()
    {
        for (String name : this.currentNodeBuilder.getChildNodeNames()) {
            NodeState child = this.currentNodeBuilder.getChildNode(name).getNodeState();
            if (child.hasProperty(NODE_TYPE)
                && ANSWER_SECTION_TYPE.equals(child.getProperty(NODE_TYPE).getValue(Type.STRING))) {
                return child.builder();
            }
        }
        return null;
    }
}
