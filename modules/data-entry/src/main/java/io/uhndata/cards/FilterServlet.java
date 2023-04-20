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
import java.util.Iterator;

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
        // Is there a questionnaire specified?
        String questionnaire = request.getParameter("questionnaire");
        String include = request.getParameter("include");

        String homepagePath = request.getResource().getPath();

        // If a questionnaire is specified, return all fields by the given questionnaire
        // Otherwise, we return all questionnaires under this node that are visible by the user
        JsonObject allProperties = questionnaire == null
            ? getAllFieldsFromAllQuestionnaires(request.getResourceResolver(), homepagePath, include)
            : getAllFields(request.getResourceResolver(), homepagePath + "/" + questionnaire, include);

        // Return the entire thing as a json file, except join together fields that have the same name and type
        final Writer out = response.getWriter();
        out.write(allProperties.toString());
    }

    /**
     * Create a JsonObject of all filterable fields from the given questionnaire.
     *
     * @param resolver a reference to a ResourceResolver
     * @param questionnairePath the path to the questionnaire to look up
     * @param include a parameter that allows to specify which kinds of filters to retrieve
     * @return a JsonObject of filterable fields
     */
    private JsonObject getAllFields(ResourceResolver resolver, String questionnairePath, String include)
    {
        // First, ensure that we're accessing the deep jsonification of the questionnaire
        String fullPath = questionnairePath.endsWith(DEEP_JSON_SUFFIX)
            ? questionnairePath : questionnairePath.concat(DEEP_JSON_SUFFIX);

        // JsonObjects are immutable, so we have to manually copy over non-questions to a new object
        JsonObjectBuilder builder = Json.createObjectBuilder();

        // Generate the metadata filters
        if (StringUtils.isBlank(include) || "metadata".equals(include)) {
            builder.add("metadataFilters", this.addMetadataFilters(false));
        }

        if (StringUtils.isBlank(include) || "questions".equals(include)) {
            // Next, convert it to a deep json object
            final Resource resource = resolver.resolve(fullPath);
            JsonObject resourceJson = resource.adaptTo(JsonObject.class);
            this.copyQuestionnaire(resourceJson, builder);
        }
        return builder.build();
    }

    /**
     * Creates a JsonObject of all filterable fields from every questionnaire under
     * the given QuestionnaireHomepage node.
     *
     * @param resolver a reference to a ResourceResolver
     * @param parentPath the path of the parent QuestionnaireHomepage
     * @param include a parameter that allows to specify which kinds of filters to retrieve
     * @return a JsonObject of filterable fields
     */
    private JsonObject getAllFieldsFromAllQuestionnaires(ResourceResolver resolver, String parentPath, String include)
    {
        final StringBuilder query =
            // We select all child nodes of the homepage, filtering out nodes that aren't ours, such as rep:policy
            new StringBuilder("select n from [cards:Questionnaire] as n where isdescendantnode(n, '"
                + parentPath + "')");
        final Iterator<Resource> results = resolver.findResources(query.toString(), Query.JCR_SQL2);

        // Generate the output via recursively adding all fields from each questionnaire.
        JsonObjectBuilder builder = Json.createObjectBuilder();

        // Generate the metadata filters
        if (StringUtils.isBlank(include) || "metadata".equals(include)) {
            builder.add("metadataFilters", this.addMetadataFilters(true));
        }

        if (StringUtils.isBlank(include) || "questions".equals(include)) {
            while (results.hasNext()) {
                Resource resource = results.next();
                String path = resource.getResourceMetadata().getResolutionPath();
                resource = resolver.resolve(path.concat(DEEP_JSON_SUFFIX));
                JsonObjectBuilder questionnaire = Json.createObjectBuilder();
                JsonObject resourceJson = resource.adaptTo(JsonObject.class);
                this.copyQuestionnaire(resourceJson, questionnaire);
                builder.add(resourceJson.getString("@name"), questionnaire);
            }
        }
        return builder.build();
    }

    private JsonArrayBuilder addMetadataFilters(final boolean addQuestionnaireFilter)
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        if (addQuestionnaireFilter) {
            builder.add(generateMetadataFilter("Questionnaire"));
        }
        builder.add(generateMetadataFilter("Subject"));
        builder.add(generateMetadataFilter("Created Date"));
        return builder;
    }

    private JsonObjectBuilder generateMetadataFilter(final String text)
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        final String path = text.replace(" ", "");
        builder.add("@path", path);
        builder.add("jcr:uuid", "cards:" + path);
        builder.add("text", text);
        builder.add("dataType", path.toLowerCase());
        return builder;
    }

    private void copyQuestionnaire(JsonObject resourceJson, JsonObjectBuilder questionnaire)
    {
        JsonArrayBuilder sectionBreadcrumbs = Json.createArrayBuilder();
        JsonArrayBuilder questions = Json.createArrayBuilder();

        for (String key : resourceJson.keySet()) {
            if (resourceJson.get(key).getValueType() != ValueType.OBJECT) {
                // Copy over the non-object keys
                questionnaire.add(key, resourceJson.get(key));
            }
        }

        this.copyQuestions(resourceJson, questions, sectionBreadcrumbs);
        questionnaire.add("questions", questions);
    }

    /**
     * Accumulates cards:Question fields from the input JsonObject, optionally handling questions that
     * may already exist in the builder.
     *
     * @param datum A JsonObject (from an cards:Questionnaire or cards:Section) whose fields may be cards:Questions
     * @param questions questions
     * @param sectionBreadcrumbs sectionBreadcrumbs
     * @return the content matching the query
     */
    private void copyQuestions(JsonObject datum, JsonArrayBuilder questions, JsonArrayBuilder sectionBreadcrumbs)
    {
        for (String key : datum.keySet()) {
            if (datum.get(key).getValueType() == ValueType.OBJECT) {
                JsonObject object = datum.getJsonObject(key);
                // Copy over information from children of sections
                if ("cards:Section".equals(object.getString("jcr:primaryType"))) {
                    if (object.get("label") != null) {
                        sectionBreadcrumbs.add(object.get("label"));
                    }
                    this.copyQuestions(object, questions, sectionBreadcrumbs);
                }

                // Copy over information from this object if this is a question
                if ("cards:Question".equals(object.getString("jcr:primaryType"))) {
                    JsonObject amendedDatum = amendWithSectionBreadcrumbs(object, sectionBreadcrumbs);
                    questions.add(amendedDatum);
                }
            }
        }
    }

    /**
     * Adds a sectionBreadcrumbs field to a question JsonObject.
     *
     * @param question The question as a JsonObject
     * @param sectionBreadcrumbs sectionBreadcrumbs
     * @return a new JsonObject with all the fields of question, plus the questionnaireTitle field
     */
    private JsonObject amendWithSectionBreadcrumbs(final JsonObject question, final JsonArrayBuilder sectionBreadcrumbs)
    {
        JsonObjectBuilder amended = Json.createObjectBuilder();
        // Copy over all the fields
        for (String key : question.keySet()) {
            amended.add(key, question.get(key));
        }
        // Add the questionnaire title
        amended.add("sectionBreadcrumbs", sectionBreadcrumbs);
        // Build and return
        return amended.build();
    }
}
