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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.vocabularies.spi.VocabularyIndexException;
import io.uhndata.cards.vocabularies.spi.VocabularyIndexer;
import io.uhndata.cards.vocabularies.spi.VocabularyParserUtils;

/**
 * Concrete subclass of {@link AbstractNCITIndexer} for indexing NCIT in flat file form.
 *
 * @version $Id$
 */
@Component(
    service = VocabularyIndexer.class,
    name = "VocabularyIndexer.ncit-flat",
    reference = { @Reference(field = "utils", name = "utils", service = VocabularyParserUtils.class) })
public class NCITFlatIndexer extends AbstractNCITIndexer
{
    // Column numbers of the properties we want to extract.
    private static final int IDENTIFIER_COLUMN = 0;

    private static final int PARENTS_COLUMN = 2;

    private static final int SYNONYMS_COLUMN = 3;

    private static final int DESCRIPTION_COLUMN = 4;

    private static final int LABEL_COLUMN = 5;

    // Charset to use when reading the source file
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /** An empty String[] array to use for {@code Set.toArray}, we don't want to create a new array for each call. */
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    @Override
    public boolean canIndex(String source)
    {
        return "ncit-flat".equals(source);
    }

    @Override
    String getDefaultSource(String version)
    {
        return "https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/Thesaurus_" + version + ".FLAT.zip";
    }

    @Override
    protected void parseNCIT(final File source, final Node vocabularyNode) throws VocabularyIndexException
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
     * @param parentsMap a map of (term, parents) pairs where "parents" is an array of parent terms
     * @param vocabularyNode the <code>Vocabulary</code> node which represents the current NCIT instance to index
     * @throws IOException thrown when file input cannot be read
     * @throws RepositoryException thrown when JCR nodes cannot be created
     * @throws VocabularyIndexException throw on failure to create appropriate JCR node
     */
    private void createTermNodes(final File source, Map<String, String[]> parentsMap, Node vocabularyNode)
        throws IOException, RepositoryException, VocabularyIndexException
    {
        // The NCIT source is an unquoted tab-delimited file
        // We need withQuote(null) to keep all quotes as part of the text, and not interpreted as special chars
        try (CSVParser csvParser = CSVParser.parse(source, DEFAULT_CHARSET,
                CSVFormat.TDF.builder().setQuote(null).build())) {
            Iterator<CSVRecord> csvIterator = csvParser.iterator();

            while (csvIterator.hasNext()) {
                CSVRecord row = csvIterator.next();

                String identifier = row.get(IDENTIFIER_COLUMN);
                String description = row.get(DESCRIPTION_COLUMN);
                String synonymString = row.get(SYNONYMS_COLUMN);

                // Synonym entry is a String with terms separated by "|" so split the String into a String[]
                String[] synonymsArray = synonymString.split("\\|");

                /*
                 * If this doesn't exist, the first synonym will be used. If there are no synonyms, a blank String will
                 * be used. This is handled in createNCITVocabularyTermNode.
                 */
                String label = row.get(LABEL_COLUMN);

                String[] parentsArray = parentsMap.get(identifier);

                /*
                 * Ancestors are recursively calculated from parents. Since computeAncestors returns the ancestors as a
                 * Set<String>, it must be converted to a String[] here.
                 */
                String[] ancestorsArray = computeAncestors(parentsMap, identifier).toArray(EMPTY_STRING_ARRAY);

                // This method is a protected method in AbstractNCITIndexer for creating VocabularyTerm nodes
                createNCITVocabularyTermNode(vocabularyNode, identifier, label, description, synonymsArray,
                    parentsArray, ancestorsArray);
            }
        }
    }

    /**
     * Returns a HashMap containing a (String, String[]) pair representing (term, parents). This is extracted from the
     * temporary NCIT zip flat file.
     *
     * @return a map which stores (term ID, parent IDs) pairs
     * @throws IOException thrown when temporary NCIT file cannot be read
     */
    private Map<String, String[]> returnParentMap(final File source)
        throws IOException
    {
        // The NCIT source is an unquoted tab-delimited file
        // We need withQuote(null) to keep all quotes as part of the text, and not interpreted as special chars
        try (CSVParser csvParser = CSVParser.parse(source, DEFAULT_CHARSET,
                CSVFormat.TDF.builder().setQuote(null).build())) {
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
     * @param parentsMap a map of (term, parents) pairs where "parents" is an array of parent terms
     * @param identifier identifier of the term
     * @return a set which stores the ancestors of the given term
     */
    private Set<String> computeAncestors(Map<String, String[]> parentsMap, String identifier)
    {
        // A set is used instead of an array so that the size need not be initially specified.
        Set<String> termAncestorSet = new HashSet<>();

        String[] parentArray = parentsMap.get(identifier);

        if (parentArray != null) {
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
