/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.serialize.internal;

import java.util.List;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DereferenceProcessor}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class DereferenceProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String FORM_TYPE = "cards:Form";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String TYPE_PROPERTY = "type";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String NAME = "dereference";
    private static final int PRIORITY = 10;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private DereferenceProcessor dereferenceProcessor;

    @Test
    public void getNameReturnDereference()
    {
        assertEquals(NAME, this.dereferenceProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        assertEquals(PRIORITY, this.dereferenceProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultTest()
    {
        assertTrue(this.dereferenceProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void processPropertyForMultiValueReferenceProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Property property = node.getProperty("relatedSubjects");
        JsonValue json = Json.createValue("relatedSubjects");
        JsonValue jsonValue = this.dereferenceProcessor.processProperty(node, property, json, this::serializeNode);
        assertNotNull(jsonValue);
        assertTrue(jsonValue instanceof JsonArray);
        assertEquals(1, ((JsonArray) jsonValue).size());
        assertEquals(Json.createValue("r1"), ((JsonArray) jsonValue).get(0));
    }

    @Test
    public void processPropertyForMultiValueStringProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Property property = node.getProperty("statusFlags");
        JsonValue json = Json.createValue("statusFlags");
        JsonValue jsonValue = this.dereferenceProcessor.processProperty(node, property, json, this::serializeNode);
        assertNotNull(jsonValue);
        assertEquals(json, jsonValue);
    }

    @Test
    public void processPropertyForMultiValuePathProperty() throws RepositoryException
    {
        Node parent = mock(Node.class);
        Property property = mock(Property.class);
        Value value = mock(Value.class);
        Node valuePathNode = mock(Node.class);
        String valueName = "valuePath";

        when(property.isMultiple()).thenReturn(true);
        when(property.getName()).thenReturn("paths");
        when(property.getType()).thenReturn(PropertyType.PATH);
        when(property.getValues()).thenReturn(new Value[] {value});
        when(value.getString()).thenReturn(valueName);
        when(property.getParent()).thenReturn(parent);
        when(parent.getNode(valueName)).thenReturn(valuePathNode);
        when(valuePathNode.getName()).thenReturn(valueName);

        JsonValue jsonValue = this.dereferenceProcessor.processProperty(mock(Node.class), property,
                mock(JsonValue.class), this::serializeNode);
        assertNotNull(jsonValue);
        assertTrue(jsonValue instanceof JsonArray);
        assertEquals(1, ((JsonArray) jsonValue).size());
        assertEquals(Json.createValue(valueName), ((JsonArray) jsonValue).get(0));
    }

    @Test
    public void processPropertyForMultiValuePathPropertyCatchesRepositoryException() throws RepositoryException
    {
        Property property = mock(Property.class);
        Value value = mock(Value.class);
        String valueName = "valuePath";

        when(property.isMultiple()).thenReturn(true);
        when(property.getName()).thenReturn("paths");
        when(property.getType()).thenReturn(PropertyType.PATH);
        when(property.getValues()).thenReturn(new Value[] {value});
        when(value.getString()).thenReturn("/" + valueName);
        when(property.getSession()).thenThrow(new RepositoryException());

        JsonValue jsonValue = this.dereferenceProcessor.processProperty(mock(Node.class), property,
                mock(JsonValue.class), this::serializeNode);
        assertNotNull(jsonValue);
        assertTrue(jsonValue instanceof JsonArray);
        assertEquals(1, ((JsonArray) jsonValue).size());
        assertEquals(Json.createValue("/" + valueName), ((JsonArray) jsonValue).get(0));
    }

    @Test
    public void processPropertyCatchesRepositoryExceptionWhileSerializationMultiValueProperty()
            throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.isMultiple()).thenReturn(true);
        when(property.getName()).thenReturn("relatedSubjects");
        when(property.getType()).thenReturn(PropertyType.REFERENCE);
        when(property.getValues()).thenReturn(new Value[] {mock(Value.class)});
        when(property.getSession()).thenThrow(new RepositoryException());
        JsonValue json = Json.createValue("relatedSubjects");
        JsonValue jsonValue = this.dereferenceProcessor.processProperty(mock(Node.class), property, json,
                mock(Function.class));
        assertNotNull(jsonValue);
        assertEquals(json, jsonValue);
    }

    @Test
    public void processPropertyForSingleValueReferenceProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Property property = node.getProperty(QUESTIONNAIRE_PROPERTY);
        JsonValue json = Json.createValue(QUESTIONNAIRE_PROPERTY);
        JsonValue jsonValue = this.dereferenceProcessor.processProperty(node, property, json, this::serializeNode);
        assertNotNull(jsonValue);
        assertEquals(Json.createValue("TestSerializableQuestionnaire"), jsonValue);
    }

    @Test
    public void processPropertyForSingleValueStringProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Property property = node.getProperty(NODE_TYPE);
        JsonValue json = Json.createValue(FORM_TYPE);
        JsonValue jsonValue = this.dereferenceProcessor.processProperty(node, property, json, this::serializeNode);
        assertNotNull(jsonValue);
        assertEquals(json, jsonValue);
    }

    @Test
    public void processPropertyCatchesRepositoryExceptionWhileSerializationSingleValueProperty()
            throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.isMultiple()).thenReturn(false);
        when(property.getType()).thenReturn(PropertyType.REFERENCE);
        when(property.getNode()).thenThrow(new RepositoryException());
        JsonValue json = Json.createValue(QUESTIONNAIRE_PROPERTY);
        JsonValue jsonValue = this.dereferenceProcessor.processProperty(mock(Node.class), property, json,
                mock(Function.class));
        assertNotNull(jsonValue);
        assertEquals(json, jsonValue);
    }

    @Test
    public void processPropertyForJcrSingleValueProperty()
            throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Property property = node.getProperty("jcr:baseVersion");
        JsonValue json = Json.createValue(FORM_TYPE);
        JsonValue jsonValue = this.dereferenceProcessor.processProperty(node, property, json, this::serializeNode);
        assertNotNull(jsonValue);
    }

    @Test
    public void processPropertyCatchesRepositoryException() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.isMultiple()).thenThrow(new RepositoryException());
        JsonValue json = Json.createValue(FORM_TYPE);
        JsonValue jsonValue = this.dereferenceProcessor.processProperty(mock(Node.class), property, json,
                mock(Function.class));
        assertNotNull(jsonValue);
        assertEquals(json, jsonValue);
    }

    @Before
    public void setUp() throws RepositoryException
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
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root subject1")
                .commit();
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTIONNAIRE_PATH + "/question_1");

        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, subject,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        "relatedSubjects", List.of(subject).toArray())
                .resource(TEST_FORM_PATH + "/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, question)
                .commit();
    }

    private JsonValue serializeNode(Node node)
    {
        try {
            return Json.createValue(node.getName());
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }
}
