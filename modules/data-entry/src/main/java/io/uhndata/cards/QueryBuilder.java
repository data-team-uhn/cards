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
package io.uhndata.cards;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.script.Bindings;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.scripting.sightly.pojo.Use;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.spi.QuickSearchEngine;
import io.uhndata.cards.spi.SearchParameters;
import io.uhndata.cards.spi.SearchParametersFactory;

/**
 * A HTL Use-API that can run a JCR query and output the results as JSON. The query to execute is taken from request
 * parameters, and the query result is retrieved from the {@code content} method. Multiple query types are supported:
 * <ul>
 * <li>{@code query}, a full query in the JCR-SQL2 syntax</li>
 * <li>{@code lucene}, a lucene query</li>
 * <li>{@code fulltext}, a textual query that will be matched against indexed nodes</li>
 * <li>{@code quick}, search input that will be matched by pluggable {@link QuickSearchEngine quick search engines}</li>
 * </ul>
 * <p>
 * If more than one of these parameters is sent, the first one, in the order above, that is not empty, will be used, and
 * the others will be ignored. If none of these parameters is sent, then an empty query result is returned.
 * </p>
 * <p>
 * The output is a JSON with some query metadata, and an array of results, in the format:
 * </p>
 * <p>
 * <code>
 * {
 *   "req": "", or string copied from the request
 *   "offset": 0, or integer value copied from the request
 *   "limit": 10, or integer value copied from the request
 *   "returnedrows": number of returned rows, 0 if no rows are returned
 *   "totalrows": number of rows matching the query, 0 if nothing matches the query
 *   "rows": [ array of search results ]
 * }
 * </code>
 * <p>
 * By default the rows are serialized nodes in the default JSON serialization format. Other request parameters can
 * change the output format:
 * </p>
 * <ul>
 * <li>{@code serializeChildren=1} causes the direct children of the results to be serialized as well</li>
 * <li>{@code rawResults=true} causes the exact query results, as specified in the query selectors, to be returned as an
 * array, instead of serializing the matching nodes</li>
 * <li>{@code showTotalRows=false} causes the total number of results to not be computed, resulting in slightly better
 * performance</li>
 * </ul>
 * <p>
 * Quick search results are returned in a special format, including match highlighting.
 * </p>
 * <p>
 * To use this API, simply place the following code in a HTL file:
 * </p>
 * <p>
 * <code>
 * &lt;sly data-sly-use.query="io.uhndata.cards.QueryBuilder"&gt;${query.content @ context='unsafe'}&lt;/sly&gt;
 * </code>
 * </p>
 * <p>
 * Or, send a HTTP request to {@code /query?query=select%20*%20from%20[cards:Form]}.
 * </p>
 *
 * @version $Id$
 */
public class QueryBuilder implements Use
{
    private Logger logger = LoggerFactory.getLogger(QueryBuilder.class);

    private String content;

    private ResourceResolver resourceResolver;

    /** Whether or not the input should be escaped, if it is used in a contains() call. */
    private boolean disableEscaping;

    /** A token sent in the request to be copied in the response, to help distinguish between multiple requests. */
    private String requestID;

    /** The requested or default offset, i.e. how many results to skip. */
    private long offset;

    /** The requested or default limit on the number of returned results. */
    private long limit;

    /** Whether to show the total number of results. */
    private boolean showTotalRows;

    private boolean serializeChildren;

    /** Resource types allowed for a search. */
    private String[] resourceTypes;

    /** Quick search engines. */
    private List<QuickSearchEngine> searchEngines;

    /**
     * Get the results of the query as a JSON array.
     *
     * @return a serialized JsonArray with all the content matching the query
     */
    public String getContent()
    {
        return this.content;
    }

