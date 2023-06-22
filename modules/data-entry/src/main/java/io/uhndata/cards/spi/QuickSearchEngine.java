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

import java.util.List;

import javax.json.JsonObject;

import org.apache.sling.api.resource.ResourceResolver;

/**
 * Service interface used by {@link io.uhndata.cards.QueryBuilder} to search for a specific type of resource.
 *
 * @version $Id$
 */
public interface QuickSearchEngine
{
    interface Results
    {
        boolean hasNext();

        void skip();

        JsonObject next();

        static Results emptyResults()
        {
            return new Results()
            {
                @Override
                public boolean hasNext()
                {
                    return false;
                }

                @Override
                public void skip()
                {
                    // Nothing to skip
                }

                @Override
                public JsonObject next()
                {
                    return null;
                }
            };
        }
    }

    /**
     * List the node types supported by this query engine.
     *
     * @return a list of JCR node types, usually a singleton, in the format {@code "cards:Resource"}
     */
    List<String> getSupportedTypes();

    /**
     * Check if the specified resource type is supported by this engine.
     *
     * @param type the JCR node type to check, in the format {@code "cards:Resource"}
     * @return {@code true} if the node type is supported, {@code false} otherwise
     */
    default boolean isTypeSupported(final String type)
    {
        return getSupportedTypes().contains(type);
    }

    /**
     * Finds resources matching the given query text. Implementations will match the query as appropriate for the actual
     * resource, either directly in properties of the resource, or as matches in properties of its descendant nodes.
     *
     * @param query the query configuration to use for searching
     * @param resourceResolver the resource resolver for this session
     * @return a supplier of results
     */
    Results quickSearch(SearchParameters query, ResourceResolver resourceResolver);
}
