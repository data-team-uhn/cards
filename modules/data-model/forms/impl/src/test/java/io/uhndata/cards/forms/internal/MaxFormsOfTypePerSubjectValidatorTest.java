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

package io.uhndata.cards.forms.internal;

import java.util.Collections;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
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

import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MaxFormsOfTypePerSubjectValidator}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class MaxFormsOfTypePerSubjectValidatorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String NODE_IDENTIFIER = "jcr:uuid";
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
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Mock
    private ResourceResolverFactory rrf;

    private MaxFormsOfTypePerSubjectValidator maxFormsOfTypePerSubjectValidator;

    private NodeState form;

    @Test
    public void constructorTest()
    {
        Assert.assertNotNull(this.maxFormsOfTypePerSubjectValidator);
    }

    @Test
    public void childNodeAddedForSectionNodeReturnsThisValidator() throws CommitFailedException
    {
        String name = this.form.getProperty(NODE_IDENTIFIER).getValue(Type.STRING);
        Validator validator = this.maxFormsOfTypePerSubjectValidator.childNodeAdded(name, getFormSection());
        Assert.assertNotNull(validator);
        Assert.assertEquals(this.maxFormsOfTypePerSubjectValidator, validator);
    }

    @Test
    public void childNodeAddedForFormNodePassesValidation() throws CommitFailedException, LoginException,
            RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_COMPUTED_QUESTIONNAIRE_PATH);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        String name = this.form.getProperty(NODE_IDENTIFIER).getValue(Type.STRING);

        ResourceResolver serviceResolver = Mockito.mock(ResourceResolver.class);
        String getQuestionnaireQuery = "SELECT * FROM [cards:Questionnaire] as q WHERE q.'jcr:uuid'='"
                + questionnaire.getIdentifier() + "'";
        when(this.rrf.getServiceResourceResolver(Mockito.anyMap())).thenReturn(serviceResolver);
        when(serviceResolver.findResources(Mockito.eq(getQuestionnaireQuery), Mockito.anyString()))
                .thenReturn(resourceResolver.findResources(getQuestionnaireQuery, "JCR-SQL2"));

        String getFormsQuery = "SELECT f.* FROM [cards:Form] AS f WHERE f.'subject'='" + subject.getIdentifier() + "'"
                + " AND f.'questionnaire'='" + questionnaire.getIdentifier() + "'";
        when(serviceResolver.findResources(
                Mockito.eq(getFormsQuery),
                Mockito.anyString())).thenReturn(resourceResolver.findResources(getFormsQuery, "JCR-SQL2"));

        Validator validator = this.maxFormsOfTypePerSubjectValidator.childNodeAdded(name, this.form);
        Assert.assertNotNull(validator);
        Assert.assertEquals(this.maxFormsOfTypePerSubjectValidator, validator);
    }

    @Test
    public void childNodeAddedTestForFormNodeWithNotExistingQuestionnaireReturnsThisValidator()
            throws CommitFailedException, LoginException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_COMPUTED_QUESTIONNAIRE_PATH);
        String name = this.form.getProperty(NODE_IDENTIFIER).getValue(Type.STRING);

        ResourceResolver serviceResolver = Mockito.mock(ResourceResolver.class);
        String getQuestionnaireQuery = "SELECT * FROM [cards:Questionnaire] as q WHERE q.'jcr:uuid'='"
                + questionnaire.getIdentifier() + "'";
        when(this.rrf.getServiceResourceResolver(Mockito.anyMap())).thenReturn(serviceResolver);
        when(serviceResolver.findResources(Mockito.eq(getQuestionnaireQuery), Mockito.anyString()))
                .thenReturn(Collections.emptyIterator());

        Validator validator = this.maxFormsOfTypePerSubjectValidator.childNodeAdded(name, this.form);
        Assert.assertNotNull(validator);
        Assert.assertEquals(this.maxFormsOfTypePerSubjectValidator, validator);
    }

    @Test
    public void childNodeAddedTestForFormNodeThrowsException() throws LoginException, RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_COMPUTED_QUESTIONNAIRE_PATH);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        String name = this.form.getProperty(NODE_IDENTIFIER).getValue(Type.STRING);

        this.context.build()
                .resource("/Forms/f1",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject)
                .commit();

        ResourceResolver serviceResolver = Mockito.mock(ResourceResolver.class);
        String getQuestionnaireQuery = "SELECT * FROM [cards:Questionnaire] as q WHERE q.'jcr:uuid'='"
                + questionnaire.getIdentifier() + "'";
        when(this.rrf.getServiceResourceResolver(Mockito.anyMap())).thenReturn(serviceResolver);
        when(serviceResolver.findResources(Mockito.eq(getQuestionnaireQuery), Mockito.anyString()))
                .thenReturn(resourceResolver.findResources(getQuestionnaireQuery, "JCR-SQL2"));

        String getFormsQuery = "SELECT f.* FROM [cards:Form] AS f WHERE f.'subject'='" + subject.getIdentifier() + "'"
                + " AND f.'questionnaire'='" + questionnaire.getIdentifier() + "'";
        when(serviceResolver.findResources(Mockito.eq(getFormsQuery), Mockito.anyString()))
                .thenReturn(resourceResolver.findResources(getFormsQuery, "JCR-SQL2"));

        Assert.assertThrows(CommitFailedException.class, () ->
            this.maxFormsOfTypePerSubjectValidator.childNodeAdded(name, this.form));
    }

    @Test
    public void childNodeChangedReturnsThisValidator() throws CommitFailedException
    {
        String name = this.form.getProperty(NODE_IDENTIFIER).getValue(Type.STRING);
        Validator validator = this.maxFormsOfTypePerSubjectValidator.childNodeChanged(name,
                Mockito.mock(NodeState.class), Mockito.mock(NodeState.class));
        Assert.assertNotNull(validator);
        Assert.assertEquals(this.maxFormsOfTypePerSubjectValidator, validator);
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        this.maxFormsOfTypePerSubjectValidator = new MaxFormsOfTypePerSubjectValidator(this.rrf);
        Session session = this.context.resourceResolver().adaptTo(Session.class);

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

        String subjectUuid = session.getNode(TEST_SUBJECT_PATH).getIdentifier();
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
        this.form = createTestForm(formUuid, questionnaireUuid, subjectUuid, answerSectionUuid,
                answerSectionBuilder.getNodeState()).getNodeState();
    }


    private NodeBuilder createTestForm(String uuid, String questionnaireUuid, String subjectUuid,
                                       String answerSectionUuid, NodeState answerSection)
    {
        NodeBuilder formBuilder = EmptyNodeState.EMPTY_NODE.builder();
        formBuilder.setProperty(NODE_TYPE, FORM_TYPE, Type.NAME);
        formBuilder.setProperty(QUESTIONNAIRE_PROPERTY, questionnaireUuid);
        formBuilder.setProperty(SUBJECT_PROPERTY, subjectUuid);
        formBuilder.setProperty(NODE_IDENTIFIER, uuid);
        formBuilder.setChildNode(answerSectionUuid, answerSection);
        return formBuilder;
    }

    private NodeBuilder createTestAnswer(String uuid, String questionUuid)
    {
        NodeBuilder answerBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerBuilder.setProperty(NODE_TYPE, ANSWER_TYPE);
        answerBuilder.setProperty(QUESTION_PROPERTY, questionUuid);
        answerBuilder.setProperty("value", 200L);
        answerBuilder.setProperty(NODE_IDENTIFIER, uuid);
        return answerBuilder;
    }

    private NodeBuilder createTestComputedAnswer(String uuid, String questionUuid)
    {
        NodeBuilder computedAnswerBuilder = EmptyNodeState.EMPTY_NODE.builder();
        computedAnswerBuilder.setProperty(NODE_TYPE, ANSWER_TYPE);
        computedAnswerBuilder.setProperty(QUESTION_PROPERTY, questionUuid);
        computedAnswerBuilder.setProperty(NODE_IDENTIFIER, uuid);
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
        answerSectionBuilder.setProperty(NODE_IDENTIFIER, uuid);
        return answerSectionBuilder;
    }

    private NodeState getFormSection()
    {
        for (String name : this.form.getChildNodeNames()) {
            NodeState child = this.form.getChildNode(name);
            if (child.hasProperty(NODE_TYPE)
                    && ANSWER_SECTION_TYPE.equals(child.getProperty(NODE_TYPE).getValue(Type.STRING))) {
                return child;
            }
        }
        return null;
    }

}
