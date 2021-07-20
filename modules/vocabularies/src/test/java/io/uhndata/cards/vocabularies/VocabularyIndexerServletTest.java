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

package io.uhndata.cards.vocabularies;

import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

import org.apache.commons.lang3.SystemUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.testing.mock.jcr.MockJcr;
import org.apache.sling.testing.mock.sling.MockSling;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;

import com.google.common.base.Function;
import io.uhndata.cards.vocabularies.internal.NCITFlatIndexer;
import io.uhndata.cards.vocabularies.spi.VocabularyIndexer;
import io.uhndata.cards.vocabularies.spi.VocabularyParserUtils;

/**
 * Unit tests for VocabularyIndexerServlet.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class VocabularyIndexerServletTest
{
    @Rule
    public SlingContext context = new SlingContext();

    @Mock
    private List<VocabularyIndexer> parsers;

    @InjectMocks
    private VocabularyIndexerServlet indexServlet;

    @InjectMocks
    private NCITFlatIndexer flatParser;

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private VocabularyParserUtils parserUtils;

    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);
        List<VocabularyIndexer> realParsers = Collections.singletonList(this.flatParser);
        Mockito.when(this.parsers.iterator()).thenReturn(realParsers.iterator());
    }

    /**
     * Registers a {@link org.apache.sling.api.resource.Resource} to {@link javax.jcr.node} adapter. At present, this
     * does not seem to be fully functional yet.
     *
     * @param session - MockJcr session used in test
     */
    public void registerResourceToNodeAdapter(Session session)
    {
        // Register the given google function
        this.context.registerAdapter(Resource.class, Node.class,
            new Function<Resource, Node>()
            {
                @Override
                public Node apply(Resource resource)
                {
                    // Take in a resource and try to do the following:
                    Node node;
                    try {
                        // Return the node at the same path as the resource
                        node = session.getNode(resource.getPath());
                    } catch (Exception e) {
                        try {
                            // If node can't be found at the same path as the resource, return the root node
                            node = session.getNode("/");
                        } catch (RepositoryException e1) {
                            // If the root node can't be found, return a null node
                            node = null;
                        }
                    }
                    return node;
                }
            });
    }

    /**
     * Creates a mock <code>VocabulariesHomepage</code> node <code>/Vocabularies</code> to act as the resource of the
     * request sent to the {@link VocabularyIndexerServlet}.
     *
     * @param resourceResolver - resource resolver of the MockSling instance
     * @throws Exception thrown if resource cannot be created
     */
    public void makeRequestResource(ResourceResolver resourceResolver)
        throws Exception
    {
        // Get root node
        Resource root = resourceResolver.getResource("/");

        // Set properties of new node to be created
        Map<String, Object> props = new HashMap<>();
        props.put("jcr:primaryType", "cards:VocabulariesHomepage");

        // Create the new node at /Vocabularies
        resourceResolver.create(root, "Vocabularies", props);
    }

    /**
     * Makes a POST request to an instance of the {@link VocabularyIndexerServlet} using the parameters given.
     *
     * @param request - mock http request to pass into servlet
     * @param response - mock http response to pass into servlet
     * @param params - parameters that the mock http request is to have
     * @throws Exception if request made fails
     */
    public void makePost(MockSlingHttpServletRequest request, MockSlingHttpServletResponse response, String params)
        throws Exception
    {
        // Configure POST request using given parameters
        request.setQueryString(params);
        request.setMethod(HttpConstants.METHOD_POST);

        // Execute POST request using the class's instance of the VocabularyIndexerServlet
        this.indexServlet.doPost(request, response);
    }

    /**
     * Method which gets a String property from a node and compares it with the correct String value.
     *
     * @param node - JCR node instance
     * @param propertyName - name of the String property we want to get from the node
     * @param correctString - correct value of the String property
     * @throws Exception thrown when property cannot be obtained or if the Strings do not match
     */
    private void checkString(Node node, String propertyName, String correctString)
        throws Exception
    {
        Property obtainedProperty = node.getProperty(propertyName);
        String obtainedString = obtainedProperty.getString();

        Assert.assertTrue(correctString.compareTo(obtainedString) == 0);
    }

    /**
     * Method which gets a String[] property from a node and compares it with the correct String[] value. Only the
     * contents are compared; the order of the contents does not matter. The arrays are loaded in to String set
     * instances and these are compared. Since sets are unordered, it allows the order of the array elements to be
     * neglected.
     *
     * @param node - JCR node instance
     * @param propertyName - name of the String[] property we want to get from the node
     * @param correctStringArray - correct value of the String[] property
     * @throws Exception when the contents of the arrays do not equal or if the String[] property cannot be obtained
     */
    private void checkStringArray(Node node, String propertyName, String[] correctStringArray)
        throws Exception
    {
        // Get value array from the node
        Property obtainedProperty = node.getProperty(propertyName);
        Value[] obtainedStringValues = obtainedProperty.getValues();

        // Check if the arrays have the same number of terms
        Assert.assertTrue(obtainedStringValues.length == correctStringArray.length);

        // Load the respective arrays into sets
        Set<String> correctStringSet = new HashSet<>();
        Set<String> obtainedStringSet = new HashSet<>();

        for (String correctString : correctStringArray) {
            correctStringSet.add(correctString);
        }

        for (Value obtainedValue : obtainedStringValues) {
            // Convert the value to String before putting into set
            obtainedStringSet.add(obtainedValue.getString());
        }

        // Assert that the sets are identical
        Assert.assertTrue(correctStringSet.equals(obtainedStringSet));
    }

    /**
     * Tests {@link VocabularyIndexerServlet} response to a request with the mandatory version parameter missing.
     *
     * @throws Exception when an unexpected response is returned or the request has failed
     */
    @Test
    public void testNoVersionProvided()
        throws Exception
    {
        // Set up mock repository

        // Instantiate a MockJcr session and register a Resource to Node adapter to it
        final Session session = MockJcr.newSession();
        registerResourceToNodeAdapter(session);

        // BundleContext and ResourceResolver for creating resources and instantiating requests
        BundleContext slingBundleContext = this.context.bundleContext();
        ResourceResolver resourceResolver = MockSling.newResourceResolver(slingBundleContext);

        // Create a mock VocabulariesHomepage node /Vocabularies to act as the resource for the request
        makeRequestResource(resourceResolver);

        // Execute request

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, slingBundleContext);

        // Set the resource of the request as the /Vocabularies node
        request.setResource(resourceResolver.getResource("/Vocabularies"));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        // Set request parameters and execute request. Note that no version is provided.
        String requestParams = "source=ncit-flat&identifier=flatTestVocabulary&localpath=./flat_NCIT_type_testcase.zip";
        makePost(request, response, requestParams);

        // Compare response to expected response

        // Read response
        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        JsonObject responseJson = reader.readObject();

        // Make sure the parsing/indexing attempt was unsuccessful
        Assert.assertFalse(responseJson.getBoolean("isSuccessful"));

        // Compare error message to expected error message
        String expectedError = "NCIT Flat indexing error: Mandatory version parameter not provided.";
        String obtainedError = responseJson.getString("error");
        Assert.assertEquals(expectedError, obtainedError);

        // Make sure that the vocabulary node was not created
        Node rootNode = request.getResource().adaptTo(Node.class);
        Assert.assertFalse(rootNode.hasNode("/flatTestvocabulary"));
    }

    /**
     * Tests {@link VocabularyIndexerServlet} response to a request with a nonexistent relative path to a zip file.
     *
     * @throws Exception when an unexpected response is returned or the request has failed
     */
    @Test
    public void testInvalidFileLocation()
        throws Exception
    {
        // Set up mock repository

        // Instantiate a MockJcr session and register a Resource to Node adapter to it
        final Session session = MockJcr.newSession();
        registerResourceToNodeAdapter(session);

        // BundleContext and ResourceResolver for creating resources and instantiating requests
        BundleContext slingBundleContext = this.context.bundleContext();
        ResourceResolver resourceResolver = MockSling.newResourceResolver(slingBundleContext);

        // Create a mock VocabulariesHomepage node /Vocabularies to act as the resource for the request
        makeRequestResource(resourceResolver);

        // Execute request

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, slingBundleContext);

        // Set the resource of the request as the /Vocabularies node
        request.setResource(resourceResolver.getResource("/Vocabularies"));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        // Set request parameters and execute request. Note that the localpath "./someLocation" does not exist.
        String requestParams = "source=ncit-flat&identifier=flatTestVocabulary&version=19.05d&localpath=./someLocation";
        makePost(request, response, requestParams);

        // Compare response to expected response

        // Read response
        JsonReader reader = Json.createReader(new StringReader(response.getOutputAsString()));
        JsonObject responseJson = reader.readObject();

        // Make sure that the parsing/indexing attempt was unsuccessful
        Assert.assertFalse(responseJson.getBoolean("isSuccessful"));

        // Compare the error message
        String expectedError = "NCIT Flat indexing error: Error: Failed to load zip vocabulary locally. "
            + (SystemUtils.IS_OS_WINDOWS
            ? ".\\someLocation (The system cannot find the file specified)"
            : "./someLocation (No such file or directory)");
        String obtainedError = responseJson.getString("error");
        Assert.assertEquals(expectedError, obtainedError);

        // Make sure the vocabulary node was not created
        Node rootNode = request.getResource().adaptTo(Node.class);
        Assert.assertFalse(rootNode.hasNode("/flatTestvocabulary"));
    }

    /**
     * Tests {@link VocabularyIndexerServlet} parsing and indexing a locally stored zip of a test vocabulary called
     * flatTestVocabulary. Checks if the resultant nodes that are created in the MockJcr instance are correct.
     *
     * @throws Exception when an unexpected response is returned or the request has failed
     */
    @Test
    public void testNCITFlatIndexing()
        throws Exception
    {
        // Set up mock repository

        // Instantiate a MockJcr session and register a Resource to Node adapter to it
        final Session session = MockJcr.newSession();
        registerResourceToNodeAdapter(session);

        // BundleContext and ResourceResolver for creating resources and instantiating requests
        BundleContext slingBundleContext = this.context.bundleContext();
        ResourceResolver resourceResolver = MockSling.newResourceResolver(slingBundleContext);

        // Create a mock VocabulariesHomepage node /Vocabularies to act as the resource for the request
        makeRequestResource(resourceResolver);

        // Execute request

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, slingBundleContext);

        // Set the resource of the request as the /Vocabularies node
        request.setResource(resourceResolver.getResource("/Vocabularies"));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        // Set the request parameters and execute request
        String requestParams = "source=ncit-flat&identifier=flatTestVocabulary&version=19.05d&localpath="
            + getClass().getResource("/flat_NCIT_type_testcase.zip").getPath();
        makePost(request, response, requestParams);

        // Get the root and vocabulary nodes from the request
        Node rootNode = request.getResource().adaptTo(Node.class);
        Node vocabNode = rootNode.getNode("flatTestVocabulary");

        // Check that all nodes representing the given terms exist
        ncitFlatTestVocabularyNode(vocabNode);

        // Check nodes representing terms C100005 and C100008 have the correct properties
        ncitFlatTestC100005(vocabNode);
        ncitFlatTestC100008(vocabNode);
    }

    /**
     * Checks if all of the terms in the test vocabulary have been created as valid nodes by the parsing/indexing
     * process.
     *
     * @param flatTestVocabulary - <code>Vocabulary</code> node created in the MockJcr instance
     * @throws Exception if the given <code>VocabularyTerm</code> nodes or the <code>Vocabulary</code> nodes do not
     *             exist.
     */
    private void ncitFlatTestVocabularyNode(Node flatTestVocabulary)
        throws Exception
    {
        String[] expectedNodes =
            { "C100000", "C100001", "C100002", "C100003", "C100004", "C100005", "C100006",
            "C100007", "C100008", "C100009" };

        // Check if each expected node exists
        for (String nodeName : expectedNodes) {
            Assert.assertTrue(flatTestVocabulary.hasNode(nodeName));
        }

        // Check if default name has been set
        Value obtainedNameValue = flatTestVocabulary.getProperty("name").getValue();
        Assert.assertTrue("National Cancer Institute Thesaurus".compareTo(obtainedNameValue.getString()) == 0);

        // Check if correct version has been set
        Value obtainedVersionValue = flatTestVocabulary.getProperty("version").getValue();
        Assert.assertTrue("19.05d".compareTo(obtainedVersionValue.getString()) == 0);
    }

    /**
     * Compares the correct values for node C100008 in the test vocabulary to the ones actually generated by the
     * {@link VocabularyIndexerServlet}.
     *
     * @param flatTestVocabulary - the <code>Vocabulary</code> node generated containing the test vocabulary
     * @throws Exception thrown when node properties don't match correct properties or if properties don't exist
     */
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

        String[] parents = { "C100006", "C100007" };
        checkStringArray(c100008, "parents", parents);

        String[] ancestors = { "C100001", "C100002", "C100004", "C100005", "C100006", "C100007" };
        checkStringArray(c100008, "ancestors", ancestors);
    }

    /**
     * Compares the correct values for node C10000 in the test vocabulary to the ones actually generated by the
     * {@link VocabularyIndexerServlet}.
     *
     * @param flatTestVocabulary - the <code>Vocabulary</code> node generated containing the test vocabulary
     * @throws Exception thrown when node properties don't match correct properties or if properties don't exist
     */
    private void ncitFlatTestC100005(Node flatTestVocabulary)
        throws Exception
    {
        Node c100005 = flatTestVocabulary.getNode("C100005");
        String description = "A procedure to evaluate the health of the an individual after "
            + "receiving a heart transplant. (ACC)";
        checkString(c100005, "description", description);

        String label = "Post-Cardiac Transplant Evaluation";
        checkString(c100005, "label", label);

        String[] synonyms = { "Post-Cardiac Transplant Evaluation", "POST-CARDIAC TRANSPLANT" };
        checkStringArray(c100005, "synonyms", synonyms);

        String[] parents = { "C100002" };
        checkStringArray(c100005, "parents", parents);

        String[] ancestors = { "C100002" };
        checkStringArray(c100005, "ancestors", ancestors);
    }
}
