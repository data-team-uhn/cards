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

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link BooleanLabelProcessor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class BooleanLabelProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_BOOLEAN_TYPE = "cards:BooleanAnswer";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/question_5";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String VALUE_PROPERTY = "value";
    private static final String DISPLAYED_VALUE_PROPERTY = "displayedValue";
    private static final String NAME = "labels";
    private static final int PRIORITY = 75;
    private static final boolean ENABLED = true;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private BooleanLabelProcessor booleanLabelProcessor;


    @Test
    public void getNameTest()
    {
        Assert.assertEquals(NAME, this.booleanLabelProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.booleanLabelProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultReturnsTrue()
    {
        Assert.assertEquals(ENABLED, this.booleanLabelProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void canProcessForFormReturnsTrue()
    {
        Resource form = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        Assert.assertTrue(this.booleanLabelProcessor.canProcess(form));
    }

    @Test
    public void canProcessForQuestionnaireReturnsFalse()
    {
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        Assert.assertFalse(this.booleanLabelProcessor.canProcess(questionnaire));
    }

    @Test
    public void leaveForBooleanAnswerNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = session.getNode("/Forms/f1/a1");
        Node question = session.getNode(TEST_QUESTION_PATH);
        question.setProperty("yesLabel", "yes");
        question.setProperty("noLabel", "yes");
        question.setProperty("unknownLabel", "unknown");

        this.booleanLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        Assert.assertFalse(jsonObject.isEmpty());
        Assert.assertTrue(jsonObject.containsKey(DISPLAYED_VALUE_PROPERTY));
        Assert.assertEquals("yes", jsonObject.getString(DISPLAYED_VALUE_PROPERTY));
    }

    @Test
    public void leaveForBooleanAnswerNodeWithValuePropertyThrowsException() throws RepositoryException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = mock(Node.class);
        when(node.isNodeType(ANSWER_BOOLEAN_TYPE)).thenReturn(true);
        when(node.hasProperty(VALUE_PROPERTY)).thenReturn(true);
        when(node.hasProperty(QUESTION_PROPERTY)).thenThrow(new RepositoryException());
        when(node.getProperty(VALUE_PROPERTY)).thenThrow(new RepositoryException());

        this.booleanLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertTrue(jsonObject.isEmpty());
    }

    @Test
    public void leaveForBooleanAnswerNodeWithoutValuePropertyThrowsException() throws RepositoryException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = mock(Node.class);
        when(node.isNodeType(ANSWER_BOOLEAN_TYPE)).thenReturn(true);
        when(node.hasProperty(VALUE_PROPERTY)).thenThrow(new RepositoryException());

        this.booleanLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertTrue(jsonObject.isEmpty());
    }

    @Test
    public void leaveForNotBooleanAnswerNodeThrowsException() throws RepositoryException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = mock(Node.class);
        when(node.isNodeType(ANSWER_BOOLEAN_TYPE)).thenThrow(new RepositoryException());

        this.booleanLabelProcessor.leave(node, json, mock(Function.class));
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
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f1/a1",
                        NODE_TYPE, ANSWER_BOOLEAN_TYPE,
                        QUESTION_PROPERTY, question,
                        VALUE_PROPERTY, 1)
                .commit();
    }

}
