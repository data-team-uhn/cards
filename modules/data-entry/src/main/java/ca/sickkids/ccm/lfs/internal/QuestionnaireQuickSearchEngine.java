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
package ca.sickkids.ccm.lfs.internal;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.json.JsonObject;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.sickkids.ccm.lfs.spi.QuickSearchEngine;
import ca.sickkids.ccm.lfs.spi.SearchUtils;

/**
 * Finds {@code [lfs:Questionnaire]}s with question or answer options matching the given full text search.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class QuestionnaireQuickSearchEngine implements QuickSearchEngine
{
    private static final List<String> SUPPORTED_TYPES = Collections.singletonList("lfs:Questionnaire");

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public List<String> getSupportedTypes()
    {
        return SUPPORTED_TYPES;
    }

    @Override
    public void quickSearch(final String query, final long maxResults, final boolean showTotalRows,
        final ResourceResolver resourceResolver, final List<JsonObject> output)
    {
        if (output.size() >= maxResults && !showTotalRows) {
            return;
        }

        final String xpathQuery = getXPathQuery(query);
        Iterator<Resource> foundResources = resourceResolver.findResources(xpathQuery.toString(), "xpath");

        while (foundResources.hasNext()) {
            // No need to go through all results list if we do not add total results number
            if (output.size() >= maxResults && !showTotalRows) {
                break;
            }
            Resource thisResource = foundResources.next();

            // Find the Questionnaire parent of this question
            Resource questionnaire = getQuestionnaire(thisResource);

            String matchedValue = null;

            String question = null;
            String path = "";
            if (thisResource.isResourceType("lfs/AnswerOption")) {
                // Found resource is of type [lfs:AnswerOption]
                String[] resourceValues = thisResource.getValueMap().get("value", String[].class);
                matchedValue = SearchUtils.getMatchFromArray(resourceValues, query);
                // Find the Question parent of this question
                Resource questionParent = getQuestion(thisResource);
                question = "Possible answer for question " + questionParent.getValueMap().get("text", String.class);
                path = questionParent.getPath();
            } else if (thisResource.isResourceType("lfs/Question")) {
                // Found resource is of type [lfs:Question]
                matchedValue = thisResource.getValueMap().get("text", String.class);
                question = "Question";
                path = thisResource.getPath();
            } else if (thisResource.isResourceType("lfs/Questionnaire")) {
                // Found resource is of type [lfs:Questionnaire]
                matchedValue = thisResource.getValueMap().get("title", String.class);
                question = "Questionnaire";
                path = thisResource.getPath();
            }

            if (matchedValue != null) {
                output.add(SearchUtils.addMatchMetadata(
                    matchedValue, query, question, questionnaire.adaptTo(JsonObject.class), false, path));
            }
        }
    }

    private String getXPathQuery(final String textQuery)
    {
        final String escapedQuery = SearchUtils.escapeLikeText(textQuery.toLowerCase());
        final StringBuilder xpathQuery = new StringBuilder();
        xpathQuery.append("/jcr:root/Questionnaires//*[jcr:like(fn:lower-case(@value),'%");
        xpathQuery.append(escapedQuery);
        xpathQuery.append("%') or jcr:like(fn:lower-case(@text),'%");
        xpathQuery.append(escapedQuery);
        xpathQuery.append("%') or jcr:like(fn:lower-case(@title),'%");
        xpathQuery.append(escapedQuery);
        xpathQuery.append("%')]");

        return xpathQuery.toString();
    }

    private Resource getQuestion(final Resource answerValue)
    {
        Resource result = answerValue;
        while (result != null && !"lfs/Question".equals(result.getResourceType())) {
            result = result.getParent();
        }
        return result;
    }

    private Resource getQuestionnaire(final Resource matchedNode)
    {
        Resource result = matchedNode;
        while (result != null && !"lfs/Questionnaire".equals(result.getResourceType())) {
            result = result.getParent();
        }
        return result;
    }
}
