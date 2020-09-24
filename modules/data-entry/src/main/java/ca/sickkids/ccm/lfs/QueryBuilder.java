/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.sickkids.ccm.lfs;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.script.Bindings;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.scripting.sightly.pojo.Use;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A HTL Use-API that can run a JCR query and output the results as JSON. The query to execute is taken from the request
 * parameter {@code query}, and must be in the JCR-SQL2 syntax. To use this API, simply place the following code in a
 * HTL file:
 *
 * <p><tt>
 * &lt;sly data-sly-use.query="ca.sickkids.ccm.lfs.QueryBuilder"&gt;${query.content @ context='unsafe'}&lt;/sly&gt;
 * </tt></p>
 *
 * <p>Or, send a HTTP request to {@code /query?query=select%20*%20from%20[lfs:Form]}.</p>
 *
 * @version $Id$
 */
public class QueryBuilder implements Use
{

    private static final int MAX_CONTEXT_MATCH = 8;

    // Property of the parent node in an quick search, outlining what needs to be highlighted
    private static final String LFS_QUERY_MATCH_KEY = "lfs:queryMatch";
    // Properties of the children nodes
    private static final String LFS_QUERY_QUESTION_KEY = "question";
    private static final String LFS_QUERY_MATCH_BEFORE_KEY = "before";
    private static final String LFS_QUERY_MATCH_TEXT_KEY = "text";
    private static final String LFS_QUERY_MATCH_AFTER_KEY = "after";
    private static final String LFS_QUERY_MATCH_NOTES_KEY = "inNotes";
    private static final String LFS_QUERY_MATCH_PATH_KEY = "@path";

    private Logger logger = LoggerFactory.getLogger(QueryBuilder.class);

    private String content;

    private ResourceResolver resourceResolver;

    /* Whether or not the input should be escaped, if it is used in a contains() call. */
    private boolean shouldEscape;

    /* The requested, default or set by admin limit. */
    private long limit;

    /* Whether to show the total number of results. */
    private boolean showTotalRows;

    /* Resource types allowed for a search. */
    private String[] resourceTypes;

    @Override
    public void init(Bindings bindings)
    {
        SlingHttpServletRequest request = (SlingHttpServletRequest) bindings.get("request");
        this.resourceResolver = (ResourceResolver) bindings.get("resolver");

        try {
            final String jcrQuery = request.getParameter("query");
            final String luceneQuery = request.getParameter("lucene");
            final String fullTextQuery = request.getParameter("fulltext");
            final String quickQuery = request.getParameter("quick");
            final long offset = getLongValueOrDefault(request.getParameter("offset"), 0);
            String requestID = request.getParameter("req");
            if (StringUtils.isBlank(requestID)) {
                requestID = "";
            }
            final String doNotEscape = request.getParameter("doNotEscapeQuery");
            final String showTotalRowsParam = request.getParameter("showTotalRows");

            this.limit = getLongValueOrDefault(request.getParameter("limit"), 10);
            this.resourceTypes = request.getParameterValues("allowedResourceTypes");
            this.shouldEscape = StringUtils.isBlank(doNotEscape) || !("true".equals(doNotEscape));
            this.showTotalRows = StringUtils.isBlank(showTotalRowsParam) || !("true".equals(showTotalRowsParam));

            // Try to use a JCR-SQL2 query first
            Iterator<JsonObject> results;
            if (StringUtils.isNotBlank(jcrQuery)) {
                results = QueryBuilder.adaptNodes(queryJCR(this.urlDecode(jcrQuery)));
            } else if (StringUtils.isNotBlank(luceneQuery)) {
                results = QueryBuilder.adaptNodes(queryLucene(this.urlDecode(luceneQuery)));
            } else if (StringUtils.isNotBlank(fullTextQuery)) {
                results = QueryBuilder.adaptNodes(fullTextSearch(this.urlDecode(fullTextQuery)));
            } else if (StringUtils.isNotBlank(quickQuery)) {
                results = quickSearch(this.urlDecode(quickQuery));
            } else {
                results = Collections.emptyIterator();
            }

            // output the results into our content
            JsonObjectBuilder builder = Json.createObjectBuilder();
            this.addObjects(builder, results, requestID, offset);
            this.content = builder.build().toString();
        } catch (Exception e) {
            this.logger.error("Failed to query resources: {}", e.getMessage(), e);
            this.content = "Unknown error: " + e.fillInStackTrace();
        }
    }

