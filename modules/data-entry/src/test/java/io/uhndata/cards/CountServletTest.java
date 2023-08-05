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
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.*;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CountServlet}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class CountServletTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String QUESTIONNAIRE_TYPE = "cards:Questionnaire";
    private static final String QUESTION_TYPE = "cards:Question";
    private static final String FORM_TYPE = "cards:Form";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String COUNT_PROPERTY = "count";
    private static final String COUNT_TYPE_PROPERTY = "countType";
    private static final String RESOURCE_TYPE_PROPERTY = "resourceType";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private CountServlet countServlet;

    private BundleContext slingBundleContext;

    private ResourceResolver resourceResolver;

    @Test
    public void doGetForExistingParametersWritesNotEmptyResponse() throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String questionnaireUuid = session.getNode(TEST_QUESTIONNAIRE_PATH).getIdentifier();

        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setResource(this.context.resourceResolver().getResource("/Forms"));
        request.setRemoteUser("admin");
        request.setParameterMap(Map.of(
                "includeallstatus", new String[]{"true"},
                "filternames", new String[]{QUESTIONNAIRE_TYPE},
                "filtervalues", new String[]{questionnaireUuid},
                "filtercomparators", new String[]{"="},
                "filtertypes", new String[]{QUESTIONNAIRE_PROPERTY},

                "fieldname", new String[]{NODE_TYPE},
                "fieldcomparator", new String[]{"="},
                "fieldvalue", new String[]{FORM_TYPE}));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.countServlet.doGet(request, response);
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals("application/json;charset=UTF-8", response.getContentType());

        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        JsonObject responseJson = reader.readObject();
        assertTrue(responseJson.containsKey(COUNT_PROPERTY));
        assertEquals(Json.createValue(1), responseJson.get(COUNT_PROPERTY));

        NodeIterator queryCacheNodeChildren = session.getNode("/QueryCache").getNodes();
        assertTrue(queryCacheNodeChildren.hasNext());
        Node queryCacheNode = queryCacheNodeChildren.nextNode();
        assertTrue(queryCacheNode.hasProperty(COUNT_TYPE_PROPERTY));
        assertEquals("=", queryCacheNode.getProperty(COUNT_TYPE_PROPERTY).getString());
        assertTrue(queryCacheNode.hasProperty(COUNT_PROPERTY));
        assertEquals(1, queryCacheNode.getProperty(COUNT_PROPERTY).getLong());
        assertTrue(queryCacheNode.hasProperty("time"));
        assertTrue(queryCacheNode.hasProperty(RESOURCE_TYPE_PROPERTY));
        assertEquals("Forms", queryCacheNode.getProperty(RESOURCE_TYPE_PROPERTY).getString());

        assertTrue(queryCacheNode.hasProperty("cards:Questionnaire="));
        assertEquals(questionnaireUuid, queryCacheNode.getProperty("cards:Questionnaire=").getString());

        assertTrue(queryCacheNode.hasProperty(NODE_TYPE + "="));
        assertEquals(FORM_TYPE, queryCacheNode.getProperty(NODE_TYPE + "=").getString());
    }

    @Test
    public void doGetForSpecialEmptyFilterWritesEmptyResponse() throws IOException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setResource(this.context.resourceResolver().getResource("/Forms"));
        request.setRemoteUser("admin");
        request.setParameterMap(Map.of("filterempty", new String[]{QUESTIONNAIRE_TYPE}));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.countServlet.doGet(request, response);
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals("application/json;charset=UTF-8", response.getContentType());

        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        JsonObject responseJson = reader.readObject();
        assertTrue(responseJson.containsKey(COUNT_PROPERTY));
        assertEquals(Json.createValue("0"), responseJson.get(COUNT_PROPERTY));
    }

    @Test
    public void doGetForNotSpecialEmptyAndNonEmptyFilterWritesNotEmptyResponse() throws IOException, RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setResource(this.context.resourceResolver().getResource("/Forms"));
        request.setRemoteUser("admin");
        request.setParameterMap(Map.of(
                "filterempty", new String[]{QUESTION_TYPE},
                "filternotempty", new String[]{SUBJECT_TYPE}));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.countServlet.doGet(request, response);
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals("application/json;charset=UTF-8", response.getContentType());

        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        JsonObject responseJson = reader.readObject();
        assertTrue(responseJson.containsKey(COUNT_PROPERTY));
        assertEquals(Json.createValue("0"), responseJson.get(COUNT_PROPERTY));

        NodeIterator queryCacheNodeChildren = session.getNode("/QueryCache").getNodes();
        assertTrue(queryCacheNodeChildren.hasNext());
        Node queryCacheNode = queryCacheNodeChildren.nextNode();
        assertTrue(queryCacheNode.hasProperty(COUNT_TYPE_PROPERTY));
        assertTrue(queryCacheNode.hasProperty(COUNT_PROPERTY));
        assertTrue(queryCacheNode.hasProperty("time"));
        assertTrue(queryCacheNode.hasProperty(RESOURCE_TYPE_PROPERTY));
        assertEquals("=", queryCacheNode.getProperty(COUNT_TYPE_PROPERTY).getString());
        assertEquals(0, queryCacheNode.getProperty(COUNT_PROPERTY).getLong());
        assertEquals("Forms", queryCacheNode.getProperty(RESOURCE_TYPE_PROPERTY).getString());

        assertTrue(queryCacheNode.hasProperty(SUBJECT_TYPE));
        assertEquals("is not empty", queryCacheNode.getProperty(SUBJECT_TYPE).getString());

        assertTrue(queryCacheNode.hasProperty(QUESTION_TYPE));
        assertEquals("is empty", queryCacheNode.getProperty(QUESTION_TYPE).getString());

    }

    @Test
    public void doGetForNotAdminRemoteUserWritesError() throws IOException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setRemoteUser("notAdmin");
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.countServlet.doGet(request, response);
        assertEquals(403, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());

        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        JsonObject responseJson = reader.readObject();
        assertTrue(responseJson.containsKey("status"));
        assertEquals(Json.createValue("error"), responseJson.get("status"));
        assertTrue(responseJson.containsKey("error"));
        assertEquals(Json.createValue("Only admin can perform this operation."), responseJson.get("error"));
    }

    @Test
    public void doGetForNotAdminRemoteUserCatchesException() throws IOException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setRemoteUser("notAdmin");
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(response.getWriter()).thenThrow(new IOException());
        this.countServlet.doGet(request, response);
        verify(response).setStatus(403);
        verify(response).setContentType("application/json;charset=UTF-8");
        verify(response).getWriter();
    }

    @Before
    public void setUp() throws RepositoryException, LoginException
    {
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
                //.resource("/QueryCache", NODE_TYPE, "sling:Folder")
                .resource("/QueryCache", NODE_TYPE, "cards:QueryCacheHomepage")
                .commit();
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTIONNAIRE_PATH + "/question_1");

        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        "subject", subject,
                        QUESTIONNAIRE_PROPERTY, questionnaire)
                .resource(TEST_FORM_PATH + "/a1",
                        NODE_TYPE, "cards:Answer",
                        "question", question)
                .commit();

        this.slingBundleContext = this.context.bundleContext();
        this.resourceResolver = this.slingBundleContext
                .getService(this.slingBundleContext.getServiceReference(ResourceResolverFactory.class))
                .getServiceResourceResolver(null);
    }
}