    @Override
    public void init(Bindings bindings)
    {
        SlingHttpServletRequest request = (SlingHttpServletRequest) bindings.get("request");
        this.resourceResolver = (ResourceResolver) bindings.get("resolver");
        final SlingScriptHelper slingHelper = (SlingScriptHelper) bindings.get("sling");
        this.searchEngines = Arrays.asList(slingHelper.getServices(QuickSearchEngine.class, null));

        try {
            this.offset = getLongValueOrDefault(request.getParameter("offset"), 0);
            this.serializeChildren = getLongValueOrDefault(request.getParameter("serializeChildren"), 0) != 0;
            this.requestID = StringUtils.defaultString(request.getParameter("req"));

            this.limit = getLongValueOrDefault(request.getParameter("limit"), 10);
            this.resourceTypes = request.getParameterValues("allowedResourceTypes");
            final String doNotEscape = request.getParameter("doNotEscapeQuery");
            this.disableEscaping = "true".equals(doNotEscape);
            final String showTotalRowsParam = request.getParameter("showTotalRows");
            this.showTotalRows = StringUtils.isBlank(showTotalRowsParam) || "true".equals(showTotalRowsParam);

            QueryResult results = query(request);
            if (results == null) {
                return;
            }

            // output the results into our content
            JsonObjectBuilder builder = Json.createObjectBuilder();
            if ("true".equals(request.getParameter("rawResults"))) {
                this.outputRawQueryResults(builder, results);
            } else {
                this.outputQueryResults(builder, results);
            }
            this.content = builder.build().toString();
        } catch (Exception e) {
            this.logger.error("Failed to query resources: {}", e.getMessage(), e);
            this.content = "Unknown error: " + e.fillInStackTrace();
        }
    }

    private QueryResult query(final SlingHttpServletRequest request)
        throws UnsupportedEncodingException, RepositoryException
    {
        final String jcrQuery = request.getParameter("query");
        final String luceneQuery = request.getParameter("lucene");
        final String fullTextQuery = request.getParameter("fulltext");
        final String quickQuery = request.getParameter("quick");

        QueryResult results;
        if (StringUtils.isNotBlank(jcrQuery)) {
            results = queryJCR(this.urlDecode(jcrQuery));
        } else if (StringUtils.isNotBlank(luceneQuery)) {
            results = queryLucene(this.urlDecode(luceneQuery));
        } else if (StringUtils.isNotBlank(fullTextQuery)) {
            results = fullTextSearch(this.urlDecode(fullTextQuery));
        } else if (StringUtils.isNotBlank(quickQuery)) {
            // A quick search is special, since the results are not simply serialized nodes, but search results
            // enriched with match information, and the quick search engines already take care of not returning more
            // results than needed
            JsonObjectBuilder builder = Json.createObjectBuilder();
            this.outputQuickSearchResults(builder, quickSearch(this.urlDecode(quickQuery)));
            this.content = builder.build().toString();
            return null;
        } else {
            results = EmptyResults.INSTANCE;
        }
        return results;
    }

    /**
     * Finds content matching the given JCR_SQL2 query.
     *
     * @param query a JCR-SQL2 query
     * @return the content matching the query
     */
    private QueryResult queryJCR(String query) throws RepositoryException
    {
        return this.resourceResolver.adaptTo(Session.class).getWorkspace().getQueryManager()
            .createQuery(query, "JCR-SQL2").execute();
    }

    /**
     * Finds content matching the given lucene query.
     *
     * @param query a lucene query
     * @return the content matching the query
     */
    private QueryResult queryLucene(String query) throws RepositoryException
    {
        // Wrap our lucene query in JCR-SQL2 syntax for the resource resolver to understand
        return queryJCR(
            String.format("select n.* from [nt:base] as n where native('lucene', '%s')"
                + " and n.'sling:resourceSuperType' = 'cards/Resource'", query.replace("'", "''")));
    }

    /**
     * Finds content using the given full text search.
     *
     * @param query text to search
     * @return the content matching the query
     */
    private QueryResult fullTextSearch(String query) throws RepositoryException
    {
        // Wrap our full-text query in JCR-SQL2 syntax for the resource resolver to understand
        return queryJCR(
            String.format("select n.* from [nt:base] as n where contains(*, '%s')", this.fullTextEscape(query)));
    }

    /**
     * Finds [cards:Form]s, [cards:Subject]s, and [cards:Questionnaire]s using the given full text search. This performs
     * the search in such a way that values in child nodes (e.g. cards:Answers of an cards:Form) are aggregated to their
     * parent.
     *
     * @param query text to search
     * @return the content matching the query
     */
    private Iterator<JsonObject> quickSearch(String query) throws RepositoryException, UnsupportedEncodingException
    {
        List<String> allowedResourceTypes = Collections.singletonList("cards:Form");
        if (this.resourceTypes != null && this.resourceTypes.length > 0) {
            allowedResourceTypes = Arrays.asList(this.resourceTypes);
        }
        List<JsonObject> resultsList = new ArrayList<>();

        final SearchParameters searchParameters = SearchParametersFactory.newSearchParameters()
            .withType("quick")
            .withQuery(query)
            .withShowTotalResults(this.showTotalRows)
            .withMaxResults(this.limit)
            .build();

        for (String type : allowedResourceTypes) {
            this.searchEngines.stream()
                .filter(engine -> engine.isTypeSupported(type))
                .forEach(engine -> engine.quickSearch(searchParameters, this.resourceResolver, resultsList));
        }
        return resultsList.listIterator();
    }

