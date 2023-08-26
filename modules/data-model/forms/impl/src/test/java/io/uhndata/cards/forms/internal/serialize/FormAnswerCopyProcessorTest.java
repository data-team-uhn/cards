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
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
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

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link FormAnswerCopyProcessor}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class FormAnswerCopyProcessorTest
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
    private static final String NAME = "answerCopy";
    private static final int PRIORITY = 95;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private FormAnswerCopyProcessor formAnswerCopyProcessor;

    @Mock
    private FormUtils formUtils;

    @Test
    public void getNameTest()
    {
        Assert.assertEquals(NAME, this.formAnswerCopyProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.formAnswerCopyProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultTest()
    {
        Assert.assertTrue(this.formAnswerCopyProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void canProcessForFormResourceReturnsTrue()
    {
        Resource resource = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        Assert.assertTrue(this.formAnswerCopyProcessor.canProcess(resource));
    }

    @Test
    public void canProcessForQuestionResourceReturnsTrue()
    {
        Resource resource = this.context.resourceResolver().getResource(TEST_QUESTION_PATH);
        Assert.assertFalse(this.formAnswerCopyProcessor.canProcess(resource));
    }

    @Test
    public void startForFormResource() throws RepositoryException, NoSuchFieldException, IllegalAccessException
    {
        String path = "/apps/cards/config/CopyAnswers/Questionnaires/TestQuestionnaire";
        Resource resource = mock(Resource.class);
        ValueMap valueMap = mock(ValueMap.class);
        Property property = mock(Property.class);
        Node node = mock(Node.class);
        when(resource.getPath()).thenReturn(TEST_FORM_PATH);
        when(resource.getValueMap()).thenReturn(valueMap);
        when(valueMap.get(Mockito.anyString(), Mockito.any())).thenReturn(property);
        when(property.getNode()).thenReturn(node);
        when(node.getName()).thenReturn(path);

        Resource configuration = mock(Resource.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        when(resource.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.getResource(Mockito.anyString())).thenReturn(configuration);
        when(configuration.adaptTo(Node.class)).thenReturn(mock(Node.class));
        this.formAnswerCopyProcessor.start(resource);

        ThreadLocal<Node> answersToCopy = (ThreadLocal<Node>) getAccessedSuperclassField("answersToCopy");
        Assert.assertNotNull(answersToCopy.get());

        ThreadLocal<String> rootResourcePath = (ThreadLocal<String>) getAccessedSuperclassField("rootResourcePath");
        Assert.assertEquals(TEST_FORM_PATH, rootResourcePath.get());
    }

    @Test
    public void leaveTest() throws RepositoryException, NoSuchFieldException, IllegalAccessException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        ThreadLocal<String> rootResourcePath = (ThreadLocal<String>) getAccessedSuperclassField("rootResourcePath");
        rootResourcePath.set(TEST_FORM_PATH);

        ThreadLocal<Node> answersToCopy = (ThreadLocal<Node>) getAccessedSuperclassField("answersToCopy");
        Node answersToCopyNode = mock(Node.class);
        PropertyIterator questionPropertyIterator = mock(PropertyIterator.class);
        Property questionProperty = mock(Property.class);
        when(answersToCopyNode.getProperties()).thenReturn(questionPropertyIterator);
        when(questionPropertyIterator.hasNext()).thenReturn(true, false);
        when(questionPropertyIterator.nextProperty()).thenReturn(questionProperty);
        when(questionProperty.getType()).thenReturn(PropertyType.REFERENCE);
        when(questionProperty.getName()).thenReturn("question_1");
        when(questionProperty.getNode()).thenReturn(session.getNode(TEST_QUESTION_PATH));
        when(this.formUtils.findAllFormRelatedAnswers(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(session.getNode("/Forms/f1/s1/a1")));
        when(this.formUtils.serializeProperty(Mockito.any())).thenReturn(Json.createValue("2023-01-01"));
        answersToCopy.set(answersToCopyNode);

        JsonObjectBuilder json = Json.createObjectBuilder();
        this.formAnswerCopyProcessor.leave(session.getNode(TEST_FORM_PATH), json, mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertTrue(jsonObject.containsKey("question_1"));
        Assert.assertEquals("2023-01-01", jsonObject.getString("question_1"));
    }

    @Test
    public void end() throws NoSuchFieldException, IllegalAccessException
    {
        ThreadLocal<Node> answersToCopy = (ThreadLocal<Node>) getAccessedSuperclassField("answersToCopy");
        answersToCopy.set(mock(Node.class));

        ThreadLocal<String> rootResourcePath = (ThreadLocal<String>) getAccessedSuperclassField("rootResourcePath");
        rootResourcePath.set(TEST_FORM_PATH);

        this.formAnswerCopyProcessor.end(mock(Resource.class));
        Assert.assertNull(answersToCopy.get());
        Assert.assertNull(rootResourcePath.get());
    }

    @Test
    public void getConfigurationPathReturnsPath() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Resource resource = resourceResolver.getResource(TEST_FORM_PATH);
        String questionnaireTitle = resourceResolver.adaptTo(Session.class).getNode(TEST_QUESTIONNAIRE_PATH).getName();
        String path = this.formAnswerCopyProcessor.getConfigurationPath(resource);
        Assert.assertEquals("Questionnaires/" + questionnaireTitle, path);
    }

    @Test
    public void getConfigurationPathCatchRepositoryExceptionAndReturnsNull() throws RepositoryException
    {
        Resource resource = mock(Resource.class);
        ValueMap valueMap = mock(ValueMap.class);
        Property property = mock(Property.class);
        when(resource.getValueMap()).thenReturn(valueMap);
        when(valueMap.get(Mockito.anyString(), Mockito.any())).thenReturn(property);
        when(property.getNode()).thenThrow(new RepositoryException());
        String path = this.formAnswerCopyProcessor.getConfigurationPath(resource);
        Assert.assertNull(path);
    }

    @Test
    public void getAnswerReturnsAnswerNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node expectedAnswer = session.getNode("/Forms/f1/s1/a1");
        Node source = session.getNode(TEST_FORM_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        when(this.formUtils.findAllFormRelatedAnswers(Mockito.eq(source), Mockito.eq(question), Mockito.any()))
                .thenReturn(List.of(expectedAnswer));
        Node actualAnswer = this.formAnswerCopyProcessor.getAnswer(source, question);
        Assert.assertEquals(expectedAnswer, actualAnswer);
    }

    @Test
    public void getAnswerReturnsNull() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node source = session.getNode(TEST_FORM_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        Assert.assertNull(this.formAnswerCopyProcessor.getAnswer(source, question));
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

    private Object getAccessedSuperclassField(String fieldName) throws NoSuchFieldException, IllegalAccessException
    {
        Field field = this.formAnswerCopyProcessor.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(this.formAnswerCopyProcessor);
    }

}
