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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.jcr.Node;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.vocabularies.spi.VocabularyIndexException;
import io.uhndata.cards.vocabularies.spi.VocabularyIndexer;
import io.uhndata.cards.vocabularies.spi.VocabularyParserUtils;

/**
 * Concrete subclass of {@link AbstractNCITIndexer} for indexing NCIT in OWL file form.
 *
 * @version $Id$
 */
@Component(
    service = VocabularyIndexer.class,
    name = "VocabularyParser.ncit-owl",
    reference = { @Reference(field = "utils", name = "utils", service = VocabularyParserUtils.class) })
public class NCITOWLIndexer extends AbstractNCITIndexer
{
    /** An empty String[] array to use for {@code Set.toArray}, we don't want to create a new array for each call. */
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /** The vocabulary node where the indexed data must be placed. */
    private ThreadLocal<Node> vocabularyNode = new ThreadLocal<>();

    /**
     * Holds the OWL Property for a vocabulary term "definition". Its code is {@code P97}, and its name is
     * {@code DEFINITION}.
     */
    private ThreadLocal<Property> descriptionProperty = new ThreadLocal<>();

    /**
     * Holds the OWL Property for a vocabulary term "synonyms". Its code is {@code P90}, and its name is
     * {@code FULL_SYN}.
     */
    private ThreadLocal<Property> synonymProperty = new ThreadLocal<>();

    @Override
    public boolean canIndex(String source)
    {
        return "ncit-owl".equals(source);
    }

    @Override
    String getDefaultSource(String version)
    {
        return "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/Thesaurus_" + version + ".OWL.zip";
    }

    @Override
    protected void parseNCIT(final File source, final Node vocabularyNode)
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

            // Set the needed objects in ThreadLocals
            this.vocabularyNode.set(vocabularyNode);
            this.descriptionProperty
                .set(ontModel.getProperty("http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#", "P97"));
            this.synonymProperty
                .set(ontModel.getProperty("http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#", "P90"));

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
            this.descriptionProperty.remove();
            this.synonymProperty.remove();
            this.vocabularyNode.remove();
        }
    }

    private void processTerm(OntClass term) throws VocabularyIndexException
    {
        // Identifier code is the local name of the term
        String identifier = term.getLocalName();

        // The description is given as a property Statement
        Statement descriptionFromTerm = term.getProperty(this.descriptionProperty.get());

        // Get String from Statement, and handle the case if the statement is blank or null
        String description =
            descriptionFromTerm == null ? "" : StringUtils.defaultIfBlank(descriptionFromTerm.getString(), "");

        String[] synonyms = getSynonyms(term);
        String[] parents = getAncestors(term, false);
        String[] ancestors = getAncestors(term, true);

        // The label is the term label. The language option is null because the OWL file doesn't specify a language.
        String label = term.getLabel(null);

        // Create VocabularyTerm node as child of vocabularyNode using inherited protected method
        createNCITVocabularyTermNode(this.vocabularyNode.get(), identifier, label, description, synonyms,
            parents, ancestors);
    }

    /**
     * Gets the synonyms for a vocabulary term.
     *
     * @param term OntClass representing the term for which synonyms should be retrieved
     * @return String array containing all the synonyms of the term
     */
    private String[] getSynonyms(OntClass term)
    {
        final Set<String> synonyms = new LinkedHashSet<>();

        final ExtendedIterator<Statement> allSynonyms = term.listProperties(this.synonymProperty.get());

        while (allSynonyms.hasNext()) {
            Statement synonymTerm = allSynonyms.next();
            synonyms.add(synonymTerm.getString());
        }
        allSynonyms.close();

        // Convert the set to an array and return it
        return synonyms.toArray(EMPTY_STRING_ARRAY);
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
        return ancestors.toArray(EMPTY_STRING_ARRAY);
    }
}
