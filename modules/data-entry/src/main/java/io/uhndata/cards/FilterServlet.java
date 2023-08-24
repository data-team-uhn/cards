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
package io.uhndata.cards;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue.ValueType;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;

/**
 * A servlet that lists the filters applicable to the given questionnaire, or all questionnaires visible by the user.
 * <p>
 * This servlet supports the following parameters:
 * </p>
 * <ul>
 * <li><code>questionnaire</code>: a path to a questionnaire whose filterable options to retrieve; if absent, retrieve
 * all filterable options from all questionnaires</li>
 * <li><code>include</code>: a parameter include that allows to specify which kinds of filters to retrieve. By default,
 * both metadata filters and question filters are retrieved;
 * <code>include=metadata</code> returns only metadata filters,
 * <code>include=questions</code> returns only question filters</li>
 * </ul>
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Questionnaire", "cards/QuestionnairesHomepage" },
    selectors = { "filters" })
public class FilterServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = 2558430802619674046L;

    private static final String DEEP_JSON_SUFFIX = ".deep.json";

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Is there a questionnaire specified?
        String questionnaire = request.getResource().isResourceType("cards/Questionnaire")
            ? request.getResource().getPath() : request.getParameter("questionnaire");
        String include = request.getParameter("include");
        boolean includeMetadata = StringUtils.isBlank(include) || "metadata".equals(include);
        boolean includeQuestions = StringUtils.isBlank(include) || "questions".equals(include);

        // JsonObjects are immutable, so we have to manually copy over non-questions to a new object
        JsonObjectBuilder builder = Json.createObjectBuilder();

        // Generate the metadata filters
        if (includeMetadata) {
            builder.add("metadataFilters", getMetadataFilters(questionnaire == null));
        }

        if (includeQuestions) {
            ResourceResolver resolver = request.getResourceResolver();

            // If a questionnaire is specified, return all fields by the given questionnaire
            // Otherwise, we return all questionnaires under this node that are visible by the user
            if (questionnaire != null) {
                addQuestionsFromQuestionnaire(resolver, questionnaire, builder);
            } else {
                String homepagePath = request.getResource().getPath();
                addAllQuestions(resolver, homepagePath, builder);
            }
        }

        // Return the entire thing as a json file
        final Writer out = response.getWriter();
        out.write(builder.build().toString());
    }

    /**
     * Builds the metadata filters associated with questionnaire resources.
     *
     * @param includeQuestionnaireFilter a flag whether filtering by Questionnaire should be enabled
     * @return a the filters definitions in a JsonArrayBuilder
     */
    private JsonArrayBuilder getMetadataFilters(final boolean includeQuestionnaireFilter)
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        if (includeQuestionnaireFilter) {
            builder.add(getMetadataFilter("Questionnaire"));
        }
        builder.add(getMetadataFilter("Subject"));
        builder.add(getMetadataFilter("Created Date"));
        return builder;
    }

    /**
     * Builds the definition for a metadata filter associated with questionnaire resources.
     *
     * @param label the label of the metadata filter to generate the definition for
     * @return the filter definition in a JsonObjectBuilder
     */
    private JsonObjectBuilder getMetadataFilter(final String label)
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        final String path = label.replace(" ", "");
        builder.add("@path", path);
        builder.add("jcr:uuid", "cards:" + path);
        builder.add("text", label);
        builder.add("dataType", path.toLowerCase());
        return builder;
    }

    /**
     * Adds all filterable fields from the given questionnaire to the JsonObjectBuilder.
     *
     * @param resolver a reference to a ResourceResolver
     * @param questionnairePath the path to the questionnaire to look up
     * @param builder a builder for creating JsonObject models from scratch
     */
    private void addQuestionsFromQuestionnaire(ResourceResolver resolver, String questionnairePath,
        JsonObjectBuilder builder)
    {
        // First, ensure that we're accessing the deep jsonification of the questionnaire
        String fullPath = questionnairePath.endsWith(DEEP_JSON_SUFFIX)
            ? questionnairePath : questionnairePath.concat(DEEP_JSON_SUFFIX);

        // Next, convert it to a deep json object
        final Resource resource = resolver.resolve(fullPath);
        JsonObject resourceJson = resource.adaptTo(JsonObject.class);
        copyQuestionnaire(resourceJson, builder);
    }

    /**
     * Adds all filterable fields from every questionnaire under the given QuestionnaireHomepage node.
     *
     * @param resolver a reference to a ResourceResolver
     * @param parentPath the path of the parent QuestionnaireHomepage
     * @param builder a builder for creating JsonObject models from scratch
     */
    private void addAllQuestions(ResourceResolver resolver, String parentPath, JsonObjectBuilder builder)
    {
        final StringBuilder query =
            // We select all child nodes of the homepage, filtering out nodes that aren't ours, such as rep:policy
            new StringBuilder("select n from [cards:Questionnaire] as n where isdescendantnode(n, '"
                + parentPath + "')");
        final Iterator<Resource> results = resolver.findResources(query.toString(), Query.JCR_SQL2);

        while (results.hasNext()) {
            Resource resource = results.next();
            String path = resource.getResourceMetadata().getResolutionPath().concat(DEEP_JSON_SUFFIX);
            JsonObject resourceJson = resolver.resolve(path).adaptTo(JsonObject.class);
            JsonObjectBuilder questionnaireBuilder = Json.createObjectBuilder();
            copyQuestionnaire(resourceJson, questionnaireBuilder);
            builder.add(resourceJson.getString("title"), questionnaireBuilder);
        }
    }

    /**
     * Copies the questionnaire definition and all questions as a flat array into a JsonObjectBuilder.
     *
     * @param resourceJson A JsonObject representing the definition of the questionnaire to copy
     * @param builder A JsonObjectBuilder where the questionnaire metadata and its questions will be copied
     */
    private void copyQuestionnaire(JsonObject resourceJson, JsonObjectBuilder builder)
    {
        List<String> ancestorSectionLabels = new ArrayList<>();
        JsonArrayBuilder questionsBuilder = Json.createArrayBuilder();

        for (String key : resourceJson.keySet()) {
            if (resourceJson.get(key).getValueType() != ValueType.OBJECT) {
                // Copy over the non-object keys
                builder.add(key, resourceJson.get(key));
            }
        }

        copyQuestions(resourceJson, questionsBuilder, ancestorSectionLabels);
        builder.add("questions", questionsBuilder);
    }

    /**
     * Accumulates cards:Question fields from the input JsonObject.
     *
     * @param datum A JsonObject (from an cards:Questionnaire or cards:Section) whose fields may be cards:Questions
     * @param builder A JsonArrayBuilder where the questions are accumulated
     * @param ancestorSectionLabels A List of Strings containing the labels of all sections that are ancestors of this
     *     resource according to the parent questionnaire structure
     */
    private void copyQuestions(JsonObject datum, JsonArrayBuilder builder, List<String> ancestorSectionLabels)
    {
        for (String key : datum.keySet()) {
            if (datum.get(key).getValueType() == ValueType.OBJECT) {
                JsonObject object = datum.getJsonObject(key);
                // Copy over information from children of sections
                if ("cards:Section".equals(object.getString("jcr:primaryType"))) {
                    List<String> newAncestorSectionLabels = new ArrayList<>(ancestorSectionLabels);
                    if (object.get("label") != null) {
                        newAncestorSectionLabels.add(object.getString("label"));
                    }
                    this.copyQuestions(object, builder, newAncestorSectionLabels);
                }

                // Copy over information from this object if this is a question
                if ("cards:Question".equals(object.getString("jcr:primaryType"))) {
                    builder.add(amendWithSectionBreadcrumbs(object, ancestorSectionLabels));
                }
            }
        }
    }

    /**
     * Adds a sectionBreadcrumbs field to a question JsonObject.
     *
     * @param question The question as a JsonObject
     * @param ancestorSectionLabels A List of Strings containing the labels of all sections that are ancestors of this
     *     question according to the parent questionnaire structure
     * @return a JsonObjectBuilder with all the fields of question, plus the list of ancestor section labels
     */
    private JsonObjectBuilder amendWithSectionBreadcrumbs(final JsonObject question,
        final List<String> ancestorSectionLabels)
    {
        JsonObjectBuilder amended = Json.createObjectBuilder();
        // Copy over all the fields
        for (String key : question.keySet()) {
            amended.add(key, question.get(key));
        }
        // Add the labels of any ancestor sections
        JsonArrayBuilder ancestorsBuilder = Json.createArrayBuilder();
        for (String label : ancestorSectionLabels) {
            ancestorsBuilder.add(label);
        }
        amended.add("sectionBreadcrumbs", ancestorsBuilder);
        return amended;
    }
}
