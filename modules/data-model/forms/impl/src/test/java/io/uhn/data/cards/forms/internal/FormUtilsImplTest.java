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

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeState;
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

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.forms.internal.FormUtilsImpl;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Unit tests for {@link OboParser}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class FormUtilsImplTest
{
    private static final String NODE_TYPE = "jcr:primaryType";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private FormUtilsImpl formUtils;

    @Mock
    private ResourceResolverFactory rrf;

    @Mock
    private QuestionnaireUtils questionnaires;

    @Mock
    private SubjectUtils subjects;

    @Test
    public void isFormForNodeWithNullArgumentReturnsFalse()
    {
        Assert.assertFalse(this.formUtils.isForm((Node) null));
    }

    @Test
    public void isFormForNodeExceptionReturnsFalse() throws RepositoryException
    {
        Node node = Mockito.mock(Node.class);
        Mockito.when(node.isNodeType("cards:Form")).thenThrow(new RepositoryException());
        Assert.assertFalse(this.formUtils.isForm(node));
    }

    @Test
    public void isFormForNodeWithActualFormReturnsTrue() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        this.context.build()
            .resource("/Forms/f1", NODE_TYPE, "cards:Form", "questionnaire",
                session.getNode("/Questionnaires/TestQuestionnaire"), "subject", session.getNode("/Subjects/Test"))
            .commit();
        Assert.assertTrue(
            this.formUtils.isForm(this.context.resourceResolver().getResource("/Forms/f1").adaptTo(Node.class)));
    }

    @Test
    public void isFormForNodeWithOtherNoteTypeReturnsFalse() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        this.context.build()
            .resource("/Forms/f1",
                NODE_TYPE, "cards:Form",
                "questionnaire", session.getNode("/Questionnaires/TestQuestionnaire"),
                "subject", session.getNode("/Subjects/Test"))
            .resource("/Forms/f2", Map.of(NODE_TYPE, "nt:unstructured"))
            .commit();
        Assert.assertFalse(this.formUtils.isForm(session.getNode("/Forms")));
        Assert.assertFalse(this.formUtils.isForm(session.getNode("/Forms/f2")));
        Assert.assertTrue(this.formUtils.isForm(session.getNode("/Forms/f1")));
    }

    @Test
    public void isAnswerForNodeWithActualAnswerReturnsTrue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode("/Questionnaires/TestQuestionnaire/section_1/question_1");
        this.context.build()
            .resource("/Forms/f1",
                NODE_TYPE, "cards:Form",
                "questionnaire", session.getNode("/Questionnaires/TestQuestionnaire"),
                "subject", session.getNode("/Subjects/Test"))
            .resource("/Forms/f1/a1", NODE_TYPE, "cards:Answer", "question", question)
            .commit();
        Assert.assertTrue(
            this.formUtils.isAnswer(this.context.resourceResolver().getResource("/Forms/f1/a1").adaptTo(Node.class)));
    }

    @Test
    public void isAnswerForNodeWithBooleanAnswerReturnsTrue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode("/Questionnaires/TestQuestionnaire/section_1/question_1");
        this.context.build()
            .resource("/Forms/f1",
                NODE_TYPE, "cards:Form",
                "questionnaire", session.getNode("/Questionnaires/TestQuestionnaire"),
                "subject", session.getNode("/Subjects/Test"))
            .resource("/Forms/f1/a1", NODE_TYPE, "cards:BooleanAnswer", "question", question)
            .commit();
        Node answer = this.context.resourceResolver().getResource("/Forms/f1/a1").adaptTo(Node.class);
        Assert.assertTrue(
            this.formUtils.isAnswer(answer));
    }

    @Test
    public void isFormForNodeStateWithRealFormReturnsTrue()
    {
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        PropertyState typeProperty = Mockito.mock(PropertyState.class);
        Mockito.when(typeProperty.getValue(Type.NAME)).thenReturn("cards:Form");
        NodeState node = Mockito.mock(NodeState.class);
        Mockito.when(node.getProperty(NODE_TYPE)).thenReturn(typeProperty);
        Assert.assertTrue(this.formUtils.isForm(node));
    }

    @Test
    public void findAllFormRelatedAnswersFindsTopLevelAnswers() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode("/Questionnaires/TestQuestionnaire/section_1/question_1");
        this.context.build()
            .resource("/Forms/f1",
                NODE_TYPE, "cards:Form",
                "questionnaire", session.getNode("/Questionnaires/TestQuestionnaire"),
                "subject", session.getNode("/Subjects/Test"))
            .resource("/Forms/f1/a1", NODE_TYPE, "cards:BooleanAnswer", "question", question)
            .resource("/Forms/f1/a2", NODE_TYPE, "cards:BooleanAnswer", "question",
                session.getNode("/Questionnaires/TestQuestionnaire/section_1/question_2"))
            .resource("/Forms/f1/a3", NODE_TYPE, "cards:BooleanAnswer", "question", question)
            .commit();
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(Mockito.mock(Node.class));
        Iterator<Node> answers = this.formUtils.findAllFormRelatedAnswers(
            session.getNode("/Forms/f1"),
            question,
            EnumSet.of(FormUtils.SearchType.FORM))
            .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a3", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
    }

    @Test
    public void findAllFormRelatedAnswersFindsAnswersInSections() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode("/Questionnaires/TestQuestionnaire/section_1/question_1");
        this.context.build()
            .resource("/Forms/f1",
                NODE_TYPE, "cards:Form",
                "questionnaire", session.getNode("/Questionnaires/TestQuestionnaire"),
                "subject", session.getNode("/Subjects/Test"))
            .resource("/Forms/f1/a1", NODE_TYPE, "cards:BooleanAnswer", "question", question)
            .resource("/Forms/f1/s1",
                Map.of(NODE_TYPE, "cards:AnswerSection", "section",
                    session.getNode("/Questionnaires/TestQuestionnaire/section_1")))
            .resource("/Forms/f1/s1/a2", NODE_TYPE, "cards:BooleanAnswer", "question",
                session.getNode("/Questionnaires/TestQuestionnaire/section_1/question_2"))
            .resource("/Forms/f1/s1/s2",
                Map.of(NODE_TYPE, "cards:AnswerSection", "section",
                    session.getNode("/Questionnaires/TestQuestionnaire/section_1")))
            .resource("/Forms/f1/s1/s2/a3", NODE_TYPE, "cards:BooleanAnswer", "question", question)
            .commit();
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(Mockito.mock(Node.class));
        Iterator<Node> answers = this.formUtils.findAllFormRelatedAnswers(
            session.getNode("/Forms/f1"),
            question,
            EnumSet.of(FormUtils.SearchType.FORM))
            .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/s1/s2/a3", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
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
        this.context.load().json("/Questionnaires.json", "/Questionnaires/TestQuestionnaire");
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
            .resource("/Subjects/Test", NODE_TYPE, "cards:Subject", "type",
                this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
            .commit();
    }
}
