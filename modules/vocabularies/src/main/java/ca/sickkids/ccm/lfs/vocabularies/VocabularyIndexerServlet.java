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

import java.io.FileOutputStream;
//import java.io.OutputStream;
import java.io.IOException;
//import java.io.Writer;
//import java.util.Collection;
//import java.util.HashSet;
import java.util.zip.ZipOutputStream;

//import javax.jcr.Repository;
//import javax.jcr.Session;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonObject;
//import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
/*
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
*/
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
/*
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;
*/
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.log.LogService;

import ca.sickkids.ccm.lfs.vocabularies.VocabularyParams;

@Component (service = {Servlet.class})
@SlingServletResourceTypes(resourceTypes = {"lfs/VocabulariesHomepage"}, methods = {"POST"})
public class VocabularyIndexerServlet extends SlingAllMethodsServlet
{
	@Reference
	private LogService logger;
	
	@Override
	public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) 
			throws IOException
	{
		int fileType = 1;
		// 1 for OWL, 2 for FLAT
		
		VocabularyParams vocabularyParams = new VocabularyParams (request.getParameter("identifier"), request.getParameter("source"), 
		    request.getParameter("name"), request.getParameter("version"), request.getParameter("website"), 
		    request.getParameter("citation"));
		
		if (!selectVocabularySource(vocabularyParams)) {
			return;
		}
		
		if (vocabularyParams.source == "ncit") {
			fileType = 2;
			if (!getNCIT(vocabularyParams)) {
				return;
			}
		}
		Node VocabulariesHomepageNode = request.getResource().adaptTo(Node.class);
		
		if (!clearVocabularyNode(VocabulariesHomepageNode, vocabularyParams.identifier)) { 
			return;
		}
		
		if (!createVocabularyNode(VocabulariesHomepageNode, vocabularyParams)) {
			return;
		}
		
		if (fileType == 1) {
			indexOWL();
		} else if (fileType == 2) {
			indexFLAT();
		}
		/*
		try {
			VocabulariesHomepageNode.getSession().save();
		} catch (RepositoryException e) {
			this.logger.log(LogService.LOG_ERROR, "Failed to save session:" + e.getMessage());
		}
		*/
	}
	
	private boolean selectVocabularySource (VocabularyParams vocabularyParams) 
			throws IOException 
	{
		if (!vocabularyParams.hasIdentifier() && !vocabularyParams.hasSource()) {
			this.logger.log(LogService.LOG_ERROR, "An identifier or a source was not provided.");
			return false;
		} else if (vocabularyParams.source == "ebi") {
			return getEBIVocabulary(vocabularyParams);
		} else if (vocabularyParams.source == "bioontology") {
			return getBioontologyVocabulary (vocabularyParams);
		} else {
			return true;
		}
	}
	
	private boolean getNCIT (VocabularyParams vocabularyParams) {
		if (!vocabularyParams.hasVersion()) {
			this.logger.log(LogService.LOG_ERROR, "A version was not provided for NCIT parsing.");
			return false;
		} else {
			vocabularyParams.source = "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/Thesaurus_" + vocabularyParams.version + ".FLAT.zip";
			HttpGet httpget = new HttpGet(vocabularyParams.source);
			httpget.setHeader("Content-Type", "application/json");
			CloseableHttpClient client = HttpClientBuilder.create().build();
			CloseableHttpResponse httpresponse;
			try {
				httpresponse = client.execute(httpget);
				if (httpresponse.getStatusLine().getStatusCode() < 400) {
					FileOutputStream fileOutput = new FileOutputStream("/temp.zip");
					ZipOutputStream zipOutput = new ZipOutputStream (fileOutput);
					httpresponse.getEntity().writeTo(zipOutput);
					httpresponse.close();
					client.close();
					return true;
				} else {
					this.logger.log(LogService.LOG_ERROR, "Failed to load NCIT: " + httpresponse.getStatusLine().getStatusCode() + 
							"Error, " + httpresponse.getStatusLine().getStatusCode());
					httpresponse.close();
					client.close();
					return false;
				}
			} catch (ClientProtocolException e) {
				this.logger.log(LogService.LOG_ERROR, "Failed to load NCIT: " + e.getMessage());
				return false;
			} catch (IOException e) {
				this.logger.log(LogService.LOG_ERROR, "Failed to load NCIT: " + e.getMessage());
				return false;
			}
		}
	}
	
	private boolean getEBIVocabulary(VocabularyParams vocabularyParams) 
	    throws IOException
	{
		HttpGet httpget = new HttpGet("https://www.ebi.ac.uk/ols/api/ontologies/" + vocabularyParams.identifier);
		httpget.setHeader("Content-Type", "application/json");
		CloseableHttpClient client = HttpClientBuilder.create().build();
		CloseableHttpResponse httpresponse = client.execute(httpget);
		if (httpresponse.getStatusLine().getStatusCode() < 400) {
			JsonReader reader = Json.createReader(httpresponse.getEntity().getContent());
			JsonObject intermediaryJson = reader.readObject();
			
			vocabularyParams.source = intermediaryJson.getJsonObject("config").getString("fileLocation");
			
			if (!vocabularyParams.hasName()) {
				vocabularyParams.source = intermediaryJson.getJsonObject("config").getString("title");
			}
			
			if (!vocabularyParams.hasVersion()) {
				vocabularyParams.version = intermediaryJson.getJsonObject("config").getString("version");
			}
			
			if (vocabularyParams.hasWebsite()) {
				vocabularyParams.website = intermediaryJson.getJsonObject("config").getString("homepage");
			}
			
			httpresponse.close();
			client.close();
			return true;
		} else {
			this.logger.log(LogService.LOG_ERROR, "Faild to load vocabulary:" + httpresponse.getStatusLine().getStatusCode() + 
					"error, "+ httpresponse.getStatusLine().getReasonPhrase());
			httpresponse.close();
			client.close();
			return false;
		}
	}
	
	private boolean getBioontologyVocabulary(VocabularyParams vocabularyParams)
	    throws IOException
	{
		HttpGet httpget = new HttpGet("https://data.bioontology.org/ontologies/"+ vocabularyParams.identifier + 
				"?apikey=8ac0298d-99f4-4793-8c70-fb7d3400f279");
		
		httpget.setHeader("Content-Type", "application/json");
		CloseableHttpClient client = HttpClientBuilder.create().build();
		CloseableHttpResponse httpresponse = client.execute(httpget);
		
		if (httpresponse.getStatusLine().getStatusCode() < 400) {
			JsonReader reader = Json.createReader(httpresponse.getEntity().getContent());
			JsonObject intermediaryJson = reader.readObject();
			
			vocabularyParams.source = intermediaryJson.getJsonObject("links").getString("download").toString();
			if (!vocabularyParams.hasName()) {
				vocabularyParams.name = intermediaryJson.getString("name");
			}
			
			httpresponse.close();
			client.close();
			return true;
		} else {
			this.logger.log(LogService.LOG_ERROR, "Faild to load vocabulary:" + httpresponse.getStatusLine().getStatusCode() + 
					"error, "+ httpresponse.getStatusLine().getReasonPhrase());
			httpresponse.close();
			client.close();
			return false;
		}
		
	}
	
	private boolean createVocabularyNode (Node VocabulariesHomepageNode, VocabularyParams vocabularyParams)
	{
		try {
			Node VocabularyNode = VocabulariesHomepageNode.addNode(vocabularyParams.identifier, "lfs:Vocabulary");
			VocabularyNode.setProperty("identifier", vocabularyParams.identifier);
			VocabularyNode.setProperty("name", vocabularyParams.name);
			VocabularyNode.setProperty("source", vocabularyParams.source);
			VocabularyNode.setProperty("version", vocabularyParams.version);
			VocabularyNode.setProperty("website", vocabularyParams.website);
			VocabularyNode.setProperty("citation", vocabularyParams.citation);
			return true;
		} catch (RepositoryException e) {
			this.logger.log(LogService.LOG_ERROR, "Failed to create Vocabulary node:" + e.getMessage());
			return false;
		}
	}
	
	private boolean clearVocabularyNode (Node VocabulariesHomepageNode, String identifier) {
		try {
			if (VocabulariesHomepageNode.hasNode(identifier)) {
				Node target = VocabulariesHomepageNode.getNode(identifier);
			    target.remove();
			}
			return true;
		} catch (RepositoryException e) {
			this.logger.log(LogService.LOG_ERROR, "Failed to delete existing Vocabulary node:" + e.getMessage());
			return false;
		}
	}
	
	private void indexOWL () 
			throws IOException 
	{
		/*
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
		*/	
	}
	
	private void indexFLAT () {
		
	}
	/*
	private void addTermNode (OntClass term, Node VocabularyNode) 
	{
		try {
			Node termNode = VocabularyNode.addNode(term.getLocalName());
			termNode.setProperty("identifier", term.getLocalName());
			termNode.setProperty("label", term.getLabel("EN"));
			
			ExtendedIterator<OntClass> superClassList = term.listSuperClasses(true);
			while (superClassList.hasNext()) {
				superClassList.next().getLocalName();
			}
			
			
		} catch (RepositoryException e) {
			this.logger.log(LogService.LOG_ERROR, "Failed to create VocabularyTerm node for term:" + e.getMessage());
		}
		
		
	}*/
}
