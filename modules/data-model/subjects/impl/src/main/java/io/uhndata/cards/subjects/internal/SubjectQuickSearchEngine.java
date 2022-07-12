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
import java.util.Iterator;
import java.util.List;

import javax.json.JsonObject;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;

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
        if (output.size() == query.getMaxResults() && !query.showTotalResults()) {
            return;
        }

        final StringBuilder xpathQuery = new StringBuilder();
        xpathQuery.append("/jcr:root/Subjects//*[jcr:like(fn:lower-case(@identifier),'%");
        xpathQuery.append(SearchUtils.escapeLikeText(query.getQuery().toLowerCase()));
        xpathQuery.append("%')]");

        Iterator<Resource> foundResources = resourceResolver.findResources(xpathQuery.toString(), "xpath");

        while (foundResources.hasNext()) {
            // No need to go through results list if we do not want total number of matches
            if (output.size() == query.getMaxResults() && !query.showTotalResults()) {
                break;
            }
            Resource thisResource = foundResources.next();

            String resourceValue = thisResource.getValueMap().get("identifier", String.class);

            if (resourceValue != null) {
                output.add(SearchUtils.addMatchMetadata(
                    resourceValue, query.getQuery(), "identifier", thisResource.adaptTo(JsonObject.class), false, ""));
            }
        }
    }
}
