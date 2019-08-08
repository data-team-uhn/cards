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
 * A servlet that queries and performs full text match on vocabulary terms.
 *
 * @version $Id %I%$
 */
@Component (service = {Servlet.class})
@SlingServletResourceTypes(resourceTypes = {"lfs/Vocabulary"}, methods = {"GET"})
public class VocabularyTermSearchServlet extends SlingSafeMethodsServlet
{
    /**
     *
     */
    private static final long serialVersionUID = -8244429250995709300L;

    private static final int LIMIT = 10;

    @Reference
    private LogService logger;

    /** The Resource Resolver for the current request. */
    private final ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    /** The output writer for this request. */
    private final ThreadLocal<Writer> writer = new ThreadLocal<>();

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        final ResourceResolver resourceResolver = request.getResourceResolver();
        this.resolver.set(resourceResolver);
        String suggest = request.getParameter("suggest");
        String query = request.getParameter("query");

        if (suggest != null) {
            handleFullTextMatch(request, response);
        } else if (query != null) {
            handleLuceneQuery(request, response);
        } else {
            Writer out = response.getWriter();
            Json.createGenerator(out).writeStartObject().writeEnd().flush();
            out.close();
        }
    }

    private void handleFullTextMatch(String suggest, final SlingHttpServletResponse response)
        throws IOException
    {
        final String oakQuery =
            String.format("select n from [lfs:VocabularyTerm] where contains(*, '%s')",
                suggest.replace("'", "''"));
        Iterator<Resource> results = this.resolver.get().findResources(oakQuery, "JCR-SQL2");

        writeResults(response.getWriter(), results);
    }

    private void handleLuceneQuery(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        final String oakQuery =
            String.format("select n from [lfs:VocabularyTerm] where native('lucene', '%s')",
                query.replace("'", "''"));
        Iterator<Resource> results = this.resolver.get().findResources(oakQuery, "JCR-SQL2");

        writeResults(response.getWriter(), results);
    }

    private void writeResults(Writer out, Iterator<Resource> results)
        throws IOException
    {
        JsonGenerator generator = Json.createGenerator(out);
        generator.writeStartArray();

        // Turn the suggestions into a writable Json
        while (results.hasNext()) {
            Resource suggestion = results.next();
            generator.write(suggestion.adaptTo(JsonObject.class));
        }

        generator.writeEnd().flush();
        out.close();
    }
}
