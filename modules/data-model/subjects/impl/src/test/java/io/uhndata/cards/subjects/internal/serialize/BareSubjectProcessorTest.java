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
package io.uhndata.cards.subjects.internal.serialize;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BareSubjectProcessor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class BareSubjectProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String TYPE_PROPERTY = "type";
    private static final String PARENTS_PROPERTY = "parents";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String NAME = "bare";
    private static final int PRIORITY = 95;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private BareSubjectProcessor bareSubjectProcessor;


    @Test
    public void getNameTest()
    {
        assertEquals(NAME, this.bareSubjectProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        assertEquals(PRIORITY, this.bareSubjectProcessor.getPriority());
    }

    @Test
    public void canProcessForSubjectReturnsTrue()
    {
        Resource subject = this.context.resourceResolver().getResource("/Subjects/r1");
        assertTrue(this.bareSubjectProcessor.canProcess(subject));
    }

    @Test
    public void canProcessForSubjectTypeReturnsFalse()
    {
        Resource subjectType = this.context.resourceResolver().getResource("/SubjectTypes/Root");
        assertFalse(this.bareSubjectProcessor.canProcess(subjectType));
    }

    @Test
    public void processPropertyForNullProperty()
    {
        assertNull(this.bareSubjectProcessor.processProperty(mock(Node.class), null, mock(JsonValue.class),
                mock(Function.class)));
    }

    @Test
    public void processPropertyThrowsRepositoryExceptionReturnsInput() throws RepositoryException
    {
        Node node = mock(Node.class);
        when(node.isNodeType(SUBJECT_TYPE)).thenThrow(new RepositoryException());
        JsonValue input = Json.createValue("Root subject1");
        JsonValue jsonValue = this.bareSubjectProcessor.processProperty(node, mock(Property.class), input,
                mock(Function.class));
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

    @Test
    public void processPropertyForTypeProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/Subjects/r1");
        Property property = node.getProperty(TYPE_PROPERTY);
        JsonValue input = Json.createValue("Root subject1");
        JsonValue jsonValue = this.bareSubjectProcessor.processProperty(node, property, input, mock(Function.class));
        assertNotNull(jsonValue);
        assertEquals(Json.createValue("Root"), jsonValue);
    }

    @Test
    public void processPropertyForParentsProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/Subjects/r1/b1");
        Property property = node.getProperty(PARENTS_PROPERTY);
        JsonValue input = Json.createValue("Branch subject1");
        JsonValue jsonValue = this.bareSubjectProcessor.processProperty(node, property, input, mock(Function.class));
        assertNotNull(jsonValue);
        assertEquals(Json.createValue("Root"), jsonValue);
    }

    @Test
    public void leaveForSubjectNode() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/Subjects/r1/b1");
        JsonObjectBuilder json = mock(JsonObjectBuilder.class);
        this.bareSubjectProcessor.leave(node, json, mock(Function.class));
        verify(json, times(1)).remove(IDENTIFIER_PROPERTY);
        verify(json, times(1)).add(SUBJECT_PROPERTY, Json.createValue("Root / Branch"));
    }

    @Test
    public void leaveForNotSubjectNodeThrowsRepositoryException() throws RepositoryException
    {
        Node node = mock(Node.class);
        when(node.isNodeType(SUBJECT_TYPE)).thenThrow(new RepositoryException());
        JsonObjectBuilder json = mock(JsonObjectBuilder.class);
        this.bareSubjectProcessor.leave(node, json, mock(Function.class));
        verify(json, times(0)).remove(IDENTIFIER_PROPERTY);
        verify(json, times(0)).add(eq(SUBJECT_PROPERTY), anyString());
    }

    @Before
    public void setupRepo()
    {
        this.context.build()
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .commit();
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");

        this.context.build()
                .resource("/Subjects/r1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root")
                .commit();
        this.context.build()
                .resource("/Subjects/r1/b1",
                NODE_TYPE, SUBJECT_TYPE,
                TYPE_PROPERTY,
                this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class),
                PARENTS_PROPERTY, this.context.resourceResolver().getResource("/Subjects/r1").adaptTo(Node.class),
                IDENTIFIER_PROPERTY, "Branch")
                .commit();
    }

}