    /**
     * URL-decodes the given request parameter.
     *
     * @param param a URL-encoded request parameter
     * @return a decoded version of the input
     * @throws UnsupportedEncodingException should not be thrown unless UTF_8 is somehow not available
     */
    private String urlDecode(String param) throws UnsupportedEncodingException
    {
        return URLDecoder.decode(param, StandardCharsets.UTF_8.name());
    }

    /**
     * Finds content matching the given lucene query.
     *
     * @param query a lucene query
     * @return the content matching the query
     */
    private Iterator<Resource> queryLucene(String query) throws RepositoryException
    {
        // Wrap our lucene query in JCR-SQL2 syntax for the resource resolver to understand
        return queryJCR(
            String.format("select n.* from [nt:base] as n where native('lucene', '%s')"
                + " and n.'sling:resourceSuperType' = 'lfs/Resource'", query.replace("'", "''")));
    }

    /**
     * Finds content using the given full text search.
     *
     * @param query text to search
     *
     * @return the content matching the query
     */
    private Iterator<Resource> fullTextSearch(String query) throws RepositoryException
    {
        // Wrap our full-text query in JCR-SQL2 syntax for the resource resolver to understand
        return queryJCR(
            String.format("select n.* from [nt:base] as n where contains(*, '%s')", this.fullTextEscape(query)));
    }

    /**
     * Escapes the input query if this.shouldEscape is true.
     *
     * @param input text to escape
     *
     * @return an escaped version of the input
     */
    private String fullTextEscape(String input)
    {
        if (!this.shouldEscape) {
            return input;
        }

        // Escape sequence taken from https://jackrabbit.apache.org/archive/wiki/JCR/EncodingAndEscaping_115513396.html
        return input.replaceAll("([\\Q+-&|!(){}[]^\"~*?:\\_%/\\E])", "\\\\$1").replaceAll("'", "''");
    }

    /**
     * Searches through a list of Strings and returns the first String in that list
     * for which in itself contains a given substring.
     *
     * @param arr the list of Strings to search through
     * @param str the String to check if any array elements contain this substring
     *
     * @return the first String in the list that contains the given substring
     */
    private String getMatchingFromArray(String[] arr, String str)
    {
        if (arr == null) {
            return null;
        }

        for (int i = 0; i < arr.length; i++) {
            if (StringUtils.containsIgnoreCase(arr[i], str)) {
                return arr[i];
            }
        }
        return null;
    }

    /**
     * Get the metadata about a match.
     * @param resourceValue The value that was matched
     * @param query The search value
     * @param question The text of the question itself
     * @param isNoteMatch Whether or not the match is on the notes of the answer, rather than the answer
     * @param path the matching answer question node path
     * @return the metadata as a JsonObject
     */
    private JsonObject getMatchMetadata(String resourceValue, String query, String question, boolean isNoteMatch,
        String path)
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(QueryBuilder.LFS_QUERY_QUESTION_KEY, question);
        builder.add(QueryBuilder.LFS_QUERY_MATCH_NOTES_KEY, isNoteMatch);
        builder.add(QueryBuilder.LFS_QUERY_MATCH_PATH_KEY, path);

        // Add metadata about the text before the match
        int matchIndex = resourceValue.toLowerCase().indexOf(query.toLowerCase());
        String matchBefore = resourceValue.substring(0, matchIndex);
        if (matchBefore.length() > QueryBuilder.MAX_CONTEXT_MATCH) {
            matchBefore = "..." + matchBefore.substring(
                matchBefore.length() - QueryBuilder.MAX_CONTEXT_MATCH, matchBefore.length()
            );
        }
        builder.add(QueryBuilder.LFS_QUERY_MATCH_BEFORE_KEY, matchBefore);

        // Add metadata about the text matched
        String matchText = resourceValue.substring(matchIndex, matchIndex + query.length());
        builder.add(QueryBuilder.LFS_QUERY_MATCH_TEXT_KEY, matchText);

        // Add metadata about the text after the match
        String matchAfter = resourceValue.substring(matchIndex + query.length());
        if (matchAfter.length() > QueryBuilder.MAX_CONTEXT_MATCH) {
            matchAfter = matchAfter.substring(0, QueryBuilder.MAX_CONTEXT_MATCH) + "...";
        }
        builder.add(QueryBuilder.LFS_QUERY_MATCH_AFTER_KEY, matchAfter);

