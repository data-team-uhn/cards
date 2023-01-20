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

package io.uhn.data.cards.forms.internal;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.forms.internal.QuestionnaireUtilsImpl;

/**
 * Unit tests for {@link QuestionnaireUtilsImpl}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class QuestionnaireUtilsImplTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String QUESTION_TYPE = "cards:Question";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private QuestionnaireUtilsImpl questionnaireUtils;

    @Mock
    private ResourceResolverFactory rrf;

    @Test
    public void getQuestionnaireWithActualIdentifierReturnsCorrectQuestionnaire() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Node questionnaireActual = this.questionnaireUtils.getQuestionnaire(questionnaire.getIdentifier());
        Assert.assertNotNull(questionnaireActual);
        Assert.assertEquals(questionnaire.getPath(), questionnaireActual.getPath());
    }

    @Test
    public void getQuestionnaireWithAnotherNodeIdentifierReturnsNull() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Node questionnaireActual = this.questionnaireUtils.getQuestionnaire(subject.getIdentifier());
        Assert.assertNull(questionnaireActual);
    }

    @Test
    public void belongsWithDeepLevelElementReturnsTrue() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node element = session.getNode("/Questionnaires/TestQuestionnaire/section_1/question_2/o1");
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Assert.assertTrue(this.questionnaireUtils.belongs(element, questionnaire));
    }

    @Test
    public void belongsWithFakeElementReturnsFalse() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node fakeElement = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Assert.assertFalse(this.questionnaireUtils.belongs(fakeElement, questionnaire));
    }

    @Test
    public void belongsWithFakeQuestionnaireReturnsFalse() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node element = session.getNode("/Questionnaires/TestQuestionnaire/section_1");
        Node fakeQuestionnaire = session.getNode(TEST_SUBJECT_PATH);
        Assert.assertFalse(this.questionnaireUtils.belongs(element, fakeQuestionnaire));
    }

    @Test
    public void getOwnerQuestionnaireWithDeepLevelElementReturnsCorrectQuestionnaire() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node element = session.getNode(TEST_QUESTION_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node questionnaireActual = this.questionnaireUtils.getOwnerQuestionnaire(element);
        Assert.assertEquals(questionnaire.getPath(), questionnaireActual.getPath());
    }

    @Test
    public void getOwnerQuestionnaireWithFakeElementThrowsException() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node fakeElement = session.getNode(TEST_SUBJECT_PATH);
        Assert.assertNull(this.questionnaireUtils.getOwnerQuestionnaire(fakeElement));
    }

    @Test
    public void getSectionWithActualIdentifierReturnsCorrectQuestionnaire() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node section = session.getNode("/Questionnaires/TestQuestionnaire/section_1");
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Node sectionActual = this.questionnaireUtils.getSection(section.getIdentifier());
        Assert.assertNotNull(sectionActual);
        Assert.assertEquals(section.getPath(), sectionActual.getPath());
    }

    @Test
    public void getSectionWithAnotherNodeIdentifierReturnsNull() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Node sectionActual = this.questionnaireUtils.getSection(subject.getIdentifier());
        Assert.assertNull(sectionActual);
    }

    @Test
    public void getQuestionReturnsCorrectQuestion() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node questionActual = this.questionnaireUtils.getQuestion(questionnaire, "section_1/question_1");
        Assert.assertNotNull(questionActual);
        Assert.assertEquals(question.getPath(), questionActual.getPath());
        Assert.assertEquals(question.getIdentifier(), questionActual.getIdentifier());
    }

    @Test
    public void getQuestionWithFakeQuestionnaireThrowsException() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node fakeQuestionnaire = session.getNode(TEST_SUBJECT_PATH);
        Node questionActual = this.questionnaireUtils.getQuestion(fakeQuestionnaire, TEST_QUESTION_PATH);
        Assert.assertNull(questionActual);
    }

    @Test
    public void isComputedQuestionForActualComputedAnswersReturnsTrue() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node computedQuestionInSection = session.getNode("/Questionnaires/TestQuestionnaire/section_1/question_4");
        Node computedQuestion = session.getNode("/Questionnaires/TestQuestionnaire/question_5");

        Assert.assertTrue(this.questionnaireUtils.isComputedQuestion(computedQuestionInSection));
        Assert.assertTrue(this.questionnaireUtils.isComputedQuestion(computedQuestion));
    }

    @Test
    public void isComputedQuestionForNotComputedAnswerReturnsFalse() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node computedQuestion = session.getNode(TEST_QUESTION_PATH);

        Assert.assertFalse(this.questionnaireUtils.isComputedQuestion(computedQuestion));
    }

    @Test
    public void isComputedQuestionForNotComputedAnswerThrowsException() throws RepositoryException
    {
        Node computedQuestion = Mockito.mock(Node.class);
        Mockito.when(computedQuestion.isNodeType(QUESTION_TYPE)).thenReturn(true);
        Mockito.when(computedQuestion.getProperty(Mockito.anyString())).thenThrow(new RepositoryException());

        Assert.assertFalse(this.questionnaireUtils.isComputedQuestion(computedQuestion));
    }

    @Test
    public void isReferenceQuestionForActualReferenceAnswersReturnsTrue() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node referenceQuestion = session.getNode("/Questionnaires/TestQuestionnaire/section_1/question_3");

        Assert.assertTrue(this.questionnaireUtils.isReferenceQuestion(referenceQuestion));
    }

    @Test
    public void isReferenceQuestionForNotReferenceAnswerThrowsException() throws RepositoryException
    {
        Node referenceQuestion = Mockito.mock(Node.class);
        Mockito.when(referenceQuestion.isNodeType(QUESTION_TYPE)).thenReturn(true);
        Mockito.when(referenceQuestion.getProperty("entryMode")).thenThrow(new RepositoryException());

        Assert.assertFalse(this.questionnaireUtils.isReferenceQuestion(referenceQuestion));
    }

    @Test
    public void getQuestionWithActualIdentifierReturnsCorrectQuestion() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Node questionActual = this.questionnaireUtils.getQuestion(question.getIdentifier());
        Assert.assertNotNull(questionActual);
        Assert.assertEquals(question.getPath(), questionActual.getPath());
    }

    @Test
    public void getQuestionWithAnotherNodeIdentifierReturnsNull() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Node questionActual = this.questionnaireUtils.getQuestion(subject.getIdentifier());
        Assert.assertNull(questionActual);
    }

    @Test
    public void getQuestionNameWithActualQuestionReturnsCorrectName() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        String questionNameActual = this.questionnaireUtils.getQuestionName(question);
        Assert.assertEquals("question_1", questionNameActual);
    }

    @Test
    public void getQuestionNameWithAnotherNodeTypeReturnsNull() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        String questionNameActual = this.questionnaireUtils.getQuestionName(subject);
        Assert.assertNull(questionNameActual);
    }

    @Test
    public void getQuestionNameTrowsException() throws RepositoryException
    {
        Node question = Mockito.mock(Node.class);
        Mockito.when(question.isNodeType(QUESTION_TYPE)).thenReturn(true);
        Mockito.when(question.getName()).thenThrow(new RepositoryException());

        Assert.assertNull(this.questionnaireUtils.getQuestionName(question));
    }

    @Test
    public void getQuestionTextWithActualQuestionReturnsCorrectText() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        String questionTextActual = this.questionnaireUtils.getQuestionText(question);
        Assert.assertEquals("Date Question", questionTextActual);
    }

    @Test
    public void getQuestionDescriptionWithActualQuestionReturnsCorrectDescription() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode("/Questionnaires/TestQuestionnaire/question_5");
        String questionDescriptionActual = this.questionnaireUtils.getQuestionDescription(question);
        Assert.assertEquals("A computed boolean question", questionDescriptionActual);
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        this.context.build()
            .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
            .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
            .commit();
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
            .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
            .commit();
    }

}
