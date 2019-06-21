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
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.script.Bindings;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.scripting.sightly.pojo.Use;

// import com.google.gson.Gson;

/**
 * Base class for UI extensions.
 * @version $Id$
 */
public class QueryBuilder implements Use
{
    private String content;
    private ResourceResolver resourceResolver;
    private SlingHttpServletRequest request;

    /**
     * Initializer from base Use class.
     */
    @Override
    public void init(Bindings bindings)
    {
        this.request = (SlingHttpServletRequest) bindings.get("request");
        this.resourceResolver = (ResourceResolver) bindings.get("resolver");

        try
        {
            this.content = findContent(this.request.getRequestParameter("query").getString());
        } catch (Exception e)
        {
            this.content = "Unknown error: " + e.fillInStackTrace();
        }
    }

    /**
     * Convert a javax.script.Bindings to a JSON string.
     * Required due to StackOverflows generated when Bindings contains
     * anything self-referential.
     * @param bindings A javax.script.Bindings object
     * @return The JSON representation of the given bindings
     */
    private String bindingsToJson(Bindings bindings)
    {
        StringBuilder builder = new StringBuilder("{\n");
        for (Map.Entry<String, Object> entry : bindings.entrySet())
        {
            builder.append(entry.getKey() + ":" + entry.getValue().toString() + "\n");
        }
        builder.append("}");
        return builder.toString();
    }

    /**
     * Finds content matching the given query.
     * @param query A Java_JCR2 query
     * @return The child jcr:content node if found, or null
     */
    private String findContent(String query) throws RepositoryException
    {
        StringBuilder builder = new StringBuilder("[\n");
        Iterator<Resource> results = this.resourceResolver.findResources(query, "JCR-SQL2");
        while (results.hasNext())
        {
            builder.append("\t{\n");
            Resource result = results.next();
            Map<String, Object> valueMap = result.getValueMap();
            boolean firstEntry = true;
            for (Map.Entry<String, Object> entry : valueMap.entrySet())
            {
                // Add a , onto the previous line, if this is the second property or later
                if (!firstEntry)
                {
                    builder.append(",\n");
                }
                firstEntry = false;

                // Convert this property into a string
                String entryJson = StringEscapeUtils.escapeJson(entry.getValue().toString());
                builder.append("\t\t\"" + entry.getKey() + "\": \"" + entryJson + "\"");
            }

            // Add the comma if there's another entry after this
            if (results.hasNext())
            {
                builder.append("\n\t},\n");
            } else {
                builder.append("\n\t}\n");
            }
        }
        builder.append("]");
        return builder.toString();
    }

    /**
     * Get all nodes with type lfs:Extension.
     * @return An iterator over all extensions.
     */
    public Iterator<Resource> getExtensions()
    {
        return this.resourceResolver.findResources("select * from [lfs:Extension] as n", "JCR-SQL2");
    }

    /**
     * Get the content for this node.
     * @return The content for this node.
     */
    public String getContent()
    {
        return this.content;
    }
}
