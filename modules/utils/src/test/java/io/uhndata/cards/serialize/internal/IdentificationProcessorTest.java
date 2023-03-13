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

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdentificationProcessor}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class IdentificationProcessorTest
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
    private static final String NAME = "identify";
    private static final int PRIORITY = 10;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private IdentificationProcessor identificationProcessor;

    @Test
    public void getNameReturnIdentify()
    {
        assertEquals(NAME, this.identificationProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        assertEquals(PRIORITY, this.identificationProcessor.getPriority());
    }

    @Test
    public void isEnabledByDefaultTest()
    {
        assertTrue(this.identificationProcessor.isEnabledByDefault(mock(Resource.class)));
    }

    @Test
    public void leaveCatchesRepositoryException() throws RepositoryException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = mock(Node.class);
        when(node.getPath()).thenThrow(new RepositoryException());
        this.identificationProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        assertTrue(jsonObject.isEmpty());
    }

    @Test
    public void leaveAddsPathAndNameAndReferencedParameters() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node node = session.getNode(TEST_FORM_PATH);
        JsonObjectBuilder json = Json.createObjectBuilder();
        this.identificationProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        assertFalse(jsonObject.isEmpty());
        assertTrue(jsonObject.containsKey("@path"));
        assertEquals(Json.createValue(TEST_FORM_PATH), jsonObject.getJsonString("@path"));
        assertTrue(jsonObject.containsKey("@name"));
        assertEquals(Json.createValue("f1"), jsonObject.getJsonString("@name"));
        assertTrue(jsonObject.containsKey("@referenced"));
        assertFalse(jsonObject.getBoolean("@referenced"));

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
