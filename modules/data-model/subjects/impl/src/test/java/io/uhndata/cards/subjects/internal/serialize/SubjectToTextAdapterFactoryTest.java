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
 * Unit tests for {@link SubjectToTextAdapterFactory}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectToTextAdapterFactoryTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";
    private static final String TEST_BRANCH_SUBJECT_PATH = "/Subjects/r1/b1";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String PARENT_PROPERTY = "parents";
    private static final String RELATED_SUBJECTS_PROPERTY = "relatedSubjects";
    private static final String REQUIRED_SUBJECT_TYPE_PROPERTY = "requiredSubjectTypes";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String FULL_IDENTIFIER_PROPERTY = "fullIdentifier";
    private static final String TYPE_PROPERTY = "type";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SubjectToTextAdapterFactory subjectToTextAdapterFactory;

    @Test
    public void canProcessForSubjectReturnsTrue()
    {
        Resource subject = this.context.resourceResolver().getResource(TEST_SUBJECT_PATH);
        assertTrue(this.subjectToTextAdapterFactory.canProcess(subject));
    }

    @Test
    public void canProcessForSubjectTypeReturnsFalse()
    {
        Resource subjectType = this.context.resourceResolver().getResource("/SubjectTypes/Root");
        assertFalse(this.subjectToTextAdapterFactory.canProcess(subjectType));
    }

    @Test
    public void serializeForSubjectResource()
    {
        Resource subject = this.context.resourceResolver().getResource(TEST_BRANCH_SUBJECT_PATH);
        String text = this.subjectToTextAdapterFactory.serialize(subject);
        assertNotNull(text);
        String[] lines = text.split("\n\n\n\n");
        assertEquals(4, lines.length);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("Branch Branch\n"
                + sdf.format(((GregorianCalendar) subject.getValueMap().get("jcr:created")).getTime()), lines[0]);
    }

    @Test
    public void serializeForNullSubjectResource()
    {
        Resource originalResource = mock(Resource.class);
        Resource resource = mock(Resource.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        when(originalResource.getPath()).thenReturn(TEST_BRANCH_SUBJECT_PATH);
        when(originalResource.getResourceMetadata()).thenReturn(resourceMetadata);
        when(resourceMetadata.getResolutionPathInfo()).thenReturn("");
        when(originalResource.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.resolve(anyString())).thenReturn(resource);
        when(resource.adaptTo(JsonObject.class)).thenReturn(null);
        String text = this.subjectToTextAdapterFactory.serialize(originalResource);
        assertNull(text);
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        this.context.build()
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .commit();
        this.context.load().json("/SerializableQuestionnaire.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");

        this.context.build()
                .resource(TEST_SUBJECT_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root")
                .commit();
        this.context.build()
                .resource(TEST_BRANCH_SUBJECT_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Branch",
                        PARENT_PROPERTY,
                        this.context.resourceResolver().getResource(TEST_SUBJECT_PATH).adaptTo(Node.class))
                .commit();

        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node rootSubject = session.getNode(TEST_SUBJECT_PATH);
        Node branchSubject = session.getNode(TEST_BRANCH_SUBJECT_PATH);
        rootSubject.setProperty(FULL_IDENTIFIER_PROPERTY, rootSubject.getIdentifier());
        branchSubject.setProperty(FULL_IDENTIFIER_PROPERTY, branchSubject.getIdentifier());
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);

        this.context.build()
                .resource("/Forms/f1",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, branchSubject,
                        RELATED_SUBJECTS_PROPERTY, List.of(branchSubject).toArray())

                .resource("/Forms/f2",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, branchSubject,
                        RELATED_SUBJECTS_PROPERTY, List.of(branchSubject).toArray())

                .resource("/Forms/f3",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, branchSubject,
                        RELATED_SUBJECTS_PROPERTY, List.of(branchSubject).toArray())
                .commit();

        this.context.registerAdapter(Resource.class, JsonObject.class, (Function<Resource, JsonObject>) resource -> {
            JsonObjectBuilder jsonObject = null;
            try {
                jsonObject = Json.createObjectBuilder(createPropertiesAndChildrenMap(resource));
                if (resource.isResourceType("cards/Subject")) {
                    ResourceResolver resourceResolver = this.context.resourceResolver();
                    JsonArray array = Json.createArrayBuilder()
                            .add(resourceResolver.getResource("/Forms/f1").adaptTo(JsonObject.class))
                            .add(resourceResolver.getResource("/Forms/f2").adaptTo(JsonObject.class))
                            .add(resourceResolver.getResource("/Forms/f3").adaptTo(JsonObject.class))
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
        propertiesAndChildrenMap.put("@name", originalResource.getName());
        propertiesAndChildrenMap.put("@path", originalResource.getPath());

        // process properties of resource
        ValueMap valueMap = originalResource.getValueMap();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
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
                    sdf.setTimeZone(((GregorianCalendar) value).getTimeZone());
                    value = ((GregorianCalendar) value).getTime();
                    propertiesAndChildrenMap.put(key, sdf.format(value));
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
