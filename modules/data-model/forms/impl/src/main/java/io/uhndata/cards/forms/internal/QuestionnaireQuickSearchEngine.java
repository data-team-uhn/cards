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
import java.util.Iterator;
import java.util.List;

import javax.json.JsonObject;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.spi.QuickSearchEngine;
import io.uhndata.cards.spi.SearchParameters;
import io.uhndata.cards.spi.SearchUtils;

/**
 * Finds {@code [cards:Questionnaire]}s with question or answer options matching the given full text search.
 * <p>
 * FIXME This should be split into Questionnaire and Question engines.
 * </p>
 *
 * @version $Id$
 */
@Component(immediate = true)
public class QuestionnaireQuickSearchEngine implements QuickSearchEngine
{
    private static final List<String> SUPPORTED_TYPES = Collections.singletonList("cards:Questionnaire");

    @Override
    public List<String> getSupportedTypes()
    {
        return SUPPORTED_TYPES;
    }

    @Override
    public QuickSearchEngine.Results quickSearch(final SearchParameters query, final ResourceResolver resourceResolver)
    {
        final String xpathQuery = getXPathQuery(query.getQuery());
        final Iterator<Resource> foundResources = resourceResolver.findResources(xpathQuery.toString(), "xpath");
        return new QuestionnaireResults(query.getQuery(), foundResources);
    }

    private String getXPathQuery(final String textQuery)
    {
        final String escapedQuery = SearchUtils.escapeLikeText(textQuery.toLowerCase());
        final StringBuilder xpathQuery = new StringBuilder();
        xpathQuery.append("/jcr:root/Questionnaires//*[")
            .append("(@jcr:primaryType = 'cards:Questionnaire' or @jcr:primaryType = 'cards:Question' ")
            .append("or @jcr:primaryType = 'cards:AnswerOption') and ")
            .append("(jcr:like(fn:lower-case(@value),'%")
            .append(escapedQuery)
            .append("%') or jcr:like(fn:lower-case(@text),'%")
            .append(escapedQuery)
            .append("%') or jcr:like(fn:lower-case(@title),'%")
            .append(escapedQuery)
            .append("%'))]");

        return xpathQuery.toString();
    }

    private final class QuestionnaireResults implements QuickSearchEngine.Results
    {
        private final String query;

        private final Iterator<Resource> queryResults;

        QuestionnaireResults(final String query, final Iterator<Resource> queryResults)
        {
            this.query = query;
            this.queryResults = queryResults;
        }

        @Override
        public boolean hasNext()
        {
            return this.queryResults.hasNext();
        }

        @Override
        public void skip()
        {
            this.queryResults.next();
        }

        @Override
        public JsonObject next()
        {
            final Resource res = this.queryResults.next();
            // Find the Questionnaire parent of this question
            final Resource questionnaire = getQuestionnaire(res);

            String matchedValue = null;

            String question = null;
            String path = "";
            if (res.isResourceType("cards/AnswerOption")) {
                // Find the Question parent of this question
                final Resource questionParent = getQuestion(res);
                if (questionParent != null) {
                    // Found resource is of type [cards:AnswerOption]
                    final String[] resourceValues = res.getValueMap().get("value", String[].class);
                    matchedValue = SearchUtils.getMatchFromArray(resourceValues, this.query);
                    question = "Possible answer for question " + questionParent.getValueMap().get("text", String.class);
                    path = questionParent.getPath();
                }
            } else if (res.isResourceType("cards/Question")) {
                // Found resource is of type [cards:Question]
                matchedValue = res.getValueMap().get("text", String.class);
                question = "Question";
                path = res.getPath();
            } else if (res.isResourceType("cards/Questionnaire")) {
                // Found resource is of type [cards:Questionnaire]
                matchedValue = res.getValueMap().get("title", String.class);
                question = "Questionnaire name";
                path = res.getPath();
            }

            if (matchedValue != null) {
                return SearchUtils.addMatchMetadata(
                    matchedValue, this.query, question, questionnaire.adaptTo(JsonObject.class), false,
                    path);
            }
            return JsonValue.EMPTY_JSON_OBJECT;
        }

        private Resource getQuestion(final Resource answerValue)
        {
            Resource result = answerValue;
            while (result != null && !"cards/Question".equals(result.getResourceType())) {
                result = result.getParent();
            }
            return result;
        }

        private Resource getQuestionnaire(final Resource matchedNode)
        {
            Resource result = matchedNode;
            while (result != null && !"cards/Questionnaire".equals(result.getResourceType())) {
                result = result.getParent();
            }
            return result;
        }
    }
}