    /**
     * Serialize search results, subject to the an offset and a limit. Write metadata about the request and response.
     * This includes the number of returned and total matching nodes, and copying some request parameters.
     *
     * @param output the JSON object generator where the results should be serialized
     * @param rows an iterator over the nodes to serialize, which will be consumed
     * @param serializeChildren whether child nodes of the search results must be serialized as well
     * @param req the current request number
     * @param offset the requested offset, may be the default value of {0}
     * @throws RepositoryException if running the query fails
     */
    private void outputQueryResults(final JsonObjectBuilder output, final QueryResult queryResults)
        throws RepositoryException
    {
        Set<String> seenPaths = new HashSet<>();
        long returnedRows = 0;
        long totalRows = 0;

        long offsetCounter = this.offset;
        long limitCounter = this.limit;

        final JsonArrayBuilder builder = Json.createArrayBuilder();

        final RowIterator rows = queryResults.getRows();

        while (rows.hasNext()) {
            try {
                final Row row = rows.nextRow();

                // It's faster to just work with paths instead of loading the full node
                final String path = row.getPath();
                if (seenPaths.contains(path)) {
                    // The query may return the same node multiple times when joins are used, we only return it once
                    continue;
                }
                seenPaths.add(path);

                // Skip results up to the offset provided
                if (offsetCounter > 0) {
                    --offsetCounter;
                    // Count up to our limit
                } else if (limitCounter > 0) {
                    builder.add(serializeNode(path));
                    --limitCounter;
                    ++returnedRows;
                } else if (!this.showTotalRows) {
                    break;
                }
                // Count the total number of results
                ++totalRows;
            } catch (RepositoryException e) {
                this.logger.warn("Failed to serialize search results: {}", e.getMessage(), e);
            }
        }

        buildResults(output, builder.build(), returnedRows, totalRows);
    }

    /**
     * Serialize search results, subject to the an offset and a limit. Write metadata about the request and response.
     * This includes the number of returned and total matching nodes, and copying some request parameters.
     *
     * @param output the JSON object generator where the results should be serialized
     * @param queryResults the raw query results
     * @throws RepositoryException if running the query fails
     */
    private void outputRawQueryResults(final JsonObjectBuilder output, final QueryResult queryResults)
        throws RepositoryException
    {
        long returnedRows = 0;
        long totalRows = 0;

        long offsetCounter = this.offset;
        long limitCounter = this.limit;

        final JsonArrayBuilder builder = Json.createArrayBuilder();

        final RowIterator rows = queryResults.getRows();

        while (rows.hasNext()) {
            try {
                final Row row = rows.nextRow();

                // Skip results up to the offset provided
                if (offsetCounter > 0) {
                    --offsetCounter;
                    // Count up to our limit
                } else if (limitCounter > 0) {
                    JsonObjectBuilder serializedRow = Json.createObjectBuilder();
                    for (String selector : queryResults.getSelectorNames()) {
                        final String path = row.getPath(selector);
                        serializedRow.add(selector, path == null ? JsonValue.NULL : Json.createValue(path));
                    }
                    for (String column : queryResults.getColumnNames()) {
                        final Value value = row.getValue(column);
                        serializedRow.add(column, value == null ? JsonValue.NULL : Json.createValue(value.getString()));
                    }
                    builder.add(serializedRow.build());
                    --limitCounter;
                    ++returnedRows;
                } else if (!this.showTotalRows) {
                    break;
                }
                // Count the total number of results
                ++totalRows;
            } catch (RepositoryException e) {
                this.logger.warn("Failed to serialize search results: {}", e.getMessage(), e);
            }
        }
        buildResults(output, builder.build(), returnedRows, totalRows);
    }

