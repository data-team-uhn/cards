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
package io.uhndata.cards.formcompletionstatus;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.forms.api.FormUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnswerCompletionStatusEditor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class AnswerCompletionStatusEditorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String NODE_IDENTIFIER = "jcr:uuid";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String SECTION_PROPERTY = "section";
    private static final String STATUS_FLAGS = "statusFlags";
    private static final String STATUS_FLAG_INCOMPLETE = "INCOMPLETE";
    private static final String STATUS_FLAG_INVALID = "INVALID";
    private static final String STATUS_FLAG_DRAFT = "DRAFT";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private AnswerCompletionStatusEditor answerCompletionStatusEditor;

    private NodeBuilder currentNodeBuilder;

    private Session session;

    @Mock
    private FormUtils formUtils;

    @Test
    public void constructorTest()
    {
        initAnswerCompletionStatusEditor(true);
        assertNotNull(this.answerCompletionStatusEditor);
    }

    @Test
    public void childNodeAddedForFormNodeReturnsNull() throws CommitFailedException
    {
        initAnswerCompletionStatusEditor(true);

        assertNull(this.answerCompletionStatusEditor.childNodeAdded("answer_1", mock(NodeState.class)));
    }

    @Test
    public void childNodeAddedForNotFormNodeReturnsAnswerCompletionStatusEditor() throws CommitFailedException
    {
        this.currentNodeBuilder = this.currentNodeBuilder.getChildNode("answerSection_1");
        initAnswerCompletionStatusEditor(false);

        Editor editor = this.answerCompletionStatusEditor.childNodeAdded("answer_2", mock(NodeState.class));
        assertNotNull(editor);
        assertTrue(editor instanceof AnswerCompletionStatusEditor);
    }

    @Test
    public void childNodeChangedForFormNodeReturnsNull() throws CommitFailedException
    {
        initAnswerCompletionStatusEditor(true);

        assertNull(this.answerCompletionStatusEditor.childNodeChanged("answer_1", mock(NodeState.class),
                mock(NodeState.class)));
    }

    @Test
    public void childNodeChangedForNotFormNodeReturnsAnswerCompletionStatusEditor() throws CommitFailedException
    {
        this.currentNodeBuilder = this.currentNodeBuilder.getChildNode("answerSection_1");
        initAnswerCompletionStatusEditor(false);

        Editor editor = this.answerCompletionStatusEditor.childNodeChanged("answer_2", mock(NodeState.class),
                mock(NodeState.class));
        assertNotNull(editor);
        assertTrue(editor instanceof AnswerCompletionStatusEditor);
    }

    @Test
    public void enterForFormNodeAddsDifferentStatusFlags() throws CommitFailedException, RepositoryException
    {
        initAnswerCompletionStatusEditor(true);
        when(this.formUtils.isAnswer(any(NodeBuilder.class))).thenReturn(true, false, true, false);
        when(this.formUtils.getQuestion(any(NodeBuilder.class)))
                .thenReturn(this.session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_2"),
                        this.session.getNode(TEST_QUESTIONNAIRE_PATH + "/question_1"));
        when(this.formUtils.isAnswerSection(any(NodeBuilder.class))).thenReturn(true);

        this.answerCompletionStatusEditor.enter(mock(NodeState.class), mock(NodeState.class));
        assertTrue(this.currentNodeBuilder.hasProperty(STATUS_FLAGS));
        Iterator<String> statusFlagsIterator =
                this.currentNodeBuilder.getProperty(STATUS_FLAGS).getValue(Type.STRINGS).iterator();
        assertEquals(STATUS_FLAG_DRAFT, statusFlagsIterator.next());
        assertEquals(STATUS_FLAG_INCOMPLETE, statusFlagsIterator.next());
        assertEquals(STATUS_FLAG_INVALID, statusFlagsIterator.next());
    }

    @Test
    public void enterForEmptyFormNode() throws CommitFailedException
    {
        this.currentNodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        initAnswerCompletionStatusEditor(true);

        this.answerCompletionStatusEditor.enter(mock(NodeState.class), mock(NodeState.class));
        assertTrue(this.currentNodeBuilder.hasProperty(STATUS_FLAGS));
        Iterator<String> statusFlagsIterator =
                this.currentNodeBuilder.getProperty(STATUS_FLAGS).getValue(Type.STRINGS).iterator();
        assertEquals(STATUS_FLAG_DRAFT, statusFlagsIterator.next());
        assertEquals(STATUS_FLAG_INCOMPLETE, statusFlagsIterator.next());
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
                .commit();
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();

        this.session = this.context.resourceResolver().adaptTo(Session.class);

        String questionUuid = this.session.getNode(TEST_QUESTIONNAIRE_PATH + "/question_1").getIdentifier();
        String answerUuid = UUID.randomUUID().toString();
        NodeBuilder answerNodeBuilder = createTestAnswer(answerUuid, questionUuid, STATUS_FLAG_INCOMPLETE);

        String sectionUuid = this.session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1").getIdentifier();
        String answerSectionUuid = UUID.randomUUID().toString();
        String questionInSectionUuid = this.session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_2")
                .getIdentifier();
        String answerInSectionUuid = UUID.randomUUID().toString();
        NodeBuilder answerInSectionNodeBuilder = createTestAnswer(answerInSectionUuid, questionInSectionUuid,
                STATUS_FLAG_INVALID);
        NodeBuilder answerSectionNodeBuilder = createTestAnswerSection(answerSectionUuid, sectionUuid,
                Map.of("answer_2", answerInSectionNodeBuilder.getNodeState()));

        String formUuid = UUID.randomUUID().toString();
        String subjectUuid = this.session.getNode(TEST_SUBJECT_PATH).getIdentifier();
        String questionnaireUuid = this.session.getNode(TEST_QUESTIONNAIRE_PATH).getIdentifier();
        this.currentNodeBuilder = createTestForm(formUuid, questionnaireUuid, subjectUuid,
                Map.of("answer_1", answerNodeBuilder.getNodeState(),
                        "answerSection_1", answerSectionNodeBuilder.getNodeState()));
    }

    private void initAnswerCompletionStatusEditor(boolean isFormType)
    {
        when(this.formUtils.isForm(this.currentNodeBuilder)).thenReturn(isFormType);
        this.answerCompletionStatusEditor = new AnswerCompletionStatusEditor(this.currentNodeBuilder, true,
                this.session, this.formUtils, List.of());
    }

    private NodeBuilder createTestAnswerSection(String uuid, String sectionUuid, Map<String, NodeState> children)
    {
        NodeBuilder answerSectionBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerSectionBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        answerSectionBuilder.setProperty(SECTION_PROPERTY, sectionUuid);
        answerSectionBuilder.setProperty(NODE_IDENTIFIER, uuid);
        for (Map.Entry<String, NodeState> child : children.entrySet()) {
            answerSectionBuilder.setChildNode(child.getKey(), child.getValue());
        }
        return answerSectionBuilder;
    }

    private NodeBuilder createTestForm(String uuid, String questionnaireUuid, String subjectUuid,
                                       Map<String, NodeState> children)
    {
        NodeBuilder formBuilder = EmptyNodeState.EMPTY_NODE.builder();
        formBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        formBuilder.setProperty(QUESTIONNAIRE_PROPERTY, questionnaireUuid);
        formBuilder.setProperty(SUBJECT_PROPERTY, subjectUuid);
        formBuilder.setProperty(NODE_IDENTIFIER, uuid);
        for (Map.Entry<String, NodeState> child : children.entrySet()) {
            formBuilder.setChildNode(child.getKey(), child.getValue());
        }
        return formBuilder;
    }

    private NodeBuilder createTestAnswer(String uuid, String questionUuid, String statusFlag)
    {
        NodeBuilder answerBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerBuilder.setProperty(NODE_TYPE, ANSWER_TYPE);
        answerBuilder.setProperty(QUESTION_PROPERTY, questionUuid);
        answerBuilder.setProperty(NODE_IDENTIFIER, uuid);
        answerBuilder.setProperty(STATUS_FLAGS, statusFlag);
        return answerBuilder;
    }

}
