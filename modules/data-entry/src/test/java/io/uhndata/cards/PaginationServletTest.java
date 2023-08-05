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
package io.uhndata.cards;

import java.io.IOException;
import java.io.StringReader;
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
import javax.json.JsonReader;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link PaginationServlet}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class PaginationServletTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String CREATED_DATE_TYPE = "cards:CreatedDate";
    private static final String QUESTIONNAIRE_TYPE = "cards:Questionnaire";
    private static final String FORM_TYPE = "cards:Form";
    private static final String LONG_ANSWER_TYPE = "cards:LongAnswer";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String FORM_PROPERTY = "form";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String RELATED_SUBJECTS_PROPERTY = "relatedSubjects";
    private static final String VALUE_PROPERTY = "value";
    private static final String ADMIN_USERNAME = "admin";

    // Keys in Response Json
    private static final String ROWS_PROPERTY = "rows";
    private static final String OFFSET_PROPERTY = "offset";
    private static final String LIMIT_PROPERTY = "limit";
    private static final String RETURNED_ROWS_PROPERTY = "returnedrows";
    private static final String TOTAL_ROWS_PROPERTY = "totalrows";
    private static final String TOTAL_IS_APPROXIMATE_PROPERTY = "totalIsApproximate";

    // Search parameters
    private static final String INCLUDE_ALL_STATUS_PARAMETER = "includeallstatus";
    private static final String FILTER_NAMES_PARAMETER = "filternames";
    private static final String FILTER_VALUES_PARAMETER = "filtervalues";
    private static final String FILTER_COMPARATORS_PARAMETER = "filtercomparators";
    private static final String FILTER_TYPES_PARAMETER = "filtertypes";
    private static final String FILTER_NODE_TYPES_PARAMETER = "filternodetypes";
    private static final String REQUIRED_PARAMETER = "req";

    // Paths
    private static final String ROOT_FORM_PATH = "/Forms";
    private static final String ROOT_SUBJECT_PATH = "/Subjects";
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String TEST_TEXT_QUESTIONNAIRE_PATH = "/Questionnaires/TestTextQuestionnaire";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private PaginationServlet paginationServlet;

    private BundleContext slingBundleContext;

    private ResourceResolver resourceResolver;

    @Test
    public void doGetForFormsResourceAndQuestionnaireNotEmptyParameterAndEqualsComparatorWrites2Matches()
            throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String questionnaireUuid = session.getNode(TEST_QUESTIONNAIRE_PATH).getIdentifier();

        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of(
                INCLUDE_ALL_STATUS_PARAMETER, "true",
                FILTER_NAMES_PARAMETER, QUESTIONNAIRE_TYPE,
                FILTER_VALUES_PARAMETER, questionnaireUuid,
                FILTER_COMPARATORS_PARAMETER, "=",
                FILTER_TYPES_PARAMETER, QUESTIONNAIRE_PROPERTY,
                REQUIRED_PARAMETER, "2"
        ));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(2, rows.size());

        assertTrue(responseJson.containsKey(REQUIRED_PARAMETER));
        assertEquals("2", responseJson.getString(REQUIRED_PARAMETER));

        assertTrue(responseJson.containsKey(OFFSET_PROPERTY));
        assertEquals(0, responseJson.getInt(OFFSET_PROPERTY));

        assertTrue(responseJson.containsKey(LIMIT_PROPERTY));
        assertEquals(10, responseJson.getInt(LIMIT_PROPERTY));

        assertTrue(responseJson.containsKey(RETURNED_ROWS_PROPERTY));
        assertEquals(2, responseJson.getInt(RETURNED_ROWS_PROPERTY));

        assertTrue(responseJson.containsKey(TOTAL_ROWS_PROPERTY));
        assertEquals(2, responseJson.getInt(TOTAL_ROWS_PROPERTY));

        assertTrue(responseJson.containsKey(TOTAL_IS_APPROXIMATE_PROPERTY));
        assertEquals(Boolean.FALSE, responseJson.getBoolean(TOTAL_IS_APPROXIMATE_PROPERTY));
    }

    @Test
    public void doGetForFormsResourceAndQuestionNotEmptyParametersAndBlankFilterTypeAndEqualsComparatorsWrites1Match()
            throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String questionUuid = session.getNode(TEST_QUESTIONNAIRE_PATH + "/question_1").getIdentifier();

        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of(
                INCLUDE_ALL_STATUS_PARAMETER, "true",
                FILTER_NAMES_PARAMETER, questionUuid,
                FILTER_VALUES_PARAMETER, "100",
                FILTER_COMPARATORS_PARAMETER, "=",
                FILTER_TYPES_PARAMETER, "",
                FILTER_NODE_TYPES_PARAMETER, "cards:Answer",
                REQUIRED_PARAMETER, "2"
        ));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(1, rows.size());
    }

    @Test
    public void doGetForFormsResourceAndDifferentNumberOfNamesAndValueParametersCatchesIllegalArgumentException()
            throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String questionUuid = session.getNode(TEST_QUESTIONNAIRE_PATH + "/question_1").getIdentifier();

        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of(
                INCLUDE_ALL_STATUS_PARAMETER, "true",
                FILTER_NAMES_PARAMETER, new String[]{questionUuid, QUESTIONNAIRE_TYPE},
                FILTER_VALUES_PARAMETER, "100",
                FILTER_COMPARATORS_PARAMETER, "=",
                FILTER_TYPES_PARAMETER, "",
                FILTER_NODE_TYPES_PARAMETER, "cards:Answer",
                REQUIRED_PARAMETER, "2"
        ));

        MockSlingHttpServletResponse primaryResponse = new MockSlingHttpServletResponse();
        MockSlingHttpServletResponse changeableResponse = primaryResponse;

        this.paginationServlet.doGet(request, changeableResponse);
        assertEquals(primaryResponse, changeableResponse);
    }

    @Test
    public void doGetForFormsResourceAndQuestionNotEmptyParametersAndContainsComparatorsWrites1Match()
            throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String questionUuid = session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH + "/question_1").getIdentifier();

        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of(
                INCLUDE_ALL_STATUS_PARAMETER, "true",
                "descending", "true",
                FILTER_NAMES_PARAMETER, questionUuid,
                FILTER_VALUES_PARAMETER, "12345",
                FILTER_COMPARATORS_PARAMETER, "contains",
                FILTER_TYPES_PARAMETER, "text",
                FILTER_NODE_TYPES_PARAMETER, "cards:Answer",
                REQUIRED_PARAMETER, "2"
        ));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        // assertEquals(1, rows.size());
    }

    @Test
    public void doGetForFormsResourceAndQuestionNotEmptyParametersAndNotesContainsComparatorsWrites1Match()
            throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String questionUuid = session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH + "/question_1").getIdentifier();

        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of(
                INCLUDE_ALL_STATUS_PARAMETER, "true",
                FILTER_NAMES_PARAMETER, questionUuid,
                FILTER_VALUES_PARAMETER, "text",
                FILTER_COMPARATORS_PARAMETER, "notes contain",
                FILTER_TYPES_PARAMETER, "text",
                FILTER_NODE_TYPES_PARAMETER, "cards:Answer",
                REQUIRED_PARAMETER, "2"
        ));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        // assertEquals(1, rows.size());
    }

    @Test
    public void doGetForSubjectsResourceAndQuestionNotEmptyParametersAndEqualsComparatorsWrites1Match()
            throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String questionUuid = session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH + "/question_1").getIdentifier();

        MockSlingHttpServletRequest request = mockServletRequest(ROOT_SUBJECT_PATH);
        request.setParameterMap(Map.of(
                INCLUDE_ALL_STATUS_PARAMETER, "true",
                FILTER_NAMES_PARAMETER, questionUuid,
                FILTER_VALUES_PARAMETER, "12345",
                FILTER_COMPARATORS_PARAMETER, "=",
                FILTER_TYPES_PARAMETER, "text",
                FILTER_NODE_TYPES_PARAMETER, "cards:Answer",
                REQUIRED_PARAMETER, "2"
        ));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        // assertEquals(1, rows.size());
    }

    @Test
    public void doGetForFormsResourceAndSubjectNotEmptyParameterAndEqualsComparatorWrites1Match()
            throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String subjectUuid = session.getNode(TEST_SUBJECT_PATH + "/b1").getIdentifier();

        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of(
                INCLUDE_ALL_STATUS_PARAMETER, "true",
                FILTER_NAMES_PARAMETER, SUBJECT_TYPE,
                FILTER_VALUES_PARAMETER, subjectUuid,
                FILTER_COMPARATORS_PARAMETER, "=",
                FILTER_TYPES_PARAMETER, SUBJECT_PROPERTY,
                REQUIRED_PARAMETER, "2"
        ));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(1, rows.size());

        assertTrue(responseJson.containsKey(REQUIRED_PARAMETER));
        assertEquals("2", responseJson.getString(REQUIRED_PARAMETER));

        assertTrue(responseJson.containsKey(OFFSET_PROPERTY));
        assertEquals(0, responseJson.getInt(OFFSET_PROPERTY));

        assertTrue(responseJson.containsKey(LIMIT_PROPERTY));
        assertEquals(10, responseJson.getInt(LIMIT_PROPERTY));

        assertTrue(responseJson.containsKey(RETURNED_ROWS_PROPERTY));
        assertEquals(1, responseJson.getInt(RETURNED_ROWS_PROPERTY));

        assertTrue(responseJson.containsKey(TOTAL_ROWS_PROPERTY));
        assertEquals(1, responseJson.getInt(TOTAL_ROWS_PROPERTY));

        assertTrue(responseJson.containsKey(TOTAL_IS_APPROXIMATE_PROPERTY));
        assertEquals(Boolean.FALSE, responseJson.getBoolean(TOTAL_IS_APPROXIMATE_PROPERTY));
    }

    @Test
    public void doGetForSubjectsResourceAndCreatedDateParameterAndLessOrEqualsComparatorWrites2Matches()
            throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(ROOT_SUBJECT_PATH);
        request.setParameterMap(generateParameterMapWithCreatedDateFilter("<="));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        JsonObject responseJson = getResponseJsonReader(response);

        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(2, rows.size());
    }

    @Test
    public void doGetForSubjectsResourceAndCreatedDateParameterAndMoreOrEqualsComparatorWrites2Matches()
            throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(ROOT_SUBJECT_PATH);
        request.setParameterMap(generateParameterMapWithCreatedDateFilter(">="));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        JsonObject responseJson = getResponseJsonReader(response);

        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(2, rows.size());
    }

    @Test
    public void doGetForSubjectsResourceAndCreatedDateParameterAndEqualsComparatorWrites2Matches()
            throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(ROOT_SUBJECT_PATH);
        request.setParameterMap(generateParameterMapWithCreatedDateFilter("="));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        JsonObject responseJson = getResponseJsonReader(response);

        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(2, rows.size());
    }

    @Test
    public void doGetForSubjectsResourceAndCreatedDateParameterAndNotEqualsComparatorWritesNoMatches()
            throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(ROOT_SUBJECT_PATH);
        request.setParameterMap(generateParameterMapWithCreatedDateFilter("<>"));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        JsonObject responseJson = getResponseJsonReader(response);

        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(0, rows.size());
    }

    @Test
    public void doGetForSubjectsResourceAndCreatedDateParameterAndLessComparatorWritesNoMatches()
            throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(ROOT_SUBJECT_PATH);
        request.setParameterMap(generateParameterMapWithCreatedDateFilter("<"));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        JsonObject responseJson = getResponseJsonReader(response);

        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(0, rows.size());
    }

    @Test
    public void doGetForSubjectsResourceAndCreatedDateParameterAndMoreComparatorWritesNoMatches()
            throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(ROOT_SUBJECT_PATH);
        request.setParameterMap(generateParameterMapWithCreatedDateFilter(">"));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        JsonObject responseJson = getResponseJsonReader(response);

        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(0, rows.size());
    }

    @Test
    public void doGetForFormsResourceAndFieldParametersAndWithoutIncludeAllParameterWritesNoMatches()
            throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String formUuid = session.getNode("/Forms/f1").getIdentifier();

        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of(
                "fieldname", "jcr:uuid",
                "fieldcomparator", "=",
                "fieldvalue", formUuid
        ));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(0, rows.size());

        assertTrue(responseJson.containsKey(REQUIRED_PARAMETER));
        assertTrue(responseJson.getString(REQUIRED_PARAMETER).isBlank());

        assertTrue(responseJson.containsKey(OFFSET_PROPERTY));
        assertEquals(0, responseJson.getInt(OFFSET_PROPERTY));

        assertTrue(responseJson.containsKey(LIMIT_PROPERTY));
        assertEquals(10, responseJson.getInt(LIMIT_PROPERTY));

        assertTrue(responseJson.containsKey(RETURNED_ROWS_PROPERTY));
        assertEquals(0, responseJson.getInt(RETURNED_ROWS_PROPERTY));

        assertTrue(responseJson.containsKey(TOTAL_ROWS_PROPERTY));
        assertEquals(0, responseJson.getInt(TOTAL_ROWS_PROPERTY));

        assertTrue(responseJson.containsKey(TOTAL_IS_APPROXIMATE_PROPERTY));
        assertEquals(Boolean.FALSE, responseJson.getBoolean(TOTAL_IS_APPROXIMATE_PROPERTY));
    }

    @Test
    public void doGetForFormsResourceAndQuestionnaireEmptyParameterWritesEmptyResponse()
            throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of("filterempty", QUESTIONNAIRE_TYPE));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(0, rows.size());

        assertTrue(responseJson.containsKey(REQUIRED_PARAMETER));
        assertTrue(responseJson.getString(REQUIRED_PARAMETER).isBlank());

        assertTrue(responseJson.containsKey(OFFSET_PROPERTY));
        assertEquals(0, responseJson.getInt(OFFSET_PROPERTY));

        assertTrue(responseJson.containsKey(LIMIT_PROPERTY));
        assertEquals(10, responseJson.getInt(LIMIT_PROPERTY));

        assertTrue(responseJson.containsKey(RETURNED_ROWS_PROPERTY));
        assertEquals(0, responseJson.getInt(RETURNED_ROWS_PROPERTY));

        assertTrue(responseJson.containsKey(TOTAL_ROWS_PROPERTY));
        assertEquals(0, responseJson.getInt(TOTAL_ROWS_PROPERTY));

        assertTrue(responseJson.containsKey(TOTAL_IS_APPROXIMATE_PROPERTY));
        assertEquals(Boolean.FALSE, responseJson.getBoolean(TOTAL_IS_APPROXIMATE_PROPERTY));
    }

    @Test
    public void doGetForFormsResourceAndSubjectEmptyParameterWritesEmptyResponse()
            throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of("filterempty", SUBJECT_TYPE));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(0, rows.size());
    }

    @Test
    public void doGetForFormsResourceAndCreatedDateEmptyParameterWritesEmptyResponse()
            throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(ROOT_FORM_PATH);
        request.setParameterMap(Map.of("filterempty", CREATED_DATE_TYPE));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.paginationServlet.doGet(request, response);
        assertCharacterEncodingAndContentType(response);

        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(ROWS_PROPERTY));
        assertTrue(responseJson.get(ROWS_PROPERTY) instanceof JsonArray);
        JsonArray rows = responseJson.getJsonArray(ROWS_PROPERTY);
        assertEquals(0, rows.size());
    }

    @Test
    public void doGetForFormsResourceCatchesNullPointerException() throws IOException
    {
        ResourceResolver resolver = mock(ResourceResolver.class);
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(resolver, this.slingBundleContext);
        request.setResource(this.context.resourceResolver().getResource("/Forms"));
        request.setRemoteUser(ADMIN_USERNAME);
        request.setParameterMap(Map.of("filternotempty", CREATED_DATE_TYPE));

        MockSlingHttpServletResponse primaryResponse = new MockSlingHttpServletResponse();
        MockSlingHttpServletResponse changeableResponse = primaryResponse;

        this.paginationServlet.doGet(request, changeableResponse);
        assertEquals(primaryResponse, changeableResponse);
    }

    @Before
    public void setUp() throws RepositoryException, LoginException
    {
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource(ROOT_SUBJECT_PATH, NODE_TYPE, "cards:SubjectsHomepage")
                .resource(ROOT_FORM_PATH, NODE_TYPE, "cards:FormsHomepage")
                .commit();
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/TextQuestionnaires.json", TEST_TEXT_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        "type", this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .resource(TEST_SUBJECT_PATH + "/b1",
                        NODE_TYPE, SUBJECT_TYPE,
                        "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class))
                .commit();
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node branchSubject = session.getNode(TEST_SUBJECT_PATH + "/b1");
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTIONNAIRE_PATH + "/question_1");

        this.context.build()
                .resource("/Forms/f1",
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, subject,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        RELATED_SUBJECTS_PROPERTY, List.of(subject).toArray())

                .resource("/Forms/f2",
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, branchSubject,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        RELATED_SUBJECTS_PROPERTY, List.of(subject, branchSubject).toArray())

                .resource("/Forms/f3",
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, subject,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH),
                        RELATED_SUBJECTS_PROPERTY, List.of(subject).toArray())
                .commit();

        // create Answers for each Form with "form" property
        this.context.build()
                .resource("/Forms/f1/a1",
                        NODE_TYPE, LONG_ANSWER_TYPE,
                        QUESTION_PROPERTY, question,
                        VALUE_PROPERTY, 100,
                        FORM_PROPERTY, session.getNode("/Forms/f1").getIdentifier())
                .resource("/Forms/f2/a1",
                        NODE_TYPE, LONG_ANSWER_TYPE,
                        QUESTION_PROPERTY, question,
                        VALUE_PROPERTY, 200,
                        FORM_PROPERTY, session.getNode("/Forms/f2").getIdentifier())
                .resource("/Forms/f3/a1",
                        NODE_TYPE, "cards:TextAnswer",
                        QUESTION_PROPERTY, session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH + "/question_1"),
                        VALUE_PROPERTY, "12345",
                        "note", "some notes",
                        FORM_PROPERTY, session.getNode("/Forms/f3").getIdentifier())
                .commit();

        this.slingBundleContext = this.context.bundleContext();
        this.resourceResolver = this.slingBundleContext
                .getService(this.slingBundleContext.getServiceReference(ResourceResolverFactory.class))
                .getServiceResourceResolver(null);

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
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        for (Map.Entry<String, Object> property : valueMap.entrySet()) {
            String key = property.getKey();
            Object value = property.getValue();
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
        return propertiesAndChildrenMap;
    }

    private Map<String, Object> generateParameterMapWithCreatedDateFilter(String comparator)
    {
        return Map.of(
                FILTER_NAMES_PARAMETER, CREATED_DATE_TYPE,
                FILTER_VALUES_PARAMETER, getFormattedCurrentDateTime(),
                FILTER_COMPARATORS_PARAMETER, comparator,
                FILTER_TYPES_PARAMETER, "createddate"
        );
    }

    private MockSlingHttpServletRequest mockServletRequest(String resourcePath)
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setResource(this.context.resourceResolver().getResource(resourcePath));
        request.setRemoteUser(ADMIN_USERNAME);
        return request;
    }

    private String getFormattedCurrentDateTime()
    {
        Calendar date = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX");
        formatter.setTimeZone(date.getTimeZone());
        return formatter.format(date.getTime());
    }

    private JsonObject getResponseJsonReader(MockSlingHttpServletResponse response)
    {
        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        return reader.readObject();
    }

    private void assertCharacterEncodingAndContentType(MockSlingHttpServletResponse response)
    {
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
    }

}
