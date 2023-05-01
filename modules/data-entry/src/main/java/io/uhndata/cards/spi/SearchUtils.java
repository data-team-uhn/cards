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
package io.uhndata.cards.spi;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NameParser;

/**
 * Service interface used by {@link io.uhndata.cards.QueryBuilder} to search for a specific type of resource.
 *
 * @version $Id$
 */
public final class SearchUtils
{
    private static final int MAX_CONTEXT_MATCH = 8;

    // Property of the parent node in an quick search, outlining what needs to be highlighted
    private static final String CARDS_QUERY_MATCH_KEY = "cards:queryMatch";

    // Properties of the children nodes
    private static final String CARDS_QUERY_QUESTION_KEY = "question";

    private static final String CARDS_QUERY_MATCH_BEFORE_KEY = "before";

    private static final String CARDS_QUERY_MATCH_TEXT_KEY = "text";

    private static final String CARDS_QUERY_MATCH_AFTER_KEY = "after";

    private static final String CARDS_QUERY_MATCH_NOTES_KEY = "inNotes";

    private static final String CARDS_QUERY_MATCH_PATH_KEY = "@path";

    private SearchUtils()
    {
        // This is a utility class, it should not be instantiated
    }

    /**
     * Escapes the input string to be free of characters with special meaning in a {@code jcr:like} query.
     *
     * @param input text to escape
     * @return an escaped version of the input
     */
    public static String escapeLikeText(final String input)
    {
        return input.replaceAll("([\\\\%_'])", "\\\\$1");
    }

    /**
     * Escapes the input string to be usable in a string argument.
     *
     * @param input text to escape
     * @return an escaped version of the input
     */
    public static String escapeQueryArgument(final String input)
    {
        return input.replace("'", "''");
    }

    /**
     * Searches through a list of Strings and returns the first String in that list for which in itself contains a given
     * substring.
     *
     * @param value the raw answer value, may be a single or multivalue of any JCR type
     * @param str the String to check if the value contains this substring
     * @return the (single) value, or the first value in a multivalue that contains the query substring
     */
    public static String getMatch(Object value, String str)
    {
        if (value == null) {
            return null;
        }

        if (value instanceof String[]) {
            return getMatchFromArray((String[]) value, str);
        } else if (value instanceof Object[]) {
            Object[] valueArray = (Object[]) value;
            String[] valueStr = new String[valueArray.length];
            for (int i = 0; i < valueArray.length; ++i) {
                valueStr[i] = String.valueOf(valueArray[i]);
            }
            return getMatchFromArray(valueStr, str);
        } else if (value != null) {
            if (StringUtils.containsIgnoreCase(value.toString(), str)) {
                return value.toString();
            }
        }

        return null;
    }

    /**
     * Searches through a list of Strings and returns the first String in that list for which in itself contains a given
     * substring.
     *
     * @param arr the list of Strings to search through
     * @param str the String to check if any array elements contain this substring
     * @return the first String in the list that contains the given substring
     */
    public static String getMatchFromArray(String[] arr, String str)
    {
        if (arr == null) {
            return null;
        }

        for (String element : arr) {
            if (StringUtils.containsIgnoreCase(element, str)) {
                return element;
            }
        }
        return null;
    }

    /**
     * Add metadata about a match to the matching object's parent.
     *
     * @param resourceValue The value that was matched
     * @param query The search value
     * @param question The text of the question itself
     * @param parent The parent of the matching node
     * @param isNoteMatch Whether or not the match is on the notes of the answer, rather than the answer
     * @param path the matching answer question node path
     * @return The given JsonObject with metadata appended to it.
     */
    public static JsonObject addMatchMetadata(String resourceValue, String query, String question, JsonObject parent,
        boolean isNoteMatch, String path)
    {
        JsonObject metadata = getMatchMetadata(resourceValue, query, question, isNoteMatch, path);

        // Construct a JsonObject that matches the parent, but with custom match metadata appended
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (String key : parent.keySet()) {
            builder.add(key, parent.get(key));
        }
        builder.add(CARDS_QUERY_MATCH_KEY, metadata);
        return builder.build();
    }

    /**
     * Get the metadata about a match.
     *
     * @param resourceValue The value that was matched
     * @param query The search value
     * @param question The text of the question itself
     * @param isNoteMatch Whether or not the match is on the notes of the answer, rather than the answer
     * @param path the matching answer question node path
     * @return the metadata as a JsonObject
     */
    private static JsonObject getMatchMetadata(String resourceValue, String query, String question, boolean isNoteMatch,
        String path)
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(CARDS_QUERY_QUESTION_KEY, question);
        builder.add(CARDS_QUERY_MATCH_NOTES_KEY, isNoteMatch);
        builder.add(CARDS_QUERY_MATCH_PATH_KEY, path);

        // Add metadata about the text before the match
        int matchIndex = resourceValue.toLowerCase().indexOf(query.toLowerCase());
        String matchBefore = resourceValue.substring(0, matchIndex);
        if (matchBefore.length() > MAX_CONTEXT_MATCH) {
            matchBefore = "..." + matchBefore.substring(
                matchBefore.length() - MAX_CONTEXT_MATCH, matchBefore.length());
        }
        builder.add(CARDS_QUERY_MATCH_BEFORE_KEY, matchBefore);

        // Add metadata about the text matched
        String matchText = resourceValue.substring(matchIndex, matchIndex + query.length());
        builder.add(CARDS_QUERY_MATCH_TEXT_KEY, matchText);

        // Add metadata about the text after the match
        String matchAfter = resourceValue.substring(matchIndex + query.length());
        if (matchAfter.length() > MAX_CONTEXT_MATCH) {
            matchAfter = matchAfter.substring(0, MAX_CONTEXT_MATCH) + "...";
        }
        builder.add(CARDS_QUERY_MATCH_AFTER_KEY, matchAfter);

        return builder.build();
    }

    /**
     * Check whether the given name is a valid node name.
     *
     * @param name Node name to check
     * @return True if the given name is a valid node name
     */
    public static boolean isValidNodeName(String name)
    {
        try {
            NameParser.checkFormat(name);
            return true;
        } catch (IllegalNameException e) {
            return false;
        }
    }
}
