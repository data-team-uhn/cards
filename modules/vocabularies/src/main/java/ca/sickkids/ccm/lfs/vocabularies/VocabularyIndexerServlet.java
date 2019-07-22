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

import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonObject;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.log.LogService;

@Component (service = {Servlet.class})
@SlingServletResourceTypes(resourceTypes = {"lfs/VocabulariesHomepage"}, methods = {"POST"})
public class VocabularyIndexerServlet extends SlingAllMethodsServlet
{
	@Reference
	private LogService logger;
	
	@Override
	public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException
	{
		String identifier = request.getParameter("identifier");
		String source = request.getParameter("source");
		String name = request.getParameter("name");
		String version = request.getParameter("version");
		String website = request.getParameter("website");
		String citation = request.getParameter("citation");
		
		if (identifier == null || identifier == "" || source == null || source == "") {
			
		} else if (source == "ebi") {
			getEBIVocabulary(identifier, source, name, version, website, citation);
		} else {
			
		}
		
		Node VocabulariesHomepageNode = request.getResource().adaptTo(Node.class);
		createVocabularyNode(VocabulariesHomepageNode, identifier, name, source, version, website, citation);
		
	}
	
	private void getEBIVocabulary(String identifier, String source, String name, String version, String website, String citation) 
			throws IOException
	{
		URL url = new URL("https://www.ebi.ac.uk/ols/api/ontologies/" + "aeo");
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		int status = con.getResponseCode();
		if (status < 300) {
			JsonReader reader = Json.createReader(con.getInputStream());
			JsonObject intermediaryJson = reader.readObject();
			
			source = intermediaryJson.getJsonObject("config").getString("fileLocation");
			if (version == null || version == "") {
				version = intermediaryJson.getJsonObject("config").getString("version");
			}
			if (name == null || name =="") {
				name = intermediaryJson.getJsonObject("config").getString("title");
			}
		} 	
		con.disconnect();
	}
	
	
	private void createVocabularyNode (Node VocabulariesHomepageNode, String identifier, String name, String source, String version, String website, String citation)
	{
		
		try {
			Node VocabularyNode = VocabulariesHomepageNode.addNode(identifier, "lfs:Vocabulary");
			VocabularyNode.setProperty("identifier", identifier);
			VocabularyNode.setProperty("name", name);
			VocabularyNode.setProperty("source", source);
			VocabularyNode.setProperty("version", version);
			VocabularyNode.setProperty("website", website);
			VocabularyNode.setProperty("citation", citation);
		} catch (RepositoryException e) {
			this.logger.log(LogService.LOG_ERROR, "Failed to create Vocabulary node:" + e.getMessage());
		}
	}
	
	private void getOWLFile (String source) 
			throws IOException 
	{
		URL url = new URL(source);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		int status = con.getResponseCode();
		
	}
}
