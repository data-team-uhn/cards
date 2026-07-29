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

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BareProcessor}.
 *
 * @version $Id$
 */
public class BareProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String RESOURCE_TYPE = "sling:resourceType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String FORM_TYPE = "cards:Form";
    private static final String ANSWER_TYPE = "cards:TextAnswer";
    private static final String TYPE_PROPERTY = "type";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String NAME = "bare";
    private static final int PRIORITY = 90;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private final BareProcessor bareProcessor = new BareProcessor();

    @SuppressWarnings("unchecked")
    private final ThreadLocal<Integer> depth = mock(ThreadLocal.class);

    @Test
    public void getNameReturnsBare()
    {
        assertEquals(NAME, this.bareProcessor.getName());
    }

    @Test
    public void getPriorityReturnsNinety()
    {
        assertEquals(PRIORITY, this.bareProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultReturnsFalse()
    {
        assertFalse(this.bareProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void getDescriptionIsNotEmpty()
    {
        assertFalse(this.bareProcessor.getDescription().isEmpty());
    }

    @Test
    public void startResetsDepth()
    {
        this.bareProcessor.start(mock(Resource.class));
        verify(this.depth).set(0);
    }

    @Test
    public void depthStartsAtZero() throws RepositoryException
    {
        // A pristine processor, without the mocked depth counter, to check the counter's initial value
        BareProcessor processor = new BareProcessor();
        Node node = mock(Node.class);
        processor.enter(node, Json.createObjectBuilder(), n -> JsonValue.NULL);

        // A balanced enter+leave returns to depth 0, where the root node's metadata is looked up
        JsonObjectBuilder json = Json.createObjectBuilder();
        processor.leave(node, json, n -> JsonValue.NULL);
        verify(node).hasProperty("jcr:created");
        assertTrue(json.build().isEmpty());
    }

    @Test
    public void leaveWithoutResourceChildDoesNotAddContent() throws RepositoryException
    {
        Node node = mock(Node.class);
        when(this.depth.get()).thenReturn(2, 1);

        NodeIterator iterator = mock(NodeIterator.class);
        Node child = mock(Node.class);
        when(node.isNodeType("nt:file")).thenReturn(true);
        when(node.getNodes()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.nextNode()).thenReturn(child);
        when(child.isNodeType("nt:resource")).thenReturn(false);

        JsonObjectBuilder json = Json.createObjectBuilder();
        this.bareProcessor.leave(node, json, n -> JsonValue.NULL);
        assertFalse(json.build().containsKey("content"));
    }

    @Test
    public void enterIncrementsDepth()
    {
        when(this.depth.get()).thenReturn(0);
        this.bareProcessor.enter(mock(Node.class), mock(JsonObjectBuilder.class), n -> JsonValue.NULL);
        verify(this.depth).get();
        verify(this.depth).set(1);
    }

    @Test
    public void processPropertyForNullProperty()
    {
        Node node = this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class);

        JsonValue jsonValue = this.bareProcessor.processProperty(node, null, mock(JsonValue.class),
                n -> JsonValue.NULL);
        assertNull(jsonValue);
    }

    @Test
    public void processPropertyCatchesRepositoryExceptionReturnsInputValue() throws RepositoryException
    {
        Node node = this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class);

        Property property = mock(Property.class);
        when(property.getName()).thenThrow(new RepositoryException());
        JsonString value = Json.createValue(node.getProperty(QUESTIONNAIRE_PROPERTY).getString());
        JsonValue jsonValue = this.bareProcessor.processProperty(node, property, value, n -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(value, jsonValue);
    }

    @Test
    public void processPropertyForQuestionnairePropertyReturnsInputValue() throws RepositoryException
    {
        Node node = this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class);

        Property property = node.getProperty(QUESTIONNAIRE_PROPERTY);
        JsonString input = Json.createValue(property.getString());
        JsonValue jsonValue = this.bareProcessor.processProperty(node, property, input, n -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

    @Test
    public void processPropertyForJcrPropertyReturnsNull() throws RepositoryException
    {
        Node node = this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class);

        Property property = node.getProperty(NODE_TYPE);
        JsonValue jsonValue = this.bareProcessor.processProperty(node, property, Json.createValue(property.getString()),
                n -> JsonValue.NULL);
        assertNull(jsonValue);
    }

    @Test
    public void processPropertyForSlingPropertyReturnsNull() throws RepositoryException
    {
        Node node = this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class);

        Property property = node.getProperty(RESOURCE_TYPE);
        JsonValue jsonValue = this.bareProcessor.processProperty(node, property, Json.createValue(property.getString()),
                n -> JsonValue.NULL);
        assertNull(jsonValue);
    }

    @Test
    public void processPropertyForFormPropertyReturnsNull() throws RepositoryException
    {
        Node node = this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class);
        node.setProperty("form", node.getIdentifier(), Type.REFERENCE.tag());

        Property property = node.getProperty("form");
        JsonValue jsonValue = this.bareProcessor.processProperty(node, property, Json.createValue(property.getString()),
                n -> JsonValue.NULL);
        assertNull(jsonValue);
    }

    @Test
    public void processChildForJcrChildReturnsNull() throws RepositoryException
    {
        Node node = this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class);
        Node child = mock(Node.class);
        when(child.getName()).thenReturn("jcr:name");

        JsonValue jsonValue = this.bareProcessor.processChild(node, child, mock(JsonValue.class), n -> JsonValue.NULL);
        assertNull(jsonValue);
    }

    @Test
    public void processChildCatchesRepositoryExceptionReturnsInputValue() throws RepositoryException
    {
        Node node = this.context.resourceResolver().getResource(TEST_FORM_PATH).adaptTo(Node.class);
        Node child = mock(Node.class);
        when(child.getName()).thenThrow(new RepositoryException());

        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.bareProcessor.processChild(node, child, input, n -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

    @Test
    public void processChildForAnswerChildReturnsInputValue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        Node child = session.getNode(TEST_FORM_PATH + "/a1");

        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.bareProcessor.processChild(node, child, input, n -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

    @Test
    public void leaveSerializesCreatedAndLastModifiedAndFileContent() throws RepositoryException, ParseException
    {
        Node node = mock(Node.class);
        when(this.depth.get()).thenReturn(1, 0);

        Calendar date = Calendar.getInstance();
        date.set(2023, Calendar.JANUARY, 1);
        mockCreatedAndLastModifiedDate(node, date);
        mockFileContent(node, getMockedDataProperty());

        JsonObjectBuilder json = Json.createObjectBuilder();
        this.bareProcessor.leave(node, json, n -> JsonValue.NULL);
        JsonObject jsonObject = json.build();

        verify(this.depth, times(3)).get();
        verify(this.depth).set(0);

        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

        assertNotNull(jsonObject);
        assertTrue(jsonObject.containsKey("created"));
        assertEquals(date.getTime(), format.parse(jsonObject.getString("created")));
        assertTrue(jsonObject.containsKey("lastModified"));
        assertEquals(date.getTime(), format.parse(jsonObject.getString("lastModified")));
        assertTrue(jsonObject.containsKey("content"));
    }

    @Test
    public void leaveWithNonRootNodeDoesNotAddMetadata() throws RepositoryException
    {
        Node node = mock(Node.class);
        when(this.depth.get()).thenReturn(2, 1);

        // No date stubbing: at non-root depth the processor must not even look at the date properties
        mockFileContent(node, getMockedDataProperty());

        JsonObjectBuilder json = Json.createObjectBuilder();
        this.bareProcessor.leave(node, json, n -> JsonValue.NULL);
        JsonObject jsonObject = json.build();

        verify(this.depth, times(3)).get();
        verify(this.depth).set(1);

        assertNotNull(jsonObject);
        assertFalse(jsonObject.containsKey("created"));
        assertFalse(jsonObject.containsKey("lastModified"));
        assertTrue(jsonObject.containsKey("content"));
    }

    @Test
    public void leaveCatchesIOException() throws RepositoryException, IOException
    {
        Node node = mock(Node.class);
        when(this.depth.get()).thenReturn(1, 0);

        Calendar date = Calendar.getInstance();
        date.set(2023, Calendar.JANUARY, 1);

        // mocking data property with closed InputStream
        Property dataProperty = mock(Property.class);
        Binary dataBinary = mock(Binary.class);
        InputStream stream = InputStream.nullInputStream();
        stream.close();
        when(dataProperty.getBinary()).thenReturn(dataBinary);
        when(dataBinary.getStream()).thenReturn(stream);

        mockCreatedAndLastModifiedDate(node, date);
        mockFileContent(node, dataProperty);

        JsonObjectBuilder json = Json.createObjectBuilder();
        this.bareProcessor.leave(node, json, n -> JsonValue.NULL);
        JsonObject jsonObject = json.build();

        verify(this.depth, times(3)).get();
        verify(this.depth).set(0);

        assertNotNull(jsonObject);
        assertTrue(jsonObject.containsKey("created"));
        assertTrue(jsonObject.containsKey("lastModified"));
        assertTrue(jsonObject.containsKey("content"));
    }

    @Test
    public void leaveCatchesRepositoryException() throws RepositoryException
    {
        Node node = mock(Node.class);

        when(node.hasProperty("jcr:created")).thenThrow(new RepositoryException());
        when(node.hasProperty("jcr:lastModified")).thenThrow(new RepositoryException());
        when(node.isNodeType("nt:file")).thenThrow(new RepositoryException());
        when(this.depth.get()).thenReturn(1, 0);

        JsonObjectBuilder json = Json.createObjectBuilder();
        this.bareProcessor.leave(node, json, n -> JsonValue.NULL);
        JsonObject jsonObject = json.build();

        verify(this.depth, times(3)).get();
        verify(this.depth).set(0);

        assertNotNull(jsonObject);
        assertFalse(jsonObject.containsKey("created"));
        assertFalse(jsonObject.containsKey("lastModified"));
        assertFalse(jsonObject.containsKey("content"));
    }

    @Before
    public void setUp() throws IllegalAccessException, RepositoryException
    {
        FieldUtils.writeField(this.bareProcessor, "depth", this.depth, true);
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
                        QUESTIONNAIRE_PROPERTY, questionnaire)
                .resource(TEST_FORM_PATH + "/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, question)
                .commit();
    }

    private void mockFileContent(Node node, Property dataProperty) throws RepositoryException
    {
        NodeIterator iterator = mock(NodeIterator.class);
        Node child = mock(Node.class);
        when(node.isNodeType("nt:file")).thenReturn(true);
        when(node.getNodes()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.nextNode()).thenReturn(child);
        when(child.isNodeType("nt:resource")).thenReturn(true);

        // data property
        when(child.hasProperty("jcr:data")).thenReturn(true);
        when(child.getProperty("jcr:data")).thenReturn(dataProperty);
    }

    private Property getMockedDataProperty() throws RepositoryException
    {
        Property dataProperty = mock(Property.class);
        Binary dataBinary = mock(Binary.class);
        when(dataProperty.getBinary()).thenReturn(dataBinary);
        when(dataBinary.getStream()).thenReturn(InputStream.nullInputStream());
        return dataProperty;
    }

    private void mockCreatedAndLastModifiedDate(Node node, Calendar createdDate) throws RepositoryException
    {
        Property createdDateProperty = mock(Property.class);
        when(node.hasProperty("jcr:created")).thenReturn(true);
        when(node.hasProperty("jcr:lastModified")).thenReturn(true);
        when(node.getProperty("jcr:created")).thenReturn(createdDateProperty);
        when(node.getProperty("jcr:lastModified")).thenReturn(createdDateProperty);
        when(createdDateProperty.getDate()).thenReturn(createdDate);
    }
}