        return builder.build();
    }

    /**
     * Add metadata about a match to the matching object's parent.
     * @param resourceValue The value that was matched
     * @param query The search value
     * @param question The text of the question itself
     * @param parent The parent of the matching node
     * @param isNoteMatch Whether or not the match is on the notes of the answer, rather than the answer
     * @param path the matching answer question node path
     * @return The given JsonObject with metadata appended to it.
     */
    private JsonObject addMatchMetadata(String resourceValue, String query, String question, JsonObject parent,
        boolean isNoteMatch, String path)
    {
        JsonObject metadata = getMatchMetadata(resourceValue, query, question, isNoteMatch, path);

        // Construct a JsonObject that matches the parent, but with custom match metadata appended
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (String key : parent.keySet()) {
            builder.add(key, parent.get(key));
        }
        builder.add(QueryBuilder.LFS_QUERY_MATCH_KEY, metadata);
        return builder.build();
    }

    /**
     * Finds the search settings for the given QuickSearchResultsWidget node label.
     *
     * @param label the label of QuickSearchResultsWidget - "query", "lucene", "fulltext" or "quick"
     * @return a JsonObject of filterable fields
     */
    private Resource getSearchSettings(final String label) throws RepositoryException
    {
        final String query =
            String.format("select n from [lfs:QuickSearchResultsWidget] as n where n.label = '%s'", label);
        Iterator<Resource> results = queryJCR(query);
        if (results.hasNext()) {
            return results.next();
        }
        return null;
    }

    /**
     * Finds [lfs:Form]s, [lfs:Subject]s, and [lfs:Questionnaire]s using the given full text search.
     * This performs the search in such a way that values in child nodes (e.g. lfs:Answers of an lfs:Form)
     * are aggregated to their parent.
     *
     * @param query text to search
     *
     * @return the content matching the query
     */
    private Iterator<JsonObject> quickSearch(String query) throws RepositoryException
    {
        List<String> allowedResourceTypes = Arrays.asList(this.resourceTypes);
        ArrayList<JsonObject> resultsList = new ArrayList<JsonObject>();

        for (String type : allowedResourceTypes) {
            // no need to go through all results list if we do not add total results number
            if (resultsList.size() == this.limit && !this.showTotalRows) {
                break;
            }
            String rtype = type.replace("lfs:", "");
            switch (rtype) {
                case "Form":
                    quickFormSearch(resultsList, query);
                    break;
                case "Subject":
                    quickSubjectSearch(resultsList, query);
                    break;
                case "Questionnaire":
                    quickQuestionnaireSearch(resultsList, query);
                    break;
                default:
            }
        }
        return resultsList.listIterator();
    }

    /**
     * Finds [lfs:Form]s with question answers or notes matching given full text search.
     * This performs the search in such a way that values in child nodes (e.g. lfs:Answers of an lfs:Form)
     * are aggregated to their parent.
     *
     * @param outputList aggregator of search results
     * @param query text to search
     *
     * @return the content matching the query
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity"})
    private void quickFormSearch(ArrayList<JsonObject> outputList, String query) throws RepositoryException,
        ItemNotFoundException
    {
        final StringBuilder xpathQuery = new StringBuilder();
        xpathQuery.append("/jcr:root/Forms//*[jcr:like(fn:lower-case(@value),'%");
        xpathQuery.append(this.fullTextEscape(query.toLowerCase()));
        xpathQuery.append("%') or jcr:like(fn:lower-case(@note),'%");
        xpathQuery.append(this.fullTextEscape(query.toLowerCase()));
        xpathQuery.append("%')]");

        Iterator<Resource> foundResources = queryXPATH(xpathQuery.toString());
        /*
        * For each Resource in foundResources, move up the tree until
        * we find an ancestor node of type `lfs:Form`
        */
        while (foundResources.hasNext()) {
            // no need to go through all results list if we do not add total results number
            if (outputList.size() == this.limit && !this.showTotalRows) {
                break;
            }
            Resource thisResource = foundResources.next();

            Resource thisParent = thisResource;
            String[] resourceValues = thisResource.getValueMap().get("value", String[].class);
            String resourceValue = getMatchingFromArray(resourceValues, query);

            String question = null;
            String path = "";
            boolean matchedNotes = false;
            Node questionNode = thisResource.adaptTo(Node.class).getProperty("question").getNode();
            if (questionNode != null) {
                question = questionNode.getProperty("text").getString();
                path = questionNode.getPath();
            }

            // As a fallback for when the query isn't in the value field, attempt to use the note field
            if (resourceValue == null) {
                String noteValue = thisResource.getValueMap().get("note", String.class);
                if (StringUtils.containsIgnoreCase(noteValue, query)) {
                    resourceValue = noteValue;
                    matchedNotes = true;
                }
            }

            if (resourceValue != null && question != null) {
                // Find the Form parent of this question
                while (thisParent != null && !"lfs/Form".equals(thisParent.getResourceType())) {
                    thisParent = thisParent.getParent();
                }

                outputList.add(this.addMatchMetadata(
                    resourceValue, query, question, thisParent.adaptTo(JsonObject.class), matchedNotes, path
                ));
            }
        }
    }

    /**
     * Finds [lfs:Subject]s with identifiers matching given full text search.
     * This performs the search in such a way that values in child nodes
     * are aggregated to their parent.
     *
     * @param outputList aggregator of search results
     * @param query text to search
     *
     * @return the content matching the query
     */
    private void quickSubjectSearch(ArrayList<JsonObject> outputList, String query) throws RepositoryException
    {
        final StringBuilder xpathQuery = new StringBuilder();
        xpathQuery.append("/jcr:root/Subjects//*[jcr:like(fn:lower-case(@identifier),'%");
        xpathQuery.append(this.fullTextEscape(query.toLowerCase()));
        xpathQuery.append("%')]");

        Iterator<Resource> foundResources = queryXPATH(xpathQuery.toString());
        while (foundResources.hasNext()) {
            // no need to go through all results list if we do not add total results number
            if (outputList.size() == this.limit && !this.showTotalRows) {
                break;
            }
            Resource thisResource = foundResources.next();

            String resourceValue = thisResource.getValueMap().get("identifier", String.class);

            if (resourceValue != null) {
                outputList.add(this.addMatchMetadata(
                    resourceValue, query, "identifier", thisResource.adaptTo(JsonObject.class), false, ""
                ));
            }
        }
    }

    /**
     * Finds [lfs:Questionnaire]s title, question text and values matching given full text search.
     * This performs the search in such a way that values in child nodes are aggregated to their parent.
     *
     * @param outputList aggregator of search results
     * @param query text to search
     *
     * @return the content matching the query
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity", "checkstyle:NPathComplexity",
        "checkstyle:ExecutableStatementCount"})
    private void quickQuestionnaireSearch(ArrayList<JsonObject> outputList, String query) throws RepositoryException,
        ItemNotFoundException
    {
        final StringBuilder xpathQuery = new StringBuilder();
        xpathQuery.append("/jcr:root/Questionnaires//*[jcr:like(fn:lower-case(@value),'%");
        xpathQuery.append(this.fullTextEscape(query.toLowerCase()));
        xpathQuery.append("%') or jcr:like(fn:lower-case(@text),'%");
        xpathQuery.append(this.fullTextEscape(query.toLowerCase()));
        xpathQuery.append("%') or jcr:like(fn:lower-case(@title),'%");
        xpathQuery.append(this.fullTextEscape(query.toLowerCase()));
        xpathQuery.append("%')]");

        Iterator<Resource> foundResources = queryXPATH(xpathQuery.toString());
        /*
        * For each Resource in foundResources, move up the tree until
        * we find an ancestor node of type `lfs:Questionnaire`
        */
        while (foundResources.hasNext()) {
            // no need to go through all results list if we do not add total results number
            if (outputList.size() == this.limit && !this.showTotalRows) {
                break;
            }
            Resource thisResource = foundResources.next();

            // Find the Questionnaire parent of this question
            Resource thisParent = thisResource;
            while (thisParent != null && !"lfs/Questionnaire".equals(thisParent.getResourceType())) {
                thisParent = thisParent.getParent();
            }

            String[] resourceValues = thisResource.getValueMap().get("value", String[].class);
            String resourceValue = getMatchingFromArray(resourceValues, query);

            String question = null;
            String path = "";
            // Find the matched question node
            if (resourceValue != null) {
                // found resource is of type [lfs:Answer]
                // Find the Question parent of this question
                Resource questionParent = thisResource;
                while (questionParent != null && !"lfs/Question".equals(questionParent.getResourceType())) {
                    questionParent = questionParent.getParent();
                }
                Node questionNode = questionParent.adaptTo(Node.class);
                if (questionNode != null) {
                    question = questionNode.getProperty("text").getString();
                    path = questionNode.getPath();
                }
            } else {
                // found resource is of type [lfs:Question]
                resourceValue = thisResource.getValueMap().get("text", String.class);
                if (resourceValue != null) {
                    path = thisResource.adaptTo(Node.class).getPath();
                } else {
                    // found resource is of type [lfs:Questionnaire]
                    resourceValue = thisResource.getValueMap().get("title", String.class);
                }

                question = resourceValue;
            }

            if (resourceValue != null && question != null) {
                outputList.add(this.addMatchMetadata(
                    resourceValue, query, question, thisParent.adaptTo(JsonObject.class), false, path
                ));
            }
        }
    }

    /**
     * Finds content matching the given JCR_SQL2 query.
     *
     * @param query a JCR-SQL2 query
     * @return the content matching the query
     */
    private Iterator<Resource> queryJCR(String query) throws RepositoryException
    {
        return this.resourceResolver.findResources(query, "JCR-SQL2");
    }

    /**
     * Finds content matching the given XPATH query.
     *
     * @param query a XPATH query
     * @return the content matching the query
     */
    private Iterator<Resource> queryXPATH(String query) throws RepositoryException
    {
        return this.resourceResolver.findResources(query, "xpath");
    }

    /**
     * Convert an iterator of nodes into an iterator of JsonObjects.
     * @param nodes the iterator to convert
     * @return An iterator of the input nodes
     */
    private static Iterator<JsonObject> adaptNodes(Iterator<Resource> resources)
    {
        ArrayList<JsonObject> list = new ArrayList<JsonObject>();
        while (resources.hasNext()) {
            list.add(resources.next().adaptTo(JsonObject.class));
        }
        return list.iterator();
    }

    /**
     * Write the contents of the input nodes, subject to the an offset and a limit. Write metadata about the request
     * and response. This includes the number of returned and total matching nodes, and copying some request parameters.
     *
     * @param jsonGen the JSON object generator where the results should be serialized
     * @param objects an iterator over the nodes to serialize, which will be consumed
     * @param req the current request number
     * @param offset the requested offset, may be the default value of {0}
     *
     */
    private void addObjects(final JsonObjectBuilder jsonGen, final Iterator<JsonObject> objects, String req,
        final long offset)
    {
        long returnedrows = 0;
        long totalrows = 0;

        long offsetCounter = offset < 0 ? 0 : offset;
        long limitCounter = this.limit < 0 ? 0 : this.limit;

        final JsonArrayBuilder builder = Json.createArrayBuilder();

        while (objects.hasNext()) {
            JsonObject n = objects.next();
            // Skip results up to the offset provided
            if (offsetCounter > 0) {
                --offsetCounter;
            // Count up to our limit
            } else if (limitCounter > 0) {
                builder.add(n);
                --limitCounter;
                ++returnedrows;
            } else if (!this.showTotalRows) {
                break;
            }
            // Count the total number of results
            ++totalrows;
        }

        jsonGen.add("rows", builder.build());
        jsonGen.add("req", req);
        jsonGen.add("offset", offset);
        jsonGen.add("limit", this.limit);
        jsonGen.add("returnedrows", returnedrows);
        jsonGen.add("totalrows", totalrows);
    }

    /**
     * Use the value given (usually from a request.getParameter()) or use a default value.
     *
     * @param stringValue the value to use if provided
     * @param defaultValue the value to use if stringValue is not given
     * @return Either stringValue, if provided, or defaultValue
     */
    private long getLongValueOrDefault(final String stringValue, final long defaultValue)
    {
        long value = defaultValue;
        try {
            value = Long.parseLong(stringValue);
        } catch (NumberFormatException exception) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Get the results of the query as a JSON array.
     *
     * @return a JsonArray with all the content matching the query
     */
    public String getContent()
    {
        return this.content;
    }
}
