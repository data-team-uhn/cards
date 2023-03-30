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
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
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
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for {@link FilterServlet}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class FilterServletTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";

    // Paths
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String TEST_TEXT_QUESTIONNAIRE_PATH = "/Questionnaires/TestTextQuestionnaire";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private FilterServlet filterServlet;

    private BundleContext slingBundleContext;

    private ResourceResolver resourceResolver;

    @Test
    public void doGetForRequestWithQuestionnaireParameterWithoutDeepJsonSuffix() throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest("/Questionnaires");
        request.setParameterMap(Map.of(
                QUESTIONNAIRE_PROPERTY, TEST_TEXT_QUESTIONNAIRE_PATH
        ));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.filterServlet.doGet(request, response);
        JsonObject jsonObject = getResponseJsonReader(response);
        assertNotNull(jsonObject);
        assertEquals(3, jsonObject.keySet().size());
    }

    @Test
    public void doGetForRequestWithQuestionnaireParameterWithDeepJsonSuffix() throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest("/Questionnaires");
        request.setParameterMap(Map.of(
                QUESTIONNAIRE_PROPERTY, TEST_TEXT_QUESTIONNAIRE_PATH + ".deep.json"
        ));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.filterServlet.doGet(request, response);
        JsonObject jsonObject = getResponseJsonReader(response);
        assertNotNull(jsonObject);
        assertEquals(3, jsonObject.keySet().size());
    }

    @Test
    public void doGetForRequestWithoutQuestionnaireParameter() throws IOException
    {
        MockSlingHttpServletRequest request = mockServletRequest("/Questionnaires");
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        this.filterServlet.doGet(request, response);
        JsonObject jsonObject = getResponseJsonReader(response);
        assertNotNull(jsonObject);
        assertEquals(11, jsonObject.keySet().size());
    }

    @Before
    public void setUp() throws LoginException
    {
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .commit();

        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/TextQuestionnaires.json", TEST_TEXT_QUESTIONNAIRE_PATH);

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

    private MockSlingHttpServletRequest mockServletRequest(String resourcePath)
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setResource(this.context.resourceResolver().getResource(resourcePath));
        return request;
    }

    private JsonObject getResponseJsonReader(MockSlingHttpServletResponse response)
    {
        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        return reader.readObject();
    }

}
