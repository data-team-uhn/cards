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
import org.apache.jackrabbit.oak.api.PropertyState;
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
import org.mockito.junit.MockitoJUnitRunner;

import io.uhndata.cards.forms.api.FormUtils;

import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RequiredSubjectTypesValidator}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class RequiredSubjectTypesValidatorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";

    private static final String NODE_IDENTIFIER = "jcr:uuid";

    private static final String FORM_TYPE = "cards:Form";

    private static final String SUBJECT_TYPE = "cards:Subject";

    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";

    private static final String TEST_COMPUTED_QUESTIONNAIRE_PATH = "/Questionnaires/TestComputedQuestionnaire";

    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";

    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";

    private static final String SUBJECT_PROPERTY = "subject";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Mock
    private ResourceResolverFactory rrf;

    @Mock
    private FormUtils formUtils;

    private RequiredSubjectTypesValidator requiredSubjectTypesValidator;

    private NodeState form;

    @Test
    public void childNodeAddedForSectionNodeReturnsThisValidator() throws CommitFailedException
    {
        String name = this.form.getProperty(NODE_IDENTIFIER).getValue(Type.STRING);
        Validator validator = this.requiredSubjectTypesValidator.childNodeAdded(name, getFormSection(this.form));
        Assert.assertNotNull(validator);
        Assert.assertTrue(validator instanceof RequiredSubjectTypesValidator);
    }

    @Test
    public void childNodeAddedForFormNodePassesValidation() throws CommitFailedException, LoginException,
        RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_COMPUTED_QUESTIONNAIRE_PATH);
        questionnaire.setProperty("requiredSubjectTypes", session.getNode("/SubjectTypes/Root"));
        String name = this.form.getProperty(NODE_IDENTIFIER).getValue(Type.STRING);

        ResourceResolver serviceResolver = Mockito.mock(ResourceResolver.class);
        String getQuestionnaireQuery = "SELECT * FROM [cards:Questionnaire] AS q WHERE q.'jcr:uuid'='"
            + questionnaire.getIdentifier() + "'";
        when(this.rrf.getServiceResourceResolver(Mockito.anyMap())).thenReturn(serviceResolver);
        when(serviceResolver.findResources(Mockito.eq(getQuestionnaireQuery), Mockito.anyString()))
            .thenReturn(resourceResolver.findResources(getQuestionnaireQuery, "JCR-SQL2"));

        String getSubjectQuery = "SELECT * FROM [cards:Subject] AS q WHERE q.'jcr:uuid'='" + subject.getIdentifier()
            + "'";
        when(serviceResolver.findResources(
            Mockito.eq(getSubjectQuery), Mockito.anyString()))
                .thenReturn(resourceResolver.findResources(getSubjectQuery, "JCR-SQL2"));

        Assert.assertNull(this.requiredSubjectTypesValidator.childNodeAdded(name, this.form));
    }

    @Test
    public void childNodeAddedForNullQuestionnaireAndSubjectResourcesReturnsNull()
        throws LoginException, RepositoryException, CommitFailedException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_COMPUTED_QUESTIONNAIRE_PATH);
        questionnaire.setProperty("requiredSubjectTypes", session.getNode("/SubjectTypes/Root/Branch"));
        String name = this.form.getProperty(NODE_IDENTIFIER).getValue(Type.STRING);

        ResourceResolver serviceResolver = Mockito.mock(ResourceResolver.class);
        String getQuestionnaireQuery = "SELECT * FROM [cards:Questionnaire] AS q WHERE q.'jcr:uuid'='"
            + questionnaire.getIdentifier() + "'";
        when(this.rrf.getServiceResourceResolver(Mockito.anyMap())).thenReturn(serviceResolver);
        when(serviceResolver.findResources(Mockito.eq(getQuestionnaireQuery), Mockito.anyString()))
            .thenReturn(Collections.emptyIterator());

        String getSubjectQuery = "SELECT * FROM [cards:Subject] AS q WHERE q.'jcr:uuid'='" + subject.getIdentifier()
            + "'";
        when(serviceResolver.findResources(Mockito.eq(getSubjectQuery), Mockito.anyString()))
            .thenReturn(Collections.emptyIterator());

        Assert.assertNull(this.requiredSubjectTypesValidator.childNodeAdded(name, this.form));
    }

    @Test
    public void childNodeChangedReturnsThisValidator()
    {
        String name = this.form.getProperty(NODE_IDENTIFIER).getValue(Type.STRING);
        Validator validator = this.requiredSubjectTypesValidator.childNodeChanged(name, Mockito.mock(NodeState.class),
            getFormSection(this.form));
        Assert.assertNotNull(validator);
        Assert.assertTrue(validator instanceof RequiredSubjectTypesValidator);
    }

    @Test
    public void propertyChangedThrowsException() throws RepositoryException, LoginException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_COMPUTED_QUESTIONNAIRE_PATH);
        questionnaire.setProperty("requiredSubjectTypes", session.getNode("/SubjectTypes/Root/Branch"));

        ResourceResolver serviceResolver = Mockito.mock(ResourceResolver.class);
        String getQuestionnaireQuery = "SELECT * FROM [cards:Questionnaire] AS q WHERE q.'jcr:uuid'='"
            + questionnaire.getIdentifier() + "'";
        when(this.rrf.getServiceResourceResolver(Mockito.anyMap())).thenReturn(serviceResolver);
        when(serviceResolver.findResources(Mockito.eq(getQuestionnaireQuery), Mockito.anyString()))
            .thenReturn(resourceResolver.findResources(getQuestionnaireQuery, "JCR-SQL2"));

        String getSubjectQuery = "SELECT * FROM [cards:Subject] AS q WHERE q.'jcr:uuid'='" + subject.getIdentifier()
            + "'";
        when(serviceResolver.findResources(
            Mockito.eq(getSubjectQuery), Mockito.anyString()))
                .thenReturn(resourceResolver.findResources(getSubjectQuery, "JCR-SQL2"));

        PropertyState after = Mockito.mock(PropertyState.class);
        when(after.getName()).thenReturn("subject");
        Assert.assertThrows(CommitFailedException.class,
            () -> this.requiredSubjectTypesValidator.propertyChanged(Mockito.mock(PropertyState.class), after));
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
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

        // Create NodeBuilder/NodeState instances of New Test Form
        String answerSectionUuid = UUID.randomUUID().toString();
        NodeBuilder answerSectionBuilder = createTestAnswerSection(answerSectionUuid);

        String formUuid = UUID.randomUUID().toString();
        this.form = createTestForm(formUuid, questionnaireUuid, subjectUuid, answerSectionUuid,
            answerSectionBuilder.getNodeState()).getNodeState();

        this.requiredSubjectTypesValidator = new RequiredSubjectTypesValidator(this.rrf, this.formUtils, this.form);

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

    private NodeBuilder createTestAnswerSection(String uuid)
    {
        NodeBuilder answerSectionBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerSectionBuilder.setProperty(NODE_TYPE, ANSWER_SECTION_TYPE);
        answerSectionBuilder.setProperty(NODE_IDENTIFIER, uuid);
        return answerSectionBuilder;
    }

    private NodeState getFormSection(NodeState currentNode)
    {
        for (String name : currentNode.getChildNodeNames()) {
            NodeState child = currentNode.getChildNode(name);
            if (child.hasProperty(NODE_TYPE)
                && ANSWER_SECTION_TYPE.equals(child.getProperty(NODE_TYPE).getValue(Type.STRING))) {
                return child;
            }
        }
        return null;
    }
}
