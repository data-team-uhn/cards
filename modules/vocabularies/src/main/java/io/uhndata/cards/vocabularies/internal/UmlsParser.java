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
import java.util.function.Consumer;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.vocabularies.spi.SourceParser;
import io.uhndata.cards.vocabularies.spi.VocabularyDescription;
import io.uhndata.cards.vocabularies.spi.VocabularyIndexException;
import io.uhndata.cards.vocabularies.spi.VocabularyParserUtils;
import io.uhndata.cards.vocabularies.spi.VocabularyTermSource;

/**
 * Parser for vocabulary sources in UMLS format.
 *
 * @version $Id$
 */

@Component(
    service = SourceParser.class,
    name = "SourceParser.UMLS")
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class UmlsParser implements SourceParser
{

    @Reference
    private VocabularyParserUtils utils;

    private InheritableThreadLocal<Property> labelProperty = new InheritableThreadLocal<>();

    @Override
    public boolean canParse(String format)
    {
        return "UMLS".equals(format);
    }

    @Override
    public void parse(final File source, final VocabularyDescription vocabularyDescription,
        final Consumer<VocabularyTermSource> consumer)
        throws VocabularyIndexException, IOException
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
            // OWL_LITE_MEM_TRANS_INF is fast enough for our needs, since ontologies aren't usually very complex,
            // having just simple subclasses and properties
            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_TRANS_INF, rawModel);

            // Cache the rdf:label property, it will be used a lot later on
            this.labelProperty.set(ontModel.getProperty("http://www.w3.org/2000/01/rdf-schema#label"));

            // This lists all the named classes, the actual terms of the vocabulary
            ExtendedIterator<OntClass> termIterator = ontModel.listNamedClasses();
            // Load each term into a vocabulary node
            while (termIterator.hasNext()) {
                processTerm(termIterator.next(), consumer);
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

    private void processTerm(final OntClass term, final Consumer<VocabularyTermSource> consumer)
        throws VocabularyIndexException
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

        // Backing up if rdf jena utils failed to get Identifier from term uri
        if (identifier.length() == 0 && gatheredProperties.get("id").size() > 0) {
            identifier = gatheredProperties.get("id").iterator().next();
        } else if (identifier.length() == 0 && term.getURI().split("/").length > 0) {
            int uriDepth = term.getURI().split("/").length;
            identifier = term.getURI().split("/")[uriDepth - 1];
        }

        String[] parents = getAncestors(term, false);
        String[] ancestors = getAncestors(term, true);

        // The label is the term label. The language option is null because the OWL file doesn't specify a language.
        String label = term.getLabel(null);

        // Create VocabularyTerm node as child of vocabularyNode using inherited protected method
        consumer.accept(new VocabularyTermSource(identifier, label, parents, ancestors, gatheredProperties,
            term.getURI()));
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
}
