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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.stream.JsonGenerator;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.osgi.service.log.LogService;

/**
 * Class allowing parsing of a NCIT vocabulary instance from a flat file and creation of JCR nodes from it.
 * @version $Id$
 */
public class NCITFlatParser implements VocabularyParser
{
    private static final String TEMPORARY_FILE_NAME = "tempncit";
    private static final String TEMPORARY_FILE_DIRECTORY = "./";

    private static final int NCIT_FLAT_IDENTIFIER_COLUMN = 0;
    private static final int NCIT_FLAT_PARENTS_COLUMN = 2;
    private static final int NCIT_FLAT_SYNONYMS_COLUMN = 3;
    private static final int NCIT_FLAT_DESCRIPTION_COLUMN = 4;
    private static final int NCIT_FLAT_LABEL_COLUMN = 5;

    /**
     * Method called by the VocabularyIndexerServlet parse a NCIT vocabulary from a flat file.
     * Three mandatory parameters are required from the http request sent to the VocabularyIndexerServlet.
     * <code>"source"</code> - This must be "ncit" in order for this method to be called.
     * <code>"identifier"</code> - the identifier the NCIT thesaurus instance is to be known by
     * <code>"version"</code> - the version of the NCIT thesaurus to be indexed
     * There are two optional parameters.
     * <code>"localpath"</code> - allows downloading of NCIT from a path relative to the
     * VocabularyIndexerServlet
     * <code>"httppath"</code>- allows downloading of NCIT from a url other than
     * "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/"
     * @param request - http request from the VocabularyIndexerServlet
     * @param response - http response from the VocabularyIndexerServlet
     * @param logger - logger from the VocabularyIndexerServlet to log exceptions caught
     * @throws IOException thrown when response Json cannot be written
     */
    public void parseVocabulary(SlingHttpServletRequest request, SlingHttpServletResponse response,
        LogService logger)
        throws IOException
    {
        String identifier = request.getParameter("identifier");
        String version = request.getParameter("version");
        String httppath = request.getParameter("httppath");
        String localpath = request.getParameter("localpath");

        Node homepage = request.getResource().adaptTo(Node.class);

        try {
            if (identifier == null) {
                throw new VocabularyIndexException("Mandatory identifier parameter not provided.");
            }

            if (version == null) {
                throw new VocabularyIndexException("Mandatory version parameter not provided.");
            }

            if (homepage == null) {
                throw new VocabularyIndexException("Could not access resource of your request.");
            }

            clearVocabularyNode(homepage, identifier);

            VocabularyZipLoader zipLoader = new VocabularyZipLoader();
            if (localpath != null) {
                zipLoader.loadZipLocal(localpath, TEMPORARY_FILE_DIRECTORY, TEMPORARY_FILE_NAME);
            } else if (httppath != null) {
                zipLoader.loadZipHttp(httppath, TEMPORARY_FILE_DIRECTORY, TEMPORARY_FILE_NAME);
            } else {
                zipLoader.loadZipHttp("https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/Thesaurus_" + version + ".FLAT.zip",
                        TEMPORARY_FILE_DIRECTORY, TEMPORARY_FILE_NAME);
            }

            Node vocabularyNode = createNCITVocabularyNode(homepage, identifier, version);

            parseNCIT(vocabularyNode);

            deleteTempZipFile();
            saveSession(homepage);

            writeStatusJson(request, response, true, null);
        } catch (Exception e) {
            writeStatusJson(request, response, false, "NCIT Flat parsing error: " + e.getMessage());
            logger.log(LogService.LOG_ERROR, "NCIT Flat parsing error: " + e.getMessage());
        }
    }

