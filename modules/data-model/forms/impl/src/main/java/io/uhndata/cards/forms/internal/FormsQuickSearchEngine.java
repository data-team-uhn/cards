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
package io.uhndata.cards.forms.internal;

import java.util.Collections;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.RowIterator;
import javax.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.spi.QuickSearchEngine;
import io.uhndata.cards.spi.SearchParameters;
import io.uhndata.cards.spi.SearchUtils;

/**
 * Finds {@code [cards:Forms]}s with answers or notes matching the given full text search.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class FormsQuickSearchEngine implements QuickSearchEngine
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QuickSearchEngine.class);

    private static final List<String> SUPPORTED_TYPES = Collections.singletonList("cards:Form");

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Reference
    private FormUtils formUtils;

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
            if (output.size() >= query.getMaxResults() && !query.showTotalResults()) {
                return;
            }

            final String xpathQuery = getXPathQuery(query.getQuery());
            RowIterator queryResults = resourceResolver.adaptTo(Session.class).getWorkspace().getQueryManager()
                .createQuery(xpathQuery.toString(), "xpath").execute().getRows();

            while (queryResults.hasNext()) {
                try {
                    // No need to go through results list if we do not want total number of matches
                    if (output.size() == query.getMaxResults() && !query.showTotalResults()) {
                        break;
                    }
                    Node item = queryResults.nextRow().getNode();

                    Pair<String, Boolean> match = getMatch(query.getQuery(), item);

                    String questionText = null;
                    String questionPath = "";
                    final Node questionNode = getQuestion(item);
                    questionText = questionNode.getProperty("text").getString();
                    questionPath = questionNode.getPath();

                    if (match != null && questionText != null) {
                        final Resource parent = getForm(item, resourceResolver);

                        output.add(SearchUtils.addMatchMetadata(
                            match.getLeft(), query.getQuery(), questionText, parent.adaptTo(JsonObject.class),
                            match.getRight(), questionPath));
                    }
                } catch (RepositoryException e) {
                    this.logger.warn("Failed to process search results: {}", e.getMessage(), e);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to search for subjects: {}", e.getMessage(), e);
        }
    }

    private String getXPathQuery(final String textQuery)
    {
        final String escapedQuery = SearchUtils.escapeLikeText(textQuery.toLowerCase());
        final StringBuilder xpathQuery = new StringBuilder();
        xpathQuery.append("/jcr:root/Forms//*[jcr:like(fn:lower-case(@value),'%");
        xpathQuery.append(escapedQuery);
        xpathQuery.append("%') or jcr:like(fn:lower-case(@note),'%");
        xpathQuery.append(escapedQuery);
        xpathQuery.append("%')]");

        return xpathQuery.toString();
    }

    /**
     * Get the question node that a matched answer node answers.
     *
     * @param answer an answer node matched by the query
     * @return the question node, non-null if the database is well formed
     * @throws RepositoryException if accessing the question node fails (shouldn't happen in practice)
     */
    private Node getQuestion(Node answer) throws RepositoryException
    {
        return answer.getProperty("question").getNode();
    }

    /**
     * Get the ancestor {@code cards:Form} node that a matched answer node belongs to.
     *
     * @param answer an answer node matched by the query
     * @return the form node, non-null if the database is well formed
     * @throws RepositoryException if accessing the repository fails
     */
    private Resource getForm(final Node answer, final ResourceResolver resourceResolver) throws RepositoryException
    {
        return resourceResolver.getResource(this.formUtils.getForm(answer).getPath());
    }

    /**
     * Find the value that was matched by the query. This is either one of the answe'r values, or its notes.
     *
     * @param query the user-entered query text to look for
     * @param answer the matched answer node
     * @return a pair, with the matched value (or notes field) in the left side, and a boolean indicating whether the
     *         match was in an answer value ({@code false}) or in the notes ({@code true}), or {@code null} if the query
     *         text couldn't be found in the answer
     * @throws RepositoryException if accessing the repository fails
     */
    private Pair<String, Boolean> getMatch(final String query, final Node answer) throws RepositoryException
    {
        Object answerValues = this.formUtils.getValue(answer);
        String matchedValue = SearchUtils.getMatch(answerValues, query);

        // As a fallback for when the match isn't in the value field, attempt to use the note field
        boolean matchedNotes = false;
        if (matchedValue == null && answer.hasProperty("note")) {
            String noteValue = answer.getProperty("note").getString();
            if (StringUtils.containsIgnoreCase(noteValue, query)) {
                matchedValue = noteValue;
                matchedNotes = true;
            }
        }
        return matchedValue == null ? null : Pair.of(matchedValue, matchedNotes);
    }
}
