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
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;

import javax.jcr.Node;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;

import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;

/**
 * Concrete subclass of AbstractNCITParser for parsing NCIT in OWL file form.
 * @version $Id$
 */
public class NCITOWLParser extends AbstractNCITParser
{
    /**
     * An implementation of the abstract method {@link AbstractNCITParser.getDefaultSource}.
     * @param version - version of NCIT wanted
     */
    @Override
    String getDefaultSource(String version)
    {
        return "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/Thesaurus_" + version + ".OWL.zip";
    }

    @Override
    public boolean canParse(String source)
    {
    	return "ncit_owl".equals(source);
    }

    /**
     */
    @Override
    protected void parseNCIT(final File source, final Node vocabularyNode)
        throws VocabularyIndexException
    {
        try
        {
            ZipInputStream inputStream = new ZipInputStream(new FileInputStream(source));
            inputStream.getNextEntry();

            // Read in NCIT to OntModel using a ZipInputStream
            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
            ontModel.read(inputStream, null);
            inputStream.close();

            // Instantiate an iterator that returns all of the terms as OntClasses
            ExtendedIterator<OntClass> termIterator = ontModel.listNamedClasses();

            while (termIterator.hasNext())
            {
                OntClass term = termIterator.next();

                // Identifier code is the local name of the term
                String identifier = term.getLocalName();

                // The definition is the property "P97". The properties are defined before all of the
                // terms in the OWL file, and they must be retrieved from the OntModel.
                Property descriptionProperty = ontModel.getProperty(term.getNameSpace(), "P97");

                // Then, the property must be passed into the getProperty function in order for the
                // statement object representing the property's contents to be obtained.
                Statement descriptionFromTerm = term.getProperty(descriptionProperty);

                // This handles the case if the statement is blank or null
                String description;
                if (descriptionFromTerm != null) {
                    description = descriptionFromTerm.getString();
                } else {
                    description = "";
                }

                String[] synonyms = getSynonyms(ontModel, term);
                String[] parents = getAncestors(term, true);
                String[] ancestors = getAncestors(term, false);

                // The label is the term label. The language option is null because "EN" doesn't return
                // a correct label.
                String suppliedLabel = term.getLabel(null);
                String defaultLabel = synonyms[0];
                String label = StringUtils.defaultIfBlank(suppliedLabel, defaultLabel);

                createNCITVocabularyTermNode(vocabularyNode, identifier, label, description, synonyms,
                    parents, ancestors);
            }

            termIterator.close();
            ontModel.close();
        } catch (FileNotFoundException e) {
            String message = "Could not find the temporary OWL zip file for parsing: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } catch (IOException e) {
            String message = "Could not read the temporary OWL zip file for parsing: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    // Instantiates an iterator which collects all properties with the name "P90" (i.e. synonym) and
    // puts it into a set, which is then converted to an array.
    private String[] getSynonyms(OntModel ontModel, OntClass term)
    {
        Set<String> synonyms = new HashSet<String>();
        final Property synonymProperty = ontModel.getProperty(term.getNameSpace(), "P90");
        final ExtendedIterator<Statement> allSynonyms = term.listProperties(synonymProperty);
        while (allSynonyms.hasNext()) {
            Statement synonymTerm = allSynonyms.next();
            synonyms.add(synonymTerm.getString());
        }
        allSynonyms.close();
        return synonyms.toArray(new String[0]);
    }

    // Instantiates an iterator which collects all the listed ancestors of the term listed in the OWL file
    // and puts it into a set, which is then converted to an array.

    // If directAncestor is true, then the iterator returned only direct ancestors (i.e. parents). If it is false
    // then all ancestors are listed. Ancestors are superclases.
    private String[] getAncestors(OntClass term, boolean directAncestor)
    {
        Set<String> ancestors = new HashSet<String>();
        final ExtendedIterator<OntClass> allAncestors = term.listSuperClasses(directAncestor);
        while (allAncestors.hasNext())
        {
            OntClass ancestorTerm = allAncestors.next();
            ancestors.add(ancestorTerm.getLocalName());
        }
        allAncestors.close();
        return ancestors.toArray(new String[0]);
    }
}
