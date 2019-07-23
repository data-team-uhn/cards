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
import java.util.Collection;
import java.util.HashSet;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonObject;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;

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
		} else if (source == "bioontology") {
			getBioontologyVocabulary (identifier, source, name, version, website, citation);
		}
		
		Node VocabulariesHomepageNode = request.getResource().adaptTo(Node.class);
		createVocabularyNode(VocabulariesHomepageNode, identifier, name, source, version, website, citation);
		try {
			VocabulariesHomepageNode.getSession().save();
		} catch (RepositoryException e) {
			this.logger.log(LogService.LOG_ERROR, "Failed to save session:" + e.getMessage());
		}
		
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
	
	private void getBioontologyVocabulary(String identifier, String source, String name, String version, String website, String citation)
	    throws IOException
	{
		
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
	
	private void clearNode (Node VocabulariesHomepageNode, String identifier) {
		try {
			if (VocabulariesHomepageNode.hasNode(identifier)) {
				Node target = VocabulariesHomepageNode.getNode(identifier);
			    target.remove();
			}
		} catch (RepositoryException e) {
			this.logger.log(LogService.LOG_ERROR, "Failed to delete existing Vocabulary node:" + e.getMessage());
		}
	}
	
	private void index (String source, Node VocabularyNode) 
			throws IOException 
	{
		final OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_TRANS_INF);
		ontModel.read(source);
		
		ExtendedIterator <OntClass> roots = ontModel.listHierarchyRootClasses();
		
		final SolrInputDocument doc = new SolrInputDocument();
		try {
			Collection <SolrInputDocument> termBatch = new HashSet<>();

			setVersion(doc, ontModel);

			while (roots.hasNext()) {
				OntClass root = roots.next();
				
				addTermNode(root, VocabularyNode);
				
				final ExtendedIterator<OntClass> subClasses = root.listSubClasses();
				
				int batchCounter = 0;

				while (subClasses.hasNext()) 
				{
					addTermNode(root, VocabularyNode);
				}
			}
		} catch () {	
		}		
	}
	
	private void addTermNode (OntClass term, Node VocabularyNode) 
	{
		try {
			Node termNode = VocabularyNode.addNode(term.getLocalName());
			termNode.setProperty("identifier", term.getLocalName());
			termNode.setProperty("label", term.getLabel("EN"));
			term.superclas
			ExtendedIterator<OntClass> superClassList = term.listSuperClasses(true);
			while (superClassList.hasNext()) {
				superClassList.next().getLocalName();
			}
			termNode.setproper
			
		} catch (RepositoryException e) {
			this.logger.log(LogService.LOG_ERROR, "Failed to create VocabularyTerm node for term:" + e.getMessage());
		}
		
		
	}

	private void setVersion (final SolrInputDocument doc, final OntModel ontModel) 
        throws IOException, SolrServerException
	{
		//Ontology ontology = ontModel.getOntology(uri)
	}
}
