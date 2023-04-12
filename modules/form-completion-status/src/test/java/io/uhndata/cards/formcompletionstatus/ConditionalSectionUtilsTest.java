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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ConditionalSectionUtils}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class ConditionalSectionUtilsTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String CONDITIONAL_TYPE = "cards:Conditional";
    private static final String CONDITIONAL_VALUE_TYPE = "cards:ConditionalValue";
    private static final String CONDITIONAL_GROUP_TYPE = "cards:ConditionalGroup";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String SECTION_PROPERTY = "section";
    private static final String VALUE_PROPERTY = "value";
    private static final String DECIMAL_DATA_TYPE = "decimal";
    private static final String DATE_DATA_TYPE = "date";
    private static final String LONG_DATA_TYPE = "long";
    private static final String BOOLEAN_DATA_TYPE = "boolean";
    private static final String TEXT_DATA_TYPE = "text";
    private static final String DOUBLE_DATA_TYPE = "double";

    private static final String TEST_QUESTION_NAME = "question_1";
    private static final String TEST_ANSWER_SECTION_NAME = "answerSection_1";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_SECTION_PATH = "/Questionnaires/TestQuestionnaire/section_1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_QUESTION_PATH = TEST_QUESTIONNAIRE_PATH + "/" + TEST_QUESTION_NAME;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private Session session;

    @Test
    public void isConditionSatisfiedForSectionWithoutConditionalReturnsTrue()
            throws RepositoryException
    {
        boolean comparableValue = true;
        createQuestion(TEST_QUESTION_PATH, "Must be true - Question", BOOLEAN_DATA_TYPE);
        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(comparableValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndBooleanTypeAnswerAndEqualComparatorReturnsFalse()
            throws RepositoryException
    {
        boolean comparableValue = true;
        boolean actualValue = false;

        createQuestion(TEST_QUESTION_PATH, "Must be true - Question", BOOLEAN_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", "=", BOOLEAN_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertFalse(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndBooleanTypeAnswerAndEqualComparatorReturnsTrue()
            throws RepositoryException
    {
        boolean comparableValue = true;

        createQuestion(TEST_QUESTION_PATH, "Must be true - Question", BOOLEAN_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", "=", BOOLEAN_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(comparableValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndStringBooleanTypeAnswerAndNotEqualComparatorReturnsTrue()
            throws RepositoryException
    {
        String comparableValue = "false";
        boolean actualValue = true;

        createQuestion(TEST_QUESTION_PATH, "Must be more not false - Question", BOOLEAN_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", "<>", BOOLEAN_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndTextTypeAnswerAndIsNotEmptyComparatorReturnsTrue()
            throws RepositoryException
    {
        String comparableValue = "someText";

        createQuestion(TEST_QUESTION_PATH, "Must not be empty - Question", TEXT_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", "is not empty", TEXT_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(comparableValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndLongTypeAnswerAndMoreComparatorReturnsTrue()
            throws RepositoryException
    {
        int comparableValue = 100;
        int actualValue = 200;

        createQuestion(TEST_QUESTION_PATH, "Must be more than 100 - Question", LONG_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", ">", LONG_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndLongTypeAnswerAndNotEqualComparatorReturnsTrue()
            throws RepositoryException
    {
        String comparableValue = "100";
        int actualValue = 200;

        createQuestion(TEST_QUESTION_PATH, "Must be not equal to 100 - Question", LONG_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", "<>", LONG_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalGroupNodeAndDoubleTypeAnswerAndEqualOrLessComparatorReturnsTrue()
            throws RepositoryException
    {
        String comparableValue = "100";
        double actualValue = 50.0;

        createQuestion(TEST_QUESTION_PATH, "Must be equal to or less than 100 - Question", DOUBLE_DATA_TYPE);
        createConditionalGroup(TEST_SECTION_PATH + "/conditionalGroup_1", true);
        createConditional(TEST_SECTION_PATH + "/conditionalGroup_1/conditional_1", "<=", DOUBLE_DATA_TYPE,
                TEST_QUESTION_NAME, comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalGroupNodeAndDoubleTypeAnswerAndLessComparatorReturnsTrue()
            throws RepositoryException
    {
        double comparableValue = 100.0;
        double actualValue = 50.0;

        createQuestion(TEST_QUESTION_PATH, "Must be less than 100 - Question", DOUBLE_DATA_TYPE);
        createConditionalGroup(TEST_SECTION_PATH + "/conditionalGroup_1", true);
        createConditional(TEST_SECTION_PATH + "/conditionalGroup_1/conditional_1", "<", DOUBLE_DATA_TYPE,
                TEST_QUESTION_NAME, comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    // for BigDecimal value
    @Test
    public void isConditionSatisfiedForConditionalNodeAndDecimalTypeAnswerAndEqualComparatorReturnsTrue()
            throws RepositoryException
    {
        BigDecimal comparableValue = new BigDecimal(100);

        createQuestion(TEST_QUESTION_PATH, "Must be equal to 100 - Question", DECIMAL_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", "=", DECIMAL_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(comparableValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndDecimalTypeAnswerAndNotEqualComparatorReturnsTrue()
            throws RepositoryException
    {
        String comparableValue = "100";
        BigDecimal actualValue = new BigDecimal(50);

        createQuestion(TEST_QUESTION_PATH, "Must be not equal to 100 - Question", DECIMAL_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", "<>", DECIMAL_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndDecimalTypeAnswerAndLessComparatorReturnsTrue()
            throws RepositoryException
    {
        Integer comparableValue = 100;
        BigDecimal actualValue = new BigDecimal(50);

        createQuestion(TEST_QUESTION_PATH, "Must be less than 100 - Question", DECIMAL_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", "<", DECIMAL_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndDecimalTypeAnswerAndEqualOrLessComparatorReturnsTrue()
            throws RepositoryException
    {
        Long comparableValue = 100L;
        BigDecimal actualValue = new BigDecimal(100);

        createQuestion(TEST_QUESTION_PATH, "Must be equal to or less than 100 - Question", DECIMAL_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", "<=", DECIMAL_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndDecimalTypeAnswerAndEqualOrMoreComparatorReturnsTrue()
            throws RepositoryException
    {
        Double comparableValue = 100.0;
        BigDecimal actualValue = new BigDecimal(200);

        createQuestion(TEST_QUESTION_PATH, "Must be equal to or more than 100 - Question", DECIMAL_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", ">=", DECIMAL_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalNodeAndDecimalTypeAnswerAndMoreComparatorReturnsTrue()
            throws RepositoryException
    {
        Float comparableValue = 100F;
        BigDecimal actualValue = new BigDecimal(200);

        createQuestion(TEST_QUESTION_PATH, "Must be more than 100 - Question", DECIMAL_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditional_1", ">", DECIMAL_DATA_TYPE, TEST_QUESTION_NAME,
                comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }


    // for Date value
    @Test
    public void isConditionSatisfiedForConditionalGroupNodeAndCalendarTypeAnswerAndEqualComparatorReturnsTrue()
            throws RepositoryException
    {
        Calendar comparableValue = Calendar.getInstance();
        comparableValue.set(2023, Calendar.JANUARY, 1);

        createQuestion(TEST_QUESTION_PATH, "Must be equal to 2023-01-01 - Question", DATE_DATA_TYPE);
        createConditionalGroup(TEST_SECTION_PATH + "/conditionalGroup_1", false);
        createConditional(TEST_SECTION_PATH + "/conditionalGroup_1/conditional_1", "=", DATE_DATA_TYPE,
                TEST_QUESTION_NAME, comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(comparableValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalGroupNodeAndCalendarTypeAnswerAndEqualOrLessComparatorReturnsTrue()
            throws RepositoryException
    {
        Calendar comparableValue = Calendar.getInstance();
        comparableValue.set(2023, Calendar.JANUARY, 1);

        Calendar actualValue = Calendar.getInstance();
        actualValue.set(2022, Calendar.JANUARY, 1);

        createQuestion(TEST_QUESTION_PATH, "Must be equal to or less than 2023-01-01 - Question", DATE_DATA_TYPE);
        createConditionalGroup(TEST_SECTION_PATH + "/conditionalGroup_1", false);
        createConditional(TEST_SECTION_PATH + "/conditionalGroup_1/conditional_1", "<=", DATE_DATA_TYPE,
                TEST_QUESTION_NAME, comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalGroupNodeAndCalendarTypeAnswerAndLessComparatorReturnsTrue()
            throws RepositoryException
    {
        Calendar comparableValue = Calendar.getInstance();
        comparableValue.set(2023, Calendar.JANUARY, 1);

        Calendar actualValue = Calendar.getInstance();
        actualValue.set(2022, Calendar.JANUARY, 1);

        createQuestion(TEST_QUESTION_PATH, "Must be less than 2023-01-01 - Question", DATE_DATA_TYPE);
        createConditionalGroup(TEST_SECTION_PATH + "/conditionalGroup_1", false);
        createConditional(TEST_SECTION_PATH + "/conditionalGroup_1/conditional_1", "<", DATE_DATA_TYPE,
                TEST_QUESTION_NAME, comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalGroupNodeAndCalendarTypeAnswerAndEqualOrMoreComparatorReturnsTrue()
            throws RepositoryException
    {
        Calendar comparableValue = Calendar.getInstance();
        comparableValue.set(2023, Calendar.JANUARY, 1);

        Calendar actualValue = Calendar.getInstance();
        actualValue.set(2023, Calendar.MARCH, 1);

        createQuestion(TEST_QUESTION_PATH, "Must be equal to or more than 2023-01-01 - Question", DATE_DATA_TYPE);
        createConditionalGroup(TEST_SECTION_PATH + "/conditionalGroup_1", false);
        createConditional(TEST_SECTION_PATH + "/conditionalGroup_1/conditional_1", ">=", DATE_DATA_TYPE,
                TEST_QUESTION_NAME, comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalGroupNodeAndStringDateTypeAnswerAndMoreComparatorReturnsTrue()
            throws RepositoryException
    {
        String comparableValue = "2023-01-01T00:00-00:00";

        Calendar actualValue = Calendar.getInstance();
        actualValue.set(2023, Calendar.MARCH, 1);

        createQuestion(TEST_QUESTION_PATH, "Must be more than 2023-01-01 - Question", DATE_DATA_TYPE);
        createConditionalGroup(TEST_SECTION_PATH + "/conditionalGroup_1", false);
        createConditional(TEST_SECTION_PATH + "/conditionalGroup_1/conditional_1", ">", DATE_DATA_TYPE,
                TEST_QUESTION_NAME, comparableValue);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(actualValue);
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
               form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForConditionalGroupWithSeveralConditionalsReturnsTrue() throws RepositoryException
    {
        double comparableValueDouble = 100.0;
        double actualValueDouble = 150.0;

        double comparableValueLong = 100;
        double actualValueLong = 50;

        createConditionalGroup(TEST_SECTION_PATH + "/conditionalGroup_1", true);

        createQuestion(TEST_QUESTION_PATH, "Must be equal to or more than 100 - Question", DOUBLE_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditionalGroup_1/conditional_1", ">=", DOUBLE_DATA_TYPE,
                TEST_QUESTION_NAME, comparableValueDouble);

        createQuestion("/Questionnaires/TestQuestionnaire/question_2", "Must be less than 100 - Question",
                LONG_DATA_TYPE);
        createConditional(TEST_SECTION_PATH + "/conditionalGroup_1/conditional_2", "<", LONG_DATA_TYPE,
                "question_2", comparableValueLong);

        NodeBuilder form = getFormNodeBuilderWithAnswerAndAnswerSectionChildren(Map.of(
                TEST_QUESTION_PATH, actualValueDouble,
                "/Questionnaires/TestQuestionnaire/question_2", actualValueLong
        ));
        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedCatchesRepositoryExceptionGettingSectionByIdReturnsTrue()
            throws RepositoryException
    {
        NodeBuilder answerSectionNodeBuilder = createTestAnswerSection(UUID.randomUUID().toString());
        String subjectUuid = this.session.getNode(TEST_SUBJECT_PATH).getIdentifier();
        String questionnaireUuid = this.session.getNode(TEST_QUESTIONNAIRE_PATH).getIdentifier();
        NodeBuilder form = createTestForm(questionnaireUuid, subjectUuid,
                Map.of(TEST_ANSWER_SECTION_NAME, answerSectionNodeBuilder.getNodeState()));

        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Test
    public void isConditionSatisfiedForAnswerSectionWithoutSectionPropertyReturnsTrue()
            throws RepositoryException
    {
        NodeBuilder answerSectionBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerSectionBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        String subjectUuid = this.session.getNode(TEST_SUBJECT_PATH).getIdentifier();
        String questionnaireUuid = this.session.getNode(TEST_QUESTIONNAIRE_PATH).getIdentifier();
        NodeBuilder form = createTestForm(questionnaireUuid, subjectUuid,
                Map.of(TEST_ANSWER_SECTION_NAME, answerSectionBuilder.getNodeState()));

        boolean isConditionSatisfied = ConditionalSectionUtils.isConditionSatisfied(this.session,
                form.getChildNode(TEST_ANSWER_SECTION_NAME), form);
        assertTrue(isConditionSatisfied);
    }

    @Before
    public void setupRepo()
    {
        this.session = this.context.resourceResolver().adaptTo(Session.class);
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
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
                .resource(TEST_QUESTIONNAIRE_PATH,
                        NODE_TYPE, "cards:Questionnaire",
                        "title", "Test Questionnaire",
                        "description", "A test questionnaire")
                .resource(TEST_SECTION_PATH,
                        NODE_TYPE, "cards:Section",
                        "label", "Section 1")
                .commit();

    }

    private void createQuestion(String questionPath, String text, String dataType)
    {
        this.context.build()
                .resource(questionPath,
                        NODE_TYPE, "cards:Question",
                        "text", text,
                        "dataType", dataType)
                .commit();
    }

    private void createConditionalGroup(String conditionalGroupPath, boolean requireAll)
    {
        this.context.build()
                .resource(conditionalGroupPath,
                        NODE_TYPE, CONDITIONAL_GROUP_TYPE,
                        "requireAll", requireAll)
                .commit();
    }

    private void createConditional(String conditionalPath, String comparator, String dataType,
                                 String referenceAnswerName, Object value)
    {
        this.context.build()
                .resource(conditionalPath,
                        NODE_TYPE, CONDITIONAL_TYPE,
                        "comparator", comparator,
                        "dataType", dataType)
                .resource(conditionalPath + "/operandA",
                        NODE_TYPE, CONDITIONAL_VALUE_TYPE,
                        VALUE_PROPERTY, List.of(referenceAnswerName).toArray(),
                        "isReference", true)
                .resource(conditionalPath + "/operandB",
                        NODE_TYPE, CONDITIONAL_VALUE_TYPE,
                        VALUE_PROPERTY, List.of(value).toArray(),
                        "isReference", false)
                .commit();
    }

    private NodeBuilder getFormNodeBuilderWithAnswerAndAnswerSectionChildren(Map<String, Object> questionAnswerValues)
            throws RepositoryException
    {
        List<NodeBuilder> answers = new ArrayList<>();
        for (Map.Entry<String, Object> questionAnswerValue : questionAnswerValues.entrySet()) {
            String questionUuid = this.session.getNode(questionAnswerValue.getKey()).getIdentifier();
            NodeBuilder answerNodeBuilder = createTestAnswer(questionUuid);
            answerNodeBuilder.setProperty(VALUE_PROPERTY, questionAnswerValue.getValue());
            answers.add(answerNodeBuilder);
        }

        // create answerSection with conditionals
        String sectionUuid = this.session.getNode(TEST_SECTION_PATH).getIdentifier();
        NodeBuilder answerSectionNodeBuilder = createTestAnswerSection(sectionUuid);
        String subjectUuid = this.session.getNode(TEST_SUBJECT_PATH).getIdentifier();
        String questionnaireUuid = this.session.getNode(TEST_QUESTIONNAIRE_PATH).getIdentifier();
        Map<String, NodeState> formChildren = new HashMap<>();
        for (NodeBuilder answer : answers) {
            formChildren.put(UUID.randomUUID().toString(), answer.getNodeState());
        }
        formChildren.put(TEST_ANSWER_SECTION_NAME, answerSectionNodeBuilder.getNodeState());

        return createTestForm(questionnaireUuid, subjectUuid, formChildren);
    }

    private NodeBuilder getFormNodeBuilderWithAnswerAndAnswerSectionChildren(Object answerValue)
            throws RepositoryException
    {
        // create referencable answer
        String questionUuid = this.session.getNode(TEST_QUESTION_PATH).getIdentifier();
        NodeBuilder answerNodeBuilder = createTestAnswer(questionUuid);
        answerNodeBuilder.setProperty(VALUE_PROPERTY, answerValue);

        // create answerSection with conditionals
        String sectionUuid = this.session.getNode(TEST_SECTION_PATH).getIdentifier();
        NodeBuilder answerSectionNodeBuilder = createTestAnswerSection(sectionUuid);
        String subjectUuid = this.session.getNode(TEST_SUBJECT_PATH).getIdentifier();
        String questionnaireUuid = this.session.getNode(TEST_QUESTIONNAIRE_PATH).getIdentifier();
        NodeBuilder form = createTestForm(questionnaireUuid, subjectUuid,
                Map.of("answer_1", answerNodeBuilder.getNodeState(),
                        TEST_ANSWER_SECTION_NAME, answerSectionNodeBuilder.getNodeState()));

        return form;
    }

    private NodeBuilder createTestAnswerSection(String sectionUuid)
    {
        NodeBuilder answerSectionBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerSectionBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        answerSectionBuilder.setProperty(SECTION_PROPERTY, sectionUuid);
        return answerSectionBuilder;
    }

    private NodeBuilder createTestForm(String questionnaireUuid, String subjectUuid,
                                       Map<String, NodeState> children)
    {
        NodeBuilder formBuilder = EmptyNodeState.EMPTY_NODE.builder();
        formBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        formBuilder.setProperty(QUESTIONNAIRE_PROPERTY, questionnaireUuid);
        formBuilder.setProperty(SUBJECT_PROPERTY, subjectUuid);
        for (Map.Entry<String, NodeState> child : children.entrySet()) {
            formBuilder.setChildNode(child.getKey(), child.getValue());
        }
        return formBuilder;
    }

    private NodeBuilder createTestAnswer(String questionUuid)
    {
        NodeBuilder answerBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerBuilder.setProperty(NODE_TYPE, ANSWER_TYPE);
        answerBuilder.setProperty(QUESTION_PROPERTY, questionUuid);
        return answerBuilder;
    }

}
