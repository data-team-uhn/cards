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
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourceOptionsLabelProcessor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceOptionsLabelProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_OPTION_TYPE = "cards:AnswerOption";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/question_9";
    private static final String TEST_OPTION_1_PATH = "/Questionnaires/TestQuestionnaire/question_9/o1";
    private static final String TEST_OPTION_2_PATH = "/Questionnaires/TestQuestionnaire/question_9/o2";
    private static final String TEST_SUBJECT_PATH = "/Subjects/TestRoot";
    private static final String TEST_BRANCH_SUBJECT_PATH = "/Subjects/TestBranch";
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
    private ResourceOptionsLabelProcessor resourceOptionsLabelProcessor;

    @Mock
    private ResourceResolverFactory rrf;

    @Test
    public void getNameTest()
    {
        Assert.assertEquals(NAME, this.resourceOptionsLabelProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.resourceOptionsLabelProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultReturnsTrue()
    {
        Assert.assertEquals(ENABLED, this.resourceOptionsLabelProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void canProcessForFormReturnsTrue()
    {
        Resource form = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        Assert.assertTrue(this.resourceOptionsLabelProcessor.canProcess(form));
    }

    @Test
    public void canProcessForQuestionnaireReturnsTrue()
    {
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        Assert.assertTrue(this.resourceOptionsLabelProcessor.canProcess(questionnaire));
    }

    @Test
    public void canProcessForSubjectReturnsFalse()
    {
        Resource subject = this.context.resourceResolver().getResource(TEST_SUBJECT_PATH);
        Assert.assertFalse(this.resourceOptionsLabelProcessor.canProcess(subject));
    }

    @Test
    public void leaveForAnswerOptionNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = session.getNode(TEST_OPTION_1_PATH);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        subject.setProperty("level", "root");
        when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());

        this.resourceOptionsLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        Assert.assertFalse(jsonObject.isEmpty());
        Assert.assertTrue(jsonObject.containsKey(LABEL_PROPERTY));
        Assert.assertEquals("root", jsonObject.getString(LABEL_PROPERTY));
    }

    @Test
    public void leaveForAnswerOptionNodeWithMultipleValue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = session.getNode(TEST_OPTION_2_PATH);
        when(this.rrf.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());

        this.resourceOptionsLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        Assert.assertFalse(jsonObject.isEmpty());
        Assert.assertTrue(jsonObject.containsKey(LABEL_PROPERTY));
        Assert.assertEquals(2, jsonObject.getJsonArray(LABEL_PROPERTY).size());
        Assert.assertEquals("TestRoot", jsonObject.getJsonArray(LABEL_PROPERTY).getString(0));
        Assert.assertEquals("TestBranch", jsonObject.getJsonArray(LABEL_PROPERTY).getString(1));
    }

    @Test
    public void leaveForAnswerOptionNodeWithoutParentThrowsException() throws RepositoryException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = mock(Node.class);
        when(node.getParent()).thenThrow(new RepositoryException());

        this.resourceOptionsLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertTrue(jsonObject.isEmpty());
    }

    @Test
    public void leaveForAnswerOptionNodeWithValuePropertyThrowsException() throws RepositoryException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = mock(Node.class);
        when(node.getParent()).thenReturn(session.getNode(TEST_QUESTION_PATH));
        when(node.isNodeType(ANSWER_OPTION_TYPE)).thenReturn(true);
        when(node.hasProperty(LABEL_PROPERTY)).thenReturn(false);
        when(node.hasProperty(VALUE_PROPERTY)).thenReturn(true);
        when(node.getProperty(VALUE_PROPERTY)).thenThrow(new RepositoryException());

        Assert.assertThrows(NullPointerException.class,
            () -> this.resourceOptionsLabelProcessor.leave(node, json, mock(Function.class)));
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
                .resource(TEST_BRANCH_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class))
                .commit();

        Session session = this.context.resourceResolver().adaptTo(Session.class);

        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);

        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .commit();
    }
}
