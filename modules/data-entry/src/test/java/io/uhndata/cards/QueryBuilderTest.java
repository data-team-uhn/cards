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
package io.uhndata.cards;

import java.io.StringReader;
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
import javax.json.JsonReader;
import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.testing.mock.sling.MockSlingScriptHelper;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;

import io.uhndata.cards.spi.QuickSearchEngine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QueryBuilder}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class QueryBuilderTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String FORM_TYPE = "cards:Form";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String RELATED_SUBJECTS_PROPERTY = "relatedSubjects";
    private static final String TYPE_PROPERTY = "type";
    private static final String IDENTIFIER_PROPERTY = "identifier";

    // Paths
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";
    private static final String ROOT_SUBJECT_TYPE = "/SubjectTypes/Root";
    private static final String BRANCH_SUBJECT_TYPE = "/SubjectTypes/Root/Branch";
    private static final String PATIENT_SUBJECT_TYPE = "/SubjectTypes/Patient";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String TEST_TEXT_QUESTIONNAIRE_PATH = "/Questionnaires/TestTextQuestionnaire";
    private static final String ALLOWED_RESOURCE_TYPES = "allowedResourceTypes";
    private static final String QUERY = "query";
    private static final String LUCENE = "lucene";
    private static final String FULL_TEXT = "fulltext";
    private static final String QUICK = "quick";
    private static final String OFFSET = "offset";
    private static final String LIMIT = "limit";
    private static final String SERIALIZE_CHILDREN = "serializeChildren";
    private static final String REQ = "req";
    private static final String DO_NOT_ESCAPE_QUERY = "doNotEscapeQuery";
    private static final String SHOW_TOTAL_ROWS = "showTotalRows";
    private static final String ROWS = "rows";
    private static final String RETURNED_ROWS = "returnedrows";
    private static final String TOTAL_ROWS = "totalrows";
    private static final String REQUEST = "request";
    private static final String RESOLVER = "resolver";
    private static final String SLING = "sling";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private QueryBuilder queryBuilder;

    @Mock
    private QuickSearchEngine quickSearchEngine;

    private BundleContext slingBundleContext;

    private ResourceResolver resourceResolver;

    @Test
    public void initForJcrQuerySelectsQuestionnairesAndSerializesChildren()
    {
        MockSlingHttpServletRequest request = mockServletRequest(TEST_TEXT_QUESTIONNAIRE_PATH, true);
        request.setParameterMap(Map.of(
                ALLOWED_RESOURCE_TYPES, new String[]{},
                QUERY, "SELECT q.* FROM [cards:Questionnaire] as q",
                OFFSET, 0,
                LIMIT, 10,
                SERIALIZE_CHILDREN, 1,
                REQ, "",
                DO_NOT_ESCAPE_QUERY, "true",
                SHOW_TOTAL_ROWS, "true"
        ));

        Bindings bindings = createBindings(request);

        this.queryBuilder.init(bindings);
        JsonObject jsonObject = getJsonObjectFromQueryBuilderContext();

        assertNotNull(jsonObject);
        assertTrue(jsonObject.containsKey(REQ));
        assertTrue(StringUtils.isBlank(jsonObject.getString(REQ)));
        assertTrue(jsonObject.containsKey(OFFSET));
        assertEquals(0, jsonObject.getInt(OFFSET));
        assertTrue(jsonObject.containsKey(LIMIT));
        assertEquals(10, jsonObject.getInt(LIMIT));
        assertTrue(jsonObject.containsKey(RETURNED_ROWS));
        assertEquals(2, jsonObject.getInt(RETURNED_ROWS));
        assertTrue(jsonObject.containsKey(TOTAL_ROWS));
        assertEquals(2, jsonObject.getInt(TOTAL_ROWS));

        assertTrue(jsonObject.containsKey(ROWS));
        JsonArray rows = jsonObject.getJsonArray(ROWS);
        assertEquals(2, rows.size());
        JsonObject questionnaire1 = rows.get(0).asJsonObject();
        JsonObject questionnaire2 = rows.get(1).asJsonObject();
        assertEquals("Test Serializable Questionnaire", questionnaire1.getString("title"));
        assertEquals("Test Text Questionnaire", questionnaire2.getString("title"));

        // assert if children of found questionnaires are serialized as well
        assertTrue(questionnaire1.get("question_1") instanceof JsonObject);
        assertTrue(questionnaire2.get("question_1") instanceof JsonObject);
    }

    @Test
    public void initForJcrQueryWithNotParsableLimitValueAndDoesNotSerializeChildren()
    {
        MockSlingHttpServletRequest request = mockServletRequest(TEST_TEXT_QUESTIONNAIRE_PATH, true);
        request.setParameterMap(Map.of(
                ALLOWED_RESOURCE_TYPES, new String[]{},
                QUERY, "SELECT q.* FROM [cards:Questionnaire] as q",
                OFFSET, 0,
                LIMIT, "notParsable",
                SERIALIZE_CHILDREN, 0,
                REQ, "",
                DO_NOT_ESCAPE_QUERY, "true",
                SHOW_TOTAL_ROWS, "true"
        ));

        Bindings bindings = createBindings(request);

        this.queryBuilder.init(bindings);
        JsonObject jsonObject = getJsonObjectFromQueryBuilderContext();

        assertNotNull(jsonObject);

        assertEquals(10, jsonObject.getInt(LIMIT));

        assertTrue(jsonObject.containsKey(ROWS));
        JsonArray rows = jsonObject.getJsonArray(ROWS);
        assertEquals(2, rows.size());
        JsonObject questionnaire1 = rows.get(0).asJsonObject();
        JsonObject questionnaire2 = rows.get(1).asJsonObject();
        assertEquals("Test Serializable Questionnaire", questionnaire1.getString("title"));
        assertEquals("Test Text Questionnaire", questionnaire2.getString("title"));

        // assert if children of found questionnaires are not serialized
        assertFalse(questionnaire1.containsKey("question_1"));
        assertFalse(questionnaire2.containsKey("question_1"));
    }

    @Test
    public void initForQuickQuerySelectsForms()
    {
        when(this.quickSearchEngine.isTypeSupported(QUICK)).thenReturn(true);
        MockSlingHttpServletRequest request = mockServletRequest("/Forms/f1", true);
        request.setParameterMap(Map.of(
                ALLOWED_RESOURCE_TYPES, new String[]{FORM_TYPE},
                QUICK, "searchValue",
                OFFSET, 0,
                LIMIT, 10,
                SERIALIZE_CHILDREN, 0,
                REQ, "",
                DO_NOT_ESCAPE_QUERY, "true",
                SHOW_TOTAL_ROWS, "false"
        ));

        Bindings bindings = createBindings(request);

        this.queryBuilder.init(bindings);
        JsonObject jsonObject = getJsonObjectFromQueryBuilderContext();

        assertNotNull(jsonObject);
        assertTrue(jsonObject.containsKey(REQ));
        assertTrue(StringUtils.isBlank(jsonObject.getString(REQ)));
        assertTrue(jsonObject.containsKey(OFFSET));
        assertEquals(0, jsonObject.getInt(OFFSET));
        assertTrue(jsonObject.containsKey(LIMIT));
        assertEquals(10, jsonObject.getInt(LIMIT));
        assertTrue(jsonObject.containsKey(RETURNED_ROWS));
        assertEquals(0, jsonObject.getInt(RETURNED_ROWS));
        assertTrue(jsonObject.containsKey(TOTAL_ROWS));
        assertEquals(0, jsonObject.getInt(TOTAL_ROWS));

        assertTrue(jsonObject.containsKey(ROWS));
        JsonArray rows = jsonObject.getJsonArray(ROWS);
        assertEquals(0, rows.size());
    }

    @Test
    public void initForFullTextQuery()
    {
        MockSlingHttpServletRequest request = mockServletRequest("/Forms/f1", true);
        request.setParameterMap(Map.of(
                ALLOWED_RESOURCE_TYPES, new String[]{},
                FULL_TEXT, "searchValue",
                OFFSET, 0,
                LIMIT, 10,
                SERIALIZE_CHILDREN, 0,
                REQ, "",
                DO_NOT_ESCAPE_QUERY, "true",
                SHOW_TOTAL_ROWS, "false"
        ));

        Bindings bindings = createBindings(request);

        this.queryBuilder.init(bindings);
        JsonObject jsonObject = getJsonObjectFromQueryBuilderContext();

        assertNotNull(jsonObject);
        assertTrue(jsonObject.containsKey(REQ));
        assertTrue(StringUtils.isBlank(jsonObject.getString(REQ)));
        assertTrue(jsonObject.containsKey(OFFSET));
        assertEquals(0, jsonObject.getInt(OFFSET));
        assertTrue(jsonObject.containsKey(LIMIT));
        assertEquals(10, jsonObject.getInt(LIMIT));
        assertTrue(jsonObject.containsKey(RETURNED_ROWS));
        assertEquals(0, jsonObject.getInt(RETURNED_ROWS));
        assertTrue(jsonObject.containsKey(TOTAL_ROWS));
        assertEquals(0, jsonObject.getInt(TOTAL_ROWS));
        assertTrue(jsonObject.containsKey(ROWS));
    }

    @Test
    public void initForLuceneQuery()
    {
        MockSlingHttpServletRequest request = mockServletRequest("/Forms/f1", true);
        request.setParameterMap(Map.of(
                ALLOWED_RESOURCE_TYPES, new String[]{},
                LUCENE, "value: 'searchValue'",
                OFFSET, 0,
                LIMIT, 10,
                SERIALIZE_CHILDREN, 0,
                REQ, "",
                DO_NOT_ESCAPE_QUERY, "true",
                SHOW_TOTAL_ROWS, "false"
        ));

        Bindings bindings = createBindings(request);

        this.queryBuilder.init(bindings);
        JsonObject jsonObject = getJsonObjectFromQueryBuilderContext();

        assertNotNull(jsonObject);
        assertTrue(jsonObject.containsKey(REQ));
        assertTrue(StringUtils.isBlank(jsonObject.getString(REQ)));
        assertTrue(jsonObject.containsKey(OFFSET));
        assertEquals(0, jsonObject.getInt(OFFSET));
        assertTrue(jsonObject.containsKey(LIMIT));
        assertEquals(10, jsonObject.getInt(LIMIT));
        assertTrue(jsonObject.containsKey(RETURNED_ROWS));
        assertEquals(0, jsonObject.getInt(RETURNED_ROWS));
        assertTrue(jsonObject.containsKey(TOTAL_ROWS));
        assertEquals(0, jsonObject.getInt(TOTAL_ROWS));
        assertTrue(jsonObject.containsKey(ROWS));
    }

    @Before
    public void setUp() throws RepositoryException, LoginException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
                .commit();

        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/TextQuestionnaires.json", TEST_TEXT_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", ROOT_SUBJECT_TYPE);
        this.context.load().json("/SubjectTypesPatient.json", PATIENT_SUBJECT_TYPE);

        this.context.build()
                .resource(TEST_SUBJECT_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource(ROOT_SUBJECT_TYPE).adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root Subject")
                .resource(TEST_SUBJECT_PATH + "/b1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource(BRANCH_SUBJECT_TYPE).adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Branch Subject")
                .commit();

        Node rootSubject = session.getNode(TEST_SUBJECT_PATH);

        this.context.build()
                .resource("/Forms/f1",
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, rootSubject,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        RELATED_SUBJECTS_PROPERTY, List.of(rootSubject).toArray())
                .resource("/Forms/f1/s1",
                        NODE_TYPE, "cards:AnswerSection",
                        SECTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1"))
                .resource("/Forms/f1/s1/a6",
                        NODE_TYPE, "cards:TextAnswer",
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_6"),
                        "value", "searchValue")
                .resource("/Forms/f2",
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, rootSubject,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH),
                        RELATED_SUBJECTS_PROPERTY, List.of(rootSubject).toArray())
                .commit();

        this.context.registerService(QuickSearchEngine.class, this.quickSearchEngine);
        this.context.registerAdapter(Resource.class, JsonObject.class, (Function<Resource, JsonObject>) resource -> {
            JsonObjectBuilder jsonObject = null;
            try {
                jsonObject = Json.createObjectBuilder(createPropertiesAndChildrenMap(resource));
            } catch (RepositoryException e) {
                throw new RuntimeException(e);
            }
            return jsonObject.build();
        });

        this.slingBundleContext = this.context.bundleContext();
        this.resourceResolver = this.slingBundleContext
                .getService(this.slingBundleContext.getServiceReference(ResourceResolverFactory.class))
                .getServiceResourceResolver(null);

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

        return propertiesAndChildrenMap;
    }

    private String getResourcePathByItsIdentifier(String identifier) throws RepositoryException
    {
        return this.context.resourceResolver().adaptTo(Session.class).getNodeByIdentifier(identifier).getPath();
    }

    private MockSlingHttpServletRequest mockServletRequest(String resourcePath, boolean isRecursive)
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setResource(this.context.resourceResolver().getResource(resourcePath));

        return request;
    }

    private Bindings createBindings(SlingHttpServletRequest request)
    {
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        MockSlingScriptHelper sling = new MockSlingScriptHelper(request, response, this.slingBundleContext);

        Bindings bindings = new SimpleBindings();
        bindings.put(REQUEST, request);
        bindings.put(RESOLVER, this.resourceResolver);
        bindings.put(SLING, sling);
        return bindings;
    }

    private JsonObject getJsonObjectFromQueryBuilderContext()
    {
        JsonReader reader = Json.createReader(new StringReader(this.queryBuilder.getContent()));
        JsonObject object = reader.readObject();
        reader.close();
        return object;
    }

}
