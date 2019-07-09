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

import java.util.Iterator;

import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.script.Bindings;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.scripting.sightly.pojo.Use;

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
    private String content;

    private ResourceResolver resourceResolver;

    private SlingHttpServletRequest request;

    @Override
    public void init(Bindings bindings)
    {
        this.request = (SlingHttpServletRequest) bindings.get("request");
        this.resourceResolver = (ResourceResolver) bindings.get("resolver");

        try {
            this.content = findContent(this.request.getRequestParameter("query").getString());
        } catch (Exception e) {
            this.content = "Unknown error: " + e.fillInStackTrace();
        }
    }

    /**
     * Finds content matching the given query.
     *
     * @param query a JCR_SQL2 query
     * @return the content matching the query
     */
    private String findContent(String query) throws RepositoryException
    {
        Iterator<Resource> results = this.resourceResolver.findResources(query, "JCR-SQL2");
        JsonArrayBuilder builder = Json.createArrayBuilder();
        while (results.hasNext())
        {
            Resource result = results.next();
            builder.add(result.adaptTo(JsonObject.class));
        }
        return builder.build().toString();
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
