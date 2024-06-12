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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolverFactory;
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

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReferenceAnswersEditor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class ReferenceAnswersEditorInSectionTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String FORM_TYPE = "cards:Form";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String REFERENCE_ANSWER_TYPE = "cards:ReferenceAnswer";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String TEST_COMPUTED_QUESTIONNAIRE_PATH = "/Questionnaires/TestComputedQuestionnaire";
    private static final String TEST_SECTION_PATH =
            "/Questionnaires/TestComputedQuestionnaire/from_long_to_computed_section";
    private static final String TEST_LONG_QUESTION_PATH =
            "/Questionnaires/TestComputedQuestionnaire/from_long_to_computed_section/long_question";
    private static final String TEST_REFERENCE_QUESTIONNAIRE_PATH = "/Questionnaires/TestReferenceQuestionnaire";
    private static final String TEST_REFERENCE_SECTION_PATH =
            "/Questionnaires/TestReferenceQuestionnaire/reference_section";
    private static final String TEST_REFERENCE_QUESTION_PATH =
            "/Questionnaires/TestReferenceQuestionnaire/reference_section/reference_question";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String UUID_PROPERTY = "jcr:uuid";
    private static final String VALUE_PROPERTY = "value";
    private static final String COPIED_FROM_PROPERTY = "copiedFrom";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String SECTION_PROPERTY = "section";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SERVICE_NAME = "referenceAnswers";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private ReferenceAnswersEditor referenceAnswersEditor;

    private Session currentSession;

    @Mock
    private ResourceResolverFactory rrf;

    private NodeBuilder nodeBuilder;

    @Mock
    private SubjectUtils subjectUtils;

    @Mock
    private FormUtils formUtils;

    @Mock
    private QuestionnaireUtils questionnaireUtils;

    @Test
    public void getServiceNameTest()
    {
        Assert.assertEquals(SERVICE_NAME, this.referenceAnswersEditor.getServiceName());
    }

    @Test
    public void constructorTest()
    {
        Assert.assertNotNull(this.referenceAnswersEditor);
    }

    @Test
    public void isMatchedAnswerNodeReturnsTrue() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);

        NodeBuilder referenceAnswer = getReferenceAnswer(this.nodeBuilder);
        Node referenceQuestion = session.getNode(TEST_REFERENCE_QUESTION_PATH);
        String referenceQuestionUuid = referenceQuestion.getIdentifier();
        when(this.referenceAnswersEditor.questionnaireUtils.getQuestion(referenceQuestionUuid))
                .thenReturn(referenceQuestion);
        Assert.assertTrue(this.referenceAnswersEditor.answerChangeTracker.isMatchedAnswerNode(
                referenceAnswer.getNodeState(), referenceQuestionUuid));

        referenceAnswer.setProperty(NODE_TYPE, REFERENCE_ANSWER_TYPE, Type.NAME);
        Assert.assertTrue(this.referenceAnswersEditor.answerChangeTracker.isMatchedAnswerNode(
                referenceAnswer.getNodeState(), referenceQuestionUuid));

    }

    @Test
    public void isMatchedAnswerNodeReturnsFalse() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);

        NodeBuilder section = getFormSection(this.nodeBuilder);
        Assert.assertFalse(this.referenceAnswersEditor.answerChangeTracker.isMatchedAnswerNode(
                section.getNodeState(), null));

        NodeBuilder referenceAnswer = getReferenceAnswer(this.nodeBuilder);
        Node referenceQuestion = Mockito.mock(Node.class);
        String referenceQuestionUuid = session.getNode(TEST_REFERENCE_QUESTION_PATH).getIdentifier();
        when(this.referenceAnswersEditor.questionnaireUtils.getQuestion(referenceQuestionUuid))
                .thenReturn(referenceQuestion);
        when(referenceQuestion.hasProperty(Mockito.anyString())).thenThrow(new RepositoryException());
        Assert.assertFalse(this.referenceAnswersEditor.answerChangeTracker.isMatchedAnswerNode(
                referenceAnswer.getNodeState(), referenceQuestionUuid));

    }

    @Test
    public void propertyAddedTest()
    {
        // for form type current node builder
        PropertyState propertyState = Mockito.mock(PropertyState.class);
        when(propertyState.getName()).thenReturn(QUESTIONNAIRE_PROPERTY);
        Assert.assertFalse(this.referenceAnswersEditor.shouldRunOnLeave);
        this.referenceAnswersEditor.propertyAdded(propertyState);
        Assert.assertTrue(this.referenceAnswersEditor.shouldRunOnLeave);

        // for not form type current node builder
        this.nodeBuilder = getFormSection(this.nodeBuilder);
        this.referenceAnswersEditor = new ReferenceAnswersEditor(this.nodeBuilder, this.currentSession, this.rrf,
                this.questionnaireUtils, this.formUtils, this.subjectUtils);
        Assert.assertFalse(this.referenceAnswersEditor.shouldRunOnLeave);
        this.referenceAnswersEditor.propertyAdded(Mockito.mock(PropertyState.class));
        Assert.assertFalse(this.referenceAnswersEditor.shouldRunOnLeave);
    }

    @Test
    public void childNodeAddedTest()
    {
        NodeBuilder referenceAnswer = getReferenceAnswer(this.nodeBuilder);
        String referenceAnswerUuid = referenceAnswer.getProperty(UUID_PROPERTY).getValue(Type.STRING);

        // for form type current node builder
        Editor editor = this.referenceAnswersEditor.childNodeAdded(referenceAnswerUuid, Mockito.mock(NodeState.class));
        Assert.assertTrue(editor instanceof AnswersEditor.AbstractAnswerChangeTracker);
        Assert.assertEquals(this.referenceAnswersEditor.answerChangeTracker, editor);

        // for not form type current node builder
        this.nodeBuilder = getFormSection(this.nodeBuilder);
        this.referenceAnswersEditor = new ReferenceAnswersEditor(this.nodeBuilder, this.currentSession, this.rrf,
                this.questionnaireUtils, this.formUtils, this.subjectUtils);

        editor = this.referenceAnswersEditor.childNodeAdded(referenceAnswerUuid, Mockito.mock(NodeState.class));
        Assert.assertTrue(editor instanceof ReferenceAnswersEditor);
    }


    @Test
    public void handleLeaveChangesReferenceValue()
    {
        this.referenceAnswersEditor.serviceSession = this.context.resourceResolver().adaptTo(Session.class);
        this.referenceAnswersEditor.handleLeave(this.nodeBuilder.getNodeState());

        Assert.assertTrue(getReferenceAnswer(this.nodeBuilder).hasProperty(VALUE_PROPERTY));
        Assert.assertEquals(Long.valueOf(200),
                getReferenceAnswer(this.nodeBuilder).getProperty(VALUE_PROPERTY).getValue(Type.LONG));
    }

    @Test
    public void handleLeaveForUncompletedSourceAnswer()
    {
        this.referenceAnswersEditor.serviceSession = this.context.resourceResolver().adaptTo(Session.class);
        when(this.formUtils.findAllSubjectRelatedAnswers(Mockito.any(Node.class), Mockito.any(), Mockito.any()))
                .thenReturn(List.of());
        this.referenceAnswersEditor.handleLeave(this.nodeBuilder.getNodeState());
        Assert.assertFalse(getReferenceAnswer(this.nodeBuilder).hasProperty(VALUE_PROPERTY));
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
        this.context.load().json("/ComputedQuestionnairesPlain.json", TEST_COMPUTED_QUESTIONNAIRE_PATH);
        this.context.load().json("/ReferenceQuestionnaires.json", TEST_REFERENCE_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();

        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String referenceQuestionnaireUuid = session.getNode(TEST_REFERENCE_QUESTIONNAIRE_PATH).getIdentifier();
        String referenceSectionUuid = session.getNode(TEST_REFERENCE_SECTION_PATH).getIdentifier();
        Node sourceQuestionnaire = session.getNode(TEST_COMPUTED_QUESTIONNAIRE_PATH);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node section = session.getNode(TEST_SECTION_PATH);

        Node sourceQuestionNode = session.getNode(TEST_LONG_QUESTION_PATH);

        Node referenceQuestionNode = session.getNode(TEST_REFERENCE_QUESTION_PATH);
        String referenceQuestionUuid = referenceQuestionNode.getIdentifier();

        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .resource("/Forms/f1",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, sourceQuestionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f1/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, section)
                .resource("/Forms/f1/s1/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, sourceQuestionNode,
                        VALUE_PROPERTY, 200L)
                .commit();

        // Create NodeBuilder/NodeState instances of New Test Source Form
        String referenceAnswerUuid = UUID.randomUUID().toString();
        NodeBuilder referenceAnswerBuilder = createTestAnswer(referenceAnswerUuid, referenceQuestionUuid,
                Map.of(COPIED_FROM_PROPERTY, "/Forms/f1/s1/a1"));
        String referenceAnswerSectionUuid = UUID.randomUUID().toString();
        NodeBuilder referenceAnswerSectionBuilder = createTestAnswerSection(referenceAnswerSectionUuid,
                referenceSectionUuid, Map.of(referenceAnswerUuid, referenceAnswerBuilder.getNodeState()));

        String formUuid = UUID.randomUUID().toString();
        NodeBuilder formBuilder = createTestForm(formUuid, referenceQuestionnaireUuid,
                Map.of(referenceAnswerSectionUuid, referenceAnswerSectionBuilder.getNodeState()));

        this.nodeBuilder = formBuilder;

        when(this.formUtils.isForm(this.nodeBuilder)).thenReturn(true);
        this.currentSession = this.context.resourceResolver().adaptTo(Session.class);
        this.referenceAnswersEditor = new ReferenceAnswersEditor(this.nodeBuilder, this.currentSession, this.rrf,
                this.questionnaireUtils, this.formUtils, this.subjectUtils);

        // mock Node getQuestionnaire()
        PropertyState propertyState = Mockito.mock(PropertyState.class);
        when(propertyState.getValue(Type.REFERENCE)).thenReturn(referenceQuestionnaireUuid);

        // mock QuestionTree getUnansweredMatchingQuestions(final Node currentNode)
        when(this.questionnaireUtils.isReferenceQuestion(Mockito.any())).thenReturn(false, false, true);
        when(this.questionnaireUtils.isQuestionnaire(Mockito.any())).thenReturn(true, false);
        when(this.questionnaireUtils.isSection(Mockito.any())).thenReturn(true, false);

        // mock Map<String, List<NodeBuilder>> getChildNodesByReference(final NodeBuilder nodeBuilder)
        when(this.formUtils.isAnswerSection(Mockito.any(NodeBuilder.class))).thenReturn(true, false);
        when(this.formUtils.isAnswer(Mockito.any(NodeBuilder.class))).thenReturn(true);
        when(this.formUtils.getSectionIdentifier(Mockito.any(NodeBuilder.class)))
                .thenReturn(session.getNode(TEST_REFERENCE_SECTION_PATH).getIdentifier());
        when(this.formUtils.getQuestionIdentifier(Mockito.any(NodeBuilder.class)))
                .thenReturn(session.getNode(TEST_REFERENCE_QUESTION_PATH).getIdentifier());

        // mock Object getAnswer(NodeState form, String questionPath)
        when(this.formUtils.getSubject(formBuilder.getNodeState())).thenReturn(subject);
        when(this.formUtils.findAllSubjectRelatedAnswers(Mockito.eq(subject), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(session.getNode("/Forms/f1/s1/a1")));
        when(this.formUtils.getValue(Mockito.any(Node.class))).thenReturn(200L);

    }


    private NodeBuilder createTestForm(String uuid, String questionnaireUuid, Map<String, NodeState> children)
    {
        NodeBuilder formBuilder = EmptyNodeState.EMPTY_NODE.builder();
        formBuilder.setProperty(NODE_TYPE, FORM_TYPE, Type.NAME);
        formBuilder.setProperty(QUESTIONNAIRE_PROPERTY, questionnaireUuid);
        formBuilder.setProperty(UUID_PROPERTY, uuid);
        for (Map.Entry<String, NodeState> child : children.entrySet()) {
            formBuilder.setChildNode(child.getKey(), child.getValue());
        }
        return formBuilder;
    }

    private NodeBuilder createTestAnswer(String uuid, String questionUuid, Map<String, Object> properties)
    {
        NodeBuilder answerBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerBuilder.setProperty(NODE_TYPE, ANSWER_TYPE, Type.NAME);
        answerBuilder.setProperty(QUESTION_PROPERTY, questionUuid);
        answerBuilder.setProperty(UUID_PROPERTY, uuid);
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            answerBuilder.setProperty(property.getKey(), property.getValue());
        }
        return answerBuilder;
    }

    private NodeBuilder createTestAnswerSection(String uuid, String sectionUuid, Map<String, NodeState> children)
    {
        NodeBuilder answerSectionBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerSectionBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE, Type.NAME);
        answerSectionBuilder.setProperty(SECTION_PROPERTY, sectionUuid);
        answerSectionBuilder.setProperty(UUID_PROPERTY, uuid);
        for (Map.Entry<String, NodeState> child : children.entrySet()) {
            answerSectionBuilder.setChildNode(child.getKey(), child.getValue());
        }
        return answerSectionBuilder;
    }

    private NodeBuilder getReferenceAnswer(NodeBuilder currentNodeBuilder)
    {
        for (String name : currentNodeBuilder.getChildNodeNames()) {
            NodeBuilder child = currentNodeBuilder.getChildNode(name);
            if (ANSWER_SECTION_TYPE.equals(child.getName(NODE_TYPE))) {
                return getReferenceAnswer(child);
            } else if (ANSWER_TYPE.equals(child.getName(NODE_TYPE))
                    && child.hasProperty(COPIED_FROM_PROPERTY)) {
                return child;
            }
        }
        return null;
    }

    private NodeBuilder getFormSection(NodeBuilder currentNode)
    {
        for (String name : currentNode.getChildNodeNames()) {
            NodeBuilder child = currentNode.getChildNode(name);
            if (child.hasProperty(NODE_TYPE)
                    && ANSWER_SECTION_TYPE.equals(child.getProperty(NODE_TYPE).getValue(Type.STRING))) {
                return child;
            }
        }
        return null;
    }

}
