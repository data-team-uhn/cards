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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.http.HttpHeaders;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;

import org.apache.sling.testing.mock.jcr.MockJcr;
import org.apache.sling.testing.mock.sling.MockJcrSlingRepository;
import org.apache.sling.testing.mock.sling.MockSling;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.junit.Rule;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
//import org.apache.sling.testing.mock.sling.context.SlingContextImpl;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.base.Function;

@RunWith(MockitoJUnitRunner.class)
public class VocabularyIndexerServletTest
{
    @Rule
    public SlingContext context = new SlingContext();

    @InjectMocks
    private VocabularyIndexerServlet indexServlet;

    @Before
    public void initialize() throws RepositoryException
    {
    }

    
    @Test
    public void testNCITFlatNodes()
        throws Exception
    {
        final Session session = MockJcr.newSession();
    	context.registerAdapter(Resource.class, Node.class, 
    			new Function<Resource, Node>() {public Node apply(Resource resource) { 
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
               }});
        Map<String, Object> props = new HashMap<>();
        props.put("jcr:primaryType", "lfs:VocabulariesHomepage");/*
        context.create().resource("/Vocabularies");
        context.currentResource("/Vocabularies");
 */
        MockSlingHttpServletRequest request;
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        ResourceResolver resourceResolver= MockSling.newResourceResolver(context.bundleContext());
        
        Resource root  = resourceResolver.getResource("/");
        resourceResolver.create(root, "Vocabularies", props);

        request = new MockSlingHttpServletRequest(resourceResolver, context.bundleContext());
        request.setResource(resourceResolver.getResource("/Vocabularies"));
        request.setQueryString("source=ncit&test=true&version=19.05d&unittest=true");
        request.setMethod(HttpConstants.METHOD_POST);
        request.setHeader(HttpHeaders.AUTHORIZATION, "Basic "
        + Base64.getEncoder().encodeToString("admin:admin".getBytes()));

        indexServlet.doPost(request, response);
        Node rootNode = request.getResource().adaptTo(Node.class);
        Node vocabNode = request.getResource().adaptTo(Node.class).getNode("flatTestVocabulary");
        //Node termNode = vocabNode.getNode("C00001");
        
        System.out.println("dfjdks");
        //Node res = resourceResolver.getResource("/").adaptTo(Node.class).getNode("/Vocabularies");
        //System.out.println(request.getResource().adaptTo(Node.class).getProperty("jcr:primaryType").getName());
        
        //Repository repository = MockJcr.newRepository();
        //Resource root  = resourceResolver.create(null, "/", null);
        //Repository repo = context.resourceResolver().resolve("./").adaptTo(Repository.class);
        /*
        MockJcrSlingRepository repo = new MockJcrSlingRepository();
        Session session = repo.login();
        Node root = session.getRootNode();
        root.addNode("/Vocabularies", "lfs:VocabulariesHomepage");
        */
        //Assert.assertTrue(response.getStatus() < 400);
        //Assert.assertTrue(response.);
        //ResourceResolver resourceresolver = MockSling.newResourceResolver(context.bundleContext());
        //Node root = resourceresolver.getResource("/Vocabularies").adaptTo(Node.class);
        //Assert.assertFalse(root.hasNode("/Vocabulaires"));
    }    
}
