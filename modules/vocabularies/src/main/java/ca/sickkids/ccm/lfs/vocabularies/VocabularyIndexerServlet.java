
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;
//import java.io.OutputStream;
//import java.io.Writer;
//import java.util.Collection;
//import java.util.zipOutputStream;
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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
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

//import org.apache.commons.csv.QuoteMode;
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
    private static final String VOCABULARY_NAME = "ncit";
    private static final String DIRECTORY_PREFIX = "./";
    private static final String ZIP_SUFFIX = ".zip";
    private static final int NCIT_FLAT_IDENTIFIER_COLUMN = 0;
    private static final int NCIT_FLAT_PARENTS_COLUMN = 2;
    private static final int NCIT_FLAT_SYNONYMS_COLUMN = 3;
    private static final int NCIT_FLAT_DESCRIPTION_COLUMN = 4;
    private static final int NCIT_FLAT_LABEL_COLUMN = 5;

    @Reference
    private LogService logger;

    @Override
    public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws IOException
    {
        Node vocabulariesHomepage = request.getResource().adaptTo(Node.class);
        clearVocabularyNode(vocabulariesHomepage);
        loadNCIT();
        createNCITVocabularyNode(vocabulariesHomepage);
        parseNCIT(vocabulariesHomepage);
        deleteTempZipFile();
        saveSession(vocabulariesHomepage);
    }

    private void clearVocabularyNode(Node vocabulariesHomepage)
    {
        try {
            if (vocabulariesHomepage.hasNode(VOCABULARY_NAME)) {
                Node target = vocabulariesHomepage.getNode(VOCABULARY_NAME);
                target.remove();
            }
        } catch (RepositoryException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to delete existing Vocabulary node:"
                 + e.getMessage());
        }
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
                File.createTempFile(VOCABULARY_NAME, ZIP_SUFFIX);
                FileOutputStream fileOutput = new FileOutputStream(DIRECTORY_PREFIX + VOCABULARY_NAME + ZIP_SUFFIX);

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
            Node vocabularyNode = vocabulariesHomepage.addNode(DIRECTORY_PREFIX + VOCABULARY_NAME, "lfs:Vocabulary");
            vocabularyNode.setProperty("identifier", "ncit");
            vocabularyNode.setProperty("name", "National Cancer Institute Thesaurus");
            vocabularyNode.setProperty("source", "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/");
            vocabularyNode.setProperty("version", "19.05d");
            vocabularyNode.setProperty("website", "https://ncit.nci.nih.gov/ncitbrowser/");
        } catch (RepositoryException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to create Vocabulary node:" + e.getMessage());
        }
    }

    private void parseNCIT(Node vocabulariesHomepage)
         throws IOException
    {
        Map<String, String[]> parentsMap = returnParentMap();

        FileInputStream fileInputStream = new FileInputStream(DIRECTORY_PREFIX + VOCABULARY_NAME + ZIP_SUFFIX);
        ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

        zipInputStream.getNextEntry();
        InputStreamReader inputStreamReader = new InputStreamReader(zipInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        CSVParser csvParser = CSVParser.parse(bufferedReader, CSVFormat.TDF.withQuote(null));

        Iterator<CSVRecord> csvIterator = csvParser.iterator();

        try {
            Node vocabularyNode = vocabulariesHomepage.getNode(VOCABULARY_NAME);
            while (csvIterator.hasNext()) {
                CSVRecord row = csvIterator.next();

                String identifier = row.get(NCIT_FLAT_IDENTIFIER_COLUMN);
                String description = row.get(NCIT_FLAT_DESCRIPTION_COLUMN);

                String synonymString = row.get(NCIT_FLAT_SYNONYMS_COLUMN);
                String[] synonymsArray = synonymString.split("\\|");

                // Make the first synonym of the term the default label if no label is supplied by the term
                String defaultLabel = synonymsArray[0];

                // The actual label supplied by the term
                String suppliedLabel = row.get(NCIT_FLAT_LABEL_COLUMN);

                String label = StringUtils.defaultIfBlank(suppliedLabel, defaultLabel);

                String[] parentsArray = parentsMap.get(identifier);
                String[] ancestorsArray = computeAncestors(parentsMap, identifier);

                createNCITVocabularyTermNode(vocabularyNode, identifier, label, description, synonymsArray,
                    parentsArray, ancestorsArray);
            }
        } catch (RepositoryException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to access Vocabulary node:" + e.getMessage());
        }

        csvParser.close();
        bufferedReader.close();
        inputStreamReader.close();
        zipInputStream.close();
        fileInputStream.close();
    }

    private Map<String, String[]> returnParentMap()
        throws IOException
    {
        FileInputStream fileInputStream = new FileInputStream(DIRECTORY_PREFIX + VOCABULARY_NAME + ZIP_SUFFIX);
        ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

        zipInputStream.getNextEntry();
        InputStreamReader inputStreamReader = new InputStreamReader(zipInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        CSVParser csvParser = CSVParser.parse(bufferedReader, CSVFormat.TDF.withQuote(null));

        Iterator<CSVRecord> csvIterator = csvParser.iterator();

        Map<String, String[]> parents = new HashMap<String, String[]>();

        while (csvIterator.hasNext()) {
            CSVRecord row = csvIterator.next();

            String identifier = row.get(NCIT_FLAT_IDENTIFIER_COLUMN);

            String parentString = row.get(NCIT_FLAT_PARENTS_COLUMN);
            String[] parentArray = parentString.split("\\|");

            if (parentArray[0].contentEquals("")) {
                parents.put(identifier, new String[0]);
            } else {
                parents.put(identifier, parentArray);
            }
        }

        csvParser.close();
        bufferedReader.close();
        inputStreamReader.close();
        zipInputStream.close();
        fileInputStream.close();

        return parents;
    }

    private String[] computeAncestors(Map<String, String[]> parentsMap, String identifier)
    {
        Set<String> termAncestorSet = new HashSet<String>();
        String[] parentArray = parentsMap.get(identifier);
        if (parentArray != null && parentArray.length > 0) {
            for (String parentIdentifier : parentArray)
            {
                termAncestorSet.add(parentIdentifier);
                termAncestorSet.addAll(recursiveComputeAncestors(parentsMap, parentIdentifier));
            }
        }

        return termAncestorSet.toArray(new String[0]);
    }

    private Set<String> recursiveComputeAncestors(Map<String, String[]> parentsMap, String identifier)
    {
        Set<String> termAncestorSet = new HashSet<String>();
        String[] parentArray = parentsMap.get(identifier);
        if (parentArray != null && parentArray.length > 0) {
            for (String parentIdentifier : parentArray)
            {
                termAncestorSet.add(parentIdentifier);
                termAncestorSet.addAll(recursiveComputeAncestors(parentsMap, parentIdentifier));
            }
        }

        return termAncestorSet;
    }

    private void createNCITVocabularyTermNode(Node vocabularyNode, String identifier, String label,
        String description, String[] synonyms, String[] parents, String[] ancestors)
    {
        try {
            Node vocabularyTermNode = vocabularyNode.addNode(DIRECTORY_PREFIX + identifier, "lfs:VocabularyTerm");
            vocabularyTermNode.setProperty("identifier", identifier);
            vocabularyTermNode.setProperty("label", label);
            vocabularyTermNode.setProperty("description", description);
            vocabularyTermNode.setProperty("synonyms", synonyms);
            vocabularyTermNode.setProperty("parents", parents);
            vocabularyTermNode.setProperty("ancestors", ancestors);
        } catch (RepositoryException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to create VocabularyTerm node:" + e.getMessage());
        }
    }

    private void deleteTempZipFile()
    {
        File tempfile = new File(DIRECTORY_PREFIX + VOCABULARY_NAME + ZIP_SUFFIX);
        tempfile.delete();
    }

    private void saveSession(Node vocabulariesHomepage)
    {
        try {
            vocabulariesHomepage.getSession().save();
        } catch (RepositoryException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to save session: " + e.getMessage());
        }
    }
}


