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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Consumer;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.vocabularies.spi.SourceParser;
import io.uhndata.cards.vocabularies.spi.VocabularyDescription;
import io.uhndata.cards.vocabularies.spi.VocabularyIndexException;
import io.uhndata.cards.vocabularies.spi.VocabularyTermSource;

/**
 * Parser for vocabulary sources in the OBO format.
 *
 * @version $Id$
 */

@Component(
    service = SourceParser.class,
    name = "SourceParser.OBO")
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class OboParser implements SourceParser
{
    /** Marks the start of a new Frame. */
    private static final String FRAME_MARKER = "^\\[[a-zA-Z]+\\]$";

    /** Marks the start of a new Term Frame. */
    private static final String TERM_MARKER = "[Term]";

    /**
     * Regex pattern for separating the tag and its value from a line: an optional even number of backslashes, followed
     * by a colon, and optional whitespace.
     */
    private static final String FIELD_NAME_VALUE_SEPARATOR = "(?<!\\\\)(?:\\\\\\\\)*:\\s*";

    /** Holds the term currently being . */
    private InheritableThreadLocal<TermData> crtTerm = new InheritableThreadLocal<>();

    /** Holds all the terms parsed so far. */
    private InheritableThreadLocal<Map<String, TermData>> data = new InheritableThreadLocal<>();

    /** Logger object used to handle thrown errors. */
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public boolean canParse(String format)
    {
        return "OBO".equalsIgnoreCase(format);
    }

    @Override
    public void parse(
        final File source,
        final VocabularyDescription vocabularyDescription,
        final Consumer<VocabularyTermSource> consumer) throws IOException, VocabularyIndexException
    {
        try {
            this.data.set(new LinkedHashMap<>());
            readLines(source);
            propagateAncestors();
            consumeData(consumer);
            this.data.remove();
        } catch (IOException ex) {
            this.logger.error("IOException: {}", ex.getMessage());
        }
    }

    /**
     * Read the source file and populate the data variable.
     *
     * @param source the file containing the vocabulary source in OBO format
     * @throws IOException if reading the source file fails
     */
    private void readLines(final File source) throws IOException
    {
        // Start by examining a new term
        this.crtTerm.set(new TermData());
        try (ConcatenatingLineReader br =
            new ConcatenatingLineReader(new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8))) {
            String line;
            // Used to skip over the header and non-Term frames
            // Initially false, since at the start of the file is the header
            boolean isTerm = false;
            while ((line = br.readLine()) != null) {
                if (line.trim().matches(FRAME_MARKER)) {
                    // We just encountered the start of a new frame
                    if (isTerm) {
                        // If the previous frame was a Term, store it
                        storeCrtTerm();
                    }
                    // Non-Term frames must be ignored, only Terms are recorded
                    isTerm = line.trim().equalsIgnoreCase(TERM_MARKER);
                    continue;
                }
                if (isTerm) {
                    // Inside a Term, process its values
                    String[] pieces = line.split(FIELD_NAME_VALUE_SEPARATOR, 2);
                    if (pieces.length != 2) {
                        continue;
                    }
                    loadField(pieces[0], pieces[1]);
                }
            }
            // Also store the last term parsed when the end of the file is encountered
            if (isTerm) {
                storeCrtTerm();
            }
        }
    }

    /**
     * Store the current term data and set up a new TermData instance to record the next term.
     */
    private void storeCrtTerm()
    {
        // Only terms with a valid identifier can be stored
        TermData termToProcess = this.crtTerm.get();
        if (termToProcess.getId() != null) {
            // Multiple frames can describe the same term, we must combine them into one
            TermData existing = this.data.get().get(termToProcess.getId());
            if (existing == null) {
                this.data.get().put(termToProcess.getId(), termToProcess);
            } else {
                existing.getAllProperties().putAll(termToProcess.getAllProperties());
            }
        }
        this.crtTerm.set(new TermData());
    }

    /**
     * Process a field ("name = value" pair) by extracting only the actual value, ignoring trailing modifiers, dbxrefs,
     * and comments, as well as synonym categories.
     *
     * @param name the name of the property
     * @param value the raw value, which may be a simple unquoted or quoted value, or a value with additional trailing
     *            modifiers, comments, and other tags
     */
    private void loadField(String name, String value)
    {
        this.crtTerm.get().addTo(process(name), process(value));
    }

    /**
     * Process a raw value read from the OBO file to extract only the real value, ignoring trailing modifiers, comments,
     * and xref lists, removing quotes if needed, and unescaping special escape sequences.
     *
     * @param rawValue the value as present in the input file
     * @return the processed value, with any trailing bits removed, unquoted, and unescaped
     */
    private String process(final String rawValue)
    {
        String realValue = rawValue;

        // If the string was quoted, then trailing modifiers and comments don't need to be trimmed from within
        final boolean wasQuoted = rawValue.startsWith("\"");

        // If the value is quoted, only keep the part inside the quotes.
        // - must match from the start: ^
        // - must start with a quote: \"
        // - start a capture: (
        // - any characters: .*
        // - lazy matching, capture the shortest match to avoid capturing between two distinct quoted strings): ?
        // (now going backwards from the last closing bracket)
        // - end the capture: )
        // - which may be proceded by an even number of backslashes, including none: (?:\\\\\\\\)*
        // -- two backslashes: \\
        // -- escaped as part of the regexp: \\\\
        // -- escaped again as part of a java string: \\\\\\\\
        // -- in a non-capturing group: (?:\\\\\\\\)
        // -- repeated any number of times, including 0: (?:\\\\\\\\)*
        // - not preceded by another backslash, which would make it an odd number of backslashes: (?<!\\\\)
        // -- a backslash: \
        // -- escaped as part of the regexp: \\
        // -- escaped again as part of a java string: \\\\
        // -- in a negative lookbehind: (?<!\\\\)
        // (resume going forward after the last closing bracket)
        // - stop capturing at a quote: \"
        // - followed by anything else: .*
        //
        // For example:
        // "Abnormally long and slender fingers (\"spider fingers\")." [HPO:probinson]
        // becomes
        // Abnormally long and slender fingers (\"spider fingers\").
        realValue = realValue.replaceFirst("^\"(.*?(?<!\\\\)(?:\\\\\\\\)*)\".*", "$1");

        // If there are trailing modifiers or comments, remove them.
        // - must match from the start: ^
        // - start a capture: (
        // - any characters: .*
        // - lazy matching, capture the shortest match to stop at the first exclamation mark): ?
        // - an even number of backslashes, including none (see explanation above for this part): (?<!\\\\)(?:\\\\\\\\)*
        // - end the capture: )
        // - an opening brace or exclamation mark: [\\{!]
        // - followed by anything else: .*
        //
        // We also trim the value, to remove any potential whitespace before the trailing bit.
        //
        // Examples:
        //
        // He said "Hello\!" and then left. ! Did he mean to say "Goodbye!" instead?
        // becomes
        // He said "Hello\!" and then left.
        //
        // Often associated with Cowden syndrome. {xref="PMID:11073535"}
        // becomes
        // Often associated with Cowden syndrome.
        if (!wasQuoted) {
            realValue = realValue.replaceFirst("^(.*?(?<!\\\\)(?:\\\\\\\\)*)[\\{!].*", "$1").trim();
        }

        // We should also remove trailing Dbxref lists, but this isn't necessary since they can only appear after a
        // quoted string, which means that they should already have been discarded during the first step when everything
        // outside the quotes was discarded

        // Unescape special symbols which are replaced by themselves: !:,"()[]{}
        // - replace any occurrence, so we don't need to match from the start
        // - an even number of backslashes, including none (explanation above): (?<!\\\\)(?:\\\\\\\\)*
        // - captured as the first group, since we want to preserve them: ((?<!\\\\)(?:\\\\\\\\)*)
        // - followed by a single backslash, escaped two times: \\\\
        // - followed by one of the special characters: [!:,"()[]{}]
        // - escaped: [!:,\"\\(\\)\\[\\]\\{\\}]
        // - captured as the second group, since we want to output it: ([!:,\"\\(\\)\\[\\]\\{\\}])
        // - replace with the optional even backslashes before the escaping backslash, and the symbol itself
        realValue = realValue.replaceAll("((?<!\\\\)(?:\\\\\\\\)*)\\\\([!:,\"\\(\\)\\[\\]\\{\\}])", "$1$2");

        // Unescape other special characters: newline, space, tab
        // - as above, captured optional even number of preceding backslashes: ((?<!\\\\)(?:\\\\\\\\)*)
        // - followed by a single backslash, escaped two times: \\\\
        // - followed by n, W, or t respectively
        // - replace with the optional even backslashes before the escaping backslash, and the special character
        realValue = realValue.replaceAll("((?<!\\\\)(?:\\\\\\\\)*)\\\\n", "$1\n")
            .replaceAll("((?<!\\\\)(?:\\\\\\\\)*)\\\\W", "$1 ")
            .replaceAll("((?<!\\\\)(?:\\\\\\\\)*)\\\\t", "$1\t");

        // Finally, unescape the escape character itself
        // - replace any two backslashes with one: \\
        // - double escaped as a regexp special symbol and as a java string: \\\\\\\\
        // - replace with a single backslash, also double escaped
        realValue = realValue.replaceAll("\\\\\\\\", "\\\\");

        return realValue;
    }

    /**
     * Determine and return the ancestors of a vocabulary term. Initially, the ancestors are not available, since the
     * vocabulary source only includes direct parents, but after the ancestors are recursively computed the first time,
     * they will be stored in the TermData for quick subsequent retrieval.
     *
     * @param termID the identifier of the term whose ancestors are to be retrieved
     * @return identifiers of the ancestors of the given term, may be an empty collection if the term has no
     *         parents/ancestors, or if the term doesn't exist
     */
    private Collection<String> findAncestors(String termID)
    {
        TermData term = this.data.get().get(termID);

        if (term == null) {
            // Unknown parent
            return Collections.emptySet();
        }
        // If the ancestors for this node have already been determined, return them as a list of IDs.
        if (term.hasKey(TermData.TERM_CATEGORY_FIELD_NAME)) {
            return term.getAllValues(TermData.TERM_CATEGORY_FIELD_NAME);
        } else {
            Collection<String> parents;
            // If the node has Parents, it definitely has Ancestors but they have not yet been determined.
            if (term.hasKey(TermData.PARENT_FIELD_NAME)) {
                parents = term.getAllValues(TermData.PARENT_FIELD_NAME);
            } else {
                // Else we have reached the root node which has no parents.
                parents = Collections.emptySet();
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

    /**
     * Recursively computes ancestors from the parents for all terms.
     */
    private void propagateAncestors()
    {
        for (String id : this.data.get().keySet()) {
            findAncestors(id);
        }
    }

    /**
     * Creates a new VocabularyTermSource object from the parsed Term and passes it to the consumer function.
     *
     * @param consumer method that will store the parsed term
     */
    private void consumeData(final Consumer<VocabularyTermSource> consumer)
    {
        String[] typeString = {};
        for (String id : this.data.get().keySet()) {
            TermData term = this.data.get().get(id);
            consumer.accept(new VocabularyTermSource(
                term.getId(),
                term.getLabel(),
                term.getAllValues(TermData.PARENT_FIELD_NAME).toArray(typeString),
                term.getAllValues(TermData.TERM_CATEGORY_FIELD_NAME).toArray(typeString),
                term.getAllProperties()));
        }
    }

    /**
     * A buffered line reader that concatenates split lines into one. In other words, whenever a real line ends with an
     * unescaped backslash, the backslash is removed and the following line is appended.
     *
     * @version $Id$
     */
    private static final class ConcatenatingLineReader extends BufferedReader
    {
        ConcatenatingLineReader(Reader in)
        {
            super(in);
        }

        @Override
        public String readLine() throws IOException
        {
            String line = super.readLine();
            StringBuilder concatenatedLine = new StringBuilder();
            boolean firstLineIsNull = true;
            while (line != null
                && line.toString().matches(".*((?<!\\\\)(?:\\\\\\\\)*)\\\\")
                && !line.toString().matches(".*(?<!\\\\)(?:\\\\\\\\)*!.*")) {
                concatenatedLine.append(line, 0, line.length() - 1);
                firstLineIsNull = false;
                line = super.readLine();
            }
            // The file may end with a last backslash, which needs to be removed
            if (line != null) {
                concatenatedLine.append(line);
            } else if (firstLineIsNull) {
                return null;
            }
            return concatenatedLine.toString();
        }
    }
}
