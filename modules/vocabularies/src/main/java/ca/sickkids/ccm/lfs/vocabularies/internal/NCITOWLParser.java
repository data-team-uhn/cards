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
            // Create an OntModel to represent the vocabulary and read in the zip file using a ZipInputStream
            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
            ontModel.read(input, null);

            // Set the needed objects in ThreadLocals
            this.vocabularyNode.set(vocabularyNode);
            this.descriptionProperty
                .set(ontModel.getProperty("http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#", "P97"));
            this.synonymProperty
                .set(ontModel.getProperty("http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#", "P90"));

            // Instantiate an iterator that returns all of the terms as OntClasses
            ExtendedIterator<OntClass> termIterator = ontModel.listNamedClasses();

            while (termIterator.hasNext()) {
                OntClass term = termIterator.next();

                // Identifier code is the local name of the term
                String identifier = term.getLocalName();

                /*
                 * Then, the property must be passed into the getProperty function in order for the statement object
                 * representing the property's contents to be obtained.
                 */
                Statement descriptionFromTerm = term.getProperty(this.descriptionProperty.get());

                // Get String from Statement, and handle the case if the statement is blank or null
                String description = StringUtils.defaultIfBlank(descriptionFromTerm.getString(), "");

                String[] synonyms = getSynonyms(term);
                String[] parents = getAncestors(term, true);
                String[] ancestors = getAncestors(term, false);

                /*
                 * The label is the term label. The language option is null because "EN" doesn't return a correct label
                 */
                String label = term.getLabel(null);

                // Create VocabularyTerm node as child of vocabularyNode using inherited protected method
                createNCITVocabularyTermNode(this.vocabularyNode.get(), identifier, label, description, synonyms,
                    parents, ancestors);
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

    /**
     * Returns a String array of synonyms for a specified term. This is done by obtaining an iterator that collects all
     * instances of the property "FULL_SYN" or "P90".
     *
     * @param term - OntClass representing the term
     * @return String array containing all the synonyms of the term
     */
    private String[] getSynonyms(OntClass term)
    {
        /*
         * A set is used for initial storage so that the number of synonyms does not need to be specified. This removes
         * order from the list of synonyms.
         */
        Set<String> synonyms = new HashSet<>();

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
     * Returns a String array of ancestors for a specified term. The method can return only the parents (direct
     * ancestors) or all of the ancestors. The method obtains an iterator which collects all of the superclasses of the
     * term directly listed in its OWL definition.
     *
     * @param term - the OntClass representing the term for which ancestors should be retrieved
     * @param directAncestor - true if only parents (i.e. direct ancestors) are wanted, false if otherwise (if all
     *            ancestors are wanted)
     * @return String array containing the identifiers of all specified ancestors
     */
    private String[] getAncestors(OntClass term, boolean directAncestor)
    {
        /*
         * A set is used for initial storage so that the number of ancestors does not need to be specified. This removes
         * order from the list of ancestors.
         */
        Set<String> ancestors = new HashSet<>();

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
