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
package io.uhndata.cards.forms.internal;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.plugins.memory.LongPropertyState;
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
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for {@link QuestionMatrixEditor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class QuestionMatrixEditorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String ANSWER_OPTION_TYPE = "cards:AnswerOption";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String TEST_MATRIX_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionMatrixQuestionnaire";
    private static final String TEST_MATRIX_PATH = "/Questionnaires/TestQuestionMatrixQuestionnaire/matrix";
    private static final String TEST_MATRIX_OPTION_1_PATH = "/Questionnaires/TestQuestionMatrixQuestionnaire/matrix/o1";
    private static final String TEST_MATRIX_OPTION_2_PATH = "/Questionnaires/TestQuestionMatrixQuestionnaire/matrix/o2";
    private static final String TEST_MATRIX_OPTION_3_PATH = "/Questionnaires/TestQuestionMatrixQuestionnaire/matrix/o3";
    private static final String TEST_MATRIX_OPTION_4_PATH =
            "/Questionnaires/TestQuestionMatrixQuestionnaire/matrix/question_1/o4";
    private static final String TEST_QUESTION_1_PATH =
            "/Questionnaires/TestQuestionMatrixQuestionnaire/matrix/question_1";
    private static final String TEST_QUESTION_2_PATH =
            "/Questionnaires/TestQuestionMatrixQuestionnaire/matrix/question_2";
    private static final String TEST_QUESTION_3_PATH =
            "/Questionnaires/TestQuestionMatrixQuestionnaire/matrix/question_3";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String MIN_ANSWER_PROPERTY = "minAnswers";
    private static final String MAX_ANSWER_PROPERTY = "maxAnswers";
    private static final String VALUE_PROPERTY = "value";
    private static final String UUID_PROPERTY = "jcr:uuid";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private NodeBuilder currentNodeBuilder;

    private QuestionMatrixEditor questionMatrixEditor;

    @Test
    public void constructorTest()
    {
        Assert.assertNotNull(this.questionMatrixEditor);
    }

    @Test
    public void propertyChangedTest() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);

        PropertyState propertyState = new LongPropertyState(MAX_ANSWER_PROPERTY, 1);
        this.questionMatrixEditor.propertyChanged(Mockito.mock(PropertyState.class), propertyState);
        Assert.assertEquals(Long.valueOf(1),
                this.currentNodeBuilder.getChildNode(session.getNode(TEST_QUESTION_1_PATH).getIdentifier())
                        .getProperty(MAX_ANSWER_PROPERTY).getValue(Type.LONG));
    }

    @Test
    public void propertyAddedTest() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);

        PropertyState propertyState = new LongPropertyState(MIN_ANSWER_PROPERTY, 1);
        this.questionMatrixEditor.propertyAdded(propertyState);
        Assert.assertEquals(Long.valueOf(1),
                this.currentNodeBuilder.getChildNode(session.getNode(TEST_QUESTION_1_PATH).getIdentifier())
                .getProperty(MIN_ANSWER_PROPERTY).getValue(Type.LONG));
    }

    @Test
    public void childNodeAddedForAnswerOptionNode() throws RepositoryException, CommitFailedException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String option3Uuid = session.getNode(TEST_MATRIX_OPTION_3_PATH).getIdentifier();
        String questionUuid = session.getNode(TEST_QUESTION_1_PATH).getIdentifier();

        this.currentNodeBuilder.setProperty(MIN_ANSWER_PROPERTY, 1);
        this.questionMatrixEditor = new QuestionMatrixEditor(this.currentNodeBuilder);

        Editor editor = this.questionMatrixEditor.childNodeAdded(option3Uuid,
                Mockito.mock(NodeState.class));
        Assert.assertNull(editor);
        Assert.assertTrue(this.currentNodeBuilder.getChildNode(questionUuid).hasChildNode(option3Uuid));
    }

    @Test
    public void childNodeAddedForQuestionNode() throws RepositoryException, CommitFailedException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String question3Uuid = session.getNode(TEST_QUESTION_3_PATH).getIdentifier();
        this.currentNodeBuilder.setProperty(MIN_ANSWER_PROPERTY, 1);
        this.questionMatrixEditor = new QuestionMatrixEditor(this.currentNodeBuilder);

        Editor editor = this.questionMatrixEditor.childNodeAdded(question3Uuid, Mockito.mock(NodeState.class));
        Assert.assertNull(editor);
        Assert.assertTrue(this.currentNodeBuilder.getChildNode(question3Uuid).hasProperty(MIN_ANSWER_PROPERTY));
        Assert.assertEquals(Long.valueOf(1), this.currentNodeBuilder.getChildNode(question3Uuid)
                .getProperty(MIN_ANSWER_PROPERTY).getValue(Type.LONG));
    }

    @Test
    public void childNodeAddedForNotMatrix() throws RepositoryException, CommitFailedException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String question3Uuid = session.getNode(TEST_QUESTION_3_PATH).getIdentifier();
        String option3Uuid = session.getNode(TEST_MATRIX_OPTION_3_PATH).getIdentifier();
        this.currentNodeBuilder = this.currentNodeBuilder.getChildNode(question3Uuid);
        this.questionMatrixEditor = new QuestionMatrixEditor(this.currentNodeBuilder);

        Editor editor = this.questionMatrixEditor.childNodeAdded(option3Uuid, Mockito.mock(NodeState.class));
        Assert.assertNotNull(editor);
        Assert.assertTrue(editor instanceof QuestionMatrixEditor);
    }

    @Test
    public void childNodeChangedForAnswerOption() throws RepositoryException, CommitFailedException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String question1Uuid = session.getNode(TEST_QUESTION_1_PATH).getIdentifier();
        String option1Uuid = session.getNode(TEST_MATRIX_OPTION_1_PATH).getIdentifier();
        this.currentNodeBuilder.getChildNode(option1Uuid).setProperty(VALUE_PROPERTY, 4L);

        Editor editor = this.questionMatrixEditor.childNodeChanged(option1Uuid, Mockito.mock(NodeState.class),
                Mockito.mock(NodeState.class));
        Assert.assertNull(editor);
        Assert.assertEquals(Long.valueOf(4), this.currentNodeBuilder.getChildNode(question1Uuid)
                .getChildNode(option1Uuid).getProperty(VALUE_PROPERTY).getValue(Type.LONG));
    }

    @Test
    public void childNodeChangedForNotMatrix() throws RepositoryException, CommitFailedException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String question1Uuid = session.getNode(TEST_QUESTION_1_PATH).getIdentifier();
        String option1Uuid = session.getNode(TEST_MATRIX_OPTION_1_PATH).getIdentifier();
        this.currentNodeBuilder.getChildNode(option1Uuid).setProperty(VALUE_PROPERTY, 4L);
        this.currentNodeBuilder = this.currentNodeBuilder.getChildNode(question1Uuid);
        this.questionMatrixEditor = new QuestionMatrixEditor(this.currentNodeBuilder);

        Editor editor = this.questionMatrixEditor.childNodeChanged(option1Uuid, Mockito.mock(NodeState.class),
                Mockito.mock(NodeState.class));
        Assert.assertNotNull(editor);
        Assert.assertTrue(editor instanceof QuestionMatrixEditor);
    }

    @Test
    public void childNodeDeletedForAnswerOption() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String question1Uuid = session.getNode(TEST_QUESTION_1_PATH).getIdentifier();
        String option4Uuid = session.getNode(TEST_MATRIX_OPTION_4_PATH).getIdentifier();

        Editor editor = this.questionMatrixEditor.childNodeDeleted(option4Uuid,
                this.currentNodeBuilder.getChildNode(question1Uuid).getChildNode(option4Uuid).getNodeState());
        Assert.assertNull(editor);
        Assert.assertFalse(this.currentNodeBuilder.getChildNode(question1Uuid).hasChildNode(option4Uuid));
    }

    @Test
    public void childNodeDeletedForNotMatrix() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String question1Uuid = session.getNode(TEST_QUESTION_1_PATH).getIdentifier();
        String option1Uuid = session.getNode(TEST_MATRIX_OPTION_1_PATH).getIdentifier();
        this.currentNodeBuilder = this.currentNodeBuilder.getChildNode(question1Uuid);
        this.questionMatrixEditor = new QuestionMatrixEditor(this.currentNodeBuilder);

        Editor editor = this.questionMatrixEditor.childNodeDeleted(option1Uuid, Mockito.mock(NodeState.class));
        Assert.assertNotNull(editor);
        Assert.assertTrue(editor instanceof QuestionMatrixEditor);
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
        this.context.load().json("/MatrixQuestionnaires.json", TEST_MATRIX_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();

        final Session session = this.context.resourceResolver().adaptTo(Session.class);

        Node option1Node = session.getNode(TEST_MATRIX_OPTION_1_PATH);
        String option1Uuid = option1Node.getIdentifier();
        NodeBuilder option1Builder = createLongOption(option1Uuid, 1L);

        Node option2Node = session.getNode(TEST_MATRIX_OPTION_2_PATH);
        String option2Uuid = option2Node.getIdentifier();
        NodeBuilder option2Builder = createLongOption(option2Uuid, 2L);

        Node option3Node = session.getNode(TEST_MATRIX_OPTION_3_PATH);
        String option3Uuid = option3Node.getIdentifier();
        NodeBuilder option3Builder = createLongOption(option3Uuid, 3L);

        Node option4Node = session.getNode(TEST_MATRIX_OPTION_4_PATH);
        String option4Uuid = option4Node.getIdentifier();
        NodeBuilder option4Builder = createLongOption(option4Uuid, 4L);

        Node question1Node = session.getNode(TEST_QUESTION_1_PATH);
        String question1Uuid = question1Node.getIdentifier();
        NodeBuilder question1Builder = createLongQuestion(question1Uuid,
                Map.of(option1Uuid, option1Builder, option2Uuid, option2Builder, option4Uuid, option4Builder));

        Node question2Node = session.getNode(TEST_QUESTION_2_PATH);
        String question2Uuid = question2Node.getIdentifier();
        NodeBuilder question2Builder = createLongQuestion(question2Uuid,
                Map.of(option1Uuid, option1Builder, option2Uuid, option2Builder));

        Node question3Node = session.getNode(TEST_QUESTION_3_PATH);
        String question3Uuid = question3Node.getIdentifier();
        NodeBuilder question3Builder = createLongQuestion(question3Uuid,
                new HashMap<>());

        String matrixUuid = session.getNode(TEST_MATRIX_PATH).getIdentifier();
        this.currentNodeBuilder = createMatrix(matrixUuid, Map.of(option1Uuid, option1Builder, option2Uuid,
                option2Builder, option3Uuid, option3Builder, question1Uuid, question1Builder, question2Uuid,
                question2Builder, question3Uuid, question3Builder));

        this.questionMatrixEditor = new QuestionMatrixEditor(this.currentNodeBuilder);
    }

    private NodeBuilder createMatrix(String uuid, Map<String, NodeBuilder> children)
    {
        NodeBuilder matrixBuilder = EmptyNodeState.EMPTY_NODE.builder();
        matrixBuilder.setProperty(NODE_TYPE, "cards:Section");
        matrixBuilder.setProperty(MAX_ANSWER_PROPERTY, 2);
        matrixBuilder.setProperty("displayMode", "matrix");
        matrixBuilder.setProperty("dataType", "long");
        matrixBuilder.setProperty("label", "Matrix label");
        matrixBuilder.setProperty(UUID_PROPERTY, uuid);
        for (Map.Entry<String, NodeBuilder> child : children.entrySet()) {
            matrixBuilder.setChildNode(child.getKey(), child.getValue().getNodeState());
        }
        return matrixBuilder;
    }

    private NodeBuilder createLongQuestion(String uuid, Map<String, NodeBuilder> children)
    {
        NodeBuilder questionBuilder = EmptyNodeState.EMPTY_NODE.builder();
        questionBuilder.setProperty(NODE_TYPE, "cards:Question");
        questionBuilder.setProperty("displayMode", "list");
        questionBuilder.setProperty("dataType", "long");
        questionBuilder.setProperty(UUID_PROPERTY, uuid);
        for (Map.Entry<String, NodeBuilder> child : children.entrySet()) {
            questionBuilder.setChildNode(child.getKey(), child.getValue().getNodeState());
        }
        return questionBuilder;
    }

    private NodeBuilder createLongOption(String uuid, Long id)
    {
        NodeBuilder optionBuilder = EmptyNodeState.EMPTY_NODE.builder();
        optionBuilder.setProperty(NODE_TYPE, ANSWER_OPTION_TYPE, Type.NAME);
        optionBuilder.setProperty("sling:resourceSuperType", ANSWER_OPTION_TYPE, Type.STRING);
        optionBuilder.setProperty("label", "Option " + id, Type.STRING);
        optionBuilder.setProperty(VALUE_PROPERTY, String.valueOf(id), Type.STRING);
        optionBuilder.setProperty("defaultOrder", String.valueOf(id), Type.STRING);
        optionBuilder.setProperty("notApplicable", true, Type.BOOLEAN);
        optionBuilder.setProperty("noneOfTheAbove", false, Type.BOOLEAN);
        optionBuilder.setProperty(UUID_PROPERTY, uuid);
        return optionBuilder;
    }

}
