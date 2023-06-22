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
import javax.jcr.query.Query;
import javax.jcr.query.RowIterator;
import javax.json.JsonObject;
import javax.json.JsonValue;

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

    @Reference
    private FormUtils formUtils;

    @Override
    public List<String> getSupportedTypes()
    {
        return SUPPORTED_TYPES;
    }

    @Override
    public QuickSearchEngine.Results quickSearch(final SearchParameters query,
        final ResourceResolver resourceResolver)
    {
        try {
            final String sqlQuery = getQuery(query.getQuery());
            final RowIterator queryResults = resourceResolver.adaptTo(Session.class).getWorkspace().getQueryManager()
                .createQuery(sqlQuery.toString(), Query.JCR_SQL2).execute().getRows();
            return new FormsResults(query.getQuery(), queryResults, resourceResolver);
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed to search for subjects: {}", e.getMessage(), e);
        }
        return QuickSearchEngine.Results.emptyResults();
    }

    private String getQuery(final String textQuery)
    {
        final String escapedQuery = SearchUtils.escapeLikeText(textQuery.toLowerCase());
        final StringBuilder sqlQuery = new StringBuilder()
            .append("select [jcr:path] from [cards:TextAnswer] as a ")
            .append("where lower([value]) like '%")
            .append(escapedQuery)
            .append("%' or lower([note]) like '%")
            .append(escapedQuery)
            .append("%' option(index tag cards)");

        return sqlQuery.toString();
    }

    private final class FormsResults implements QuickSearchEngine.Results
    {
        private final RowIterator queryResults;

        private final String query;

        private final ResourceResolver resolver;

        FormsResults(final String query, final RowIterator queryResults, final ResourceResolver resolver)
        {
            this.query = query;
            this.queryResults = queryResults;
            this.resolver = resolver;
        }

        @Override
        public boolean hasNext()
        {
            return this.queryResults.hasNext();
        }

        @Override
        public void skip()
        {
            this.queryResults.nextRow();
        }

        @Override
        public JsonObject next()
        {
            try {
                final Node item = this.queryResults.nextRow().getNode();
                final Pair<String, Boolean> match = getMatch(this.query, item);
                String questionText = null;
                String questionPath = "";
                final Node questionNode = getQuestion(item);
                questionText = questionNode.getProperty("text").getString();
                questionPath = questionNode.getPath();

                if (match != null && questionText != null) {
                    final Resource parent = getForm(item);

                    return SearchUtils.addMatchMetadata(
                        match.getLeft(), this.query, questionText, parent.adaptTo(JsonObject.class),
                        match.getRight(), questionPath);
                }
            } catch (final RepositoryException e) {
                LOGGER.warn("Failed to process search results: {}", e.getMessage(), e);
            }
            return JsonValue.EMPTY_JSON_OBJECT;
        }

        /**
         * Get the ancestor {@code cards:Form} node that a matched answer node belongs to.
         *
         * @param answer an answer node matched by the query
         * @return the form node, non-null if the database is well formed
         * @throws RepositoryException if accessing the repository fails
         */
        private Resource getForm(final Node answer) throws RepositoryException
        {
            return this.resolver.getResource(FormsQuickSearchEngine.this.formUtils.getForm(answer).getPath());
        }

        /**
         * Get the question node that a matched answer node answers.
         *
         * @param answer an answer node matched by the query
         * @return the question node, non-null if the database is well formed
         * @throws RepositoryException if accessing the question node fails (shouldn't happen in practice)
         */
        private Node getQuestion(final Node answer) throws RepositoryException
        {
            return answer.getProperty("question").getNode();
        }

        /**
         * Find the value that was matched by the query. This is either one of the answe'r values, or its notes.
         *
         * @param query the user-entered query text to look for
         * @param answer the matched answer node
         * @return a pair, with the matched value (or notes field) in the left side, and a boolean indicating whether
         *         the match was in an answer value ({@code false}) or in the notes ({@code true}), or {@code null} if
         *         the query text couldn't be found in the answer
         * @throws RepositoryException if accessing the repository fails
         */
        private Pair<String, Boolean> getMatch(final String query, final Node answer) throws RepositoryException
        {
            final Object answerValues = FormsQuickSearchEngine.this.formUtils.getValue(answer);
            String matchedValue = SearchUtils.getMatch(answerValues, query);

            // As a fallback for when the match isn't in the value field, attempt to use the note field
            boolean matchedNotes = false;
            if (matchedValue == null && answer.hasProperty("note")) {
                final String noteValue = answer.getProperty("note").getString();
                if (StringUtils.containsIgnoreCase(noteValue, query)) {
                    matchedValue = noteValue;
                    matchedNotes = true;
                }
            }
            return matchedValue == null ? null : Pair.of(matchedValue, matchedNotes);
        }
    }
}
