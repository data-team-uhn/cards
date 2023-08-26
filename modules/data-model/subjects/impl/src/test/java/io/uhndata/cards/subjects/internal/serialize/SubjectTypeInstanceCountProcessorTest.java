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

import java.util.UUID;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.RowIterator;
import javax.json.JsonObjectBuilder;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectTypeInstanceCountProcessor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectTypeInstanceCountProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String NODE_IDENTIFIER = "jcr:uuid";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String TYPE_PROPERTY = "type";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String NAME = "instanceCount";
    private static final int PRIORITY = 55;
    private static final boolean ENABLED = true;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SubjectTypeInstanceCountProcessor subjectTypeInstanceCountProcessor;

    @Mock
    private ThreadLocal<String> originalPath;

    @Test
    public void getNameTest()
    {
        assertEquals(NAME, this.subjectTypeInstanceCountProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        assertEquals(PRIORITY, this.subjectTypeInstanceCountProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultReturnsTrue()
    {
        assertEquals(ENABLED, this.subjectTypeInstanceCountProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void canProcessForSubjectTypeReturnsTrue()
    {
        Resource subjectType = this.context.resourceResolver().getResource("/SubjectTypes/Root");
        assertTrue(this.subjectTypeInstanceCountProcessor.canProcess(subjectType));
    }

    @Test
    public void canProcessForSubjectReturnsFalse()
    {
        Resource subject = this.context.resourceResolver().getResource("/Subjects/r1");
        assertFalse(this.subjectTypeInstanceCountProcessor.canProcess(subject));
    }

    @Test
    public void startForSubjectTypeNode()
    {
        Resource subject = this.context.resourceResolver().getResource("/SubjectTypes/Root");
        this.subjectTypeInstanceCountProcessor.start(subject);
        verify(this.originalPath).set("/SubjectTypes/Root");
    }

    @Test
    public void leaveThrowsRepositoryException() throws RepositoryException
    {
        Node node = mock(Node.class);
        when(node.getPath()).thenThrow(new RepositoryException());
        JsonObjectBuilder json = mock(JsonObjectBuilder.class);
        this.subjectTypeInstanceCountProcessor.leave(node, json, mock(Function.class));
        verify(json, times(0)).add(anyString(), anyLong());
    }

    @Test
    public void leaveForSubjectTypeNodeDoesNotMatchOriginalPath() throws RepositoryException
    {
        ThreadLocal<String> originalPathValue = new ThreadLocal<>();
        originalPathValue.set("/SubjectTypes/Root/Branch");
        Whitebox.setInternalState(this.subjectTypeInstanceCountProcessor, "originalPath", originalPathValue);

        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/SubjectTypes/Root");
        JsonObjectBuilder json = mock(JsonObjectBuilder.class);
        this.subjectTypeInstanceCountProcessor.leave(node, json, mock(Function.class));
        verify(json, times(0)).add(anyString(), anyLong());
    }

    @Test
    public void leaveForSubjectTypeNode() throws RepositoryException
    {
        ThreadLocal<String> originalPathValue = new ThreadLocal<>();
        originalPathValue.set("/SubjectTypes/Root");
        Whitebox.setInternalState(this.subjectTypeInstanceCountProcessor, "originalPath", originalPathValue);

        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode("/SubjectTypes/Root");
        JsonObjectBuilder json = mock(JsonObjectBuilder.class);
        this.subjectTypeInstanceCountProcessor.leave(node, json, mock(Function.class));
        verify(json, times(1)).add(NAME, Long.valueOf(3));
    }

    @Test
    public void leaveManuallyCountsMatches() throws RepositoryException
    {
        ThreadLocal<String> originalPathValue = new ThreadLocal<>();
        originalPathValue.set("/SubjectTypes/Root/Branch/Leaf");
        Whitebox.setInternalState(this.subjectTypeInstanceCountProcessor, "originalPath", originalPathValue);

        Node node = mock(Node.class);
        Property property = mock(Property.class);
        Session session = mock(Session.class);
        Workspace workspace = mock(Workspace.class);
        QueryManager queryManager = mock(QueryManager.class);
        Query query = mock(Query.class);
        QueryResult queryResult = mock(QueryResult.class);
        RowIterator rowIterator = mock(RowIterator.class);
        when(node.getPath()).thenReturn("/SubjectTypes/Root/Branch/Leaf");
        when(node.getProperty(NODE_IDENTIFIER)).thenReturn(property);
        when(property.getString()).thenReturn(UUID.randomUUID().toString());
        when(node.getSession()).thenReturn(session);
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getQueryManager()).thenReturn(queryManager);
        when(queryManager.createQuery(anyString(), anyString())).thenReturn(query);
        when(query.execute()).thenReturn(queryResult);
        when(queryResult.getRows()).thenReturn(rowIterator);
        when(rowIterator.getSize()).thenReturn((long) -1);
        when(rowIterator.hasNext()).thenReturn(true, false);
        when(rowIterator.next()).thenReturn(mock(Object.class));

        JsonObjectBuilder json = mock(JsonObjectBuilder.class);
        this.subjectTypeInstanceCountProcessor.leave(node, json, mock(Function.class));
        verify(json, times(1)).add(NAME, Long.valueOf(1));
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
                        IDENTIFIER_PROPERTY, "Root subject1")
                .resource("/Subjects/r1/b1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Branch subject1")
                .resource("/Subjects/r1/b1/l1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY, this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch/Leaf")
                                .adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Leaf subject1")
                .resource("/Subjects/r2",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root subject2")
                .resource("/Subjects/r2/b2",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Branch subject2")
                .resource("/Subjects/r3",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root subject3")
                .commit();
    }

}
