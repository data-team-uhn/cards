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
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
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
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BareFormProcessor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class BareFormProcessorTest
{

    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_1";
    private static final String TEST_QUESTION_2_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_2";
    private static final String TEST_SECTION_PATH = "/Questionnaires/TestQuestionnaire/section_1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String NAME = "bare";
    private static final int PRIORITY = 95;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private BareFormProcessor bareFormProcessor;

    @Test
    public void getNameReturnBare()
    {
        Assert.assertEquals(NAME, this.bareFormProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.bareFormProcessor.getPriority());
    }

    @Test
    public void canProcessReturnTrue()
    {
        Assert.assertTrue(this.bareFormProcessor.canProcess(
                this.context.resourceResolver().getResource(TEST_FORM_PATH)));
    }

    @Test
    public void canProcessReturnFalse()
    {
        Assert.assertFalse(this.bareFormProcessor.canProcess(
                this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH)));
    }

    @Test
    public void processPropertyForFormNodeAndQuestionnaireProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Property property = node.getProperty("questionnaire");
        JsonValue input = Json.createValue(node.getProperty(NODE_TYPE).getString());
        JsonValue jsonValueActual =
                this.bareFormProcessor.processProperty(node, property, input, Mockito.mock(Function.class));
        Assert.assertEquals(Json.createValue("Test Questionnaire"), jsonValueActual);
    }

    @Test
    public void processPropertyForFormNodeAndSubjectProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Property property = node.getProperty("subject");
        JsonValue input = Json.createValue(node.getProperty(NODE_TYPE).getString());
        JsonValue jsonValueActual =
                this.bareFormProcessor.processProperty(node, property, input, Mockito.mock(Function.class));
        Assert.assertEquals(Json.createValue("Test"), jsonValueActual);
    }

    @Test
    public void processPropertyForSectionNodeAndSectionProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/Forms/f1/s1");
        Property property = node.getProperty(SECTION_PROPERTY);
        JsonValue input = Json.createValue(node.getProperty(NODE_TYPE).getString());
        JsonValue jsonValueActual =
                this.bareFormProcessor.processProperty(node, property, input, Mockito.mock(Function.class));
        Assert.assertEquals(Json.createValue("Section 1"), jsonValueActual);
    }

    @Test
    public void processPropertyForAnswerNodeAndQuestionProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/Forms/f1/s1/a1");
        Property property = node.getProperty("question");
        JsonValue input = Json.createValue(node.getProperty(NODE_TYPE).getString());
        JsonValue jsonValueActual =
                this.bareFormProcessor.processProperty(node, property, input, Mockito.mock(Function.class));
        Assert.assertEquals(Json.createValue("Date Question"), jsonValueActual);
    }

    @Test
    public void processPropertyForFormNodeAndStatusFlagProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_QUESTION_PATH);
        node.setProperty("statusFlags", "completed");
        Property property = node.getProperty("statusFlags");
        JsonValue input = Json.createValue(node.getProperty(NODE_TYPE).getString());
        Assert.assertNull(this.bareFormProcessor.processProperty(node, property, input, Mockito.mock(Function.class)));
    }

    @Test
    public void processPropertyForNullValue()
    {
        Node node = Mockito.mock(Node.class);
        JsonValue input = Mockito.mock(JsonValue.class);
        Assert.assertNull(this.bareFormProcessor.processProperty(node, null, input, Mockito.mock(Function.class)));
    }

    @Test
    public void processChildForSectionNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_SECTION_PATH);
        Node child = Mockito.mock(Node.class);
        JsonValue input = Mockito.mock(JsonValue.class);
        Assert.assertNull(this.bareFormProcessor.processChild(node, child, input, Mockito.mock(Function.class)));
    }

    @Test
    public void processChildForAnswerSectionChild() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Node child = session.getNode("/Forms/f1/s1");
        JsonValue input = Mockito.mock(JsonValue.class);
        when(input.asJsonObject()).thenReturn(Mockito.mock(JsonObject.class));
        Assert.assertNull(this.bareFormProcessor.processChild(node, child, input, Mockito.mock(Function.class)));
    }

    @Test
    public void processChildForFormNodeAndSubjectChild() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Node child = session.getNode(TEST_SUBJECT_PATH);
        JsonValue input = Mockito.mock(JsonValue.class);
        Assert.assertNotNull(this.bareFormProcessor.processChild(node, child, input, Mockito.mock(Function.class)));
    }

    @Test
    public void processChildForNullValue()
    {
        Node node = Mockito.mock(Node.class);
        Node child = Mockito.mock(Node.class);
        Assert.assertNull(this.bareFormProcessor.processChild(node, child, null, Mockito.mock(Function.class)));
    }

    @Test
    public void leaveTest() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        JsonObjectBuilder json = Json.createObjectBuilder();

        this.bareFormProcessor.leave(node, json, Mockito.mock(Function.class));
        Assert.assertEquals(0, json.build().size());
    }

    @Test
    public void leaveTestForFormWithNotRecurrentSection() throws RepositoryException, NoSuchFieldException,
            IllegalAccessException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node form = session.getNode(TEST_FORM_PATH);
        Node answerSection = session.getNode("/Forms/f1/s1");
        JsonObjectBuilder json = Json.createObjectBuilder();

        Field childrenJsonsField = this.bareFormProcessor.getClass().getDeclaredField("childrenJsons");
        childrenJsonsField.setAccessible(true);
        ThreadLocal<Map<String, JsonObject>> childrenJsons =
                (ThreadLocal<Map<String, JsonObject>>) childrenJsonsField.get(this.bareFormProcessor);

        childrenJsons.get().put(
                answerSection.getIdentifier(), Json.createObjectBuilder()
                    .add(SECTION_PROPERTY,
                        Json.createValue(session.getNode(TEST_SECTION_PATH).getIdentifier()))
                    .add(NODE_TYPE, ANSWER_SECTION_TYPE).build());

        this.bareFormProcessor.leave(form, json, Mockito.mock(Function.class));
        Assert.assertEquals(1, json.build().size());
        Assert.assertTrue(childrenJsons.get().isEmpty());
    }

    @Test
    public void leaveTestForFormWithRecurrentSection() throws RepositoryException, NoSuchFieldException,
            IllegalAccessException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node form = session.getNode(TEST_FORM_PATH);
        session.getNode(TEST_SECTION_PATH).setProperty("recurrent", true);
        Node answerSection = session.getNode("/Forms/f1/s1");
        JsonObjectBuilder json = Json.createObjectBuilder();

        Field childrenJsonsField = this.bareFormProcessor.getClass().getDeclaredField("childrenJsons");
        childrenJsonsField.setAccessible(true);
        ThreadLocal<Map<String, JsonObject>> childrenJsons =
                (ThreadLocal<Map<String, JsonObject>>) childrenJsonsField.get(this.bareFormProcessor);

        childrenJsons.get().put(
                answerSection.getIdentifier(), Json.createObjectBuilder()
                    .add(SECTION_PROPERTY, Json.createValue(session.getNode(TEST_SECTION_PATH).getIdentifier()))
                    .add(NODE_TYPE, ANSWER_SECTION_TYPE).build());

        this.bareFormProcessor.leave(form, json, Mockito.mock(Function.class));
        Assert.assertEquals(1, json.build().size());
        Assert.assertTrue(childrenJsons.get().isEmpty());
    }

    @Test
    public void endTestForSection() throws RepositoryException, NoSuchFieldException, IllegalAccessException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String answerIdentifier = session.getNode("/Forms/f1/s1/a1").getIdentifier();
        String questionIdentifier = session.getNode(TEST_QUESTION_PATH).getIdentifier();

        Field childrenJsonsField = this.bareFormProcessor.getClass().getDeclaredField("childrenJsons");
        childrenJsonsField.setAccessible(true);
        ThreadLocal<Map<String, JsonObject>> childrenJsons =
                (ThreadLocal<Map<String, JsonObject>>) childrenJsonsField.get(this.bareFormProcessor);

        Field questionNamesField = this.bareFormProcessor.getClass().getDeclaredField("questionNames");
        questionNamesField.setAccessible(true);
        ThreadLocal<Map<String, String>> questionNames =
                (ThreadLocal<Map<String, String>>) questionNamesField.get(this.bareFormProcessor);

        childrenJsons.get().put(
                answerIdentifier, Json.createObjectBuilder()
                        .add(QUESTION_PROPERTY, Json.createValue(questionIdentifier))
                        .add(NODE_TYPE, ANSWER_TYPE).build());

        questionNames.get().put(answerIdentifier, "question_1");

        this.bareFormProcessor.end(Mockito.mock(Resource.class));
        Assert.assertTrue(childrenJsons.get().isEmpty());
        Assert.assertTrue(questionNames.get().isEmpty());
    }

    @Test
    public void leaveTestForSection() throws RepositoryException, NoSuchFieldException, IllegalAccessException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node section = session.getNode("/Forms/f1/s1");
        String answerIdentifier = session.getNode("/Forms/f1/s1/a1").getIdentifier();
        String questionIdentifier = session.getNode(TEST_QUESTION_PATH).getIdentifier();
        JsonObjectBuilder json = Json.createObjectBuilder();
        Field childrenJsonsField = this.bareFormProcessor.getClass().getDeclaredField("childrenJsons");
        Field questionNamesField = this.bareFormProcessor.getClass().getDeclaredField("questionNames");
        childrenJsonsField.setAccessible(true);
        ThreadLocal<Map<String, JsonObject>> childrenJsons =
                (ThreadLocal<Map<String, JsonObject>>) childrenJsonsField.get(this.bareFormProcessor);
        questionNamesField.setAccessible(true);
        ThreadLocal<Map<String, String>> questionNames =
                (ThreadLocal<Map<String, String>>) questionNamesField.get(this.bareFormProcessor);

        childrenJsons.get().put(answerIdentifier,
                Json.createObjectBuilder()
                        .add(QUESTION_PROPERTY, Json.createValue(questionIdentifier))
                        .add(NODE_TYPE, ANSWER_TYPE).build());
        questionNames.get().put(answerIdentifier, "question_1");

        this.bareFormProcessor.leave(section, json, Mockito.mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertEquals(1, jsonObject.size());
        Assert.assertTrue(jsonObject.containsKey("question_1"));
        Assert.assertTrue(childrenJsons.get().isEmpty());
        Assert.assertTrue(questionNames.get().isEmpty());
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
                .resource(TEST_SUBJECT_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        "type", this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
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
                        SUBJECT_PROPERTY, subject)
                .resource("/Forms/f1/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, section)
                .resource("/Forms/f1/s1/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, question)
                .resource("/Forms/f1/s1/a2",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, question2)
                .commit();
    }
}
