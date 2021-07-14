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

package io.uhndata.cards.vocabularies.internal;

import java.io.File;
import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.vocabularies.spi.VocabularyIndexException;
import io.uhndata.cards.vocabularies.spi.VocabularyIndexer;
import io.uhndata.cards.vocabularies.spi.VocabularyParserUtils;

/**
 * Abstract class specifying a vocabulary ontology indexer specifically for the National Cancer Institute Thesaurus. The
 * class implements methods common to parsers for the NCIT, but omits file-type specific methods. The parsing and node
 * creation process is done as a transaction, meaning that if it fails, then proposed changes saved in storage will not
 * be applied, and the repository will be left in its original state.
 * <p>
 * The indexer assumes that the resource of the response it is given is a <code>VocabulariesHomepage</code> node under
 * which the <code>Vocabulary</code> node instance should be stored in the Jackrabbit Oak repository as a child.
 * </p>
 *
 * @version $Id$
 */
public abstract class AbstractNCITIndexer implements VocabularyIndexer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNCITIndexer.class);

    @Reference
    protected VocabularyParserUtils utils;

    /**
     * Method called by the {@link io.uhndata.cards.vocabularies.VocabularyIndexerServlet} to parse and index a NCIT
     * vocabulary. Specifying the version to index is mandatory. There are two optional parameters.
     * <p>
     * <code>"localpath"</code> - allows downloading of NCIT from a path relative to the VocabularyIndexerServlet.
     * </p>
     * <p>
     * <code>"httppath"</code>- allows downloading of NCIT from a url other than
     * "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/".
     * </p>
     * Also the following parameter is required if you want to overwrite a vocabulary that already exists in the
     * repository:
     * <p>
     * <code>overwrite</code> - must be "true" or else overwritting is not permitted and a
     * {@link io.uhndata.cards.vocabularies.spi.VocabularyIndexException} is thrown.
     * </p>
     * You cannot create a vocabulary with the same identifier as an existing vocabulary unless you overwrite it.
     *
     * @param request http request from {@link io.uhndata.cards.vocabularies.VocabularyIndexerServlet}
     * @param response http response from {@link io.uhndata.cards.vocabularies.VocabularyIndexerServlet}
     * @throws IOException thrown when response Json cannot be written
     */
    @Override
    public void index(final String source, final SlingHttpServletRequest request,
        final SlingHttpServletResponse response)
        throws IOException, VocabularyIndexException
    {
        // Obtain relevant request parameters.
        String identifier = StringUtils.defaultIfBlank(request.getParameter("identifier"), "ncit");
        String version = request.getParameter("version");
        String httppath = request.getParameter("httppath");
        String localpath = request.getParameter("localpath");
        String overwrite = request.getParameter("overwrite");

        // Obtain the resource of the request and adapt it to a JCR node. This must be the /Vocabularies homepage node.
        Node homepage = request.getResource().adaptTo(Node.class);

        final File temporaryFile = File.createTempFile(identifier, "");
        try {
            // Throw exceptions if mandatory parameters are not found or if homepage node cannot be found
            if (version == null) {
                throw new VocabularyIndexException("Mandatory version parameter not provided.");
            }

            if (homepage == null) {
                throw new VocabularyIndexException("Could not access resource of your request.");
            }

            // Delete the Vocabulary node already representing this vocabulary instance if it exists
            this.utils.clearVocabularyNode(homepage, identifier, overwrite);

            // Load temporary NCIT zip file. Default location is at https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/
            String sourceLocation = getDefaultSource(version);

            VocabularyZipLoader zipLoader = new VocabularyZipLoader();
            if (localpath != null) {
                sourceLocation = localpath;
                zipLoader.loadZipLocal(localpath, temporaryFile);
            } else if (httppath != null) {
                sourceLocation = httppath;
                zipLoader.loadZipHttp(httppath, temporaryFile);
            } else {
                zipLoader.loadZipHttp(sourceLocation, temporaryFile);
            }

            // Create a new Vocabulary node instance representing this vocabulary instance
            String name = "National Cancer Institute Thesaurus";
            Node vocabularyNode = createNCITVocabularyNode(homepage, identifier, name, sourceLocation, version);

            // Parse the NCIT zip file and create VocabularyTerm node children
            parseNCIT(temporaryFile, vocabularyNode);

            /*
             * Save the JCR session. If any errors occur before this step, all proposed changes will not be applied and
             * the repository will remain in its original state. Lucene indexing is automatically performed by the
             * Jackrabbit Oak repository when this is performed.
             */
            saveSession(homepage);

            // Success response json
            this.utils.writeStatusJson(request, response, true, null);
        } catch (Exception e) {
            // If parsing fails, return an error json with the exception message
            this.utils.writeStatusJson(request, response, false, "NCIT Flat indexing error: " + e.getMessage());
            LOGGER.error("NCIT indexing error: {}", e.getMessage(), e);
        } finally {
            // Delete temporary source file
            FileUtils.deleteQuietly(temporaryFile);
        }
    }

    /**
     * Creates a <code>Vocabulary</code> node that represents the current vocabulary instance with the identifier. as
     * the name of the node. The vocabulary property <code>website</code> is currently fixed to
     * https://ncit.nci.nih.gov/ncitbrowser/.
     *
     * @param homepage <code>VocabulariesHomepage</code> node instance that will be parent of the new vocabulary node
     * @param identifier short unique identifier of the vocabulary
     * @param name the official name of the vocabulary
     * @param source source of the vocabulary, usually a URL
     * @param version the version of the vocabulary, a short string
     * @return the <code>Vocabulary</code> node that was created
     * @throws VocabularyIndexException when node cannot be created
     */
    private Node createNCITVocabularyNode(Node homepage, String identifier, String name, String source, String version)
        throws VocabularyIndexException
    {
        try {
            Node vocabularyNode = homepage.addNode("./" + identifier, "cards:Vocabulary");
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
     * @param vocabularyNode the parent <code>Vocabulary</code> node
     * @param identifier short identifier code for the term
     * @param label long-form name for the term
     * @param description longer definition or description of the term
     * @param synonyms synonyms for this the term
     * @param parents the parent terms (direct ancestors) of the given term, as a list of identifiers
     * @param ancestors ancestor terms of the given term, as a list of identifiers
     * @throws VocabularyIndexException when node cannot be created
     */
    protected void createNCITVocabularyTermNode(Node vocabularyNode, String identifier, String label,
        String description, String[] synonyms, String[] parents, String[] ancestors)
        throws VocabularyIndexException
    {
        try {
            Node vocabularyTermNode = vocabularyNode.addNode("./" + identifier, "cards:VocabularyTerm");
            vocabularyTermNode.setProperty("identifier", identifier);

            // If the label does not exist, use the first synonym that is listed
            // In the impossible case that there are no synonyms, use a blank String
            String defaultLabel = synonyms != null && synonyms.length > 0 ? synonyms[0] : "";
            String safeLabel = StringUtils.defaultIfBlank(label, defaultLabel);
            vocabularyTermNode.setProperty("label", safeLabel);

            vocabularyTermNode.setProperty("description", description);
            vocabularyTermNode.setProperty("synonyms", synonyms);
            vocabularyTermNode.setProperty("parents", parents);
            vocabularyTermNode.setProperty("ancestors", ancestors);
        } catch (RepositoryException e) {
            // If the identifier exists, print the identifier in the error message to identify node
            String message =
                "Failed to create VocabularyTerm node " + StringUtils.defaultString(identifier) + ": " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Saves the JCR session of the homepage node that was obtained from the resource of the request. If this is
     * successful, then the changes made already will be applied to the JCR repository. If not, then all of the changes
     * will not be applied. After the session is saved, then the JCR repository will automatically begin Lucene
     * indexing.
     *
     * @param vocabulariesHomepage the <code>VocabulariesHomepage</code> node obtained from the request
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
     * Parses the temporary NCIT source file and creates <code>VocabularyTerm</code> nodes for each term. The new term
     * nodes must be children of the given <code>Vocabulary</code> node representing the NCIT vocabulary instance.
     *
     * @param vocabularyNode <code>Vocabulary</code> node being indexed
     * @throws VocabularyIndexException when an error occurs with parsing
     */
    protected abstract void parseNCIT(File sourceFile, Node vocabularyNode) throws VocabularyIndexException;

    /**
     * Returns the default source from which to obtain the NCIT zip file. This is an abstract method as individual
     * subclasses will implement their own default sources.
     *
     * @param version the version of NCIT wanted, must be an available version
     */
    abstract String getDefaultSource(String version);
}
