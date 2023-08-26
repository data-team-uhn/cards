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
package io.uhndata.cards.forms.internal.serialize;

import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
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

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectAnswerCopyProcessor}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectAnswerCopyProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_1";
    private static final String TEST_SECTION_PATH = "/Questionnaires/TestQuestionnaire/section_1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String NAME = "answerCopy";
    private static final int PRIORITY = 95;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SubjectAnswerCopyProcessor subjectAnswerCopyProcessor;

    @Mock
    private FormUtils formUtils;

    @Mock
    private SubjectUtils subjectUtils;

    @Mock
    private SubjectTypeUtils subjectTypeUtils;

    @Test
    public void getNameTest()
    {
        Assert.assertEquals(NAME, this.subjectAnswerCopyProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.subjectAnswerCopyProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultTest()
    {
        Assert.assertTrue(this.subjectAnswerCopyProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void canProcessForSubjectResourceReturnsTrue()
    {
        Resource resource = this.context.resourceResolver().getResource(TEST_SUBJECT_PATH);
        Assert.assertTrue(this.subjectAnswerCopyProcessor.canProcess(resource));
    }

    @Test
    public void canProcessForQuestionResourceReturnsTrue()
    {
        Resource resource = this.context.resourceResolver().getResource(TEST_QUESTION_PATH);
        Assert.assertFalse(this.subjectAnswerCopyProcessor.canProcess(resource));
    }

    @Test
    public void getConfigurationPathReturnsPath() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        Resource resource = resourceResolver.getResource(TEST_SUBJECT_PATH);
        when(this.subjectUtils.getType(Mockito.any())).thenReturn(session.getNode("/SubjectTypes/Root"));
        when(this.subjectTypeUtils.isSubjectType(Mockito.any(Node.class))).thenReturn(true, false);
        when(this.subjectTypeUtils.getLabel(Mockito.any())).thenReturn("Root");
        String path = this.subjectAnswerCopyProcessor.getConfigurationPath(resource);
        String expectedConfigurationPath = "SubjectTypes/Root";
        Assert.assertNotNull(path);
        Assert.assertEquals(expectedConfigurationPath, path);
    }

    @Test
    public void getConfigurationPathCatchRepositoryExceptionAndReturnsNull() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Resource resource = mock(Resource.class);
        Node currentSubjectType = mock(Node.class);
        when(resource.adaptTo(Node.class)).thenReturn(mock(Node.class));
        when(this.subjectUtils.getType(Mockito.any())).thenReturn(currentSubjectType);
        when(this.subjectTypeUtils.isSubjectType(Mockito.any(Node.class))).thenReturn(true, false);
        when(this.subjectTypeUtils.getLabel(Mockito.any())).thenReturn("Root");
        when(currentSubjectType.getParent()).thenThrow(new RepositoryException());
        String path = this.subjectAnswerCopyProcessor.getConfigurationPath(resource);
        Assert.assertNull(path);
    }

    @Test
    public void getAnswerReturnsAnswerNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node expectedAnswer = session.getNode("/Forms/f1/s1/a1");
        Node source = session.getNode(TEST_SUBJECT_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        when(this.formUtils.findAllSubjectRelatedAnswers(Mockito.eq(source), Mockito.eq(question), Mockito.any()))
                .thenReturn(List.of(expectedAnswer));
        Node actualAnswer = this.subjectAnswerCopyProcessor.getAnswer(source, question);
        Assert.assertEquals(expectedAnswer, actualAnswer);
    }

    @Test
    public void getAnswerReturnsNull() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node source = session.getNode(TEST_FORM_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        Assert.assertNull(this.subjectAnswerCopyProcessor.getAnswer(source, question));
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

        Session session = this.context.resourceResolver().adaptTo(Session.class);

        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node section = session.getNode(TEST_SECTION_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);

        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f1/s1", NODE_TYPE, ANSWER_SECTION_TYPE, SECTION_PROPERTY, section)
                .resource("/Forms/f1/s1/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, question,
                        "value", "2023-01-01")
                .commit();
    }

}
