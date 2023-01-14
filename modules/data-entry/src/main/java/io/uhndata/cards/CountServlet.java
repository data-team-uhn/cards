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
package io.uhndata.cards;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servlet that counts the number of resources that meet specified filters.
 * <p>
 * This servlet supports the following parameters:
 * </p>
 * <ul>
 * <li><code>filter</code>: a (lucene-like) search term, such as {@code germline}, {@code cancer OR tumor},
 * {@code (*blastoma OR *noma OR tumor*) recurrent}; no filter set by default</li>
 * <li><code>includeallstatus</code>: if true, incomplete forms will be included. Otherwise, they will be excluded
 * unless searched for directly using {@code fieldname="statusFlags"}
 * </ul>
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
        resourceTypes = { "cards/ResourceHomepage" },
        selectors = { "count" })
public class CountServlet extends PaginationServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CountServlet.class);

    private static final long serialVersionUID = -6068156942302219324L;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, IllegalArgumentException
    {
        try {
            // Ensure that this can only be run when logged in as admin
            final String remoteUser = request.getRemoteUser();
            if (!"admin".equals(remoteUser.toLowerCase(Locale.ROOT))) {
                // admin login required
                writeError(403, "Only admin can perform this operation.", response);
                return;
            }
            final ResourceResolver resolver = request.getResourceResolver();
            final Session session = resolver.adaptTo(Session.class);

            // Parse the request to build a list of filters
            final Map<FilterType, List<Filter>> filters = parseFiltersFromRequest(request);

            // Check for special cases in request and return zero results if any
            if (checkForSpecialEmptyFilter(request, filters, response)) {
                // Write the empty response
                writeEmptyResponse(response);
                return;
            }

            // Get a QueryManager object
            final QueryManager queryManager = session.getWorkspace().getQueryManager();

            // Create the Query object
            Query filterQuery = queryManager.createQuery(createQuery(request, session, filters), "JCR-SQL2");

            // Get the results and write the response
            writeResponse(request, response, filterQuery, filters, session);
        } catch (Exception e) {
            LOGGER.warn("Failed to execute query: {}", e.getMessage(), e);
            return;
        }
    }

    /**
     * Write an empty results response.
     *
     * @param response the HTTP response
     * @throws IOException if failed or interrupted I/O operation
     */
    private void writeEmptyResponse(final SlingHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // The writer doesn't need to be explicitly closed since the auto-closed jsonGen will also close the writer
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            jsonGen.write("count", "0");
            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Write the response.
     *
     * @param request the current request
     * @param response the HTTP response
     * @param query the query to execute
     * @throws IOException if failed or interrupted I/O operation
     * @throws RepositoryException if accessing the repository fails
     */
    private void writeResponse(final SlingHttpServletRequest request, final SlingHttpServletResponse response,
                               final Query query, final Map<FilterType, List<Filter>> filters, Session session)
            throws IOException, RepositoryException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // The writer doesn't need to be explicitly closed since the auto-closed jsonGen will also close the writer
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            long count = getCount(query, response);
            jsonGen.write("count", count);
            createQueryCacheNode(request, session, count, filters);
            try {
                session.save();
            } catch (final RepositoryException e) {
                LOGGER.error("Failed to commit queryCache: {}", e.getMessage(), e);
            }
            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Creates a <code>QueryCache</code> node that represents the current QueryCache instance with identified
     * filters.
     *
     * @param request the current request
     * @param session the current session
     * @param count the number of Resources that meet the conditions specified in the request
     * @param filters a list of filters
     * @throws RepositoryException if accessing the repository fails
     */
    private void createQueryCacheNode(final SlingHttpServletRequest request, final Session session, final long count,
                                      final Map<FilterType, List<Filter>> filters) throws RepositoryException
    {
        Node node = session.getNode("/QueryCache").addNode(UUID.randomUUID().toString(), "cards:QueryCache");
        node.setProperty("countType", "=");
        node.setProperty("count", count);
        node.setProperty("time", DATE_FORMAT.format(new Date()));
        node.setProperty("resourceType", request.getResource().getName());
        if (filters.isEmpty()) {
            return;
        }
        filters.forEach((filterType, filtersByFilterType) -> filtersByFilterType.forEach(filter -> {
            try {
                if (filterType.equals(FilterType.CHILD)) {
                    node.setProperty(filter.getName() + filter.getComparator(), filter.getValue());
                } else if (filterType.equals(FilterType.EMPTY)) {
                    node.setProperty(filter.getName(), "is empty");
                } else {
                    node.setProperty(filter.getName(), "is not empty");
                }
            } catch (RepositoryException e) {
                LOGGER.error("Failed to set node property: {}", e.getMessage(), e);
            }
        }));

        Map<String, String> fieldParameters = getSanitizedFieldParameters(request);
        if (!fieldParameters.isEmpty() && !fieldParameters.get(FIELDNAME).isBlank()) {
            node.setProperty(fieldParameters.get(FIELDNAME) + fieldParameters.get(FIELDCOMPARATOR),
                    fieldParameters.get(FIELDVALUE));
        }
    }

    /**
     * Counts the number of Resources that meet the conditions specified in the query.
     *
     * @param query the query to execute
     * @return a long-typed number of the number of Resources with the specified parameters
     * @throws IOException if failed or interrupted I/O operation
     */
    private long getCount(final Query query, final SlingHttpServletResponse response) throws IOException
    {
        long count = 0;
        // Which unique items have been seen so far in the query results
        final Set<String> seenResources = new HashSet<>();
        // Execute the query
        try {
            final QueryResult filterResult = query.execute();
            final NodeIterator results = filterResult.getNodes();

            while (results.hasNext()) {
                Node n = results.nextNode();
                // If this resource was already seen, ignore it
                if (!seenResources.contains(n.getPath())) {
                    seenResources.add(n.getPath());
                    ++count;
                }
            }
        } catch (RepositoryException e) {
            writeEmptyResponse(response);
        }

        return count;
    }

    private void writeError(final int status, final String message, final SlingHttpServletResponse response)
            throws IOException
    {
        final JsonObjectBuilder json = Json.createObjectBuilder();
        json.add("status", "error");
        json.add("error", message);

        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.build().toString());
    }
}
