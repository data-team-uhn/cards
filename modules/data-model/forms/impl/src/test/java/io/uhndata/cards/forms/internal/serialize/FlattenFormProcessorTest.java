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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
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

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link FlattenFormProcessor}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class FlattenFormProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_4";
    private static final String TEST_QUESTION_2_PATH = "/Questionnaires/TestQuestionnaire/question_5";
    private static final String TEST_SECTION_PATH = "/Questionnaires/TestQuestionnaire/section_1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String NAME = "flatten";
    private static final int PRIORITY = 100;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private FlattenFormProcessor flattenFormProcessor;

    @Test
    public void getNameTest()
    {
        Assert.assertEquals(NAME, this.flattenFormProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.flattenFormProcessor.getPriority());
    }

    @Test
    public void canProcessForFormReturnsTrue()
    {
        Resource form = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        Assert.assertTrue(this.flattenFormProcessor.canProcess(form));
    }

    @Test
    public void canProcessForQuestionnaireReturnsFalse()
    {
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        Assert.assertFalse(this.flattenFormProcessor.canProcess(questionnaire));
    }

    @Test
    public void processChildForNullInput()
    {
        Assert.assertNull(this.flattenFormProcessor.processChild(
                mock(Node.class), mock(Node.class), null, mock(Function.class)));
    }

    @Test
    public void processChildForAnswerChildNode() throws NoSuchFieldException, IllegalAccessException
    {
        Node child = this.context.resourceResolver().getResource("/Forms/f1/s1/a1").adaptTo(Node.class);
        JsonValue input = mock(JsonValue.class);
        when(input.asJsonObject()).thenReturn(Json.createObjectBuilder().build());

        JsonValue jsonValue = this.flattenFormProcessor.processChild(
                mock(Node.class), child, input, mock(Function.class));
        ThreadLocal<Map<String, JsonObject>> childrenJsons =
                (ThreadLocal<Map<String, JsonObject>>) getAccessedField("childrenJsons");
        Assert.assertNull(jsonValue);
        Assert.assertEquals(1, childrenJsons.get().size());
        Assert.assertTrue(childrenJsons.get().containsKey("a1"));
    }

    @Test
    public void processChildForAnswerSectionChildNode()
    {
        Node child = this.context.resourceResolver().getResource("/Forms/f1/s1").adaptTo(Node.class);
        Assert.assertNull(this.flattenFormProcessor.processChild(
                mock(Node.class), child, mock(JsonValue.class), mock(Function.class)));
    }

    @Test
    public void processChildForQuestionChildNode()
    {
        Node child = this.context.resourceResolver().getResource(TEST_QUESTION_PATH).adaptTo(Node.class);
        Assert.assertNotNull(this.flattenFormProcessor.processChild(
                mock(Node.class), child, mock(JsonValue.class), mock(Function.class)));
    }

    @Test
    public void leaveForFormNode() throws NoSuchFieldException, IllegalAccessException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class);
        ThreadLocal<Map<String, JsonObject>> childrenJsons =
                (ThreadLocal<Map<String, JsonObject>>) getAccessedField("childrenJsons");
        childrenJsons.set(Map.of(
                "a1", Json.createObjectBuilder().build(),
                "a2", Json.createObjectBuilder().build()));
        this.flattenFormProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertFalse(jsonObject.isEmpty());
        Assert.assertTrue(jsonObject.containsKey("a1"));
        Assert.assertTrue(jsonObject.containsKey("a2"));
    }

    @Test
    public void leaveForSubjectNode() throws NoSuchFieldException, IllegalAccessException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = this.context.resourceResolver().getResource(TEST_SUBJECT_PATH).adaptTo(Node.class);
        ThreadLocal<Map<String, JsonObject>> childrenJsons =
                (ThreadLocal<Map<String, JsonObject>>) getAccessedField("childrenJsons");
        childrenJsons.set(Map.of(
                "a1", Json.createObjectBuilder().build(),
                "a2", Json.createObjectBuilder().build()));
        this.flattenFormProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertTrue(jsonObject.isEmpty());
    }

    @Test
    public void endCleansChildrenJsons() throws NoSuchFieldException, IllegalAccessException
    {
        ThreadLocal<Map<String, JsonObject>> childrenJsons =
                (ThreadLocal<Map<String, JsonObject>>) getAccessedField("childrenJsons");
        childrenJsons.set(Map.of(
                "a1", Json.createObjectBuilder().build(),
                "a2", Json.createObjectBuilder().build()));
        this.flattenFormProcessor.end(mock(Resource.class));
        Assert.assertTrue(childrenJsons.get().isEmpty());
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
        Node question2 = session.getNode(TEST_QUESTION_2_PATH);

        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f1/s1", NODE_TYPE, ANSWER_SECTION_TYPE, SECTION_PROPERTY, section)
                .resource("/Forms/f1/s1/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
                .resource("/Forms/f1/a2", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question2)
                .commit();
    }

    private Object getAccessedField(String fieldName) throws NoSuchFieldException, IllegalAccessException
    {
        Field field = this.flattenFormProcessor.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(this.flattenFormProcessor);
    }

}
