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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Consumer;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.sickkids.ccm.lfs.vocabularies.spi.SourceParser;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyDescription;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyTermSource;

/**
 * Generic indexer for vocabularies available on the <a href="http://data.bioontology.org/">BioOntology</a> portal.
 * BioOntology is a RESTfull server serving a large collection of vocabularies, available as OBO sources, along with
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
    service = SourceParser.class,
    name = "SourceParser.OBO")
public class OboParser implements SourceParser
{
    /** Marks the start of a new Term. */
    private static final String TERM_MARKER = "[Term]";

    /** Not all entities are terms prompted by the presence of a {@link #TERM_MARKER}. */
    private static final String ENTITY_SEPARATION_REGEX = "^\\[[a-zA-Z]+\\]$";

    /** Regex pattern for a String -> String mapping. */
    private static final String FIELD_NAME_VALUE_SEPARATOR = "\\s*:\\s+";


    /** The data structure for the term currently being processed. */
    private TermData crtTerm;

    /** The data structure for all the terms processed. */
    private Map<String, TermData> data;

    /** Logger object used to handle thrown errors. */
    private Logger logger;

    /**
     * Default Constructor.
     */
    public OboParser()
    {
        super();
        this.crtTerm = new TermData();
        this.data = new LinkedHashMap<>();
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    @Override
    public boolean canParse(String format)
    {
        return "OBO".equals(format);
    }

    @Override
    public void parse(
        final File source,
        final VocabularyDescription vocabularyDescription,
        final Consumer<VocabularyTermSource> consumer) throws IOException, VocabularyIndexException
    {
        try {
            readLines(source);
            propagateAncestors();
            consumeData(consumer);
        } catch (IOException ex) {
            this.logger.error("IOException: {}", ex.getMessage());
        } catch (NullPointerException ex) {
            this.logger.error("NullPointer: {}", ex.getMessage());
        }
    }

    /**
     * Read the source file and populate the data variable.
     *
     * @param source the file containing information about the vocabulary.
     * @throws IOException if it cannot read the source file at any point
     * @throws NullPointerException if it encounters a null pointer
     */
    private void readLines(final File source) throws IOException, NullPointerException
    {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(source)));
        String line;
        Boolean canStore = false;
        /*
        * When encountering a separator that is not a term separator, all data should be skipped until a term
        * separator is encountered again.
        */
        boolean skip = false;
        while ((line = br.readLine()) != null) {
            if (line.trim().matches(ENTITY_SEPARATION_REGEX)) {
                if (canStore) {
                    storeCrtTerm();
                }
                // Overridden below
                skip = true;
            }
            if (line.trim().equalsIgnoreCase(TERM_MARKER)) {
                canStore = true;
                skip = false;
                continue;
            }
            if (!skip) {
                String[] pieces = line.split(FIELD_NAME_VALUE_SEPARATOR, 2);
                if (pieces.length != 2) {
                    continue;
                }
                if (pieces[0].trim().equals("data-version")) {
                    this.crtTerm.addTo("version", pieces[1]);
                    this.crtTerm.addTo(TermData.ID_FIELD_NAME, "HEADER_INFO");
                    canStore = true;
                }
                loadField(pieces[0], pieces[1]);
            }
        }
        storeCrtTerm();
    }

    private void storeCrtTerm()
    {
        if (this.crtTerm.getId() != null) {
            this.data.put(this.crtTerm.getId(), this.crtTerm);
        }
        this.crtTerm = new TermData();
    }

    private void loadField(String name, String value)
    {
        // "\"Autosomal dominant type\" RELATED [HPO:skoehler]" -> "Autosomal dominant type"
        String newValue = value.replaceFirst("^\"(.+)\"\\s*?(?:[A-Z]+|\\[).*", "$1")
            // "  {xref="PMID:16424154"}" at the end of the String -> ""
            .replaceFirst("\\s+\\{.*$", "");
        if (name.equals(TermData.PARENT_FIELD_NAME)) {
            // "VOCAB:1234567 ! ..." at start of String -> "VOCAB:1234567"
            newValue = newValue.replaceFirst("^(\\S+) ! .*", "$1");
        }
        this.crtTerm.addTo(name, newValue.replace("\\\"", "\""));
        // [A-Z]+:[A-Z]*[0-9]* ! .*
        // .*:.* ! .*
    }

    /**
     * Recursively determine the ancestors of the initial Vocabulary Term,
     * as well as all the Terms that are parents of it.
     *
     * @param termID is the ID of the term whose ancestors are to be propogated
     * @return IDs of the ancestors of the given term
     * @throws NullPointerException if there was no term in the source file with the ID termID
     */
    private Collection<String> findAncestors(String termID)
    {
        TermData term = this.data.get(termID);
        // If the ancestors for this node have already been determined, return them as a list of IDs.
        if (term.hasKey(TermData.TERM_CATEGORY_FIELD_NAME)) {
            return term.getCollection(TermData.TERM_CATEGORY_FIELD_NAME);
        } else {
            Collection<String> parents;
            // If the node has Parents, it definitely has Ancestors but they have not yet been determined.
            if (term.hasKey(TermData.PARENT_FIELD_NAME)) {
                parents = term.getCollection(TermData.PARENT_FIELD_NAME);
            } else {
                // Else we have reached the root node which has no parents.
                parents = Collections.emptyList();
            }
            Collection<String> ancestors = new LinkedHashSet<>(parents);
            // Take the Union of the ancestors IDs of all parent nodes and the IDs of parents.
            for (String parent : parents) {
                ancestors.addAll(findAncestors(parent));
            }
            // This Union is set as the current node's ancestors.
            term.addTo(TermData.TERM_CATEGORY_FIELD_NAME, ancestors);
            // Return Union
            return ancestors;
        }
    }

    private void propagateAncestors()
    {
        for (String id : this.data.keySet()) {
            findAncestors(id);
        }
    }

    /**
     * Creates a new VocabularyTermSource object from the parsed Term
     * and passes it to the consumer.accept() function.
     *
     * @param consumer method that will store the parsed term
     */
    private void consumeData(final Consumer<VocabularyTermSource> consumer)
    {
        String[] typeString = {};
        for (String id : this.data.keySet()) {
            TermData term = this.data.get(id);
            consumer.accept(new VocabularyTermSource(
                term.getId(),
                term.getLabel(),
                term.getCollection(TermData.PARENT_FIELD_NAME).toArray(typeString),
                term.getCollection(TermData.TERM_CATEGORY_FIELD_NAME).toArray(typeString),
                term.getAllProperties()
            ));
        }
    }
}
