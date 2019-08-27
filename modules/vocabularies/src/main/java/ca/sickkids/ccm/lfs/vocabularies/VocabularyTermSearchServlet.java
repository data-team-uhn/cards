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

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.LogService;

/**
 * A servlet that performs full text match and lucene queries on vocabulary terms.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "lfs/Vocabulary" }, methods = { "GET" })
public class VocabularyTermSearchServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -8244429250995709300L;

    private static final int DEFAULT_LIMIT = 10;

    private static final int MAX_LIMIT = 1000;

    @Reference
    private LogService logger;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        String suggest = request.getParameter("suggest");
        String query = request.getParameter("query");
        response.setContentType("application/json");
        String limitStr = request.getParameter("limit");
        int limit = DEFAULT_LIMIT;
        if (NumberUtils.isDigits(limitStr)) {
            try {
                limit = Integer.valueOf(limitStr);
            } catch (NumberFormatException e) {
                // Bad limit, the default will be used
            }
        }
        // To avoid overloading the server, we set a limit on the number of nodes that can be returned
        limit = Math.min(limit, MAX_LIMIT);

        if (suggest != null) {
            handleFullTextQuery(suggest, request.getResourceResolver(), response, limit);
        } else if (query != null) {
            handleLuceneQuery(query, request.getResourceResolver(), response, limit);
        } else {
            Writer out = response.getWriter();
            Json.createGenerator(out).writeStartObject().writeEnd().flush();
            out.close();
        }
    }

    /**
     * Outputs the results of a full-text query on the given string.
     *
     * @param suggest a string to perform full text matching upon
     * @param resolver the resource resolver to handle the full text match
     * @param response the response object to write to
     */
    private void handleFullTextQuery(String suggest, final ResourceResolver resolver,
        final SlingHttpServletResponse response, final int limit) throws IOException
    {
        final String oakQuery =
            String.format("select * from [lfs:VocabularyTerm] where contains(*, '%s')",
                suggest.replace("'", "''"));
        Iterator<Resource> results = resolver.findResources(oakQuery, "JCR-SQL2");

        writeResults(response.getWriter(), results, limit);
    }

    /**
     * Outputs the results of executing the given Lucene query.
     *
     * @param query the Lucene query to execute
     * @param resolver the resource resolver to handle the query
     * @param response the response object to write to
     */
    private void handleLuceneQuery(String query, final ResourceResolver resolver,
        final SlingHttpServletResponse response, final int limit) throws IOException
    {
        final String oakQuery =
            String.format("select * from [lfs:VocabularyTerm] where native('lucene', '%s')",
                query.replace("'", "''"));
        Iterator<Resource> results = resolver.findResources(oakQuery, "JCR-SQL2");

        writeResults(response.getWriter(), results, limit);
    }

    /**
     * Outputs the given resources as a JSON array.
     *
     * @param out the writer to output to
     * @param response the resources to output
     */
    private void writeResults(Writer out, Iterator<Resource> results, final int limit)
        throws IOException
    {
        JsonGenerator generator = Json.createGenerator(out);
        generator.writeStartArray();

        // Turn the suggestions into a writable Json
        int i = 0;
        while (results.hasNext() && i < limit) {
            Resource suggestion = results.next();
            generator.write(suggestion.adaptTo(JsonObject.class));
            ++i;
        }

        generator.writeEnd().flush();
        out.close();
    }
}
