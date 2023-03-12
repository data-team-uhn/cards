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
package io.uhndata.cards.serialize;

import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.JsonObject;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourceToJsonAdapterFactory}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceToJsonAdapterFactoryTest
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

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private ResourceToJsonAdapterFactory factory;

    @Test
    public void getAdapterForNullAdaptableObjectReturnsNull()
    {
        assertNull(this.factory.getAdapter(null, JsonObject.class));
    }

    @Test
    public void getAdapterForResourceAdaptableObjectReturnsSerializedAdapter()
    {
        Resource adaptable = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);

        when(processor.isEnabledByDefault(adaptable)).thenReturn(true);
        when(processor.getName()).thenReturn("name");
        when(processor.canProcess(adaptable)).thenReturn(true);

        Whitebox.setInternalState(this.factory, "allProcessors", List.of(processor));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        assertNotNull(adapter);
    }

    @Test
    public void getAdapterForNullNodeReturnsNull()
    {
        Resource adaptable = mock(Resource.class);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        when(adaptable.getResourceMetadata()).thenReturn(resourceMetadata);
        when(adaptable.adaptTo(Node.class)).thenReturn(null);
        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);

        when(processor.isEnabledByDefault(adaptable)).thenReturn(true);
        when(processor.getName()).thenReturn("name");
        when(processor.canProcess(adaptable)).thenReturn(true);

        Whitebox.setInternalState(this.factory, "allProcessors", List.of(processor));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        assertNull(adapter);
    }

    @Test
    public void getAdapterCatchesRepositoryExceptionReturnsNull() throws RepositoryException
    {
        Resource adaptable = mock(Resource.class);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        Node node = mock(Node.class);
        when(adaptable.getResourceMetadata()).thenReturn(resourceMetadata);
        when(adaptable.adaptTo(Node.class)).thenReturn(node);

        when(node.getPath()).thenReturn(TEST_FORM_PATH);
        when(node.getProperties()).thenThrow(new RepositoryException());
        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);

        when(processor.isEnabledByDefault(adaptable)).thenReturn(true);
        when(processor.getName()).thenReturn("name");
        when(processor.canProcess(adaptable)).thenReturn(true);

        Whitebox.setInternalState(this.factory, "allProcessors", List.of(processor));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        assertNull(adapter);
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
                        QUESTIONNAIRE_PROPERTY, questionnaire)
                .resource(TEST_FORM_PATH + "/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, question)
                .commit();
    }
}
