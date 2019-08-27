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

package ca.sickkids.ccm.lfs.vocabularies.internal;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.stream.JsonGenerator;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.osgi.service.log.LogService;

import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyParser;

/**
 * Abstract class specifying a vocabulary ontology parser specifically for the National Cancer Institute Thesaurus. The
 * class implements methods common to parsers for the NCIT, but omits file-type specific methods. The parsing and node
 * creation process is done as a transaction, meaning that if it fails, then proposed changes saved in storage will not
 * be applied, and the repository will be left in its original state.
 * <p>
 * The parser assumes that the resource of the response it is given is a <code>VocabulariesHomepage</code> node under
 * which the <code>Vocabulary</code> node instance should be stored in the Jackrabbit Oak repository as a child. The
 * homepage node is obtained by adapting the resource of the response from a
 * {@link org.apache.sling.api.resource.Resource} to a {@link javax.jcr.node}.
 * </p>
 *
 * @version $Id$
 */
public abstract class AbstractNCITParser implements VocabularyParser
{
    /**
     * Method called by the {@link VocabularyIndexerServlet} to parse and index a NCIT vocabulary. Specifying the
     * version to index is mandatory. There are two optional parameters.
     * <p>
     * <code>"localpath"</code> - allows downloading of NCIT from a path relative to the VocabularyIndexerServlet.
     * </p>
     * <p>
     * <code>"httppath"</code>- allows downloading of NCIT from a url other than
     * "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/".
     * </p>
     * The method obtains the <code>VocabulariesHomepage</code> node by getting the resource of the request and adapting
     * it from a {@link org.apache.sling.api.resource.Resource} to a {@link javax.jcr.node}.
     *
     * @param request http request from {@link VocabularyIndexerServlet}
     * @param response http response from {@link VocabularyIndexerServlet}
     * @param logger - logger from the VocabularyIndexerServlet to log exceptions caught
     * @throws IOException thrown when response Json cannot be written
     */
    @Override
    public void parseVocabulary(SlingHttpServletRequest request, SlingHttpServletResponse response,
        LogService logger)
        throws IOException
    {
        // Obtain relevant request parameters.
        String identifier = request.getParameter("identifier");
        String version = request.getParameter("version");
        String httppath = request.getParameter("httppath");
        String localpath = request.getParameter("localpath");

        // Obtain the resource of the request and adapt it to a JCR node. This is taken as the homepage node
        Node homepage = request.getResource().adaptTo(Node.class);

        // Throw exceptions if mandatory parameters are not found or if homepage node cannot be found
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

            // Delete the Vocabulary node already representing this vocabulary instance if it exists
            clearVocabularyNode(homepage, identifier);

            // Load temporary NCIT zip file. Default location is at https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/
            String source = "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/Thesaurus_" + version + ".FLAT.zip";
            VocabularyZipLoader zipLoader = new VocabularyZipLoader();
            if (localpath != null) {
                source = localpath;
                zipLoader.loadZipLocal(localpath, getTempFileDirectory(), getTempFileName());
            } else if (httppath != null) {
                source = httppath;
                zipLoader.loadZipHttp(httppath, getTempFileDirectory(), getTempFileName());
            } else {
                zipLoader.loadZipHttp(source, getTempFileDirectory(), getTempFileName());
            }

            // Create a new Vocabulary node instance representing this vocabulary instance
            String name = "National Cancer Institute Thesaurus";
            Node vocabularyNode = createNCITVocabularyNode(homepage, identifier, name, source, version);

            // Parse the NCIT zip file and create VocabularyTerm node children
            parseNCIT(vocabularyNode);

            // Delete temporary NCIT zip file
            deleteTempZipFile(getTempFileDirectory(), getTempFileName());

            /*
             * Save the JCR session. If any errors occur before this step, all proposed changes will not be applied and
             * the repository will remain in its original state. Lucene indexing is automatically performed by the
             * Jackrabbit Oak repository when this is performed.
             */
            saveSession(homepage);

            // Success response json
            writeStatusJson(request, response, true, null);
        } catch (Exception e) {
            // If parsing fails, delete the temporary zip file and return an error json with the exception message
            deleteTempZipFile(getTempFileDirectory(), getTempFileName());
            writeStatusJson(request, response, false, "NCIT Flat parsing error: " + e.getMessage());
            if (logger != null) {
                logger.log(LogService.LOG_ERROR, "NCIT Flat parsing error: " + e.getMessage());
            }
        }
    }

    /**
     * Writes a json to the http response consisting of two entries.
     * <p>
     * <code>isSuccessful</code> - true if the parsing attempt was successful; false otherwise
     * </p>
     * <p>
     * <code>error"</code> - error message of the exception causing the failure; null if there is no exception
     * </p>
     *
     * @param request - http request from the VocabularyIndexerServlet
     * @param response - http response from the VocabularyIndexerServlet
     * @param isSuccessful - boolean variable which is true if parsing is successful and false otherwise
     * @param errors - the error message caught from the exception which is null if there is no error
     * @throws IOException thrown when json cannot be written
     */
    @Override
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
     * Remove any previous instances of the vocabulary which is to be parsed and indexed in the JCR repository by
     * deleting the vocabulary node instance. This will also cause the node's children to be deleted.
     *
     * @param homepage - an instance of the VocabulariesHomepage node serving as the root of Vocabulary nodes
     * @param name - identifier of the vocabulary which will become its node name
     * @throws VocabularyIndexException thrown when node cannot be removed
     */
    @Override
    public void clearVocabularyNode(Node homepage, String name)
        throws VocabularyIndexException
    {
        try {
            // Only delete the node if it exists
            if (homepage.hasNode(name)) {
                Node target = homepage.getNode(name);
                target.remove();
            }
        } catch (RepositoryException e) {
            String message = "Error: Failed to delete existing Vocabulary node. " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Creates a <code>Vocabulary</code> node that represents the current vocabulary instance with the identifier. as
     * the name of the node. The vocabulary property <code>website</code> is currently fixed to
     * https://ncit.nci.nih.gov/ncitbrowser/.
     *
     * @param homepage - <code>VocabulariesHomepage</code> node instance that will be parent of the created node
     * @param identifier - identifier of the vocabulary
     * @param name - name of the vocabulary
     * @param source - source of the vocabulary
     * @param version - version of the vocabulary
     * @return the <code>Vocabulary</code> node that is created
     * @throws VocabularyIndexException when node cannot be created
     */
    private Node createNCITVocabularyNode(Node homepage, String identifier, String name, String source, String version)
        throws VocabularyIndexException
    {
        try {
            Node vocabularyNode = homepage.addNode("./" + identifier, "lfs:Vocabulary");
            vocabularyNode.setProperty("identifier", identifier);
            vocabularyNode.setProperty("name", name);
            vocabularyNode.setProperty("source", source);
            vocabularyNode.setProperty("version", version);
            vocabularyNode.setProperty("website", "https://ncit.nci.nih.gov/ncitbrowser/");
            return vocabularyNode;
        } catch (RepositoryException e) {
            String message = "Failed to create Vocabulary node: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Creates a <code>VocabularyTerm</code> node representing an individual term of the NCIT. This method is protected
     * to allow subclass implementations of {@link parseNCIT} to use this method, allowing the node creation process to
     * be standardized.
     * <p>
     * Note that if the label does not exist, then the first synonym that exists is used instead for the label.
     * </p>
     *
     * @param vocabularyNode - the <code>Vocabulary</code> node inst
     * @param identifier - identifier code for the term
     * @param label - long-form English-language name for the term
     * @param description - definition or description of the term
     * @param synonyms - synonyms of the term
     * @param parents - parent terms (direct ancestors) of the given term
     * @param ancestors - ancestor terms of the given term
     * @throws VocabularyIndexException when node cannot be created
     */
    protected void createNCITVocabularyTermNode(Node vocabularyNode, String identifier, String label,
        String description, String[] synonyms, String[] parents, String[] ancestors)
        throws VocabularyIndexException
    {
        try {
            Node vocabularyTermNode = vocabularyNode.addNode("./" + identifier, "lfs:VocabularyTerm");
            vocabularyTermNode.setProperty("identifier", identifier);

            // If the label does not exist, use the first synonym that is listed
            if (label == null) {
                vocabularyTermNode.setProperty("label", synonyms[0]);
            } else {
                vocabularyTermNode.setProperty("label", label);
            }

            vocabularyTermNode.setProperty("description", description);
            vocabularyTermNode.setProperty("synonyms", synonyms);
            vocabularyTermNode.setProperty("parents", parents);
            vocabularyTermNode.setProperty("ancestors", ancestors);
        } catch (RepositoryException e) {
            // If the identifier exists, print the identifier in the error message to identify node
            String nodeName = "";
            if (identifier != null) {
                nodeName = identifier;
            }
            String message = "Failed to create VocabularyTerm node" + identifier + ": " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Deletes the temporary NCIT zip file that was downloaded.
     *
     * @param directory - directory in which the NCIT zip file is located relative to the parser
     * @param name - name of the temporary file
     */
    private void deleteTempZipFile(String directory, String name)
    {
        File tempfile = new File(directory + name + ".zip");
        tempfile.delete();
    }

    /**
     * Saves the JCR session of the homepage node that was obtained from the resource of the request. If this is
     * successful, then the changes made already will be applied to the JCR repository. If not, then all of the changes
     * will not be applied. After the session is saved, then the JCR repository will automatically begin Lucene
     * indexing.
     *
     * @param vocabulariesHomepage - the <code>VocabulariesHomepage</code> node obtained from the request
     * @throws VocabularyIndexException if session is not successfully saved
     */
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

    /**
     * Parses the temporary NCIT zip file and creates <code>VocabularyTerm</code> nodes for each term which are children
     * of the given <code>Vocabulary</code> node representing the NCIT vocabulary instance. Subclasses will have
     * concrete implementations of this method that are specific for their given file types.
     *
     * @param vocabularyNode <code>Vocabulary</code> node which represents the current NCIT instance
     * @throws VocabularyIndexException thrown when an error occurs with parsing
     */

    abstract void parseNCIT(Node vocabularyNode) throws VocabularyIndexException;

    /**
     * Returns the directory path that the temporary NCIT zip file that is downloaded will be placed in. This abstract
     * class allows different parser implementations to control where they put their temporary NCIT zip files are.
     *
     * @return String directory path that the temporary NCIT zip file will be in
     */
    abstract String getTempFileDirectory();

    /**
     * Returns the name that the temporary NCIT zip file to be downloaded is to have. This abstract class allows
     * different parser implementations to specify different temporary file names, which may be useful for avoiding
     * conflicts.
     *
     * @return String name of the temporary NCIT zip file
     */
    abstract String getTempFileName();
}
