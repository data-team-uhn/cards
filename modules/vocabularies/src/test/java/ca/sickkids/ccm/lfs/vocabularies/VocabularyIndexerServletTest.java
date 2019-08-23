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

package ca.sickkids.ccm.lfs.vocabularies;

import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.testing.mock.jcr.MockJcr;
import org.apache.sling.testing.mock.sling.MockSling;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;

import com.google.common.base.Function;

@RunWith(MockitoJUnitRunner.class)
public class VocabularyIndexerServletTest
{
    @Rule
    public SlingContext context = new SlingContext();

    @InjectMocks
    private VocabularyIndexerServlet indexServlet;

    public void registerResourceToNodeAdapter(Session session)
    {
        this.context.registerAdapter(Resource.class, Node.class,
            new Function<Resource, Node>()
            {
                public Node apply(Resource resource)
                {
                    Node node;
                    try {
                        node = session.getNode(resource.getPath());
                    } catch (Exception e) {
                        try {
                            node = session.getNode("/");
                        } catch (RepositoryException e1) {
                            node = null;
                        }
                    }
                    return node;
                }
            }
        );
    }

    public void makeRequestResource(ResourceResolver resourceResolver)
        throws Exception
    {
        Resource root = resourceResolver.getResource("/");

        Map<String, Object> props = new HashMap<>();
        props.put("jcr:primaryType", "lfs:VocabulariesHomepage");

        resourceResolver.create(root, "Vocabularies", props);
    }

    public void makePost(MockSlingHttpServletRequest request, MockSlingHttpServletResponse response, String params)
        throws Exception
    {
        request.setQueryString(params);
        request.setMethod(HttpConstants.METHOD_POST);
        this.indexServlet.doPost(request, response);
    }

    @Test
    public void testNoVersionProvided()
        throws Exception
    {
        final Session session = MockJcr.newSession();
        registerResourceToNodeAdapter(session);

        BundleContext slingBundleContext = this.context.bundleContext();
        ResourceResolver resourceResolver = MockSling.newResourceResolver(slingBundleContext);

        makeRequestResource(resourceResolver);

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, slingBundleContext);
        request.setResource(resourceResolver.getResource("/Vocabularies"));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        String requestParams = "source=ncit&identifier=flatTestVocabulary&localpath=./flat_NCIT_type_testcase.zip";
        makePost(request, response, requestParams);

        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        JsonObject responseJson = reader.readObject();
        Assert.assertFalse(responseJson.getBoolean("isSuccessful"));
        String expectedError = "NCIT FLat parsing error: Mandatory version parameter not provided.";
        String obtainedError = responseJson.getString("errors");
        Assert.assertTrue(expectedError.equalsIgnoreCase(obtainedError));

