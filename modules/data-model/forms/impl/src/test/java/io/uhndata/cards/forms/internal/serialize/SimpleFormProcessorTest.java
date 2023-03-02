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
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

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
 * Unit tests for {@link SimpleFormProcessor}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class SimpleFormProcessorTest
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
    private static final String NAME = "simple";
    private static final int PRIORITY = 50;


    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SimpleFormProcessor simpleFormProcessor;

    @Test
    public void getNameTest()
    {
        Assert.assertEquals(NAME, this.simpleFormProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.simpleFormProcessor.getPriority());
    }

    @Test
    public void canProcessForFormReturnsTrue()
    {
        Resource form = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        Assert.assertTrue(this.simpleFormProcessor.canProcess(form));
    }

    @Test
    public void canProcessForSubjectReturnsFalse()
    {
        Resource subject = this.context.resourceResolver().getResource(TEST_SUBJECT_PATH);
        Assert.assertFalse(this.simpleFormProcessor.canProcess(subject));
    }

    @Test
    public void processPropertyForNullProperty()
    {
        Assert.assertNull(this.simpleFormProcessor.processProperty(
                mock(Node.class), null, mock(JsonValue.class), mock(Function.class)));
    }

    @Test
    public void processPropertyJcrPropertyOfAnswerNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/Forms/f1/s1/a1");
        Property property = node.getProperty("jcr:createdBy");
        JsonValue input = mock(JsonValue.class);
        Assert.assertNull(this.simpleFormProcessor.processProperty(node, property, input, mock(Function.class)));
    }

    @Test
    public void processPropertyTypePropertyOfSubjectNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_SUBJECT_PATH);
        Property property = node.getProperty("type");
        JsonValue input = mock(JsonValue.class);
        JsonObject jsonObject = mock(JsonObject.class);
        when(input.asJsonObject()).thenReturn(jsonObject);
        when(jsonObject.get("label")).thenReturn(Json.createValue("Root"));
        Assert.assertEquals(Json.createValue("Root"),
                this.simpleFormProcessor.processProperty(node, property, input, mock(Function.class)));
    }

    @Test
    public void processPropertyForQuestionNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_QUESTION_PATH);
        Property property = node.getProperty("dataType");
        JsonValue input = mock(JsonValue.class);
        Assert.assertNull(this.simpleFormProcessor.processProperty(node, property, input, mock(Function.class)));
    }

    @Test
    public void processPropertyThrowsRepositoryException() throws RepositoryException
    {
        Property property = mock(Property.class);
        JsonValue input = mock(JsonValue.class);
        when(property.getName()).thenThrow(new RepositoryException());
        Assert.assertEquals(input,
                this.simpleFormProcessor.processProperty(mock(Node.class), property, input, mock(Function.class)));
    }

    @Test
    public void processChildForQuestionNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_QUESTION_PATH);
        JsonValue input = mock(JsonValue.class);
        Assert.assertNull(this.simpleFormProcessor.processChild(node, mock(Node.class), input, mock(Function.class)));
    }

    @Test
    public void processChildForAnswerSectionNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/Forms/f1/s1");
        JsonValue input = mock(JsonValue.class);
        Assert.assertEquals(input,
                this.simpleFormProcessor.processChild(node, mock(Node.class), input, mock(Function.class)));
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
