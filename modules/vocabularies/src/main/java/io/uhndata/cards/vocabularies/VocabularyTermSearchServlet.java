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
package io.uhndata.cards.vocabularies;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;

/**
 * A servlet that performs full text match and lucene queries on vocabulary terms.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Vocabulary" },
    methods = { "GET" },
    selectors = { "search" }
    )
public class VocabularyTermSearchServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -8244429250995709300L;

    private static final int DEFAULT_LIMIT = 10;

    private static final int MAX_LIMIT = 1000;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        String suggest = request.getParameter("suggest");
        String query = request.getParameter("query");
        String filter = request.getParameter("customFilter");
        String sort = request.getParameter("sort");
        long offset = getLongValueOrDefault(request.getParameter("offset"), 0);
        long limit = getLongValueOrDefault(request.getParameter("limit"), DEFAULT_LIMIT);
        // To avoid overloading the server, we set a limit on the number of nodes that can be returned
        limit = Math.min(limit, MAX_LIMIT);

        // Parse and execute the given suggest or query
        String parentPath = request.getResource().getPath();
        String oakQuery = constructQuery(suggest, query, filter, sort, parentPath);
        Iterator<Resource> results = request.getResourceResolver().findResources(oakQuery, "JCR-SQL2");

        // Write the output
        // The writer doesn't need to be explicitly closed since the auto-closed jsonGen will also close the writer
        response.setContentType("application/json");
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            long[] limits = writeNodes(jsonGen, results, offset, limit);
            writeSummary(jsonGen, request, limits, oakQuery);
            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Construct a JCR-SQL2 query with the given parameters.
     *
     * @param suggest A fulltext query to perform, or null if it was not given
     * @param query A lucene query to perform, or null if it was not given
     * @param filter A filter to apply
     * @param sort Sorting to apply
     * @param parentPath The path of the parent resource
     * @return A formatted JCR-SQL2 query.
     */
    private String constructQuery(String suggest, String query, String filter, String sort, String parentPath)
    {
        // Start by parsing the suggest or query
        String oakQuery = "";
        if (StringUtils.isNotBlank(suggest)) {
            oakQuery += handleFullTextQuery(suggest, parentPath);
        } else if (StringUtils.isNotBlank(query)) {
            oakQuery += handleLuceneQuery(query, parentPath);
        } else {
            oakQuery += String.format("select a.* from [cards:VocabularyTerm] as a where isdescendantnode(a, '%s')",
                parentPath);
        }

        // Apply filters, if given
        if (StringUtils.isNotBlank(filter)) {
            oakQuery += getConditionFromFilter(filter);
        }

        // Apply sorting, if given
        if (StringUtils.isNotBlank(sort)) {
            oakQuery += getOrderFromSort(sort);
        }

        return oakQuery;
    }

    /**
     * Outputs the results of a full-text query on the given string.
     *
     * @param suggest a string to perform full text matching upon
     * @param parentPath the location of the vocabulary whose children we're searching
     * @return The JCR-SQL2 query to perform
     */
    private String handleFullTextQuery(String suggest, String parentPath)
    {
        return (String.format(
            "select a.* from [cards:VocabularyTerm] as a where contains(a.*, '*%s*') and "
            + "isdescendantnode(a, '%s')",
            suggest.replace("'", "''"),
            parentPath
            ));
    }

    /**
     * Outputs the results of executing the given Lucene query.
     *
     * @param query the Lucene query to execute
     * @param parentPath the location of the vocabulary whose children we're searching
     * @return The JCR-SQL2 query to perform
     */
    private String handleLuceneQuery(String query, String parentPath)
    {
        return (String.format(
            "select a.* from [cards:VocabularyTerm] as a where native('lucene', '%s') and "
            + "isdescendantnode(a, '%s')",
            query.replace("'", "''"),
            parentPath
            ));
    }

    /**
     * Outputs the input SolR filters into an equivalent JCR-SQL2 conditional.
     * This will not work for everything, but it currently supports is_a:, term_category: and id: calls
     *
     * @param filters the SolR filters to convert
     * @return A JCR-SQL2 conditional, prepended with " AND "
     */
    private String getConditionFromFilter(String filters)
    {
        // URL-decode the filters
        String decodedFilters = "";
        try {
            decodedFilters = URLDecoder.decode(filters, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // not going to happen - value came from JDK's own StandardCharsets
        }

        decodedFilters = decodedFilters.replaceAll("'", "''");
        decodedFilters = decodedFilters.replaceAll("is_a:(.+)", "a.'parents'='$1'");
        decodedFilters = decodedFilters.replaceAll("term_category:([^ ()]+)", "a.'ancestors'='$1'");
        decodedFilters = decodedFilters.replaceAll("id:(.+)", "a.'parents'='$1'");

        // TODO: Guard against UNIONs?

        return (" AND " + decodedFilters);
    }

    private String getOrderFromSort(String sort)
    {
        String decodedSort = sort.replace("nameSort", "[label]");
        return (" ORDER BY " + decodedSort);
    }

    /**
     * Write metadata about the request and response. This includes the number of returned and total matching nodes, and
     * copying some request parameters.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param request the current request
     * @param offset the requested offset, may be the default value of {0}
     * @param limit the requested limit, may be the default value of {10}
     * @param returnedNodes the number of matching nodes included in the response, may be {@code 0} if no nodes were
     *            returned
     * @param totalMatchingNodes the total number of accessible nodes matching the request, may be {@code 0} if no nodes
     *            match the filters, or the current user cannot access the nodes
     * @param oakQuery test code, do not commit
     */
    private void writeSummary(final JsonGenerator jsonGen, final SlingHttpServletRequest request, final long[] limits,
        final String oakQuery)
    {
        jsonGen.write("req", request.getParameter("req"));
        jsonGen.write("offset", limits[0]);
        jsonGen.write("limit", limits[1]);
        jsonGen.write("returnedrows", limits[2]);
        jsonGen.write("totalrows", limits[3]);
        jsonGen.write("oakquery", oakQuery);
    }

    private long[] writeNodes(final JsonGenerator jsonGen, final Iterator<Resource> nodes,
        final long offset, final long limit)
    {
        final long[] counts = new long[4];
        counts[0] = offset;
        counts[1] = limit;
        counts[2] = 0;
        counts[3] = 0;

        long offsetCounter = offset < 0 ? 0 : offset;
        long limitCounter = limit < 0 ? 0 : limit;

        jsonGen.writeStartArray("rows");

        while (nodes.hasNext()) {
            Resource n = nodes.next();
            if (offsetCounter > 0) {
                --offsetCounter;
            } else if (limitCounter > 0) {
                jsonGen.write(n.adaptTo(JsonObject.class));
                --limitCounter;
                ++counts[2];
            }
            ++counts[3];
        }

        jsonGen.writeEnd();

        return counts;
    }

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
}
