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

    private void ncitFlatTestC100008(Node flatTestVocabulary)
        throws Exception
    {
        Node c100008 = flatTestVocabulary.getNode("C100008");
        String actualDescription = "A percutaneous coronary intervention is imperative for a myocardial"
            + " infarction that presents with ST segment elevation after an unsatisfactory response to a"
            + " full dose of thrombolytic therapy. (ACC)";
        Value obtainedDescriptionValue = c100008.getProperty("description").getValue();
        String obtainedDescription = obtainedDescriptionValue.getString();
        Assert.assertTrue(actualDescription.compareTo(obtainedDescription) == 0);

        String actualLabel = "Rescue Percutaneous Coronary Intervention for ST Elevation Myocardial "
            + "Infarction After Failed Full-Dose Thrombolytic Therapy";
        Value obtainedLabelValue = c100008.getProperty("label").getValue();
        String obtainedLabel = obtainedLabelValue.getString();
        Assert.assertTrue(actualLabel.compareTo(obtainedLabel) == 0);

        String[] actualSynonyms = {
            "Rescue Percutaneous Coronary Intervention for ST Elevation Myocardial Infarction After Failed "
                + "Full-Dose Thrombolytic Therapy",
            "RESCUE PERCUTANEOUS CORONARY INTERVENTION (PCI) FOR ST ELEVATION MYOCARDIAL INFARCTION (STEMI) "
                + "(AFTER FAILED FULL-DOSE THROMBOLYTICS)"
        };
        Value[] obtainedSynonymValues = c100008.getProperty("synonyms").getValues();
        for (int i = 0; i < obtainedSynonymValues.length; i++) {
            Assert.assertTrue(actualSynonyms[i].compareTo(obtainedSynonymValues[i].getString()) == 0);
        }

        String[] actualParents = {"C100006", "C100007"};
        Set<String> actualParentMap = new HashSet<String>();
        for (String parent : actualParents) {
            actualParentMap.add(parent);
        }
        Value[] obtainedParents = c100008.getProperty("ancestors").getValues();
        Set<String> obtainedParentMap = new HashSet<String>();
        for (Value parent : obtainedParents) {
            obtainedParentMap.add(parent.getString());
        }

        String[] actualAncestors = {"C100001", "C100002", "C100004", "C100005", "C100006", "C100007"};
        Set<String> actualAncestorMap = new HashSet<String>();
        for (String ancestor : actualAncestors) {
            actualAncestorMap.add(ancestor);
        }
        Value[] obtainedAncestors = c100008.getProperty("ancestors").getValues();
        Set<String> obtainedAncestorMap = new HashSet<String>();
        for (Value ancestor : obtainedAncestors) {
            obtainedAncestorMap.add(ancestor.getString());
        }
        Assert.assertTrue(actualAncestorMap.equals(obtainedAncestorMap));
    }

    private void ncitFlatTestC100005(Node flatTestVocabulary)
        throws Exception
    {
        Node c100005 = flatTestVocabulary.getNode("C100005");
        String actualDescription = "A procedure to evaluate the health of the an individual after "
            + "receiving a heart transplant. (ACC)";
        Value obtainedDescriptionValue = c100005.getProperty("description").getValue();
        String obtainedDescription = obtainedDescriptionValue.getString();
        Assert.assertTrue(actualDescription.compareTo(obtainedDescription) == 0);

        String actualLabel = "Post-Cardiac Transplant Evaluation";
        Value obtainedLabelValue = c100005.getProperty("label").getValue();
        String obtainedLabel = obtainedLabelValue.getString();
        Assert.assertTrue(actualLabel.compareTo(obtainedLabel) == 0);

        String[] actualSynonyms = {
            "Post-Cardiac Transplant Evaluation",
            "POST-CARDIAC TRANSPLANT"
        };
        Value[] obtainedSynonymValues = c100005.getProperty("synonyms").getValues();
        for (int i = 0; i < obtainedSynonymValues.length; i++) {
            Assert.assertTrue(actualSynonyms[i].compareTo(obtainedSynonymValues[i].getString()) == 0);
        }

        String[] actualParents = {"C100002"};
        Set<String> actualParentMap = new HashSet<String>();
        for (String parent : actualParents) {
            actualParentMap.add(parent);
        }
        Value[] obtainedParents = c100005.getProperty("ancestors").getValues();
        Set<String> obtainedParentMap = new HashSet<String>();
        for (Value parent : obtainedParents) {
            obtainedParentMap.add(parent.getString());
        }

        String[] actualAncestors = {"C100002"};
        Set<String> actualAncestorMap = new HashSet<String>();
        for (String ancestor : actualAncestors) {
            actualAncestorMap.add(ancestor);
        }
        Value[] obtainedAncestors = c100005.getProperty("ancestors").getValues();
        Set<String> obtainedAncestorMap = new HashSet<String>();
        for (Value ancestor : obtainedAncestors) {
            obtainedAncestorMap.add(ancestor.getString());
        }
        Assert.assertTrue(actualAncestorMap.equals(obtainedAncestorMap));
    }
}
