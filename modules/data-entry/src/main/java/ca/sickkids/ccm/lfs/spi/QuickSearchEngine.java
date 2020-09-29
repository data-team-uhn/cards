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
package ca.sickkids.ccm.lfs.spi;

import java.util.List;

import javax.json.JsonObject;

import org.apache.sling.api.resource.ResourceResolver;

/**
 * Service interface used by {@link ca.sickkids.ccm.lfs.QueryBuilder} to search for a specific type of resource.
 *
 * @version $Id$
 */
public interface QuickSearchEngine
{
    /**
     * List the resource types supported by this query engine.
     *
     * @return a list of Sling resource types, usually a singleton, in the format {@code "lfs:Resource"}
     */
    List<String> getSupportedTypes();

    /**
     * Finds resources matching the given query text. Implementations will match the query as appropriate for the actual
     * resource, either directly in properties of the resource, or as matches in properties of its descendant nodes.
     *
     * @param query text to search
     * @param maxResults maximum number of results to return; the results are added up among all search engines, so the
     *            maximum doesn't apply only to a particular search engine
     * @param showTotalRows whether the total number of matching items is needed or not
     * @param resourceResolver the resource resolver for this session
     * @param output aggregator of search results
     */
    void quickSearch(String query, long maxResults, boolean showTotalRows, ResourceResolver resourceResolver,
        List<JsonObject> output);
}
