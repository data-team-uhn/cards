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

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.version.VersionManager;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeleteServlet}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class DeleteServletTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String SUBJECT_TYPE_TYPE = "cards:SubjectType";
    private static final String QUESTIONNAIRE_TYPE = "cards:Questionnaire";
    private static final String FORM_TYPE = "cards:Form";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String RELATED_SUBJECTS_PROPERTY = "relatedSubjects";
    private static final String TYPE_PROPERTY = "type";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String LABEL_PROPERTY = "label";
    private static final String STATUS_CODE = "status.code";

    // Paths
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";
    private static final String ROOT_SUBJECT_TYPE = "/SubjectTypes/Root";
    private static final String BRANCH_SUBJECT_TYPE = "/SubjectTypes/Root/Branch";
    private static final String LEAF_SUBJECT_TYPE = "/SubjectTypes/Root/Branch/Leaf";
    private static final String PATIENT_SUBJECT_TYPE = "/SubjectTypes/Patient";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String TEST_TEXT_QUESTIONNAIRE_PATH = "/Questionnaires/TestTextQuestionnaire";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private DeleteServlet deleteServlet;

    private BundleContext slingBundleContext;

    private ResourceResolver resourceResolver;

    @Test
    public void doDeleteNotRecursiveForFormResourceWithItsSectionAndAnswerChildren() throws ServletException,
            IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest("/Forms/f1", false);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        ResourceResolver resolver = this.context.resourceResolver();
        this.deleteServlet.doDelete(request, response);
        assertNull(resolver.getResource("/Forms/f1"));
        assertNull(resolver.getResource("/Forms/f1/s1"));
        assertNull(resolver.getResource("/Forms/f1/s1/a6"));
    }

    @Test
    public void doDeleteNotRecursiveForSubjectResourceWithReferencesSendsJsonError() throws ServletException,
            IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(TEST_SUBJECT_PATH, false);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        ResourceResolver resolver = this.context.resourceResolver();
        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);
        assertConflictResponseError(response, "This item is referenced in 2 forms.");
        assertNotNull(resolver.getResource(TEST_SUBJECT_PATH));
    }

    @Test
    public void doDeleteNotRecursiveForSubjectTypeResourceWithMultipleTypesReferencesSendsJsonError()
            throws ServletException, IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node questionnaire = session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH);
        Node rootSubjectType = session.getNode(ROOT_SUBJECT_TYPE);
        questionnaire.setProperty("requiredSubjectTypes", rootSubjectType.getIdentifier(), PropertyType.REFERENCE);

        MockSlingHttpServletRequest request = mockServletRequest(ROOT_SUBJECT_TYPE, false);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        ResourceResolver resolver = this.context.resourceResolver();
        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);
        assertConflictResponseError(response,
                "This item is referenced in 2 forms and 2 subjects (Root Subject and Branch Subject).");
        assertNotNull(resolver.getResource(ROOT_SUBJECT_TYPE));
    }

    @Test
    public void doDeleteNotRecursiveForSectionResourceWithReferencesSendsJsonError() throws ServletException,
            IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(TEST_QUESTIONNAIRE_PATH + "/section_1", false);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        ResourceResolver resolver = this.context.resourceResolver();
        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);
        assertConflictResponseError(response, "This item is referenced in 1 answer section.");
        assertNotNull(resolver.getResource(TEST_QUESTIONNAIRE_PATH + "/section_1"));
    }

    @Test
    public void doDeleteNotRecursiveForQuestionResourceWithReferencesSendsJsonError() throws ServletException,
            IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(TEST_QUESTIONNAIRE_PATH + "/section_1/question_6",
                false);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        ResourceResolver resolver = this.context.resourceResolver();
        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);
        assertConflictResponseError(response, "This item is referenced in 1 answer.");
        assertNotNull(resolver.getResource(TEST_QUESTIONNAIRE_PATH + "/section_1/question_6"));
    }

    @Test
    public void doDeleteNotRecursiveForSubjectTypeResourceWithReferencesInSubjectSendsJsonError()
            throws ServletException, IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(BRANCH_SUBJECT_TYPE, false);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        ResourceResolver resolver = this.context.resourceResolver();
        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);
        assertConflictResponseError(response, "This item is referenced in 1 subject (Branch Subject).");
        assertNotNull(resolver.getResource(BRANCH_SUBJECT_TYPE));
    }

    @Test
    public void doDeleteNotRecursiveForSubjectTypeResourceWithReferencesInSubjectTypeSendsJsonError()
            throws ServletException, IOException
    {
        this.context.build()
                .resource(PATIENT_SUBJECT_TYPE + "/Visit",
                        NODE_TYPE, SUBJECT_TYPE_TYPE,
                        LABEL_PROPERTY, "Visit",
                        "subjectListLabel", "Visits",
                        "cards:defaultOrder", 1,
                        "reference",
                        this.context.resourceResolver().getResource(LEAF_SUBJECT_TYPE).adaptTo(Node.class))
                .commit();
        MockSlingHttpServletRequest request = mockServletRequest(LEAF_SUBJECT_TYPE, false);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        ResourceResolver resolver = this.context.resourceResolver();
        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);
        assertConflictResponseError(response, "This item is referenced in 1 subject type (Visit).");
        assertNotNull(resolver.getResource(LEAF_SUBJECT_TYPE));
    }

    @Test
    public void doDeleteNotRecursiveForSubjectTypeResourceWithReferencesInQuestionnaireSendsJsonError()
            throws ServletException, IOException
    {
        this.context.build()
                .resource("/Questionnaires/TestReferenceQuestionnaire",
                        NODE_TYPE, QUESTIONNAIRE_TYPE,
                        "title", "Test Reference Questionnaire",
                        "description", "A test reference questionnaire",
                        "requiredSubjectTypes",
                        this.context.resourceResolver().getResource(LEAF_SUBJECT_TYPE).adaptTo(Node.class))
                .commit();
        MockSlingHttpServletRequest request = mockServletRequest(LEAF_SUBJECT_TYPE, false);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        ResourceResolver resolver = this.context.resourceResolver();
        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);
        assertConflictResponseError(response,
                "This item is referenced in 1 questionnaire (Test Reference Questionnaire).");
        assertNotNull(resolver.getResource(LEAF_SUBJECT_TYPE));
    }

    @Test
    public void doDeleteRecursiveForQuestionnaireResourceWithReferences() throws ServletException, IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest(TEST_TEXT_QUESTIONNAIRE_PATH, true);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        ResourceResolver resolver = this.context.resourceResolver();
        this.deleteServlet.doDelete(request, response);
        assertNull(resolver.getResource(TEST_TEXT_QUESTIONNAIRE_PATH));
        assertNull(resolver.getResource("/Forms/f2"));
    }

    @Test
    public void doDeleteCatchesRepositoryExceptionAndSendsJsonError() throws RepositoryException, ServletException,
            IOException
    {
        ResourceResolver resolver = mock(ResourceResolver.class);
        Session mockedSession = mock(Session.class);
        Workspace mockedWorkspace = mock(Workspace.class);
        when(resolver.adaptTo(Session.class)).thenReturn(mockedSession);
        when(mockedSession.getWorkspace()).thenReturn(mockedWorkspace);
        when(mockedWorkspace.getVersionManager()).thenThrow(new RepositoryException());

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resolver, this.slingBundleContext);
        request.setResource(this.resourceResolver.getResource("/Forms/f1"));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(STATUS_CODE));
        assertEquals(500, responseJson.getInt(STATUS_CODE));

        assertTrue(responseJson.containsKey("error"));
        JsonObject error = responseJson.get("error").asJsonObject();
        assertEquals(2, error.size());
        assertTrue(error.containsKey("message"));
        assertTrue(error.containsKey("class"));
        assertEquals("javax.jcr.RepositoryException", error.getString("class"));
    }

    @Test
    public void doDeleteForEmptyRemoteUserCatchesAccessDeniedExceptionAndSendsJsonError() throws RepositoryException,
            ServletException, IOException
    {
        ResourceResolver resolver = mock(ResourceResolver.class);
        Session mockedSession = mock(Session.class);
        Workspace mockedWorkspace = mock(Workspace.class);
        VersionManager mockedVersionManager = mock(VersionManager.class);
        when(resolver.adaptTo(Session.class)).thenReturn(mockedSession);
        when(mockedSession.getWorkspace()).thenReturn(mockedWorkspace);
        when(mockedWorkspace.getVersionManager()).thenReturn(mockedVersionManager);
        doThrow(new AccessDeniedException()).when(mockedSession).save();

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resolver, this.slingBundleContext);
        request.setParameterMap(Map.of("recursive", false));
        request.setResource(this.resourceResolver.getResource("/Forms/f1"));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(STATUS_CODE));
        assertEquals(401, responseJson.getInt(STATUS_CODE));
    }

    @Test
    public void doDeleteForNotEmptyRemoteUserCatchesAccessDeniedExceptionAndSendsJsonError() throws RepositoryException,
            ServletException, IOException
    {
        ResourceResolver resolver = mock(ResourceResolver.class);
        Session mockedSession = mock(Session.class);
        Workspace mockedWorkspace = mock(Workspace.class);
        VersionManager mockedVersionManager = mock(VersionManager.class);
        when(resolver.adaptTo(Session.class)).thenReturn(mockedSession);
        when(mockedSession.getWorkspace()).thenReturn(mockedWorkspace);
        when(mockedWorkspace.getVersionManager()).thenReturn(mockedVersionManager);
        doThrow(new AccessDeniedException()).when(mockedSession).save();

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resolver, this.slingBundleContext);
        request.setParameterMap(Map.of("recursive", false));
        request.setResource(this.resourceResolver.getResource("/Forms/f1"));
        request.setRemoteUser("notAdmin");
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.deleteServlet.doDelete(request, response);
        assertCharacterEncodingAndContentType(response);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(STATUS_CODE));
        assertEquals(403, responseJson.getInt(STATUS_CODE));
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
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_6"))
                .resource("/Forms/f2",
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, rootSubject,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH),
                        RELATED_SUBJECTS_PROPERTY, List.of(rootSubject).toArray())
                .commit();

        this.slingBundleContext = this.context.bundleContext();
        this.resourceResolver = this.slingBundleContext
                .getService(this.slingBundleContext.getServiceReference(ResourceResolverFactory.class))
                .getServiceResourceResolver(null);
    }


    private MockSlingHttpServletRequest mockServletRequest(String resourcePath, boolean isRecursive)
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setResource(this.context.resourceResolver().getResource(resourcePath));
        request.setParameterMap(Map.of(
                "recursive", isRecursive
        ));
        return request;
    }

    private JsonObject getResponseJsonReader(MockSlingHttpServletResponse response)
    {
        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        return reader.readObject();
    }

    private void assertConflictResponseError(MockSlingHttpServletResponse response, String expectedMessage)
    {
        assertEquals(HttpServletResponse.SC_CONFLICT, response.getStatus());
        JsonObject responseJson = getResponseJsonReader(response);
        assertTrue(responseJson.containsKey(STATUS_CODE));
        assertEquals(409, responseJson.getInt(STATUS_CODE));
        assertTrue(responseJson.containsKey("status.message"));
        assertEquals(expectedMessage, responseJson.getString("status.message"));
    }


    private void assertCharacterEncodingAndContentType(MockSlingHttpServletResponse response)
    {
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
    }

}
