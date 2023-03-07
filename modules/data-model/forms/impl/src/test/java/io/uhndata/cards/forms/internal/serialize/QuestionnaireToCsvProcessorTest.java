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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonArray;
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
 * Unit tests for {@link QuestionnaireToCsvProcessor}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class QuestionnaireToCsvProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test/BranchTest";
    private static final String TEST_FORM_1_PATH = "/Forms/f1";
    private static final String TEST_FORM_2_PATH = "/Forms/f2";
    private static final String TEST_FORM_3_PATH = "/Forms/f3";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String PARENT_PROPERTY = "parents";
    private static final String RELATED_SUBJECTS_PROPERTY = "relatedSubjects";
    private static final String REQUIRED_SUBJECT_TYPE_PROPERTY = "requiredSubjectTypes";
    private static final String DISPLAYED_VALUE_PROPERTY = "displayedValue";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String TYPE_PROPERTY = "type";
    private static final String CREATED_DATE_PROPERTY = "jcr:created";
    private static final String MODIFIED_DATE_PROPERTY = "jcr:lastModified";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private QuestionnaireToCsvProcessor questionnaireToCsvProcessor;

    @Test
    public void canProcessForQuestionnaireReturnsTrue()
    {
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        assertTrue(this.questionnaireToCsvProcessor.canProcess(questionnaire));
    }

    @Test
    public void canProcessForFormReturnsFalse()
    {
        Resource form = this.context.resourceResolver().getResource(TEST_FORM_1_PATH);
        assertFalse(this.questionnaireToCsvProcessor.canProcess(form));
    }

    @Test
    public void serializeForQuestionnaireWithoutRequiredSubjectType() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        String csvText = this.questionnaireToCsvProcessor.serialize(questionnaire);
        assertNotNull(csvText);
        String[] lines = csvText.split("\r\n");
        assertEquals(4, lines.length);
        assertEquals("Identifier,Root ID,Branch ID,Leaf ID,Created,Last modified,Text Question,Boolean Question,"
                + "Long Question,Date Question,Pedigree Question", lines[0]);

        Node form = session.getNode("/Forms/f1");
        String createdDateForm1 = getFormattedDate(form.getProperty(CREATED_DATE_PROPERTY).getValue().getDate());
        String modifiedDateForm1 = getFormattedDate(form.getProperty(MODIFIED_DATE_PROPERTY).getValue().getDate());
        assertEquals("f1,Root Subject,Branch Subject,," + createdDateForm1 + "," + modifiedDateForm1
                        + ",,,100,2023-01-01,yes", lines[1]);

        Node form2 = session.getNode(TEST_FORM_2_PATH);
        String createdDateForm2 = getFormattedDate(form2.getProperty(CREATED_DATE_PROPERTY).getValue().getDate());
        String modifiedDateForm2 = getFormattedDate(form2.getProperty(MODIFIED_DATE_PROPERTY).getValue().getDate());
        assertEquals("f2,Root Subject,Branch Subject,," + createdDateForm2 + "," + modifiedDateForm2 + ",,true,,,",
                lines[2]);

        Node form3 = session.getNode(TEST_FORM_3_PATH);
        String createdDateForm3 = getFormattedDate(form3.getProperty(CREATED_DATE_PROPERTY).getValue().getDate());
        String modifiedDateForm3 = getFormattedDate(form3.getProperty(MODIFIED_DATE_PROPERTY).getValue().getDate());
        assertEquals("f3,Root Subject,Branch Subject,," + createdDateForm3 + "," + modifiedDateForm3 + ",some text,,,,",
                lines[3]);
    }

    @Test
    public void serializeForQuestionnaireWithRequiredSubjectType() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        questionnaire.adaptTo(Node.class).setProperty(REQUIRED_SUBJECT_TYPE_PROPERTY, new String[]{
            session.getNode("/SubjectTypes/Root/Branch").getIdentifier()
        });
        String csvText = this.questionnaireToCsvProcessor.serialize(questionnaire);
        assertNotNull(csvText);
    }

    @Test
    public void serializeForNullQuestionnaireResource()
    {
        Resource originalResource = mock(Resource.class);
        Resource resource = mock(Resource.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        when(originalResource.getPath()).thenReturn(TEST_QUESTIONNAIRE_PATH);
        when(originalResource.getResourceMetadata()).thenReturn(resourceMetadata);
        when(resourceMetadata.getResolutionPathInfo()).thenReturn("");
        when(originalResource.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.resolve(anyString())).thenReturn(resource);
        when(resource.adaptTo(JsonObject.class)).thenReturn(null);
        String csvText = this.questionnaireToCsvProcessor.serialize(originalResource);
        assertNull(csvText);
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
                .resource("/Subjects/Test", NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root Subject").commit();
        this.context.build().resource("/Subjects/Test/BranchTest", NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Branch Subject",
                        PARENT_PROPERTY,
                        this.context.resourceResolver().getResource("/Subjects/Test").adaptTo(Node.class))
                .commit();

        Session session = this.context.resourceResolver().adaptTo(Session.class);

        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);

        this.context.build()
                .resource(TEST_FORM_1_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        RELATED_SUBJECTS_PROPERTY, List.of(subject).toArray())
                .resource(TEST_FORM_1_PATH + "/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1"))
                .resource(TEST_FORM_1_PATH + "/s1/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_1"),
                        "value", "2023-01-01")
                .resource(TEST_FORM_1_PATH + "/s1/a2",
                        NODE_TYPE, "cards:PedigreeAnswer",
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_2"),
                        DISPLAYED_VALUE_PROPERTY, "pedigreeDisplayedValue",
                        "note", "Pedigree note")
                .resource(TEST_FORM_1_PATH + "/s2",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_2"))
                .resource(TEST_FORM_1_PATH + "/s2/a3",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_2/question_3"),
                        DISPLAYED_VALUE_PROPERTY, 100)

                .resource(TEST_FORM_2_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        RELATED_SUBJECTS_PROPERTY, List.of(subject).toArray())
                .resource(TEST_FORM_2_PATH + "/s3",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3"))
                .resource(TEST_FORM_2_PATH + "/s3/a4",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3/question_4"),
                        DISPLAYED_VALUE_PROPERTY, "true")

                .resource(TEST_FORM_3_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        RELATED_SUBJECTS_PROPERTY, List.of(subject).toArray())
                .resource(TEST_FORM_3_PATH + "/s3",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3"))
                .resource(TEST_FORM_3_PATH + "/s3/a4",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3/question_4"))
                .resource(TEST_FORM_3_PATH + "/s3/s4",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3/section_4"))
                .resource(TEST_FORM_3_PATH + "/s3/s4/a5",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_3/section_4/question_5"),
                        DISPLAYED_VALUE_PROPERTY, "some text")
                .commit();

        this.context.registerAdapter(Resource.class, JsonObject.class, (Function<Resource, JsonObject>) resource -> {
            JsonObjectBuilder jsonObject = null;
            try {
                jsonObject = Json.createObjectBuilder(createPropertiesAndChildrenMap(resource));
                jsonObject.add("@name", resource.getName());
                if (resource.isResourceType("cards/Questionnaire")) {
                    ResourceResolver resourceResolver = this.context.resourceResolver();
                    JsonArray array = Json.createArrayBuilder()
                            .add(resourceResolver.getResource(TEST_FORM_1_PATH).adaptTo(JsonObject.class))
                            .add(resourceResolver.getResource(TEST_FORM_2_PATH).adaptTo(JsonObject.class))
                            .add(resourceResolver.getResource(TEST_FORM_3_PATH).adaptTo(JsonObject.class))
                            .build();
                    jsonObject.add("@data", array);
                }
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
        final List<String> objectTypeProperties = List.of(QUESTIONNAIRE_PROPERTY, SUBJECT_PROPERTY, SECTION_PROPERTY,
                QUESTION_PROPERTY, TYPE_PROPERTY, PARENT_PROPERTY);
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
                        } else if (REQUIRED_SUBJECT_TYPE_PROPERTY.equals(key)) {
                            Resource reference = this.context.resourceResolver()
                                    .getResource(getResourcePathByItsIdentifier((String) valueUnit));
                            arrayBuilder.add(Json.createObjectBuilder(createPropertiesAndChildrenMap(reference))
                                    .build());
                        } else {
                            arrayBuilder.add((String) valueUnit);
                        }
                    }
                    propertiesAndChildrenMap.put(key, arrayBuilder.build());
                } else if (value instanceof GregorianCalendar) {
                    propertiesAndChildrenMap.put(key, getFormattedDate((GregorianCalendar) value));
                } else {
                    propertiesAndChildrenMap.put(key, value);
                }
            }
        }

        if (originalResource.getResourceType().equals("cards/Subject")
                || originalResource.getResourceType().equals("cards/SubjectType")) {
            return propertiesAndChildrenMap;
        }

        // process children of question/answer/section/answerSection/questionnaire/form resources to avoid collision
        for (Resource child : originalResource.getChildren()) {
            JsonObject jsonObject = Json.createObjectBuilder(createPropertiesAndChildrenMap(child))
                    .add("@name", child.getName())
                    .build();
            propertiesAndChildrenMap.put(child.getName(), jsonObject);
        }
        return propertiesAndChildrenMap;
    }

    private String getResourcePathByItsIdentifier(String identifier) throws RepositoryException
    {
        return this.context.resourceResolver().adaptTo(Session.class).getNodeByIdentifier(identifier).getPath();
    }

    private String getFormattedDate(Calendar date)
    {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(date.getTimeZone());
        String dateTime = sdf.format(date.getTime());
        return dateTime;
    }

}
