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

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
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
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FormToTextAdapterFactory}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class FormToTextAdapterFactoryTest
{
    private static final String NEXT_LINE = "\n";
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String DISPLAYED_VALUE_PROPERTY = "displayedValue";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private FormToTextAdapterFactory formToTextAdapterFactory;

    @Test
    public void canProcessForFormReturnsTrue()
    {
        Resource form = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        assertTrue(this.formToTextAdapterFactory.canProcess(form));
    }

    @Test
    public void canProcessForQuestionnaireReturnsFalse()
    {
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        assertFalse(this.formToTextAdapterFactory.canProcess(questionnaire));
    }

    @Test
    public void serializeForFormWithOneFooterAndOneDefaultSections() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        String subjectFullIdentifier = resourceResolver.getResource(TEST_SUBJECT_PATH).adaptTo(Node.class)
                .getIdentifier();
        Resource form = resourceResolver.getResource(TEST_FORM_PATH);
        String markdown = this.formToTextAdapterFactory.serialize(form);
        assertNotNull(markdown);
        assertEquals(subjectFullIdentifier + NEXT_LINE
                + "TEST SERIALIZABLE QUESTIONNAIRE" + NEXT_LINE
                + java.time.LocalDate.now() + NEXT_LINE
                + NEXT_LINE
                + "---------------------------------------------" + NEXT_LINE
                + NEXT_LINE
                + "SECTION 2" + NEXT_LINE
                + NEXT_LINE
                + "Long Question" + NEXT_LINE
                + "  100" + NEXT_LINE
                + NEXT_LINE
                + "SECTION 1" + NEXT_LINE
                + NEXT_LINE
                + "Date Question" + NEXT_LINE
                + "  —" + NEXT_LINE
                + NEXT_LINE
                + "Pedigree Question" + NEXT_LINE
                + "  Pedigree provided" + NEXT_LINE
                + NEXT_LINE
                + "  NOTES" + NEXT_LINE
                + "  Pedigree note", markdown);
    }

    @Test
    public void serializeForFormWithHeaderSection() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        String subjectFullIdentifier = resourceResolver.getResource(TEST_SUBJECT_PATH).adaptTo(Node.class)
                .getIdentifier();
        Resource form = resourceResolver.getResource("/Forms/f2");

        String markdown = this.formToTextAdapterFactory.serialize(form);
        assertNotNull(markdown);
        assertEquals(subjectFullIdentifier + NEXT_LINE
                + "TEST SERIALIZABLE QUESTIONNAIRE" + NEXT_LINE
                + java.time.LocalDate.now() + NEXT_LINE
                + NEXT_LINE
                + "SECTION 3" + NEXT_LINE
                + NEXT_LINE
                + "Boolean Question" + NEXT_LINE
                + "  true" + NEXT_LINE
                + NEXT_LINE
                + "---------------------------------------------", markdown);
    }

    @Test
    public void serializeForFormWithRecurrentSection() throws RepositoryException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        String subjectFullIdentifier = resourceResolver.getResource(TEST_SUBJECT_PATH).adaptTo(Node.class)
                .getIdentifier();
        Resource form = resourceResolver.getResource("/Forms/f3");
        String markdown = this.formToTextAdapterFactory.serialize(form);
        assertNotNull(markdown);
        assertEquals(subjectFullIdentifier + NEXT_LINE
                + "TEST SERIALIZABLE QUESTIONNAIRE" + NEXT_LINE
                + java.time.LocalDate.now() + NEXT_LINE
                + NEXT_LINE
                + "SECTION 3" + NEXT_LINE
                + NEXT_LINE
                + "SECTION 4 #1" + NEXT_LINE
                + NEXT_LINE
                + "Text Question" + NEXT_LINE
                + "  some text" + NEXT_LINE
                + NEXT_LINE
                + "Boolean Question" + NEXT_LINE
                + "  true" + NEXT_LINE
                + NEXT_LINE
                + "---------------------------------------------", markdown);
    }

    @Test
    public void serializeForNullFormResource()
    {
        Resource originalResource = mock(Resource.class);
        Resource resource = mock(Resource.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        when(originalResource.getPath()).thenReturn(TEST_FORM_PATH);
        when(originalResource.getResourceMetadata()).thenReturn(resourceMetadata);
        when(resourceMetadata.getResolutionPathInfo()).thenReturn("");
        when(originalResource.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.resolve(anyString())).thenReturn(resource);
        when(resource.adaptTo(JsonObject.class)).thenReturn(null);
        String markdown = this.formToTextAdapterFactory.serialize(originalResource);
        assertNull(markdown);
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
        this.context.load().json("/SerializableQuestionnaire.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();

        Session session = this.context.resourceResolver().adaptTo(Session.class);

        Node subject = session.getNode(TEST_SUBJECT_PATH);
        subject.setProperty("fullIdentifier", subject.getIdentifier());
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);

        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource(TEST_FORM_PATH + "/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1"))
                .resource(TEST_FORM_PATH + "/s1/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_1"),
                        "value", "2023-01-01")
                .resource(TEST_FORM_PATH + "/s1/a2",
                        NODE_TYPE, "cards:PedigreeAnswer",
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_2"),
                        DISPLAYED_VALUE_PROPERTY, "pedigreeDisplayedValue",
                        "note", "Pedigree note")
                .resource(TEST_FORM_PATH + "/s2",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_2"))
                .resource(TEST_FORM_PATH + "/s2/a3",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_2/question_3"),
                        DISPLAYED_VALUE_PROPERTY, "100")

                .resource("/Forms/f2",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f2/s3",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3"))
                .resource("/Forms/f2/s3/a4",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3/question_4"),
                        DISPLAYED_VALUE_PROPERTY, "true")

                .resource("/Forms/f3",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f3/s3",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3"))
                .resource("/Forms/f3/s3/a4",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3/question_4"),
                        DISPLAYED_VALUE_PROPERTY, "true")
                .resource("/Forms/f3/s3/s4",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3/section_4"))
                .resource("/Forms/f3/s3/s4/a5",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3/section_4/question_5"),
                        DISPLAYED_VALUE_PROPERTY, "some text")
                .commit();

        this.context.registerAdapter(Resource.class, JsonObject.class, (Function<Resource, JsonObject>) resource -> {
            JsonObjectBuilder jsonObject = null;
            try {
                jsonObject = Json.createObjectBuilder(createPropertiesAndChildrenMap(resource));
            } catch (RepositoryException e) {
                throw new RuntimeException(e);
            }
            return jsonObject.build();
        });
    }

    private Map<String, Object> createPropertiesAndChildrenMap(Resource originalResource) throws RepositoryException
    {
        Map<String, Object> propertiesAndChildrenMap = new HashMap<>();

        // process properties of resource
        ValueMap valueMap = originalResource.getValueMap();
        List<String> objectTypeProperties = List.of(QUESTIONNAIRE_PROPERTY, SUBJECT_PROPERTY, SECTION_PROPERTY,
                QUESTION_PROPERTY);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        for (Map.Entry<String, Object> property : valueMap.entrySet()) {
            String key = property.getKey();
            Object value = property.getValue();
            if (objectTypeProperties.contains(key)) {
                Resource reference =
                        this.context.resourceResolver().getResource(getResourcePathByItsIdentifier((String) value));
                JsonObjectBuilder referenceJson = Json.createObjectBuilder(createPropertiesAndChildrenMap(reference));
                propertiesAndChildrenMap.put(key, referenceJson.build());
            } else {
                if (value.getClass().isArray()) {
                    JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                    for (Object valueUnit : (Object[]) value) {
                        if (valueUnit instanceof Resource) {
                            arrayBuilder.add(Json.createObjectBuilder(
                                    createPropertiesAndChildrenMap((Resource) valueUnit)).build());
                        } else {
                            arrayBuilder.add((String) valueUnit);
                        }
                    }
                    propertiesAndChildrenMap.put(key, arrayBuilder.build());
                } else if (value instanceof GregorianCalendar) {
                    sdf.setTimeZone(((GregorianCalendar) value).getTimeZone());
                    value = ((GregorianCalendar) value).getTime();
                    propertiesAndChildrenMap.put(key, sdf.format(value));
                } else {
                    propertiesAndChildrenMap.put(key, value);
                }
            }
        }

        // process children of resource
        for (Resource child : originalResource.getChildren()) {
            JsonObject jsonObject = Json.createObjectBuilder(createPropertiesAndChildrenMap(child)).build();
            propertiesAndChildrenMap.put(child.getName(), jsonObject);
        }

        return propertiesAndChildrenMap;
    }

    private String getResourcePathByItsIdentifier(String identifier) throws RepositoryException
    {
        return this.context.resourceResolver().adaptTo(Session.class).getNodeByIdentifier(identifier).getPath();
    }
}
