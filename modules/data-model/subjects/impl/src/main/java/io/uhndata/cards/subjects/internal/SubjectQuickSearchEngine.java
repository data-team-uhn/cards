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
package io.uhndata.cards.subjects.internal;

import java.util.Collections;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.RowIterator;
import javax.json.JsonObject;

import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.spi.QuickSearchEngine;
import io.uhndata.cards.spi.SearchParameters;
import io.uhndata.cards.spi.SearchUtils;

/**
 * Finds {@code [cards:Subject]}s with identifiers matching the given full text search.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class SubjectQuickSearchEngine implements QuickSearchEngine
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QuickSearchEngine.class);

    private static final List<String> SUPPORTED_TYPES = Collections.singletonList("cards:Subject");

    @Override
    public List<String> getSupportedTypes()
    {
        return SUPPORTED_TYPES;
    }

    @Override
    public void quickSearch(final SearchParameters query, final ResourceResolver resourceResolver,
        final List<JsonObject> output)
    {
        try {
            if (output.size() == query.getMaxResults() && !query.showTotalResults()) {
                return;
            }

            final StringBuilder sqlQuery = new StringBuilder()
                .append("select [jcr:path] from [cards:Subject] as a where lower([identifier]) like '%")
                .append(SearchUtils.escapeLikeText(query.getQuery().toLowerCase()))
                .append("%' order by [identifier] option(index tag cards)");

            final RowIterator queryResults = resourceResolver.adaptTo(Session.class).getWorkspace().getQueryManager()
                .createQuery(sqlQuery.toString(), Query.JCR_SQL2).execute().getRows();

            while (queryResults.hasNext()) {
                // No need to go through results list if we do not want total number of matches
                if (output.size() == query.getMaxResults() && !query.showTotalResults()) {
                    break;
                }
                Node item = queryResults.nextRow().getNode();

                String resourceValue = item.getProperty("identifier").getString();

                if (resourceValue != null) {
                    output.add(SearchUtils.addMatchMetadata(
                        resourceValue, query.getQuery(), "identifier",
                        resourceResolver.getResource(item.getPath()).adaptTo(JsonObject.class), false, ""));
                }
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to search for subjects: {}", e.getMessage(), e);
        }
    }
}
