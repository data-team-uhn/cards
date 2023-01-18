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

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.json.JsonValue;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.value.*;
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
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String ANSWER_BOOLEAN_TYPE = "cards:BooleanAnswer";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_1";
    private static final String TEST_QUESTION_2_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_2";
    private static final String TEST_SECTION_PATH = "/Questionnaires/TestQuestionnaire/section_1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";

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

    // Form methods
    // isForm checks

    @Test
    public void isFormForNodeWithNullArgumentReturnsFalse()
    {
        Assert.assertFalse(this.formUtils.isForm((Node) null));
    }

    @Test
    public void isFormForNodeExceptionReturnsFalse() throws RepositoryException
    {
        Node node = Mockito.mock(Node.class);
        Mockito.when(node.isNodeType(FORM_TYPE)).thenThrow(new RepositoryException());
        Assert.assertFalse(this.formUtils.isForm(node));
    }

    @Test
    public void isFormForNodeWithActualFormReturnsTrue() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        this.context.build()
            .resource(TEST_FORM_PATH, NODE_TYPE, FORM_TYPE, QUESTIONNAIRE_PROPERTY,
                session.getNode(TEST_QUESTIONNAIRE_PATH), SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
            .commit();
        Assert.assertTrue(
            this.formUtils.isForm(this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class)));
    }

    @Test
    public void isFormForNodeWithOtherNodeTypeReturnsFalse() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        this.context.build()
            .resource(TEST_FORM_PATH,
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
            .resource("/Forms/f2", Map.of(NODE_TYPE, "nt:unstructured"))
            .commit();
        Assert.assertFalse(this.formUtils.isForm(session.getNode("/Forms")));
        Assert.assertFalse(this.formUtils.isForm(session.getNode("/Forms/f2")));
        Assert.assertTrue(this.formUtils.isForm(session.getNode(TEST_FORM_PATH)));
    }

    @Test
    public void isFormForNodeBuilderWithNullArgumentReturnsFalse()
    {
        Assert.assertFalse(this.formUtils.isForm((NodeBuilder) null));
    }

    @Test
    public void isFormForNodeBuilderWithActualFormReturnsTrue()
    {
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder rootBuilder = EmptyNodeState.EMPTY_NODE.builder();
        rootBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        Assert.assertTrue(this.formUtils.isForm(rootBuilder));
    }

    @Test
    public void isFormForNodeStateWithActualFormReturnsTrue()
    {
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        PropertyState typeProperty = Mockito.mock(PropertyState.class);
        Mockito.when(typeProperty.getValue(Type.NAME)).thenReturn(FORM_TYPE);
        NodeState node = Mockito.mock(NodeState.class);
        Mockito.when(node.getProperty(NODE_TYPE)).thenReturn(typeProperty);
        Assert.assertTrue(this.formUtils.isForm(node));
    }

    // getQuestionnaire checks
    @Test
    public void getQuestionnaireForNodeWithActualFormReturnsCorrectQuestionnaire() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
                .commit();

        Node questionnaire = this.formUtils.getQuestionnaire(this.context.resourceResolver().getResource(TEST_FORM_PATH)
                .adaptTo(Node.class));
        Assert.assertEquals(TEST_QUESTIONNAIRE_PATH, questionnaire.getPath());
    }

    @Test
    public void getQuestionnaireForNodeStateWithRealFormReturnsCorrectQuestionnaire() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.questionnaires.getQuestionnaire(questionnaire.getIdentifier()))
                .thenReturn(questionnaire);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        nodeBuilder.setProperty(QUESTIONNAIRE_PROPERTY, questionnaire.getIdentifier());

        Node questionnaireActual = this.formUtils.getQuestionnaire(nodeBuilder.getNodeState());
        Assert.assertEquals(TEST_QUESTIONNAIRE_PATH, questionnaireActual.getPath());
    }

    @Test
    public void getQuestionnaireForNodeBuilderWithRealFormReturnsCorrectQuestionnaire() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.questionnaires.getQuestionnaire(questionnaire.getIdentifier()))
                .thenReturn(questionnaire);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        nodeBuilder.setProperty(QUESTIONNAIRE_PROPERTY, questionnaire.getIdentifier());

        Node questionnaireActual = this.formUtils.getQuestionnaire(nodeBuilder);
        Assert.assertEquals(TEST_QUESTIONNAIRE_PATH, questionnaireActual.getPath());
    }

    // getQuestionnaireIdentifier checks
    @Test
    public void getQuestionnaireIdentifierForNodeWithRealFormReturnsCorrectIdentifier() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        this.context.build()
                .resource(TEST_FORM_PATH, NODE_TYPE, FORM_TYPE, QUESTIONNAIRE_PROPERTY,
                        questionnaire, SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH)).commit();

        String identifierActual = this.formUtils.getQuestionnaireIdentifier(this.context.resourceResolver()
                .getResource(TEST_FORM_PATH).adaptTo(Node.class));
        Assert.assertEquals(questionnaire.getIdentifier(), identifierActual);
    }

    @Test
    public void getQuestionnaireIdentifierForNodeStateWithRealFormReturnsCorrectIdentifier()
            throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        String questionnaireIdentifier = questionnaire.getIdentifier();

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.questionnaires.getQuestionnaire(questionnaireIdentifier))
                .thenReturn(questionnaire);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        nodeBuilder.setProperty(QUESTIONNAIRE_PROPERTY, questionnaireIdentifier);

        String identifier = this.formUtils.getQuestionnaireIdentifier(nodeBuilder.getNodeState());
        Assert.assertEquals(questionnaireIdentifier, identifier);
    }

    @Test
    public void getQuestionnaireIdentifierForNodeBuilderWithRealFormReturnsCorrectIdentifier()
            throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        String questionnaireIdentifier = questionnaire.getIdentifier();

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.questionnaires.getQuestionnaire(questionnaireIdentifier))
                .thenReturn(questionnaire);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        nodeBuilder.setProperty(QUESTIONNAIRE_PROPERTY, questionnaireIdentifier);

        String identifier = this.formUtils.getQuestionnaireIdentifier(nodeBuilder);
        Assert.assertEquals(questionnaireIdentifier, identifier);
    }

    // getSubject checks
    @Test
    public void getSubjectForNodeWithRealFormReturnsCorrectSubject() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
                .commit();

        Node subject = this.formUtils.getSubject(this.context.resourceResolver().getResource(TEST_FORM_PATH)
                .adaptTo(Node.class));
        Assert.assertEquals(TEST_SUBJECT_PATH, subject.getPath());
    }

    @Test
    public void getSubjectForNodeStateWithRealFormReturnsCorrectSubject() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.subjects.getSubject(subject.getIdentifier())).thenReturn(subject);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        nodeBuilder.setProperty(SUBJECT_PROPERTY, subject.getIdentifier());

        Node subjectActual = this.formUtils.getSubject(nodeBuilder.getNodeState());
        Assert.assertEquals(TEST_SUBJECT_PATH, subjectActual.getPath());
    }

    @Test
    public void getSubjectForNodeBuilderWithRealFormReturnsCorrectSubject() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.subjects.getSubject(subject.getIdentifier())).thenReturn(subject);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        nodeBuilder.setProperty(SUBJECT_PROPERTY, subject.getIdentifier());

        Node subjectActual = this.formUtils.getSubject(nodeBuilder);
        Assert.assertEquals(TEST_SUBJECT_PATH, subjectActual.getPath());
    }

    // getSubject identifier
    @Test
    public void getSubjectIdentifierForNodeWithRealFormReturnsCorrectIdentifier() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        this.context.build()
                .resource(TEST_FORM_PATH, NODE_TYPE, FORM_TYPE, QUESTIONNAIRE_PROPERTY,
                        session.getNode(TEST_QUESTIONNAIRE_PATH), SUBJECT_PROPERTY, subject)
                .commit();

        String identifierActual = this.formUtils.getSubjectIdentifier(this.context.resourceResolver()
                .getResource(TEST_FORM_PATH).adaptTo(Node.class));
        Assert.assertEquals(subject.getIdentifier(), identifierActual);
    }

    @Test
    public void getSubjectIdentifierForNodeStateWithRealFormReturnsCorrectIdentifier() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        String subjectIdentifier = subject.getIdentifier();

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.subjects.getSubject(subjectIdentifier)).thenReturn(subject);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        nodeBuilder.setProperty(SUBJECT_PROPERTY, subjectIdentifier);

        String identifierActual = this.formUtils.getSubjectIdentifier(nodeBuilder.getNodeState());
        Assert.assertEquals(subjectIdentifier, identifierActual);
    }

    @Test
    public void getSubjectIdentifierForNodeBuilderWithRealFormReturnsCorrectIdentifier() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        String subjectIdentifier = subject.getIdentifier();

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.subjects.getSubject(subjectIdentifier)).thenReturn(subject);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, FORM_TYPE);
        nodeBuilder.setProperty(SUBJECT_PROPERTY, subjectIdentifier);

        String identifierActual = this.formUtils.getSubjectIdentifier(nodeBuilder);
        Assert.assertEquals(subjectIdentifier, identifierActual);
    }

    // AnswerSection methods
    // isAnswerSection check
    @Test
    public void isAnswerSectionForNodeWithActualSectionReturnsTrue() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionSection = session.getNode(TEST_SECTION_PATH);

        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
                .resource("/Forms/f1/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        "section", questionSection)
                .commit();

        Node answerSection = this.context.resourceResolver().getResource("/Forms/f1/s1").adaptTo(Node.class);
        Assert.assertTrue(this.formUtils.isAnswerSection(answerSection));
    }

    @Test
    public void isAnswerSectionForNodeStateWithActualSectionReturnsTrue()
    {
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        PropertyState typeProperty = Mockito.mock(PropertyState.class);
        Mockito.when(typeProperty.getValue(Type.NAME)).thenReturn(ANSWER_SECTION_TYPE);
        NodeState node = Mockito.mock(NodeState.class);
        Mockito.when(node.getProperty(NODE_TYPE)).thenReturn(typeProperty);
        Assert.assertTrue(this.formUtils.isAnswerSection(node));
    }

    @Test
    public void isAnswerSectionForNodeBuilderWithActualSectionReturnsTrue()
    {
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder node = EmptyNodeState.EMPTY_NODE.builder();
        node.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        Assert.assertTrue(this.formUtils.isAnswerSection(node));
    }

    // getSection check
    @Test
    public void getSectionForNodeWithActualAnswerSectionReturnsCorrectSection() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionSection = session.getNode(TEST_SECTION_PATH);
        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
                .resource("/Forms/f1/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        "section", questionSection)
                .commit();

        Node section = this.formUtils.getSection(this.context.resourceResolver().getResource("/Forms/f1/s1")
                .adaptTo(Node.class));
        Assert.assertEquals(TEST_SECTION_PATH, section.getPath());
    }

    @Test
    public void getSectionForNodeStateWithActualAnswerSectionReturnsCorrectSection() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node section = session.getNode(TEST_SECTION_PATH);

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.questionnaires.getSection(section.getIdentifier()))
                .thenReturn(section);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        nodeBuilder.setProperty("section", section.getIdentifier());

        Node questionnaireActual = this.formUtils.getSection(nodeBuilder.getNodeState());
        Assert.assertEquals(TEST_SECTION_PATH, questionnaireActual.getPath());
    }

    @Test
    public void getSectionForNodeBuilderWithActualAnswerSectionReturnsCorrectSection() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node section = session.getNode(TEST_SECTION_PATH);

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.questionnaires.getSection(section.getIdentifier()))
                .thenReturn(section);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        nodeBuilder.setProperty("section", section.getIdentifier());

        Node questionnaireActual = this.formUtils.getSection(nodeBuilder);
        Assert.assertEquals(TEST_SECTION_PATH, questionnaireActual.getPath());
    }

    // getSectionIdentifier checks
    @Test
    public void getSectionIdentifierForNodeWithActualAnswerSectionReturnsCorrectIdentifier() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionSection = session.getNode(TEST_SECTION_PATH);
        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
                .resource("/Forms/f1/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        "section", questionSection)
                .commit();

        String identifierActual = this.formUtils.getSectionIdentifier(this.context.resourceResolver()
                .getResource("/Forms/f1/s1").adaptTo(Node.class));
        Assert.assertEquals(questionSection.getIdentifier(), identifierActual);
    }

    @Test
    public void getSectionIdentifierForNodeStateWithActualAnswerSectionReturnsCorrectIdentifier()
            throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node section = session.getNode(TEST_SECTION_PATH);
        String sectionIdentifier = section.getIdentifier();

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.subjects.getSubject(sectionIdentifier)).thenReturn(section);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        nodeBuilder.setProperty("section", sectionIdentifier);

        String identifierActual = this.formUtils.getSectionIdentifier(nodeBuilder.getNodeState());
        Assert.assertEquals(sectionIdentifier, identifierActual);
    }

    @Test
    public void getSectionIdentifierForNodeBuilderWithActualAnswerSectionReturnsCorrectIdentifier()
            throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node section = session.getNode(TEST_SECTION_PATH);
        String sectionIdentifier = section.getIdentifier();

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.subjects.getSubject(sectionIdentifier)).thenReturn(section);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        nodeBuilder.setProperty("section", sectionIdentifier);

        String identifierActual = this.formUtils.getSectionIdentifier(nodeBuilder);
        Assert.assertEquals(sectionIdentifier, identifierActual);
    }

    // Answer methods
    @Test
    public void isAnswerForNodeWithActualAnswerReturnsTrue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        this.context.build()
            .resource(TEST_FORM_PATH,
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
            .resource("/Forms/f1/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
            .commit();
        Assert.assertTrue(
            this.formUtils.isAnswer(this.context.resourceResolver().getResource("/Forms/f1/a1").adaptTo(Node.class)));
    }

    @Test
    public void isAnswerForNodeWithBooleanAnswerReturnsTrue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        this.context.build()
            .resource(TEST_FORM_PATH,
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
            .resource("/Forms/f1/a1", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
            .commit();
        Node answer = this.context.resourceResolver().getResource("/Forms/f1/a1").adaptTo(Node.class);
        Assert.assertTrue(
            this.formUtils.isAnswer(answer));
    }

    @Test
    public void isAnswerForNodeStateWithActualAnswerReturnsTrue()
    {
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        PropertyState typeProperty = Mockito.mock(PropertyState.class);
        Mockito.when(typeProperty.getValue(Type.NAME)).thenReturn(ANSWER_TYPE);
        NodeState node = Mockito.mock(NodeState.class);
        Mockito.when(node.getProperty(NODE_TYPE)).thenReturn(typeProperty);

        Assert.assertTrue(this.formUtils.isAnswer(node));
    }

    @Test
    public void isAnswerForNodeBuilderWithBooleanAnswerReturnsTrue()
    {
        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        NodeBuilder node = EmptyNodeState.EMPTY_NODE.builder();
        node.setProperty(NODE_TYPE, ANSWER_BOOLEAN_TYPE);
        Assert.assertTrue(this.formUtils.isAnswer(node));
    }

    // getQuestionSection checks
    @Test
    public void getQuestionForNodeWithActualAnswerReturnsCorrectQuestion() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
                .resource("/Forms/f1/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
                .commit();

        Node questionActual = this.formUtils.getQuestion(this.context.resourceResolver().getResource("/Forms/f1/a1")
                .adaptTo(Node.class));
        Assert.assertEquals(TEST_QUESTION_PATH, questionActual.getPath());
    }

    @Test
    public void getQuestionForNodeStateWithActualAnswerReturnsCorrectQuestio() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.questionnaires.getQuestion(question.getIdentifier()))
                .thenReturn(question);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, ANSWER_TYPE);
        nodeBuilder.setProperty(QUESTION_PROPERTY, question.getIdentifier());

        Node questionActual = this.formUtils.getQuestion(nodeBuilder.getNodeState());
        Assert.assertEquals(TEST_QUESTION_PATH, questionActual.getPath());
    }

    @Test
    public void getQuestionForNodeBuilderWithBooleanAnswerReturnsCorrectQuestio() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.questionnaires.getQuestion(question.getIdentifier()))
                .thenReturn(question);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, ANSWER_BOOLEAN_TYPE);
        nodeBuilder.setProperty(QUESTION_PROPERTY, question.getIdentifier());

        Node questionActual = this.formUtils.getQuestion(nodeBuilder);
        Assert.assertEquals(TEST_QUESTION_PATH, questionActual.getPath());
    }

    // question identifier
    @Test
    public void getQuestionIdentifierForNodeWithActualAnswerReturnsCorrectIdentifier() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
                .resource("/Forms/f1/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
                .commit();

        String identifierActual = this.formUtils.getQuestionIdentifier(this.context.resourceResolver()
                .getResource("/Forms/f1/a1").adaptTo(Node.class));
        Assert.assertEquals(question.getIdentifier(), identifierActual);
    }

    @Test
    public void getQuestionIdentifierForNodeBuilderWithActualAnswerReturnsCorrectIdentifier()
            throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        String questionIdentifier = question.getIdentifier();

        Mockito.when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Mockito.when(this.subjects.getSubject(questionIdentifier)).thenReturn(question);
        NodeBuilder nodeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        nodeBuilder.setProperty(NODE_TYPE, ANSWER_BOOLEAN_TYPE);
        nodeBuilder.setProperty(QUESTION_PROPERTY, questionIdentifier);

        String identifierActual = this.formUtils.getQuestionIdentifier(nodeBuilder);
        Assert.assertEquals(questionIdentifier, identifierActual);
    }

    // getValue checks
    @Test
    public void getValueForAnswerNodeReturnsCorrectValue() throws RepositoryException
    {
        Node answer = Mockito.mock(Node.class);
        Property valueProperty = Mockito.mock(Property.class);
        Calendar date = Calendar.getInstance();
        Value value = new DateValue(date);
        Mockito.when(answer.getProperty("value")).thenReturn(valueProperty);
        Mockito.when(valueProperty.isMultiple()).thenReturn(false);
        Mockito.when(valueProperty.getValue()).thenReturn(value);

        Assert.assertEquals(date, this.formUtils.getValue(answer));
    }

    @Test
    public void getValueMultipleForAnswerNodeReturnsCorrectValues() throws RepositoryException
    {
        Node answer = Mockito.mock(Node.class);
        Property valueProperty = Mockito.mock(Property.class);
        Value[] values = {
            new BinaryValue("0b101"), new BooleanValue(true), new DecimalValue(new BigDecimal("11.11")),
            new DoubleValue(200.0), new LongValue(100), new StringValue("test")
        };
        Mockito.when(answer.getProperty("value")).thenReturn(valueProperty);
        Mockito.when(valueProperty.isMultiple()).thenReturn(true);
        Mockito.when(valueProperty.getValues()).thenReturn(values);
        Object[] result = (Object[]) this.formUtils.getValue(answer);

        Assert.assertEquals(6, result.length);
        Assert.assertNotNull(result[0]);
        Assert.assertEquals("0b101", result[0]);

        Assert.assertNotNull(result[1]);
        Assert.assertEquals(true, result[1]);

        Assert.assertNotNull(result[2]);
        Assert.assertEquals(new BigDecimal("11.11"), result[2]);

        Assert.assertNotNull(result[3]);
        Assert.assertEquals(200.0, result[3]);

        Assert.assertNotNull(result[4]);
        Assert.assertEquals(100L, result[4]);

        Assert.assertNotNull(result[5]);
        Assert.assertEquals("test", result[5]);
    }

    @Test
    public void getValueForNullNodeReturnsNull()
    {
        Assert.assertNull(this.formUtils.getValue((Node) null));
    }

    @Test
    public void getValueForAnswerNodeBuilderReturnsCorrectValue()
    {
        NodeBuilder answer = Mockito.mock(NodeBuilder.class);
        NodeState answerState = Mockito.mock(NodeState.class);
        PropertyState valuePropertyState = Mockito.mock(PropertyState.class);
        Type<?> valueType = Type.NAMES;

        Mockito.when(answer.getNodeState()).thenReturn(answerState);
        Mockito.when(answerState.getProperty("value")).thenReturn(valuePropertyState);
        Mockito.when(valuePropertyState.isArray()).thenReturn(true);
        Mockito.when(valuePropertyState.count()).thenReturn(2);
        Mockito.when(valuePropertyState.getValue(Type.NAME, 0)).thenReturn("test");
        Mockito.when(valuePropertyState.getValue(Type.NAME, 1)).thenReturn("second-test");
        Mockito.doReturn(valueType).when(valuePropertyState).getType();

        Object[] result = (Object[]) this.formUtils.getValue(answer);

        Assert.assertEquals(2, result.length);
        Assert.assertNotNull(result[0]);
        Assert.assertEquals("test", result[0]);

        Assert.assertNotNull(result[1]);
        Assert.assertEquals("second-test", result[1]);
    }

    @Test
    public void getValueForAnswerNodeStateReturnsCorrectValue() throws RepositoryException
    {
        NodeState answer = Mockito.mock(NodeState.class);
        PropertyState valuePropertyState = Mockito.mock(PropertyState.class);
        Type<?> valueType = Type.DOUBLE;
        Mockito.when(answer.getProperty("value")).thenReturn(valuePropertyState);
        Mockito.when(valuePropertyState.isArray()).thenReturn(false);
        Mockito.doReturn(valueType).when(valuePropertyState).getType();
        Mockito.doReturn(100.00).when(valuePropertyState).getValue(valueType);

        Assert.assertEquals(100.00, this.formUtils.getValue(answer));
    }

    @Test
    public void getValueForNullNodeStateReturnsNull()
    {
        Assert.assertNull(this.formUtils.getValue((NodeState) null));
    }


    // getAnswer checks
    @Test
    public void getAnswerForActualFormAndQuestionNodes() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        this.context.build()
            .resource(TEST_FORM_PATH,
                NODE_TYPE, FORM_TYPE,
                QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
            .resource("/Forms/f1/s1/a1", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
            .resource("/Forms/f1/a2", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY,
                session.getNode(TEST_QUESTION_2_PATH))
            .commit();

        Node actualAnswer = this.formUtils.getAnswer(session.getNode(TEST_FORM_PATH), question);
        Assert.assertNotNull(actualAnswer);
        Assert.assertEquals("/Forms/f1/s1/a1", actualAnswer.getPath());
    }

    @Test
    public void getAnswerForFakeFormNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        this.context.build()
            .resource(TEST_FORM_PATH,
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
            .commit();

        Assert.assertNull(this.formUtils.getAnswer(session.getNode("/Forms"), question));
    }

    @Test
    public void getAllAnswersFindsTopLevelAnswers() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
                .resource("/Forms/f1/a1", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
                .resource("/Forms/f1/a2", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY,
                        session.getNode(TEST_QUESTION_2_PATH))
                .resource("/Forms/f1/a3", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
                .commit();
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(Mockito.mock(Node.class));
        Iterator<Node> answers = this.formUtils.getAllAnswers(
                        session.getNode(TEST_FORM_PATH),
                        question)
                .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a3", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
    }

    // findAllRelatedAnswers checks
    // FormRelated
    @Test
    public void findAllFormRelatedAnswersFindsTopLevelAnswers() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        this.context.build()
            .resource(TEST_FORM_PATH,
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
            .resource("/Forms/f1/a1", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
            .resource("/Forms/f1/a2", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY,
                session.getNode(TEST_QUESTION_2_PATH))
            .resource("/Forms/f1/a3", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
            .commit();
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(Mockito.mock(Node.class));
        Iterator<Node> answers = this.formUtils.findAllFormRelatedAnswers(
            session.getNode(TEST_FORM_PATH),
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
        Node question = session.getNode(TEST_QUESTION_PATH);
        this.context.build()
            .resource(TEST_FORM_PATH,
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                SUBJECT_PROPERTY, session.getNode(TEST_SUBJECT_PATH))
            .resource("/Forms/f1/a1", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
            .resource("/Forms/f1/s1",
                Map.of(NODE_TYPE, ANSWER_SECTION_TYPE, "section",
                    session.getNode(TEST_SECTION_PATH)))
            .resource("/Forms/f1/s1/a2", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY,
                session.getNode(TEST_QUESTION_2_PATH))
            .resource("/Forms/f1/s1/s2",
                Map.of(NODE_TYPE, ANSWER_SECTION_TYPE, "section",
                    session.getNode(TEST_SECTION_PATH)))
            .resource("/Forms/f1/s1/s2/a3", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
            .commit();
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(Mockito.mock(Node.class));
        Iterator<Node> answers = this.formUtils.findAllFormRelatedAnswers(
            session.getNode(TEST_FORM_PATH),
            question,
            EnumSet.of(FormUtils.SearchType.FORM))
            .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/s1/s2/a3", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
    }

    @Test
    public void findAllFormRelatedAnswersFindsTopLevelAnswersWithSubjectFormsScope() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaireTest = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        commitResources(session);
        Mockito.when(this.subjects.isSubject(Mockito.any(Node.class))).thenReturn(Boolean.TRUE);
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(questionnaireTest);
        Iterator<Node> answers = this.formUtils.findAllFormRelatedAnswers(
                session.getNode("/Forms/f2"),
                question,
                EnumSet.of(FormUtils.SearchType.SUBJECT_FORMS))
            .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f2/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f2/a3", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
    }

    @Test
    public void findAllFormRelatedAnswersFindsMiddleAndLowLevelsAnswersWithDescendingFormsScope()
            throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaireTest = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        commitResources(session);
        Mockito.when(this.subjects.isSubject(Mockito.any(Node.class))).thenReturn(Boolean.TRUE);
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(questionnaireTest);
        Iterator<Node> answers = this.formUtils.findAllFormRelatedAnswers(
            session.getNode(TEST_FORM_PATH),
            question,
            EnumSet.of(FormUtils.SearchType.DESCENDANTS_FORMS))
            .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f4/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f4/a2", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f5/a2", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
    }

    @Test
    public void findAllFormRelatedAnswersFindsTopAndMiddleLevelsAnswersWithAscendingFormsScope()
            throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaireTest = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        commitResources(session);
        Mockito.when(this.subjects.isSubject(Mockito.any(Node.class))).thenReturn(Boolean.TRUE);
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(questionnaireTest);
        Iterator<Node> answers = this.formUtils.findAllFormRelatedAnswers(
            session.getNode("/Forms/f5"),
            question,
            EnumSet.of(FormUtils.SearchType.ANCESTORS_FORMS))
            .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f4/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f4/a2", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f2/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f2/a3", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
    }

    @Test
    public void findAllFormRelatedAnswersWithNullTargetQuestionnaire() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        commitResources(session);
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(null);
        Iterator<Node> answers = this.formUtils.findAllFormRelatedAnswers(
                session.getNode(TEST_FORM_PATH),
                question,
                EnumSet.of(FormUtils.SearchType.FORM))
            .iterator();
        Assert.assertFalse(answers.hasNext());
    }

    // SubjectRelated
    @Test
    public void findAllSubjectRelatedAnswersFindsTopLevelAnswersWithSubjectFormsScope() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaireTest = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        commitResources(session);
        Mockito.when(this.subjects.isSubject(Mockito.any(Node.class))).thenReturn(Boolean.TRUE);
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(questionnaireTest);
        Iterator<Node> answers = this.formUtils.findAllSubjectRelatedAnswers(
            session.getNode(TEST_SUBJECT_PATH),
            question,
            EnumSet.of(FormUtils.SearchType.SUBJECT_FORMS))
            .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f2/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f2/a3", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
    }

    @Test
    public void findAllSubjectRelatedAnswersFindsMiddleAndLowLevelsAnswersWithDescendingFormsScope()
            throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaireTest = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        commitResources(session);
        Mockito.when(this.subjects.isSubject(Mockito.any(Node.class))).thenReturn(Boolean.TRUE);
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(questionnaireTest);
        Iterator<Node> answers = this.formUtils.findAllSubjectRelatedAnswers(
            session.getNode(TEST_SUBJECT_PATH),
            question,
            EnumSet.of(FormUtils.SearchType.DESCENDANTS_FORMS))
            .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f4/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f4/a2", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f5/a2", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
    }

    @Test
    public void findAllSubjectRelatedAnswersFindsTopAndMiddleLevelsAnswersWithAscendingFormsScope()
            throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaireTest = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        commitResources(session);
        Mockito.when(this.subjects.isSubject(Mockito.any(Node.class))).thenReturn(Boolean.TRUE);
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(questionnaireTest);
        Iterator<Node> answers = this.formUtils.findAllSubjectRelatedAnswers(
            session.getNode("/Subjects/Test/TestTumor/TestTumorRegion"),
            question,
            EnumSet.of(FormUtils.SearchType.ANCESTORS_FORMS))
            .iterator();
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f4/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f4/a2", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f1/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f2/a1", answers.next().getPath());
        Assert.assertTrue(answers.hasNext());
        Assert.assertEquals("/Forms/f2/a3", answers.next().getPath());
        Assert.assertFalse(answers.hasNext());
    }

    @Test
    public void findAllSubjectRelatedAnswersFindsTopLevelAnswersNullTargetQuestionnaire() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        commitResources(session);
        Mockito.when(this.questionnaires.getOwnerQuestionnaire(question)).thenReturn(null);
        Iterator<Node> answers = this.formUtils.findAllSubjectRelatedAnswers(
            session.getNode(TEST_SUBJECT_PATH),
            question,
            EnumSet.of(FormUtils.SearchType.FORM))
            .iterator();
        Assert.assertFalse(answers.hasNext());
    }

    // serializeProperty checks
    @Test
    public void serializePropertyNullValued()
    {
        Assert.assertEquals(JsonValue.NULL, this.formUtils.serializeProperty(null));
    }

    @Test
    public void serializePropertySingleValued() throws RepositoryException, ParseException
    {
        Property property = Mockito.mock(Property.class);

        Value value = new StringValue("test");
        Mockito.when(property.isMultiple()).thenReturn(false);
        Mockito.when(property.getType()).thenReturn(PropertyType.STRING);
        Mockito.when(property.getValue()).thenReturn(value);
        Assert.assertEquals("test",
                getStringWithoutDoubleQuotationMarks(this.formUtils.serializeProperty(property).toString()));

        value = new BinaryValue("0b101");
        Mockito.when(property.getType()).thenReturn(PropertyType.BINARY);
        Mockito.when(property.getValue()).thenReturn(value);
        Assert.assertEquals("0b101",
                getStringWithoutDoubleQuotationMarks(this.formUtils.serializeProperty(property).toString()));

        Calendar date = Calendar.getInstance();
        value = new DateValue(date);
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Mockito.when(property.getType()).thenReturn(PropertyType.DATE);
        Mockito.when(property.getValue()).thenReturn(value);
        Assert.assertEquals(
                date.getTime(), format.parse(
                        getStringWithoutDoubleQuotationMarks(this.formUtils.serializeProperty(property).toString())));

        value = new BooleanValue(true);
        Mockito.when(property.getType()).thenReturn(PropertyType.BOOLEAN);
        Mockito.when(property.getValue()).thenReturn(value);
        Assert.assertEquals(JsonValue.TRUE, this.formUtils.serializeProperty(property));

        value = new DecimalValue(new BigDecimal("11.11"));
        Mockito.when(property.getType()).thenReturn(PropertyType.DECIMAL);
        Mockito.when(property.getValue()).thenReturn(value);
        Assert.assertEquals("11.11",
                getStringWithoutDoubleQuotationMarks(this.formUtils.serializeProperty(property).toString()));

        value = new DoubleValue(200.0);
        Mockito.when(property.getType()).thenReturn(PropertyType.DOUBLE);
        Mockito.when(property.getValue()).thenReturn(value);
        Assert.assertEquals("200.0", this.formUtils.serializeProperty(property).toString());

        value = new LongValue(100);
        Mockito.when(property.getType()).thenReturn(PropertyType.LONG);
        Mockito.when(property.getValue()).thenReturn(value);
        Assert.assertEquals("100", this.formUtils.serializeProperty(property).toString());
    }

    @Test
    public void serializePropertyMultiValued() throws RepositoryException, ParseException
    {
        Property property = Mockito.mock(Property.class);

        // date property type
        Calendar date = Calendar.getInstance();
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Value[] values = { new DateValue(date) };
        Mockito.when(property.isMultiple()).thenReturn(true);
        Mockito.when(property.getType()).thenReturn(PropertyType.DATE);
        Mockito.when(property.getValues()).thenReturn(values);
        Object[] json = this.formUtils.serializeProperty(property).asJsonArray().toArray();

        Assert.assertNotNull(json);
        Assert.assertEquals(1, json.length);
        Assert.assertEquals(date.getTime(), format.parse(getStringWithoutDoubleQuotationMarks(json[0].toString())));

        // binary property type
        values[0] = new BinaryValue("0b101");
        Mockito.when(property.getType()).thenReturn(PropertyType.BINARY);
        Mockito.when(property.getValues()).thenReturn(values);
        json = this.formUtils.serializeProperty(property).asJsonArray().toArray();

        Assert.assertNotNull(json);
        Assert.assertEquals("0b101",
                getStringWithoutDoubleQuotationMarks(json[0].toString()));

        // double property type
        values[0] = new DoubleValue(200.0);
        Mockito.when(property.getType()).thenReturn(PropertyType.DOUBLE);
        Mockito.when(property.getValues()).thenReturn(values);
        json = this.formUtils.serializeProperty(property).asJsonArray().toArray();

        Assert.assertNotNull(json);
        Assert.assertEquals("200.0", json[0].toString());

        // long property type
        values[0] = new LongValue(100);
        Mockito.when(property.getType()).thenReturn(PropertyType.LONG);
        Mockito.when(property.getValues()).thenReturn(values);
        json = this.formUtils.serializeProperty(property).asJsonArray().toArray();

        Assert.assertNotNull(json);
        Assert.assertEquals("100", json[0].toString());

        // decimal property type
        values[0] = new DecimalValue(new BigDecimal("11.11"));
        Mockito.when(property.getType()).thenReturn(PropertyType.DECIMAL);
        Mockito.when(property.getValues()).thenReturn(values);
        json = this.formUtils.serializeProperty(property).asJsonArray().toArray();

        Assert.assertNotNull(json);
        Assert.assertEquals("11.11", getStringWithoutDoubleQuotationMarks(json[0].toString()));

        // decimal property type
        values[0] = new StringValue("test");
        Mockito.when(property.getType()).thenReturn(PropertyType.STRING);
        Mockito.when(property.getValues()).thenReturn(values);
        json = this.formUtils.serializeProperty(property).asJsonArray().toArray();

        Assert.assertNotNull(json);
        Assert.assertEquals("test", getStringWithoutDoubleQuotationMarks(json[0].toString()));

        // boolean property type
        values[0] = new BooleanValue(true);
        Mockito.when(property.getType()).thenReturn(PropertyType.BOOLEAN);
        Mockito.when(property.getValues()).thenReturn(values);
        json = this.formUtils.serializeProperty(property).asJsonArray().toArray();

        Assert.assertNotNull(json);
        Assert.assertEquals(JsonValue.TRUE, json[0]);
    }

    @Test
    public void serializePropertyTrowRepositoryException() throws RepositoryException
    {
        Property property = Mockito.mock(Property.class);

        Mockito.when(property.isMultiple()).thenThrow(new RepositoryException());
        Assert.assertEquals(JsonValue.NULL, this.formUtils.serializeProperty(property));
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
    }

    private String getStringWithoutDoubleQuotationMarks(String json)
    {
        return json.substring(1, json.length() - 1);
    }

    private void commitResources(Session session) throws RepositoryException
    {
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        Node question2 = session.getNode(TEST_QUESTION_2_PATH);

        this.context.build()
            .resource("/Subjects/Test/TestTumor", NODE_TYPE, SUBJECT_TYPE, "type",
                this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class))
            .resource("/Subjects/Test/TestTumor/TestTumorRegion", NODE_TYPE, SUBJECT_TYPE, "type",
                this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch/Leaf").adaptTo(Node.class))
            .resource("/Subjects/Test_2", NODE_TYPE, SUBJECT_TYPE, "type",
                this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
            // form1 for Subject
            .resource(TEST_FORM_PATH,
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, questionnaire,
                SUBJECT_PROPERTY, subject,
                "relatedSubjects", List.of(subject).toArray())
            .resource("/Forms/f1/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
            // form2 for Subject
            .resource("/Forms/f2",
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, questionnaire,
                SUBJECT_PROPERTY, subject)
            .resource("/Forms/f2/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
            .resource("/Forms/f2/a2", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question2)
            .resource("/Forms/f2/a3", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
            // form for subject 2
            .resource("/Forms/f3",
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, questionnaire,
                SUBJECT_PROPERTY, session.getNode("/Subjects/Test_2"))
            .resource("/Forms/f3/a1", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question2)
            // form for subject/branch
            .resource("/Forms/f4",
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, questionnaire,
                SUBJECT_PROPERTY, session.getNode("/Subjects/Test/TestTumor"))
            .resource("/Forms/f4/a1", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
            .resource("/Forms/f4/a2", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
            .resource("/Forms/f4/a3", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question2)
            // form for subject/branch/leaf
            .resource("/Forms/f5",
                NODE_TYPE, FORM_TYPE,
                    QUESTIONNAIRE_PROPERTY, questionnaire,
                SUBJECT_PROPERTY, session.getNode("/Subjects/Test/TestTumor/TestTumorRegion"))
            .resource("/Forms/f5/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question2)
            .resource("/Forms/f5/a2", NODE_TYPE, ANSWER_BOOLEAN_TYPE, QUESTION_PROPERTY, question)
            .commit();
    }
}
