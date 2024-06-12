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
package io.uhndata.cards.forms.internal.serialize.labels;

import java.util.List;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VocabularyOptionsLabelProcessor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class VocabularyOptionsLabelProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String ANSWER_OPTION_TYPE = "cards:AnswerOption";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_BOOLEAN_TYPE = "cards:BooleanAnswer";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/question_7";
    private static final String TEST_OPTION_1_PATH = "/Questionnaires/TestQuestionnaire/question_7/o1";
    private static final String TEST_OPTION_2_PATH = "/Questionnaires/TestQuestionnaire/question_7/o2";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String VALUE_PROPERTY = "value";
    private static final String LABEL_PROPERTY = "label";
    private static final String NAME = "labels";
    private static final int PRIORITY = 75;
    private static final boolean ENABLED = true;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private VocabularyOptionsLabelProcessor vocabularyOptionsLabelProcessor;


    @Test
    public void getNameTest()
    {
        Assert.assertEquals(NAME, this.vocabularyOptionsLabelProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.vocabularyOptionsLabelProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultReturnsTrue()
    {
        Assert.assertEquals(ENABLED, this.vocabularyOptionsLabelProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void canProcessForFormReturnsTrue()
    {
        Resource form = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        Assert.assertTrue(this.vocabularyOptionsLabelProcessor.canProcess(form));
    }

    @Test
    public void canProcessForQuestionnaireReturnsTrue()
    {
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        Assert.assertTrue(this.vocabularyOptionsLabelProcessor.canProcess(questionnaire));
    }

    @Test
    public void canProcessForSubjectReturnsFalse()
    {
        Resource subject = this.context.resourceResolver().getResource(TEST_SUBJECT_PATH);
        Assert.assertFalse(this.vocabularyOptionsLabelProcessor.canProcess(subject));
    }

    @Test
    public void leaveForAnswerOption() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = session.getNode(TEST_OPTION_1_PATH);

        this.vocabularyOptionsLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        Assert.assertFalse(jsonObject.isEmpty());
        Assert.assertTrue(jsonObject.containsKey(LABEL_PROPERTY));
        Assert.assertEquals("Option 1", jsonObject.getString(LABEL_PROPERTY));
    }

    @Test
    public void leaveForAnswerOptionWithMultipleValue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = session.getNode(TEST_OPTION_2_PATH);

        this.vocabularyOptionsLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        Assert.assertFalse(jsonObject.isEmpty());
        Assert.assertTrue(jsonObject.containsKey(LABEL_PROPERTY));
        Assert.assertEquals(2, jsonObject.getJsonArray(LABEL_PROPERTY).size());
        Assert.assertEquals("Option 1", jsonObject.getJsonArray(LABEL_PROPERTY).getString(0));
        Assert.assertEquals("Option 2", jsonObject.getJsonArray(LABEL_PROPERTY).getString(1));
    }

    @Test
    public void leaveForAnswerOptionNodeWithValuePropertyThrowsException() throws RepositoryException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = mock(Node.class);
        when(node.isNodeType(ANSWER_OPTION_TYPE)).thenReturn(true);
        when(node.getParent())
                .thenReturn(this.context.resourceResolver().getResource(TEST_QUESTION_PATH).adaptTo(Node.class));
        when(node.hasProperty(VALUE_PROPERTY)).thenReturn(true);
        when(node.getProperty(VALUE_PROPERTY)).thenThrow(new RepositoryException());

        Assert.assertThrows(NullPointerException.class,
            () -> this.vocabularyOptionsLabelProcessor.leave(node, json, mock(Function.class)));
    }

    @Test
    public void leaveForNotAnswerOptionThrowsException() throws RepositoryException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = mock(Node.class);
        when(node.isNodeType(ANSWER_OPTION_TYPE)).thenThrow(new RepositoryException());

        this.vocabularyOptionsLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        Assert.assertTrue(jsonObject.isEmpty());
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
                .resource("/Vocabularies", NODE_TYPE, "cards:FormsHomepage")
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
        Node question = session.getNode(TEST_QUESTION_PATH);

        this.context.build()
                .resource("/Vocabularies/Option1",
                        NODE_TYPE, ANSWER_OPTION_TYPE,
                        LABEL_PROPERTY, "Option 1",
                        VALUE_PROPERTY, "O1")
                .resource("/Vocabularies/Option2",
                        NODE_TYPE, ANSWER_OPTION_TYPE,
                        LABEL_PROPERTY, "Option 2",
                        VALUE_PROPERTY, "O2")
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f1/a1",
                        NODE_TYPE, ANSWER_BOOLEAN_TYPE,
                        QUESTION_PROPERTY, question)
                .commit();
    }

}
