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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.forms.api.ExpressionUtils;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

import static org.mockito.Mockito.when;


/**
 * Unit tests for {@link ComputedAnswersEditor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class ComputedAnswersEditorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String TEST_COMPUTED_QUESTIONNAIRE_PATH = "/Questionnaires/TestComputedQuestionnaire";
    private static final String TEST_COMPUTED_QUESTION_PATH =
            "/Questionnaires/TestComputedQuestionnaire/from_long_to_computed_section/computed_question";
    private static final String TEST_LONG_QUESTION_PATH =
            "/Questionnaires/TestComputedQuestionnaire/from_long_to_computed_section/long_question";
    private static final String TEST_SECTION_PATH =
            "/Questionnaires/TestComputedQuestionnaire/from_long_to_computed_section";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private ComputedAnswersEditor computedAnswersEditor;
    private NodeState after;

    @Mock
    private ResourceResolverFactory rrf;

    @Mock
    private NodeBuilder nodeBuilder;

    @Mock
    private ExpressionUtils expressionUtils;

    @Mock
    private FormUtils formUtils;

    @Mock
    private QuestionnaireUtils questionnaireUtils;

    @Test
    public void constructorTest()
    {
        Assert.assertNotNull(this.computedAnswersEditor);
    }

    @Test
    public void isMatchedAnswerNodeReturnsTrue() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Map<String, NodeState> answers = getAnswersFromFormWithSection();

        Node computedQuestion = session.getNode(TEST_COMPUTED_QUESTION_PATH);
        String computedQuestionUuid = computedQuestion.getIdentifier();
        when(this.computedAnswersEditor.questionnaireUtils.getQuestion(computedQuestionUuid))
                .thenReturn(computedQuestion);
        Assert.assertTrue(this.computedAnswersEditor.answerChangeTracker
                .isMatchedAnswerNode(answers.get("computed"), computedQuestionUuid));
    }

    @Test
    public void isMatchedAnswerNodeReturnsFalse() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Map<String, NodeState> answers = getAnswersFromFormWithSection();

        Node question = session.getNode(TEST_LONG_QUESTION_PATH);
        String questionUuid = question.getIdentifier();
        when(this.computedAnswersEditor.questionnaireUtils.getQuestion(questionUuid)).thenReturn(question);
        Assert.assertFalse(this.computedAnswersEditor.answerChangeTracker
                .isMatchedAnswerNode(answers.get("not_computed"), questionUuid));
    }

    @Test
    public void childNodeAddedTest()
    {
        // for form type current node builder
        String name = "form_1";
        Editor editor = this.computedAnswersEditor.childNodeAdded(name, Mockito.mock(NodeState.class));
        Assert.assertTrue(editor instanceof AnswersEditor.AbstractAnswerChangeTracker);
        Assert.assertEquals(this.computedAnswersEditor.answerChangeTracker, editor);

        // for not form type current node builder
        when(this.formUtils.isForm(this.nodeBuilder)).thenReturn(false);
        this.computedAnswersEditor = new ComputedAnswersEditor(this.nodeBuilder, this.rrf, this.questionnaireUtils,
                this.formUtils, this.expressionUtils);

        PropertyState typeProperty = Mockito.mock(PropertyState.class);
        NodeState formNodeState = Mockito.mock(NodeState.class);
        NodeBuilder formNodeBuilder = Mockito.mock(NodeBuilder.class);

        when(typeProperty.getValue(Type.NAME)).thenReturn(FORM_TYPE);
        when(formNodeState.getProperty(NODE_TYPE)).thenReturn(typeProperty);
        when(formNodeBuilder.getNodeState()).thenReturn(formNodeState);
        when(this.nodeBuilder.getChildNode(Mockito.eq(name))).thenReturn(formNodeBuilder);

        editor = this.computedAnswersEditor.childNodeAdded(name, Mockito.mock(NodeState.class));
        Assert.assertTrue(editor instanceof ComputedAnswersEditor);
        Assert.assertEquals(formNodeBuilder, ((ComputedAnswersEditor) editor).currentNodeBuilder);
    }

    @Test
    public void handleLeaveTest()
    {
        this.computedAnswersEditor.serviceSession = this.context.resourceResolver().adaptTo(Session.class);
        this.computedAnswersEditor.currentSession = this.context.resourceResolver().adaptTo(Session.class);
        this.computedAnswersEditor.handleLeave(this.after);
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        initializeEditorForFormNodeBuilder();

        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
                .commit();
        this.context.load().json("/ComputedQuestionnairesPlain.json", TEST_COMPUTED_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();

        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        String questionnaireUuid = session.getNode(TEST_COMPUTED_QUESTIONNAIRE_PATH).getIdentifier();
        String sectionUuid = session.getNode(TEST_SECTION_PATH).getIdentifier();

        Node questionNode = session.getNode(TEST_LONG_QUESTION_PATH);
        String questionUuid = questionNode.getIdentifier();

        Node computedQuestionNode = session.getNode(TEST_COMPUTED_QUESTION_PATH);
        String computedQuestionUuid = computedQuestionNode.getIdentifier();

        // Create NodeBuilder/NodeState instances of New Test Form
        String answerUuid = UUID.randomUUID().toString();
        NodeBuilder answerBuilder = createTestAnswer(answerUuid, questionUuid);

        String computedAnswerUuid = UUID.randomUUID().toString();
        NodeBuilder computedAnswerBuilder = createTestComputedAnswer(computedAnswerUuid, computedQuestionUuid);

        String answerSectionUuid = UUID.randomUUID().toString();
        NodeBuilder answerSectionBuilder =
                createTestAnswerSection(answerSectionUuid, sectionUuid, answerUuid, answerBuilder.getNodeState(),
                        computedQuestionUuid, computedAnswerBuilder.getNodeState());

        String formUuid = UUID.randomUUID().toString();
        NodeBuilder formBuilder = createTestForm(formUuid, questionnaireUuid, answerSectionUuid,
                answerSectionBuilder.getNodeState());

        this.after = formBuilder.getNodeState();

        // mock Map<String, Object> getNodeAnswers(final NodeState currentNode)
        when(this.formUtils.isForm(this.after)).thenReturn(true);

        NodeState answerSectionState = this.after.getChildNode(answerSectionUuid);
        NodeState answerSection = this.after.getChildNode(answerSectionUuid).getChildNode(answerUuid);
        NodeState computedAnswerSection = this.after.getChildNode(answerSectionUuid).getChildNode(computedAnswerUuid);
        when(this.formUtils.isAnswerSection(answerSectionState)).thenReturn(true);
        when(this.formUtils.isAnswer(answerSection)).thenReturn(true);
        when(this.formUtils.isAnswer(computedAnswerSection)).thenReturn(true);

        // for simple answer
        when(this.formUtils.getQuestion(answerSection)).thenReturn(questionNode);
        when(this.questionnaireUtils.getQuestionName(Mockito.eq(questionNode))).thenReturn("long_question");
        when(this.formUtils.getValue(answerSection)).thenReturn(200L);

        // for computed answer
        when(this.formUtils.getQuestion(computedAnswerSection)).thenReturn(computedQuestionNode);
        when(this.questionnaireUtils.getQuestionName(AdditionalMatchers.not(Mockito.eq(questionNode))))
                .thenReturn("computed_question");
        when(this.formUtils.getValue(computedAnswerSection)).thenReturn(null);

        // mock Node getQuestionnaire()
        PropertyState propertyState = Mockito.mock(PropertyState.class);
        when(this.nodeBuilder.getProperty("questionnaire")).thenReturn(propertyState);
        when(propertyState.getValue(Type.REFERENCE)).thenReturn(questionnaireUuid);

        // mock QuestionTree getUnansweredMatchingQuestions(final Node currentNode)
        when(this.questionnaireUtils.isComputedQuestion(Mockito.any())).thenReturn(false, false, false, true);
        when(this.questionnaireUtils.isQuestionnaire(Mockito.any())).thenReturn(true, false);
        when(this.questionnaireUtils.isSection(Mockito.any())).thenReturn(true, false);

        // mock Map<QuestionTree, NodeBuilder> createMissingNodes(
        //        final QuestionTree questionTree, final NodeBuilder currentNode)
        when(this.nodeBuilder.hasProperty("jcr:primaryType")).thenReturn(true);
        // mock Map<String, List<NodeBuilder>> getChildNodesByReference(final NodeBuilder nodeBuilder)
        when(this.nodeBuilder.getChildNodeNames()).thenReturn(List.of("from_long_to_computed_section"));
        when(this.nodeBuilder.getChildNode(Mockito.eq("from_long_to_computed_section")))
                .thenReturn(answerSectionBuilder);
        when(this.formUtils.isAnswerSection(answerSectionBuilder)).thenReturn(true);
        when(this.formUtils.getSectionIdentifier(answerSectionBuilder)).thenReturn(sectionUuid);

        // mock void computeAnswer(final Map.Entry<QuestionTree, NodeBuilder> entry,
        //        final Map<String, Object> answersByQuestionName)
        when(this.expressionUtils.getDependencies(Mockito.any(Node.class)))
                .thenReturn(new HashSet<>(Set.of("long_question")));
        when(this.expressionUtils.evaluate(Mockito.any(Node.class), Mockito.anyMap(), Mockito.eq(Type.LONG)))
            .thenReturn(200L);
    }

    private void initializeEditorForFormNodeBuilder()
    {
        when(this.formUtils.isForm(this.nodeBuilder)).thenReturn(true);
        this.computedAnswersEditor = new ComputedAnswersEditor(this.nodeBuilder, this.rrf, this.questionnaireUtils,
                this.formUtils, this.expressionUtils);
    }

    private NodeBuilder createTestForm(String uuid, String questionnaireUuid, String answerSectionUuid,
                                       NodeState answerSection)
    {
        NodeBuilder formBuilder = EmptyNodeState.EMPTY_NODE.builder();
        formBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        formBuilder.setProperty("questionnaire", questionnaireUuid);
        formBuilder.setProperty("jcr:uuid", uuid);
        formBuilder.setChildNode(answerSectionUuid, answerSection);
        return formBuilder;
    }

    private NodeBuilder createTestAnswer(String uuid, String questionUuid)
    {
        NodeBuilder answerBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerBuilder.setProperty(NODE_TYPE, ANSWER_TYPE);
        answerBuilder.setProperty("question", questionUuid);
        answerBuilder.setProperty("value", 200L);
        answerBuilder.setProperty("jcr:uuid", uuid);
        return answerBuilder;
    }

    private NodeBuilder createTestComputedAnswer(String uuid, String questionUuid)
    {
        NodeBuilder computedAnswerBuilder = EmptyNodeState.EMPTY_NODE.builder();
        computedAnswerBuilder.setProperty(NODE_TYPE, ANSWER_TYPE);
        computedAnswerBuilder.setProperty("question", questionUuid);
        computedAnswerBuilder.setProperty("jcr:uuid", uuid);
        return computedAnswerBuilder;
    }

    private NodeBuilder createTestAnswerSection(String uuid, String sectionUuid, String answerUuid, NodeState answer,
                                                String computedAnswerUuid, NodeState computedAnswer)
    {
        NodeBuilder answerSectionBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerSectionBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        answerSectionBuilder.setChildNode(computedAnswerUuid, computedAnswer);
        answerSectionBuilder.setChildNode(answerUuid, answer);
        answerSectionBuilder.setProperty("section", sectionUuid);
        answerSectionBuilder.setProperty("jcr:uuid", uuid);
        return answerSectionBuilder;
    }

    private Map<String, NodeState> getAnswersFromFormWithSection()
    {
        Map<String, NodeState> answers = new HashMap<>();
        this.after.getChildNodeNames().forEach(
            formChildName -> {
                NodeState formChild = this.after.getChildNode(formChildName);
                formChild.getChildNodeNames().forEach(
                    childName -> {
                        NodeState child = formChild.getChildNode(childName);
                        if (child.hasProperty("value")) {
                            answers.put("not_computed", child);
                        } else {
                            answers.put("computed", child);
                        }
                    });
            });
        return answers;
    }
}
