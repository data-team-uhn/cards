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
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

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

/**
 * @version $Id$
 */
@Component (service = {Servlet.class})
@SlingServletResourceTypes(resourceTypes = {"lfs/VocabulariesHomepage"}, methods = {"POST"})
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
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
    private static final String TRUESTRING = "true";

    @Reference
    private LogService logger;

    private String errors;

    @Override
    public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws IOException
    {
        this.errors = "";
        String source = request.getParameter("source");
        int status = 0;
        if (source != null && "ncit".equalsIgnoreCase(source)) {
            status = handleNCIT(request);
        }

        if (status == 1) {
            writeStatusJson(request, response, false, this.errors);
        } else {
            writeStatusJson(request, response, true, this.errors);
        }
    }

    private void writeStatusJson(SlingHttpServletRequest request, SlingHttpServletResponse response,
        boolean status, String errors)
        throws IOException
    {
        Writer out = response.getWriter();
        JsonGenerator generator = Json.createGenerator(out);
        generator.writeStartObject();
        generator.write("success", status);
        generator.write("errors", errors);
        generator.writeEnd();
        generator.flush();
    }

    private int handleNCIT(SlingHttpServletRequest request)
    {
        String version = request.getParameter("version");
        String test = request.getParameter("test");
        String unittest = request.getParameter("unittest");
        Node vocabulariesHomepage = request.getResource().adaptTo(Node.class);
        try {
            clearVocabularyNode(vocabulariesHomepage, test);
            if (unittest != null && TRUESTRING.equalsIgnoreCase(unittest)) {
                unitTestLoadNCIT();
            } else {
                loadNCIT(version, test);
            }
            Node vocabularyNode = createNCITVocabularyNode(vocabulariesHomepage, version, test);
            parseNCIT(vocabulariesHomepage, vocabularyNode, test);
            deleteTempZipFile();
            saveSession(vocabulariesHomepage);
            return 0;
        } catch (Exception e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to parse NCIT flat file." + e.getMessage());
            return 1;
        }
    }

    private void clearVocabularyNode(Node vocabulariesHomepage, String test)
        throws RepositoryException
    {
        try {
            if (test != null && TRUESTRING.equalsIgnoreCase(test)) {
                if (vocabulariesHomepage.hasNode("flatTestVocabulary")) {
                    Node target = vocabulariesHomepage.getNode("flatTestVocabulary");
                    target.remove();
                }
            } else {
                if (vocabulariesHomepage.hasNode(VOCABULARY_NAME)) {
                    Node target = vocabulariesHomepage.getNode(VOCABULARY_NAME);
                    target.remove();
                }
            }
        } catch (RepositoryException e) {
            String message = "Error: Failed to delete existing Vocabulary node" + e.getMessage();
            this.logger.log(LogService.LOG_ERROR, message);
            this.errors += (message + " ");
            throw e;
        }
    }

    private void loadNCIT(String version, String test)
        throws Exception
    {
        String source;
        HttpGet httpget;
        if (test != null && TRUESTRING.equalsIgnoreCase(test)) {
            source = "http://localhost:8080/tests/flat_NCIT_type_testcase.zip";
        } else {
            source = "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/Thesaurus_" + version + ".FLAT.zip";
        }
        httpget = new HttpGet(source);
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
                String message = "Failed to load NCIT: " + httpresponse.getStatusLine().getStatusCode() + " Error";
                this.logger.log(LogService.LOG_ERROR, message);
                this.errors += (message + ". ");
                httpresponse.close();
                httpclient.close();
                throw new Exception();
            }
        } catch (ClientProtocolException e) {
            String message = "Failed to load NCIT: " + e.getMessage();
            this.logger.log(LogService.LOG_ERROR, message);
            this.errors += (message + " ");
            throw e;
        } catch (IOException e) {
            String message = "Failed to load NCIT: " + e.getMessage();
            this.logger.log(LogService.LOG_ERROR, message);
            this.errors += (message + " ");
            throw e;
        }
    }

    private void unitTestLoadNCIT()
        throws IOException
    {
        String source = "./flat_NCIT_type_testcase.zip";
        File.createTempFile(VOCABULARY_NAME, ZIP_SUFFIX);
        FileOutputStream fileOutputStream = new FileOutputStream(DIRECTORY_PREFIX + VOCABULARY_NAME + ZIP_SUFFIX);
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

        FileInputStream fileInputStream = new FileInputStream(source);
        ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

        zipInputStream.getNextEntry();
        try {
            int c;
            zipOutputStream.putNextEntry(new ZipEntry("temp.txt"));
            while ((c = zipInputStream.read()) != -1) {
                zipOutputStream.write(c);
            }
            zipOutputStream.closeEntry();
        } finally {
            fileInputStream.close();
            zipInputStream.close();
            zipOutputStream.close();
            fileOutputStream.close();
        }
    }

    private Node createNCITVocabularyNode(Node vocabulariesHomepage, String version, String test)
        throws RepositoryException
    {
        try {
            Node vocabularyNode;
            if (test != null && TRUESTRING.equalsIgnoreCase(test)) {
                vocabularyNode = vocabulariesHomepage.addNode(DIRECTORY_PREFIX + "flatTestVocabulary",
                    "lfs:Vocabulary");
            } else {
                vocabularyNode = vocabulariesHomepage.addNode(DIRECTORY_PREFIX + VOCABULARY_NAME, "lfs:Vocabulary");
            }
            vocabularyNode.setProperty("identifier", "ncit");
            vocabularyNode.setProperty("name", "National Cancer Institute Thesaurus");
            vocabularyNode.setProperty("source", "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/");
            vocabularyNode.setProperty("version", version);
            vocabularyNode.setProperty("website", "https://ncit.nci.nih.gov/ncitbrowser/");
            return vocabularyNode;
        } catch (RepositoryException e) {
            String message = "Failed to create Vocabulary node:" + e.getMessage();
            this.logger.log(LogService.LOG_ERROR, message);
            this.errors += (message + " ");
            throw e;
        }
    }

    private void parseNCIT(Node vocabulariesHomepage, Node vocabularyNode, String test)
        throws IOException, RepositoryException
    {
        try {
            Map<String, String[]> parentsMap = returnParentMap();
            createTermNodes(parentsMap, vocabulariesHomepage, vocabularyNode, test);
        } catch (RepositoryException e) {
            String message = "Failed to access Vocabulary node:" + e.getMessage();
            this.logger.log(LogService.LOG_ERROR, message);
            this.errors += (message + " ");
            throw e;
        } catch (IOException e) {
            String message = "Failed to read flat file:" + e.getMessage();
            this.logger.log(LogService.LOG_ERROR, message);
            this.errors += (message + " ");
            throw e;
        }
    }

    private void createTermNodes(Map<String, String[]> parentsMap, Node vocabulariesHomepage,
        Node vocabularyNode, String test)
        throws IOException, RepositoryException
    {
        FileInputStream fileInputStream = new FileInputStream(DIRECTORY_PREFIX + VOCABULARY_NAME + ZIP_SUFFIX);
        ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

        zipInputStream.getNextEntry();
        InputStreamReader inputStreamReader = new InputStreamReader(zipInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        CSVParser csvParser = CSVParser.parse(bufferedReader, CSVFormat.TDF.withQuote(null));

        Iterator<CSVRecord> csvIterator = csvParser.iterator();

        //Node vocabularyNode;
        /*
        if (test != null && TRUESTRING.equalsIgnoreCase(test)) {
            vocabularyNode = vocabulariesHomepage.getNode("flatTestVocabulary");
        } else {
            vocabularyNode = vocabulariesHomepage.getNode(VOCABULARY_NAME);
        }
        */
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
        throws RepositoryException
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
            String message = "Failed to create VocabularyTerm node:" + e.getMessage();
            this.logger.log(LogService.LOG_ERROR, message);
            this.errors = (message + " ");
            throw e;
        }
    }

    private void deleteTempZipFile()
    {
        File tempfile = new File(DIRECTORY_PREFIX + VOCABULARY_NAME + ZIP_SUFFIX);
        tempfile.delete();
    }

    private void saveSession(Node vocabulariesHomepage)
        throws RepositoryException
    {
        try {
            vocabulariesHomepage.getSession().save();
        } catch (RepositoryException e) {
            String message = "Failed to save session: " + e.getMessage();
            this.logger.log(LogService.LOG_ERROR, message);
            this.errors += (message + " ");
            throw e;
        }
    }
}


