
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

//import java.io.OutputStream;
//import java.io.Writer;
//import java.util.Collection;
//import java.util.HashSet;
//import java.util.zip.ZipOutputStream;
//import javax.jcr.Repository;
//import javax.jcr.Session;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
//import javax.jcr.RepositoryException;
//import javax.json.Json;
//import javax.json.JsonReader;
//import javax.json.JsonObject;
//import javax.json.stream.JsonGenerator;
/*
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.lang3.StringUtils;*/
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.LogService;

/*import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;*/
/*import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;*/
//import ca.sickkids.ccm.lfs.vocabularies.VocabularyParams;
/**
 * @version $Id$
 */
@Component (service = {Servlet.class})
@SlingServletResourceTypes(resourceTypes = {"lfs/VocabulariesHomepage"}, methods = {"POST"})
public class VocabularyIndexerServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -2156160697967947088L;
    private static final String TEMP_ZIP_FILE_NAME = "ncit";

    @Reference
    private LogService logger;

    @Override
    public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws IOException
    {
        loadNCIT();

        Node vocabulariesHomepage = request.getResource().adaptTo(Node.class);
        createNCITVocabularyNode(vocabulariesHomepage);
        deleteTempZipFile();
        saveSession(vocabulariesHomepage);
    }

    private void loadNCIT()
    {
        String source = "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/Thesaurus_" + "19.05d" + ".FLAT.zip";

        HttpGet httpget = new HttpGet(source);
        httpget.setHeader("Content-Type", "application/json");

        CloseableHttpClient httpclient = HttpClientBuilder.create().build();
        CloseableHttpResponse httpresponse;

        try {
            httpresponse = httpclient.execute(httpget);

            if (httpresponse.getStatusLine().getStatusCode() < 400) {
                File.createTempFile(TEMP_ZIP_FILE_NAME, ".zip");
                FileOutputStream fileOutput = new FileOutputStream("./" + TEMP_ZIP_FILE_NAME + ".zip");

                httpresponse.getEntity().writeTo(fileOutput);

                httpresponse.close();
                httpclient.close();
            } else {
                this.logger.log(LogService.LOG_ERROR, "Failed to load NCIT: "
                    + httpresponse.getStatusLine().getStatusCode()
                    + "Error, " + httpresponse.getStatusLine().getStatusCode());
                httpresponse.close();
                httpclient.close();
            }
        } catch (ClientProtocolException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to load NCIT: " + e.getMessage());
        } catch (IOException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to load NCIT: " + e.getMessage());
        }
    }

    private void createNCITVocabularyNode(Node vocabulariesHomepage)
    {
        try {
            Node vocabularyNode = vocabulariesHomepage.addNode("./ncit", "lfs:Vocabulary");
            vocabularyNode.setProperty("identifier", "ncit");
            vocabularyNode.setProperty("name", "National Cancer Institute Thesaurus");
            vocabularyNode.setProperty("source", "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/");
            vocabularyNode.setProperty("version", "19.05d");
            vocabularyNode.setProperty("website", "https://ncit.nci.nih.gov/ncitbrowser/");
        } catch (RepositoryException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to create Vocabulary node:" + e.getMessage());
        }
    }

    private void deleteTempZipFile()
    {
        File tempfile = new File("./" + TEMP_ZIP_FILE_NAME + ".zip");
        tempfile.delete();
    }

    private void saveSession (Node vocabulariesHomepage) {
        try {
            vocabulariesHomepage.getSession().save();
        } catch (RepositoryException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to save session: " + e.getMessage());
        }
    }
}


