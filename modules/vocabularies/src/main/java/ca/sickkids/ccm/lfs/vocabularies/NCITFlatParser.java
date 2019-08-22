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
package ca.sickkids.ccm.lfs.vocabularies;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

/**
 * Concrete subclass of AbstractNCITParser for parsing NCIT in flat file form.
 * @version $Id$
 */
public class NCITFlatParser extends AbstractNCITParser
{
    private static final int NCIT_FLAT_IDENTIFIER_COLUMN = 0;

    private static final int NCIT_FLAT_PARENTS_COLUMN = 2;

    private static final int NCIT_FLAT_SYNONYMS_COLUMN = 3;

    private static final int NCIT_FLAT_DESCRIPTION_COLUMN = 4;

    private static final int NCIT_FLAT_LABEL_COLUMN = 5;

    @Override
    String getTempFileDirectory()
    {
        return "./";
    }

    @Override
    String getTempFileName()
    {
        return "temp_ncit_flat";
    }

    @Override
    void parseNCIT(Node vocabularyNode)
        throws VocabularyIndexException
    {
        try {
            Map<String, String[]> parentsMap = returnParentMap();
            createTermNodes(parentsMap, vocabularyNode);
        } catch (RepositoryException e) {
            String message = "Failed to access Vocabulary node: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } catch (IOException e) {
            String message = "Failed to read flat file: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    private void createTermNodes(Map<String, String[]> parentsMap, Node vocabularyNode)
        throws IOException, RepositoryException, VocabularyIndexException
    {
        FileInputStream fileInputStream = new FileInputStream(getTempFileDirectory() + getTempFileName() + ".zip");
        ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

        zipInputStream.getNextEntry();
        InputStreamReader inputStreamReader = new InputStreamReader(zipInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        CSVParser csvParser = CSVParser.parse(bufferedReader, CSVFormat.TDF.withQuote(null));

        Iterator<CSVRecord> csvIterator = csvParser.iterator();

        while (csvIterator.hasNext()) {
            CSVRecord row = csvIterator.next();

            String identifier = row.get(NCIT_FLAT_IDENTIFIER_COLUMN);
            String description = row.get(NCIT_FLAT_DESCRIPTION_COLUMN);

            String synonymString = row.get(NCIT_FLAT_SYNONYMS_COLUMN);
            String[] synonymsArray = synonymString.split("\\|");

            // Make the first synonym of the term the default label if no label is supplied by the term
            String defaultLabel = synonymsArray[0];

            // The actual label supplied by the term
            String suppliedLabel = row.get(NCIT_FLAT_LABEL_COLUMN);

            String label = StringUtils.defaultIfBlank(suppliedLabel, defaultLabel);

            String[] parentsArray = parentsMap.get(identifier);
            String[] ancestorsArray = computeAncestors(parentsMap, identifier);

            createNCITVocabularyTermNode(vocabularyNode, identifier, label, description, synonymsArray,
                parentsArray, ancestorsArray);
        }

        csvParser.close();
        bufferedReader.close();
        inputStreamReader.close();
        zipInputStream.close();
        fileInputStream.close();
    }

    private Map<String, String[]> returnParentMap()
        throws IOException
    {
        FileInputStream fileInputStream = new FileInputStream(getTempFileDirectory() + getTempFileName() + ".zip");
        ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

        zipInputStream.getNextEntry();
        InputStreamReader inputStreamReader = new InputStreamReader(zipInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        CSVParser csvParser = CSVParser.parse(bufferedReader, CSVFormat.TDF.withQuote(null));

        Iterator<CSVRecord> csvIterator = csvParser.iterator();

        Map<String, String[]> parents = new HashMap<String, String[]>();

        while (csvIterator.hasNext()) {
            CSVRecord row = csvIterator.next();

            String identifier = row.get(NCIT_FLAT_IDENTIFIER_COLUMN);

            String parentString = row.get(NCIT_FLAT_PARENTS_COLUMN);
            String[] parentArray = parentString.split("\\|");

            if (parentArray[0].contentEquals("")) {
                parents.put(identifier, new String[0]);
            } else {
                parents.put(identifier, parentArray);
            }
        }

        csvParser.close();
        bufferedReader.close();
        inputStreamReader.close();
        zipInputStream.close();
        fileInputStream.close();

        return parents;
    }

    private String[] computeAncestors(Map<String, String[]> parentsMap, String identifier)
    {
        Set<String> termAncestorSet = new HashSet<String>();
        String[] parentArray = parentsMap.get(identifier);
        if (parentArray != null && parentArray.length > 0) {
            for (String parentIdentifier : parentArray)
            {
                termAncestorSet.add(parentIdentifier);
                termAncestorSet.addAll(recursiveComputeAncestors(parentsMap, parentIdentifier));
            }
        }

        return termAncestorSet.toArray(new String[0]);
    }

    private Set<String> recursiveComputeAncestors(Map<String, String[]> parentsMap, String identifier)
    {
        Set<String> termAncestorSet = new HashSet<String>();
        String[] parentArray = parentsMap.get(identifier);
        if (parentArray != null && parentArray.length > 0) {
            for (String parentIdentifier : parentArray)
            {
                termAncestorSet.add(parentIdentifier);
                termAncestorSet.addAll(recursiveComputeAncestors(parentsMap, parentIdentifier));
            }
        }
        return termAncestorSet;
    }
}