    /**
     * Serialize quick search results. Write metadata about the request and response. This includes the number of
     * returned and total matching nodes, and copying some request parameters.
     *
     * @param output the JSON object generator where the results should be serialized
     * @param searchResults an iterator over quick search results, which will be consumed
     */
    private void outputQuickSearchResults(final JsonObjectBuilder output, final Iterator<JsonObject> searchResults)
    {
        long returnedRows = 0;
        long totalRows = 0;

        long offsetCounter = this.offset;
        long limitCounter = this.limit;

        final JsonArrayBuilder builder = Json.createArrayBuilder();

        while (searchResults.hasNext()) {
            final JsonObject next = searchResults.next();
            // Skip results up to the offset provided
            if (offsetCounter > 0) {
                --offsetCounter;
                // Count up to our limit
            } else if (limitCounter > 0) {
                builder.add(next);
                --limitCounter;
                ++returnedRows;
            } else if (!this.showTotalRows) {
                break;
            }
            // Count the total number of results
            ++totalRows;
        }

        buildResults(output, builder.build(), returnedRows, totalRows);
    }

    /**
     * Serialize a node to be placed in the output.
     *
     * @param path JCR path of the node to serialize
     * @return a JsonObject
     */
    private JsonObject serializeNode(final String path)
    {
        final Resource resource = this.resourceResolver.getResource(path);
        // If there are children we can add, we'll add them as child properties of the JsonObject
        if (this.serializeChildren && resource.hasChildren()) {
            Iterator<Resource> children = resource.listChildren();
            JsonObjectBuilder builder = Json.createObjectBuilder(resource.adaptTo(JsonObject.class));
            while (children.hasNext()) {
                Resource child = children.next();
                builder.add(child.getName(), child.adaptTo(JsonObject.class));
            }

            return builder.build();
        } else {
            return resource.adaptTo(JsonObject.class);
        }
    }

    private void buildResults(final JsonObjectBuilder output, final JsonArray data, final long returnedRows,
        final long totalRows)
    {
        output.add("rows", data);
        output.add("req", this.requestID);
        output.add("offset", this.offset);
        output.add("limit", this.limit);
        output.add("returnedrows", returnedRows);
        output.add("totalrows", this.showTotalRows ? totalRows : -1L);
    }

    /**
     * Use the value given (usually from a request.getParameter()) or use a default value.
     *
     * @param stringValue the value to use if provided
     * @param defaultValue the value to use if stringValue is not given, not a number, or a negative number
     * @return a positive number
     */
    private long getLongValueOrDefault(final String stringValue, final long defaultValue)
    {
        long value = defaultValue;
        try {
            value = Long.parseLong(stringValue);
        } catch (NumberFormatException exception) {
            value = defaultValue;
        }
        return value < 0 ? defaultValue : value;
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
     * Escapes the input query if this.shouldEscape is true.
     *
     * @param input text to escape
     * @return an escaped version of the input
     */
    private String fullTextEscape(String input)
    {
        if (this.disableEscaping) {
            return input;
        }

        // Escape sequence taken from https://jackrabbit.apache.org/archive/wiki/JCR/EncodingAndEscaping_115513396.html
        return input.replaceAll("([\\Q+-&|!(){}[]^\"~*?:\\_%/\\E])", "\\\\$1").replaceAll("'", "''");
    }

    /**
     * Helper class, an empty query result to use when no query string is sent in the request.
     */
    private static final class EmptyResults implements QueryResult
    {
        public static final QueryResult INSTANCE = new EmptyResults();

        @Override
        public String[] getSelectorNames() throws RepositoryException
        {
            return new String[0];
        }

        @Override
        public String[] getColumnNames() throws RepositoryException
        {
            return new String[0];
        }

        @Override
        public RowIterator getRows() throws RepositoryException
        {
            return EmptyIterator.INSTANCE;
        }

        @Override
        public NodeIterator getNodes() throws RepositoryException
        {
            return EmptyIterator.INSTANCE;
        }
    }

    /**
     * Helper class, an empty iterator to use when no query string is sent in the request.
     */
    private static final class EmptyIterator implements RowIterator, NodeIterator
    {
        public static final EmptyIterator INSTANCE = new EmptyIterator();

        @Override
        public long getSize()
        {
            return 0;
        }

        @Override
        public boolean hasNext()
        {
            return false;
        }

        @Override
        public Object next()
        {
            return null;
        }

        @Override
        public Row nextRow()
        {
            return null;
        }

        @Override
        public Node nextNode()
        {
            return null;
        }

        @Override
        public void skip(long skipNum)
        {
            // Nothing to do
        }

        @Override
        public long getPosition()
        {
            return 0;
        }
    }
}
