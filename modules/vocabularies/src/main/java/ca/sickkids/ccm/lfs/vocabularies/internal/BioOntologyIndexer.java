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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.sickkids.ccm.lfs.vocabularies.spi.RepositoryHandler;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyDescription;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexer;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyParserUtils;

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
    name = "VocabularyIndexer.bioontology")
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class BioOntologyIndexer implements VocabularyIndexer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BioOntologyIndexer.class);

    @Reference
    private VocabularyParserUtils utils;

    @Reference(name = "RepositoryHandler.bioontology")
    private RepositoryHandler repository;

    /** The vocabulary node where the indexed data must be placed. */
    private ThreadLocal<Node> vocabularyNode = new ThreadLocal<>();

    private ThreadLocal<Property> labelProperty = new ThreadLocal<>();

    @Override
    public boolean canIndex(String source)
    {
        return "bioontology".equals(source);
    }

    @Override
    public void index(final String source, final SlingHttpServletRequest request,
        final SlingHttpServletResponse response)
        throws IOException, VocabularyIndexException
    {
        // Obtain relevant request parameters.
        String identifier = request.getParameter("identifier");
        String version = request.getParameter("version");
        String overwrite = request.getParameter("overwrite");

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
            VocabularyDescription description = this.repository.getVocabularyDescription(identifier, version);

            // Download the source
            temporaryFile = this.repository.downloadVocabularySource(description);

            // Create a new Vocabulary node representing this vocabulary
            this.vocabularyNode.set(createVocabularyNode(homepage, description));

            // Parse the source file and create VocabularyTerm node children
            parse(temporaryFile);

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

    protected void parse(final File source)
        throws VocabularyIndexException
    {
        // For efficiency, we load the ontology in a temporary filesystem-backed database instead of all-in-memory
        Path temporaryDatasetPath = null;
        try (InputStream input = new FileInputStream(source)) {
            // First step, load the data from the OWL file into the data store
            temporaryDatasetPath = Files.createTempDirectory(null);
            Dataset store = TDB2Factory.connectDataset(temporaryDatasetPath.toString());
            // This starts a transaction for the loading part
            store.begin(ReadWrite.WRITE);
            Model rawModel = store.getDefaultModel();
            rawModel.read(input, null);
            rawModel.commit();
            store.end();

            // Second step, read the model and load it into Sling
            // Also in a transaction; although reading shouldn't require one, Jena recommends it
            store.begin(ReadWrite.READ);
            // OWL_LITE_MEM_TRANS_INF is fast enough for our needs, since the NCIT ontology isn't very complex,
            // it has simple subclasses and properties
            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_TRANS_INF, rawModel);

            // Cache the rdf:label property, it will be used a lot later on
            this.labelProperty.set(ontModel.getProperty("http://www.w3.org/2000/01/rdf-schema#label"));

            // This lists all the named classes, which for NCIT means all the terms of the thesaurus
            ExtendedIterator<OntClass> termIterator = ontModel.listNamedClasses();
            // Load each term into a vocabulary node
            while (termIterator.hasNext()) {
                processTerm(termIterator.next());
            }

            // Close iterator for terms and OntModel to save memory
            termIterator.close();
            ontModel.close();
            // Close the transaction
            store.end();
        } catch (FileNotFoundException e) {
            String message = "Could not find the temporary OWL file for parsing: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } catch (IOException e) {
            String message = "Could not read the temporary OWL file for parsing: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } finally {
            // Delete the temporary data store
            FileUtils.deleteQuietly(temporaryDatasetPath.toFile());
            // Clean up threadlocal variables so that memory can be reclaimed
            this.labelProperty.remove();
        }
    }

    private void processTerm(OntClass term) throws VocabularyIndexException
    {
        // Identifier code is the local name of the term
        String identifier = term.getLocalName();

        // Read all the statements about this term, and extract property=value pairs
        StmtIterator properties = term.listProperties();
        MultiValuedMap<String, String> gatheredProperties = new ArrayListValuedHashMap<>();
        while (properties.hasNext()) {
            Statement statement = properties.next();
            Property predicate = statement.getPredicate();
            String label = predicate.hasProperty(this.labelProperty.get())
                ? predicate.getProperty(this.labelProperty.get()).getString()
                : predicate.getLocalName();
            RDFNode object = statement.getObject();
            String value = object.isResource() ? object.asResource().getLocalName() : object.asLiteral().getString();
            gatheredProperties.put(label, value);
        }

        String[] parents = getAncestors(term, false);
        String[] ancestors = getAncestors(term, true);

        // The label is the term label. The language option is null because the OWL file doesn't specify a language.
        String label = term.getLabel(null);

        // Create VocabularyTerm node as child of vocabularyNode using inherited protected method
        createVocabularyTermNode(this.vocabularyNode.get(), identifier, label, parents, ancestors, gatheredProperties);
    }

    /**
     * Gets the ancestors for a vocabulary term. The method can return only the parents (direct ancestors), or all of
     * the transitive ancestors.
     *
     * @param term the OntClass representing the term for which ancestors should be retrieved
     * @param transitive {@code false} if only parents (i.e. direct ancestors) are wanted, {@code true} if all
     *            transitive ancestors are wanted
     * @return String array containing the identifiers of all the term's ancestors
     */
    private String[] getAncestors(OntClass term, boolean transitive)
    {
        final Set<String> ancestors = new LinkedHashSet<>();

        final ExtendedIterator<OntClass> allAncestors = term.listSuperClasses(!transitive);
        while (allAncestors.hasNext()) {
            // Obtain the identifier of each ancestor and add it to the set
            OntClass ancestorTerm = allAncestors.next();
            ancestors.add(ancestorTerm.getLocalName());
        }
        allAncestors.close();

        // Convert the set to an array and return it
        return ancestors.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
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
    private void createVocabularyTermNode(Node vocabularyNode, String identifier, String label,
        String[] parents, String[] ancestors, MultiValuedMap<String, String> gatheredProperties)
        throws VocabularyIndexException
    {
        try {
            Node vocabularyTermNode;
            try {
                vocabularyTermNode = vocabularyNode.addNode("./" + identifier, "lfs:VocabularyTerm");
            } catch (ItemExistsException e) {
                // Sometimes terms appear twice; we'll just update the existing node
                vocabularyTermNode = vocabularyNode.getNode(identifier);
            }
            vocabularyTermNode.setProperty("identifier", identifier);

            vocabularyTermNode.setProperty("label", StringUtils.defaultString(label, identifier));
            vocabularyTermNode.setProperty("parents", parents);
            vocabularyTermNode.setProperty("ancestors", ancestors);

            Iterator<Map.Entry<String, Collection<String>>> it = gatheredProperties.asMap().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Collection<String>> entry = it.next();
                String[] valuesArray = entry.getValue().toArray(ArrayUtils.EMPTY_STRING_ARRAY);
                // Sometimes the source may contain more than one label or description, but we can't allow that.
                // Always use one value for these special fields.
                if (valuesArray.length == 1 || "label".equals(entry.getKey()) || "description".equals(entry.getKey())) {
                    vocabularyTermNode.setProperty(entry.getKey(), valuesArray[0]);
                } else {
                    vocabularyTermNode.setProperty(entry.getKey(), valuesArray);
                }
            }
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
}
