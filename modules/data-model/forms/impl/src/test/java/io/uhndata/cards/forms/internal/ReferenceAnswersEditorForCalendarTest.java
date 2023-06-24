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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
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
public class ReferenceAnswersEditorForCalendarTest
{

    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String FORM_TYPE = "cards:Form";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String TEST_SOURCE_QUESTIONNAIRE_PATH = "/Questionnaires/TestSourceQuestionnaire";
    private static final String TEST_SOURCE_QUESTION_PATH = "/Questionnaires/TestSourceQuestionnaire/source_question";
    private static final String TEST_REFERENCE_QUESTIONNAIRE_PATH =
            "/Questionnaires/TestCalendarReferenceQuestionnaire";
    private static final String TEST_REFERENCE_QUESTION_PATH =
            "/Questionnaires/TestCalendarReferenceQuestionnaire/reference_question";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String UUID_PROPERTY = "jcr:uuid";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private ReferenceAnswersEditor referenceAnswersEditor;
    private NodeBuilder nodeBuilder;

    @Mock
    private ResourceResolverFactory rrf;

    @Mock
    private SubjectUtils subjectUtils;

    @Mock
    private FormUtils formUtils;

    @Mock
    private QuestionnaireUtils questionnaireUtils;


    @Test
    public void handleLeaveForCalendarTypeQuestionValue() throws ParseException, RepositoryException
    {
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
                .commit();
        this.context.load().json("/reference/SourceCalendarQuestionnaires.json", TEST_SOURCE_QUESTIONNAIRE_PATH);
        this.context.load().json("/reference/ReferenceCalendarQuestionnaires.json", TEST_REFERENCE_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();

        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String referenceQuestionnaireUuid = session.getNode(TEST_REFERENCE_QUESTIONNAIRE_PATH).getIdentifier();
        Node sourceQuestionnaire = session.getNode(TEST_SOURCE_QUESTIONNAIRE_PATH);
        Node subject = session.getNode(TEST_SUBJECT_PATH);

        Node sourceQuestionNode = session.getNode(TEST_SOURCE_QUESTION_PATH);

        Node referenceQuestionNode = session.getNode(TEST_REFERENCE_QUESTION_PATH);
        String referenceQuestionUuid = referenceQuestionNode.getIdentifier();

        Calendar date = Calendar.getInstance();
        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .resource("/Forms/f1",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, sourceQuestionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f1/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, sourceQuestionNode,
                        "value", date)
                .commit();

        // Create NodeBuilder/NodeState instances of New Test Source Form
        String referenceAnswerUuid = UUID.randomUUID().toString();
        NodeBuilder referenceAnswerBuilder = createTestAnswer(referenceAnswerUuid, referenceQuestionUuid,
                Map.of("copiedFrom", "/Forms/f1/a1"));

        String formUuid = UUID.randomUUID().toString();
        NodeBuilder formBuilder = createTestForm(formUuid, referenceQuestionnaireUuid,
                Map.of(referenceAnswerUuid, referenceAnswerBuilder.getNodeState()));

        this.nodeBuilder = formBuilder;

        when(this.formUtils.isForm(this.nodeBuilder)).thenReturn(true);
        this.referenceAnswersEditor = new ReferenceAnswersEditor(this.nodeBuilder, this.rrf, this.questionnaireUtils,
                this.formUtils, this.subjectUtils);

        // mock Node getQuestionnaire()
        PropertyState propertyState = Mockito.mock(PropertyState.class);
        when(propertyState.getValue(Type.REFERENCE)).thenReturn(referenceQuestionnaireUuid);

        // mock QuestionTree getUnansweredMatchingQuestions(final Node currentNode)
        when(this.questionnaireUtils.isReferenceQuestion(Mockito.any())).thenReturn(false, true);
        when(this.questionnaireUtils.isQuestionnaire(Mockito.any())).thenReturn(true, false);

        // mock Map<String, List<NodeBuilder>> getChildNodesByReference(final NodeBuilder nodeBuilder)
        when(this.formUtils.isAnswer(Mockito.any(NodeBuilder.class))).thenReturn(true);
        when(this.formUtils.getQuestionIdentifier(Mockito.any(NodeBuilder.class)))
                .thenReturn(session.getNode(TEST_REFERENCE_QUESTION_PATH).getIdentifier());

        // mock Object getAnswer(NodeState form, String questionPath)
        when(this.formUtils.getSubject(formBuilder.getNodeState())).thenReturn(subject);
        when(this.formUtils.findAllSubjectRelatedAnswers(Mockito.eq(subject), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(session.getNode("/Forms/f1/a1")));
        when(this.formUtils.getValue(Mockito.any(Node.class))).thenReturn(date);

        this.referenceAnswersEditor.serviceSession = this.context.resourceResolver().adaptTo(Session.class);
        this.referenceAnswersEditor.currentSession = this.context.resourceResolver().adaptTo(Session.class);
        this.referenceAnswersEditor.handleLeave(this.nodeBuilder.getNodeState());

        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Assert.assertTrue(getReferenceAnswer(this.nodeBuilder).hasProperty("value"));
        Assert.assertEquals(date.getTime(),
                format.parse(getReferenceAnswer(this.nodeBuilder).getProperty("value").getValue(Type.STRING)));
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
        answerBuilder.setProperty("question", questionUuid);
        answerBuilder.setProperty(UUID_PROPERTY, uuid);
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            answerBuilder.setProperty(property.getKey(), property.getValue());
        }
        return answerBuilder;
    }

    private NodeBuilder getReferenceAnswer(NodeBuilder currentNodeBuilder)
    {
        for (String name : currentNodeBuilder.getChildNodeNames()) {
            NodeBuilder child = currentNodeBuilder.getChildNode(name);
            if (ANSWER_SECTION_TYPE.equals(child.getName(NODE_TYPE))) {
                return getReferenceAnswer(child);
            } else if (ANSWER_TYPE.equals(child.getName(NODE_TYPE))
                    && child.hasProperty("copiedFrom")) {
                return child;
            }
        }
        return null;
    }

}
