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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;

import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyParser;

/**
 * Concrete subclass of AbstractNCITParser for parsing NCIT in flat file form.
 *
 * @version $Id$
 */
@Component(service = VocabularyParser.class, name = "ncit-flat")
public class NCITFlatParser extends AbstractNCITParser
{
    // Column numbers of the properties we want to extract.
    private static final int IDENTIFIER_COLUMN = 0;

    private static final int PARENTS_COLUMN = 2;

    private static final int SYNONYMS_COLUMN = 3;

    private static final int DESCRIPTION_COLUMN = 4;

    private static final int LABEL_COLUMN = 5;

    // UTF_8 charset instance to use
    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    /** An empty String[] array to use for {@code Set.toArray}, we don't want to create a new array for each call. */
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    @Override
    public boolean canParse(String source)
    {
        return "ncit".equals(source);
    }

    /**
     * An implementation of the abstract method {@link AbstractNCITParser.parseNCIT}. Parses the temporary NCIT zip file
     * and creates JCR nodes for each term. All exceptions from the classes that it uses are handled here.
     *
     * @param vocabularyNode - the <code>Vocabulary</code> node which represents the current NCIT instance to index
     * @throws VocabularyIndexException upon failure to parse vocabulary
     */
    @Override
    void parseNCIT(final File source, final Node vocabularyNode) throws VocabularyIndexException
    {
        try {
            // Extracts term parents the flat file and returns a map of (term, parents) pairs
            Map<String, String[]> parentsMap = returnParentMap(source);

            // Extracts all other properties and creates VocabularyTerm nodes based on them
            createTermNodes(source, parentsMap, vocabularyNode);
        } catch (RepositoryException e) {
            String message = "Failed to access Vocabulary node: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } catch (IOException e) {
            String message = "Failed to read flat file: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Extracts all <code>VocabularyTerm</code> node properties from the NCIT flat file zip and creates JCR nodes.
     * Ancestors of terms are calculated recursively from the parents.
     *
     * @param parentsMap - a map of (term, parents) pairs where "parents" is an array of parent terms
     * @param vocabularyNode - the <code>Vocabulary</code> node which represents the current NCIT instance to index
     * @throws IOException thrown when file input cannot be read
     * @throws RepositoryException thrown when JCR nodes cannot be created
     * @throws VocabularyIndexException throw by failure to create appropriate JCR node
     */
    private void createTermNodes(final File source, Map<String, String[]> parentsMap, Node vocabularyNode)
        throws IOException, RepositoryException, VocabularyIndexException
    {
        try (Reader input = new BufferedReader(new InputStreamReader(new FileInputStream(source), UTF_8));
            // The NCIT source is an unquoted tab-delimited file
            // We need withQuote(null) to keep all quotes as part of the text, and not interpreted as special chars
            CSVParser csvParser = CSVParser.parse(input, CSVFormat.TDF.withQuote(null));) {

            Iterator<CSVRecord> csvIterator = csvParser.iterator();

            while (csvIterator.hasNext()) {
                CSVRecord row = csvIterator.next();

                String identifier = row.get(IDENTIFIER_COLUMN);
                String description = row.get(DESCRIPTION_COLUMN);

                String synonymString = row.get(SYNONYMS_COLUMN);

                // Synonym entry is a String with terms separated by "|" so split the String into a String[]
                String[] synonymsArray = synonymString.split("\\|");

                // Make the first synonym of the term the default label if no label is supplied by the term
                String defaultLabel = synonymsArray[0];
                String suppliedLabel = row.get(LABEL_COLUMN);
                String label = StringUtils.defaultIfBlank(suppliedLabel, defaultLabel);

                String[] parentsArray = parentsMap.get(identifier);

                /*
                 * Ancestors are recursively calculated from parents. Since computeAnestors returns the ancestors as a
                 * Set<String>, it must be converted to a String[] here.
                 */
                String[] ancestorsArray = computeAncestors(parentsMap, identifier).toArray(EMPTY_STRING_ARRAY);

                // This method is a protected method in AbstractNCITParser for creating VocabularyTerm nodes
                createNCITVocabularyTermNode(vocabularyNode, identifier, label, description, synonymsArray,
                    parentsArray, ancestorsArray);
            }
        }
    }

    /**
     * Returns a HashMap containing a (String, String[]) pair representing (term, parents). This is extracted from the
     * temporary NCIT zip flat file.
     *
     * @return a map which stores (term, parent) pairs
     * @throws IOException thrown when temporary NCIT file cannot be read
     */
    private Map<String, String[]> returnParentMap(final File source)
        throws IOException
    {

        try (Reader input = new BufferedReader(new InputStreamReader(new FileInputStream(source), UTF_8));
            // The NCIT source is an unquoted tab-delimited file
            // We need withQuote(null) to keep all quotes as part of the text, and not interpreted as special chars
            CSVParser csvParser = CSVParser.parse(input, CSVFormat.TDF.withQuote(null));) {

            Iterator<CSVRecord> csvIterator = csvParser.iterator();

            Map<String, String[]> parents = new HashMap<>();

            while (csvIterator.hasNext()) {
                CSVRecord row = csvIterator.next();

                String identifier = row.get(IDENTIFIER_COLUMN);

                String parentString = row.get(PARENTS_COLUMN);

                // Parent entry is a String with terms separated by "|" so split the String into a String[]
                String[] parentArray = parentString.split("\\|");

                // Put a blank array if there are no parents
                if (parentArray[0].contentEquals("")) {
                    parents.put(identifier, EMPTY_STRING_ARRAY);
                } else {
                    parents.put(identifier, parentArray);
                }
            }
            return parents;
        }
    }

    /**
     * Recursively calculates the ancestors of a given term, using the map of parents to accumulate a list of ancestors.
     * This is returned as a String set.
     *
     * @param parentsMap - a map of (term, parents) pairs where "parents" is an array of parent terms
     * @param identifier - identifier of the term
     * @return a set which stores the ancestors of the given term
     */
    private Set<String> computeAncestors(Map<String, String[]> parentsMap, String identifier)
    {
        // A set is used instead of an array so that the size need not be initially specified.
        Set<String> termAncestorSet = new HashSet<>();

        String[] parentArray = parentsMap.get(identifier);

        if (parentArray != null && parentArray.length > 0) {
            for (String parentIdentifier : parentArray) {
                // Add parent as an ancestor
                termAncestorSet.add(parentIdentifier);

                // Add recursively calculated ancestors of parent
                termAncestorSet.addAll(computeAncestors(parentsMap, parentIdentifier));
            }
        }

        return termAncestorSet;
    }
}
