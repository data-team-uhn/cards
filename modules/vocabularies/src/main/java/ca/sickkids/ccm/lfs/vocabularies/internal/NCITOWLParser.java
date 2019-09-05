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
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Node;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyParser;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyParserUtils;

/**
 * Concrete subclass of AbstractNCITParser for parsing NCIT in OWL file form.
 *
 * @version $Id$
 */
@Component(
    service = VocabularyParser.class,
    name = "ncit-owl",
    reference = { @Reference(field = "utils", name = "utils", service = VocabularyParserUtils.class) })
public class NCITOWLParser extends AbstractNCITParser
{
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
    public boolean canParse(String source)
    {
        return "ncit_owl".equals(source);
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
        try (InputStream input = new FileInputStream(source)) {
            // Create an OntModel to represent the vocabulary and read in the source
            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
            ontModel.read(input, null);

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
        } catch (FileNotFoundException e) {
            String message = "Could not find the temporary OWL file for parsing: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } catch (IOException e) {
            String message = "Could not read the temporary OWL file for parsing: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } finally {
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
        String[] parents = getAncestors(term, true);
        String[] ancestors = getAncestors(term, false);

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
        final Set<String> synonyms = new HashSet<>();

        final ExtendedIterator<Statement> allSynonyms = term.listProperties(this.synonymProperty.get());

        while (allSynonyms.hasNext()) {
            Statement synonymTerm = allSynonyms.next();
            synonyms.add(synonymTerm.getString());
        }
        allSynonyms.close();

        // Convert the set to an array and return it
        return synonyms.toArray(new String[0]);
    }

    /**
     * Gets the ancestors for a vocabulary term. The method can return only the parents (direct ancestors), or all of
     * the transitive ancestors.
     *
     * @param term the OntClass representing the term for which ancestors should be retrieved
     * @param directAncestor {@code true} if only parents (i.e. direct ancestors) are wanted, {@code false} if all
     *            transitive ancestors are wanted
     * @return String array containing the identifiers of all the term's ancestors
     */
    private String[] getAncestors(OntClass term, boolean directAncestor)
    {
        final Set<String> ancestors = new HashSet<>();

        final ExtendedIterator<OntClass> allAncestors = term.listSuperClasses(directAncestor);
        while (allAncestors.hasNext()) {
            // Obtain the identifier of each ancestor and add it to the set
            OntClass ancestorTerm = allAncestors.next();
            ancestors.add(ancestorTerm.getLocalName());
        }
        allAncestors.close();

        // Convert the set to an array and return it
        return ancestors.toArray(new String[0]);
    }
}
