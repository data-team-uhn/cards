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
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.io.FileUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.sickkids.ccm.lfs.vocabularies.spi.SourceParser;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyDescription;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyDescriptionBuilder;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexer;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyParserUtils;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyTermSource;

/**
 * Generic indexer for vocabularies available on the <a href="http://data.bioontology.org/">BioOntology</a> portal.
 * BioOntology is a RESTfull server serving a large collection of vocabularies, available as OWL sources, along with
 * meta-information.
 * <p>
 * To be invoked, this indexer requires that:
 * <ul>
 * <li>the {@code source} request parameter is {@code bioontology}</li>
 * <li>the {@code identifier} request parameter is a valid, case-sensitive identifier of a vocabulary available in the
 * BioOntology server</li>
 * </ul>
 * An optional {@code version} parameter can be used to index a specific version of the target vocabulary. If not
 * specified, then the latest available version will be used.
 *
 * @version $Id$
 */
@Component(
    service = VocabularyIndexer.class,
    name = "VocabularyIndexer.fileupload")
public class FileUploadIndexer implements VocabularyIndexer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadIndexer.class);

    @Reference
    private VocabularyParserUtils utils;

    /**
     * Automatically injected list of all available parsers. A {@code volatile} list dynamically changes when
     * implementations are added, removed, or replaced.
     */
    @Reference
    private volatile List<SourceParser> parsers;

    /** The vocabulary node where the indexed data must be placed. */
    private InheritableThreadLocal<Node> vocabularyNode = new InheritableThreadLocal<>();

    @Override
    public boolean canIndex(String source)
    {
        return "fileupload".equals(source);
    }

    @Override
    public void index(final String source, final SlingHttpServletRequest request,
        final SlingHttpServletResponse response)
        throws IOException, VocabularyIndexException
    {
        // Obtain relevant request parameters.
        String identifier = request.getParameter("identifier");
        String version = request.getParameter("version");
        String vocabName = request.getParameter("vocabName");
        String overwrite = request.getParameter("overwrite");
        RequestParameter uploadedOntology = request.getRequestParameter("filename");

        // Obtain the resource of the request and adapt it to a JCR node. This must be the /Vocabularies homepage node.
        Node homepage = request.getResource().adaptTo(Node.class);

        File temporaryFile = null;
        try {
            // Throw exceptions if mandatory parameters are not found or if homepage node cannot be found
            if (identifier == null) {
                throw new VocabularyIndexException("Mandatory [identifier] parameter not provided.");
            }

            if (homepage == null) {
                throw new VocabularyIndexException("Could not access resource of your request.");
            }

            // Delete the Vocabulary node already representing this vocabulary instance if it exists
            this.utils.clearVocabularyNode(homepage, identifier, overwrite);

            // Load the description
            VocabularyDescription description;
            if (uploadedOntology == null) {
                description = null;
            } else {
                description = new VocabularyDescriptionBuilder()
                    .withSource("fileupload")
                    .withSourceFormat(OntologyFormatDetection.getSourceFormat(uploadedOntology.getFileName()))
                    .withIdentifier(identifier)
                    .withName(vocabName)
                    .withVersion(version)
                    .build();
            }

            // Check that we have a known parser for this vocabulary
            SourceParser parser =
                this.parsers.stream().filter(p -> p.canParse(description.getSourceFormat())).findFirst()
                    .orElseThrow(() -> new VocabularyIndexException("No known parsers for vocabulary [" + identifier
                        + "] in format [" + description.getSourceFormat() + "]"));

            // Download the source
            if (uploadedOntology == null) {
                temporaryFile = null;
            } else {
                temporaryFile = File.createTempFile("LocalUpload-" + identifier, "");
                FileUtils.copyInputStreamToFile(uploadedOntology.getInputStream(), temporaryFile);
            }

            // Create a new Vocabulary node representing this vocabulary
            this.vocabularyNode.set(createVocabularyNode(homepage, description));

            // Parse the source file and create VocabularyTerm node children
            parser.parse(temporaryFile, description, this::createVocabularyTermNode);

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
            this.utils.writeStatusJson(request, response, false, "Vocabulary indexing error: " + e.getMessage());
            LOGGER.error("Vocabulary indexing error: {}", e.getMessage(), e);
        } finally {
            // Delete temporary source file
            FileUtils.deleteQuietly(temporaryFile);
            this.vocabularyNode.remove();
        }
    }

    /**
     * Creates a <code>Vocabulary</code> node that represents the current vocabulary instance with the identifier as the
     * name of the node.
     *
     * @param homepage <code>VocabulariesHomepage</code> node instance that will be parent of the new vocabulary node
     * @param description the vocabulary description, holding all the relevant information about the vocabulary
     * @return the <code>Vocabulary</code> node that was created
     * @throws VocabularyIndexException when node cannot be created
     */
    private Node createVocabularyNode(final Node homepage, final VocabularyDescription description)
        throws VocabularyIndexException
    {
        try {
            Node result = homepage.addNode("./" + description.getIdentifier(), "lfs:Vocabulary");
            result.setProperty("identifier", description.getIdentifier());
            result.setProperty("name", description.getName());
            result.setProperty("description", description.getDescription());
            result.setProperty("source", description.getSource());
            result.setProperty("version", description.getVersion());
            result.setProperty("website", description.getWebsite());
            result.setProperty("citation", description.getCitation());
            return result;
        } catch (RepositoryException e) {
            String message = "Failed to create Vocabulary node: " + e.getMessage();
            LOGGER.error(message, e);
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Creates a <code>VocabularyTerm</code> node representing an individual term of the vocabulary.
     *
     * @param term the term data
     * @throws VocabularyIndexException when a node cannot be created
     */
    private void createVocabularyTermNode(VocabularyTermSource term)
    {
        OntologyIndexerUtils.createVocabularyTermNode(term, this.vocabularyNode);
    }

    /**
     * Saves the JCR session of the homepage node that was obtained from the resource of the request. If this is
     * successful, then the changes made already will be applied to the JCR repository. If not, then all of the changes
     * will be discarded, reverting to the original state.
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
}