    /**
     * Writes a json to the http response consisting of two entries, "isSuccessful" which indicates if the
     * parsing attempt was successful or not, and "error" which is the error message in case of an error.
     * @param request - http request from the VocabularyIndexerServlet
     * @param response - http response from the VocabularyIndexerServlet
     * @param isSuccessful - boolean variable which is true if parsing is successful and false otherwise
     * @param errors - the error message caught which is null if there is no error
     * @throws IOException thrown when json cannot be written
     */
    public void writeStatusJson(SlingHttpServletRequest request, SlingHttpServletResponse response,
        boolean isSuccessful, String errors)
        throws IOException
    {
        Writer out = response.getWriter();
        JsonGenerator generator = Json.createGenerator(out);
        generator.writeStartObject();
        generator.write("isSuccessful", isSuccessful);
        generator.write("errors", errors);
        generator.writeEnd();
        generator.flush();
    }

    /**
     * Remove any previous instances of the vocabulary which is to be parsed and indexed in the JCR repository.
     * @param homepage - an instance of the VocabulariesHomepage node serving as the root of Vocabulary nodes
     * @param name - identifier of the vocabulary which will become its node name
     * @throws VocabularyIndexException thrown when node cannot be removed
     */
    public void clearVocabularyNode(Node homepage, String name)
        throws VocabularyIndexException
    {
        try {
            if (homepage.hasNode(name)) {
                Node target = homepage.getNode(name);
                target.remove();
            }
        } catch (RepositoryException e) {
            String message = "Error: Failed to delete existing Vocabulary node. " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    private Node createNCITVocabularyNode(Node homepage, String identifier, String version)
        throws VocabularyIndexException
    {
        try {
            Node vocabularyNode = homepage.addNode("./" + identifier, "lfs:Vocabulary");

            vocabularyNode.setProperty("identifier", "ncit");
            vocabularyNode.setProperty("name", "National Cancer Institute Thesaurus");
            vocabularyNode.setProperty("source", "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/");
            vocabularyNode.setProperty("version", version);
            vocabularyNode.setProperty("website", "https://ncit.nci.nih.gov/ncitbrowser/");
            return vocabularyNode;
        } catch (RepositoryException e) {
            String message = "Failed to create Vocabulary node: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    private void parseNCIT(Node vocabularyNode)
        throws VocabularyIndexException
    {
        try {
            Map<String, String[]> parentsMap = returnParentMap();
            createTermNodes(parentsMap, vocabularyNode);
        } catch (RepositoryException e) {
            String message = "Failed to access Vocabulary node: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } catch (IOException e) {
            String message = "Failed to read flat file: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    private void createTermNodes(Map<String, String[]> parentsMap, Node vocabularyNode)
        throws IOException, RepositoryException, VocabularyIndexException
    {
        FileInputStream fileInputStream = new FileInputStream(TEMPORARY_FILE_DIRECTORY + TEMPORARY_FILE_NAME + ".zip");
        ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

        zipInputStream.getNextEntry();
        InputStreamReader inputStreamReader = new InputStreamReader(zipInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        CSVParser csvParser = CSVParser.parse(bufferedReader, CSVFormat.TDF.withQuote(null));

        Iterator<CSVRecord> csvIterator = csvParser.iterator();

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
        FileInputStream fileInputStream = new FileInputStream("./" + TEMPORARY_FILE_NAME + ".zip");
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
        throws VocabularyIndexException
    {
        try {
            Node vocabularyTermNode = vocabularyNode.addNode("./" + identifier, "lfs:VocabularyTerm");
            vocabularyTermNode.setProperty("identifier", identifier);
            vocabularyTermNode.setProperty("label", label);
            vocabularyTermNode.setProperty("description", description);
            vocabularyTermNode.setProperty("synonyms", synonyms);
            vocabularyTermNode.setProperty("parents", parents);
            vocabularyTermNode.setProperty("ancestors", ancestors);
        } catch (RepositoryException e) {
            String message = "Failed to create VocabularyTerm node: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    private void deleteTempZipFile()
    {
        File tempfile = new File(TEMPORARY_FILE_DIRECTORY + TEMPORARY_FILE_NAME + ".zip");
        tempfile.delete();
    }

    private void saveSession(Node vocabulariesHomepage)
        throws VocabularyIndexException
    {
        try {
            vocabulariesHomepage.getSession().save();
        } catch (RepositoryException e) {
            String message = "Failed to save session: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }
}
