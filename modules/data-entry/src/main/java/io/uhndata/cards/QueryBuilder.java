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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.json.Json;
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
 * A HTL Use-API that can run a JCR query and output the results as JSON. The query to execute is taken from the request
 * parameter {@code query}, and must be in the JCR-SQL2 syntax. To use this API, simply place the following code in a
 * HTL file:
 *
 * <p><code>
 * &lt;sly data-sly-use.query="io.uhndata.cards.QueryBuilder"&gt;${query.content @ context='unsafe'}&lt;/sly&gt;
 * </code></p>
 *
 * <p>Or, send a HTTP request to {@code /query?query=select%20*%20from%20[cards:Form]}.</p>
 *
 * @version $Id$
 */
public class QueryBuilder implements Use
{
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

    /** Quick search engines. */
    private List<QuickSearchEngine> searchEngines;

    @SuppressWarnings({"checkstyle:ExecutableStatementCount"})
    @Override
    public void init(Bindings bindings)
    {
        SlingHttpServletRequest request = (SlingHttpServletRequest) bindings.get("request");
        this.resourceResolver = (ResourceResolver) bindings.get("resolver");
        final SlingScriptHelper slingHelper = (SlingScriptHelper) bindings.get("sling");
        this.searchEngines = Arrays.asList(slingHelper.getServices(QuickSearchEngine.class, null));

        try {
            final String jcrQuery = request.getParameter("query");
            final String luceneQuery = request.getParameter("lucene");
            final String fullTextQuery = request.getParameter("fulltext");
            final String quickQuery = request.getParameter("quick");
            final long offset = getLongValueOrDefault(request.getParameter("offset"), 0);
            final boolean serializeChildren = getLongValueOrDefault(request.getParameter("serializeChildren"), 0) != 0;
            String requestID = request.getParameter("req");
            if (StringUtils.isBlank(requestID)) {
                requestID = "";
            }
            final String doNotEscape = request.getParameter("doNotEscapeQuery");
            final String showTotalRowsParam = request.getParameter("showTotalRows");

            this.limit = getLongValueOrDefault(request.getParameter("limit"), 10);
            this.resourceTypes = request.getParameterValues("allowedResourceTypes");
            this.shouldEscape = !("true".equals(doNotEscape));
            this.showTotalRows = StringUtils.isBlank(showTotalRowsParam) || "true".equals(showTotalRowsParam);

            // Try to use a JCR-SQL2 query first
            Iterator<JsonObject> results;
            if (StringUtils.isNotBlank(jcrQuery)) {
                results = QueryBuilder.adaptNodes(queryJCR(this.urlDecode(jcrQuery)), serializeChildren);
            } else if (StringUtils.isNotBlank(luceneQuery)) {
                results = QueryBuilder.adaptNodes(queryLucene(this.urlDecode(luceneQuery)), serializeChildren);
            } else if (StringUtils.isNotBlank(fullTextQuery)) {
                results = QueryBuilder.adaptNodes(fullTextSearch(this.urlDecode(fullTextQuery)), serializeChildren);
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
                + " and n.'sling:resourceSuperType' = 'cards/Resource'", query.replace("'", "''")));
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
     * Finds [cards:Form]s, [cards:Subject]s, and [cards:Questionnaire]s using the given full text search.
     * This performs the search in such a way that values in child nodes (e.g. cards:Answers of an cards:Form)
     * are aggregated to their parent.
     *
     * @param query text to search
     *
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
     * Convert an iterator of nodes into an iterator of JsonObjects.
     * @param nodes the iterator to convert
     * @param serializeChildren If true, this also includes the immediate children of each node
     * @return An iterator of the input nodes
     */
    private static Iterator<JsonObject> adaptNodes(Iterator<Resource> resources, boolean serializeChildren)
    {
        ArrayList<JsonObject> list = new ArrayList<>();
        while (resources.hasNext()) {
            Resource resource = resources.next();

            // If there are children we can add, we'll add them as child properties of the JsonObject
            if (serializeChildren && resource.hasChildren()) {
                Iterator<Resource> children = resource.listChildren();
                JsonObjectBuilder builder = Json.createObjectBuilder();

                // First convert the original JsonObject into a JsonObjectBuilder we can adjust
                JsonObject original = resource.adaptTo(JsonObject.class);
                for (Map.Entry<String, JsonValue> entry : original.entrySet()) {
                    builder.add(entry.getKey(), entry.getValue());
                }

                // Next, add each child
                while (children.hasNext()) {
                    Resource child = children.next();
                    builder.add(child.getName(), child.adaptTo(JsonObject.class));
                }

                list.add(builder.build());
            } else {
                list.add(resource.adaptTo(JsonObject.class));
            }
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