        Node rootNode = request.getResource().adaptTo(Node.class);
        Assert.assertFalse(rootNode.hasNode("/flatTestvocabulary"));
    }

    @Test
    public void testInvalidFileLocation()
        throws Exception
    {
        final Session session = MockJcr.newSession();
        registerResourceToNodeAdapter(session);

        BundleContext slingBundleContext = this.context.bundleContext();
        ResourceResolver resourceResolver = MockSling.newResourceResolver(slingBundleContext);

        makeRequestResource(resourceResolver);

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, slingBundleContext);
        request.setResource(resourceResolver.getResource("/Vocabularies"));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        String requestParams = "source=ncit&identifier=flatTestVocabulary&version=19.05d&localpath=./someLocation";
        makePost(request, response, requestParams);

        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        JsonObject responseJson = reader.readObject();
        Assert.assertFalse(responseJson.getBoolean("isSuccessful"));
        String expectedError = "NCIT FLat parsing error: Error: Failed to load zip vocabulary locally. "
            + "./someLocation (No such file or directory)";
        String obtainedError = responseJson.getString("errors");
        Assert.assertTrue(expectedError.equalsIgnoreCase(obtainedError));

        Node rootNode = request.getResource().adaptTo(Node.class);
        Assert.assertFalse(rootNode.hasNode("/flatTestvocabulary"));
    }

    @Test
    public void testNCITFlatIndexing()
        throws Exception
    {
        final Session session = MockJcr.newSession();
        registerResourceToNodeAdapter(session);

        BundleContext slingBundleContext = this.context.bundleContext();
        ResourceResolver resourceResolver = MockSling.newResourceResolver(slingBundleContext);

        makeRequestResource(resourceResolver);

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, slingBundleContext);
        request.setResource(resourceResolver.getResource("/Vocabularies"));
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        String requestParams = "source=ncit&identifier=flatTestVocabulary&version=19.05d&localpath="
                + "./flat_NCIT_type_testcase.zip";
        makePost(request, response, requestParams);

        Node rootNode = request.getResource().adaptTo(Node.class);
        Node vocabNode = rootNode.getNode("flatTestVocabulary");

        ncitFlatTestVocabularyNode(vocabNode);
        ncitFlatTestC100005(vocabNode);
        ncitFlatTestC100008(vocabNode);
    }

    private void ncitFlatTestVocabularyNode(Node flatTestVocabulary)
        throws Exception
    {
        String[] expectedNodes = {"C100000", "C100001", "C100002", "C100003", "C100004", "C100005", "C100006",
            "C100007", "C100008", "C100009"};

        for (String nodeName : expectedNodes) {
            Assert.assertTrue(flatTestVocabulary.hasNode(nodeName));
        }

        Value obtainedNameValue = flatTestVocabulary.getProperty("name").getValue();
        Assert.assertTrue("National Cancer Institute Thesaurus".compareTo(obtainedNameValue.getString()) == 0);

        Value obtainedVersionValue = flatTestVocabulary.getProperty("version").getValue();
        Assert.assertTrue("19.05d".compareTo(obtainedVersionValue.getString()) == 0);
    }

    private void checkString(Node node, String propertyName, String correctString)
        throws Exception
    {
        Property obtainedProperty = node.getProperty(propertyName);
        String obtainedString = obtainedProperty.getString();

        Assert.assertTrue(correctString.compareTo(obtainedString) == 0);
    }

    private void checkStringArray(Node node, String propertyName, String[] correctStringArray)
        throws Exception
    {
        Property obtainedProperty = node.getProperty(propertyName);
        Value[] obtainedStringValues = obtainedProperty.getValues();

        Assert.assertTrue(obtainedStringValues.length == correctStringArray.length);

        Set<String> correctStringSet = new HashSet<String>();
        Set<String> obtainedStringSet = new HashSet<String>();

        for (String correctString : correctStringArray)
        {
            correctStringSet.add(correctString);
        }

        for (Value obtainedValue : obtainedStringValues)
        {
            obtainedStringSet.add(obtainedValue.getString());
        }

        Assert.assertTrue(correctStringSet.equals(obtainedStringSet));
    }

    private void ncitFlatTestC100008(Node flatTestVocabulary)
        throws Exception
    {
        Node c100008 = flatTestVocabulary.getNode("C100008");

        String description = "A percutaneous coronary intervention is imperative for a myocardial"
            + " infarction that presents with ST segment elevation after an unsatisfactory response to a"
            + " full dose of thrombolytic therapy. (ACC)";
        checkString(c100008, "description", description);

        String label = "Rescue Percutaneous Coronary Intervention for ST Elevation Myocardial "
            + "Infarction After Failed Full-Dose Thrombolytic Therapy";
        checkString(c100008, "label", label);

        String[] synonyms = {
            "Rescue Percutaneous Coronary Intervention for ST Elevation Myocardial Infarction After Failed "
                + "Full-Dose Thrombolytic Therapy",
            "RESCUE PERCUTANEOUS CORONARY INTERVENTION (PCI) FOR ST ELEVATION MYOCARDIAL INFARCTION (STEMI) "
                + "(AFTER FAILED FULL-DOSE THROMBOLYTICS)"
        };
        checkStringArray(c100008, "synonyms", synonyms);

        String[] parents = {"C100006", "C100007"};
        checkStringArray(c100008, "parents", parents);

        String[] ancestors = {"C100001", "C100002", "C100004", "C100005", "C100006", "C100007"};
        checkStringArray(c100008, "ancestors", ancestors);
    }

    private void ncitFlatTestC100005(Node flatTestVocabulary)
        throws Exception
    {
        Node c100005 = flatTestVocabulary.getNode("C100005");
        String description = "A procedure to evaluate the health of the an individual after "
            + "receiving a heart transplant. (ACC)";
        checkString(c100005, "description", description);

        String label = "Post-Cardiac Transplant Evaluation";
        checkString(c100005, "label", label);

        String[] synonyms = {
            "Post-Cardiac Transplant Evaluation",
            "POST-CARDIAC TRANSPLANT"
        };
        checkStringArray(c100005, "synonyms", synonyms);

        String[] parents = {"C100002"};
        checkStringArray(c100005, "parents", parents);

        String[] ancestors = {"C100002"};
        checkStringArray(c100005, "ancestors", ancestors);
    }
}
