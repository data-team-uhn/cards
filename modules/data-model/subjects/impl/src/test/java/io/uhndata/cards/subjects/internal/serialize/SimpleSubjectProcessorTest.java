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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import jakarta.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SimpleSubjectProcessor}.
 *
 * @version $Id$
 */
public class SimpleSubjectProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String NODE_IDENTIFIER = "jcr:uuid";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String TYPE_PROPERTY = "type";
    private static final String PARENTS_PROPERTY = "parents";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String NAME = "simple";
    private static final int PRIORITY = 50;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private final SimpleSubjectProcessor simpleSubjectProcessor = new SimpleSubjectProcessor();

    @Test
    public void getDescriptionIsNotEmpty()
    {
        assertFalse(this.simpleSubjectProcessor.getDescription().isEmpty());
    }

    @Test
    public void getNameReturnsProcessorName()
    {
        assertEquals(NAME, this.simpleSubjectProcessor.getName());
    }

    @Test
    public void getPriorityReturnsProcessorPriority()
    {
        assertEquals(PRIORITY, this.simpleSubjectProcessor.getPriority());
    }

    @Test
    public void canProcessForSubjectReturnsTrue()
    {
        Resource subject = this.context.resourceResolver().getResource("/Subjects/r1");
        assertTrue(this.simpleSubjectProcessor.canProcess(subject));
    }

    @Test
    public void canProcessForSubjectTypeReturnsFalse()
    {
        Resource subjectType = this.context.resourceResolver().getResource("/SubjectTypes/Root");
        assertFalse(this.simpleSubjectProcessor.canProcess(subjectType));
    }

    @Test
    public void processPropertyForNullPropertyReturnNull()
    {
        assertNull(this.simpleSubjectProcessor.processProperty(mock(Node.class), null, mock(JsonValue.class),
                n -> JsonValue.NULL));
    }

    @Test
    public void processPropertyThrowsRepositoryExceptionReturnsInput() throws RepositoryException
    {
        Property property = mock(Property.class);
        when(property.getName()).thenThrow(new RepositoryException());
        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.simpleSubjectProcessor.processProperty(mock(Node.class), property, input,
                n -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
    }

    @Test
    public void processPropertyForNotSubjectNodeAndUuidPropertyReturnsNull() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/SubjectTypes/Root");
        Property property = node.getProperty(NODE_IDENTIFIER);
        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.simpleSubjectProcessor.processProperty(node, property, input, n -> JsonValue.NULL);
        assertNull(jsonValue);
    }

    @Test
    public void processPropertyForPrimaryTypePropertyReturnsInput() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/Subjects/r1");
        Property property = node.getProperty(NODE_TYPE);
        JsonValue input = mock(JsonValue.class);
        JsonValue jsonValue = this.simpleSubjectProcessor.processProperty(node, property, input, n -> JsonValue.NULL);
        assertNotNull(jsonValue);
        assertEquals(input, jsonValue);
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
