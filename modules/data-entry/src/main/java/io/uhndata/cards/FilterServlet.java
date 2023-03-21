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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.query.Query;
import javax.json.Json;
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
 * <li><code>questionnaire</code>: a path to a questionnaire whose filterable options to retrieve</li>
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

    private static final String JCR_UUID = "jcr:uuid";

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        // Is there a questionnaire specified?
        String questionnaire = request.getParameter("questionnaire");
        String homepagePath = request.getResource().getPath();

        // If a questionnaire is specified, return all fields by the given questionnaire
        // Otherwise, we return all questionnaires under this node that are visible by the user
        JsonObject allProperties = questionnaire == null
            ? getAllFieldsFromAllQuestionnaires(request.getResourceResolver(), homepagePath)
            : getAllFields(request.getResourceResolver(), questionnaire);

        // Return the entire thing as a json file, except join together fields that have the same
        // name and type
        final Writer out = response.getWriter();
        out.write(allProperties.toString());
    }

    /**
     * Create a JsonObject of all filterable fields from the given questionnaire.
     *
     * @param resolver a reference to a ResourceResolver
     * @param questionnairePath the path to the questionnaire to look up
     * @return a JsonObject of filterable fields
     */
    private JsonObject getAllFields(ResourceResolver resolver, String questionnairePath)
    {
        // First, ensure that we're accessing the deep jsonification of the questionnaire
        String fullPath = questionnairePath.endsWith(DEEP_JSON_SUFFIX)
            ? questionnairePath : questionnairePath.concat(DEEP_JSON_SUFFIX);

        // Next, convert it to a deep json object
        final Resource resource = resolver.resolve(fullPath);

        // JsonObjects are immutable, so we have to manually copy over non-questions to a new object
        JsonObjectBuilder builder = Json.createObjectBuilder();
        this.copyQuestions(resource.adaptTo(JsonObject.class), builder);
        return builder.build();
    }

    /**
     * Creates a JsonObject of all filterable fields from every questionnaire under
     * the given QuestionnaireHomepage node.
     *
     * @param resolver a reference to a ResourceResolver
     * @param parentPath the path of the parent QuestionnaireHomepage
     * @return a JsonObject of filterable fields
     */
    private JsonObject getAllFieldsFromAllQuestionnaires(ResourceResolver resolver, String parentPath)
    {
        final StringBuilder query =
            // We select all child nodes of the homepage, filtering out nodes that aren't ours, such as rep:policy
            new StringBuilder("select n from [cards:Questionnaire] as n where isdescendantnode(n, '"
                + parentPath + "')");
        final Iterator<Resource> results =
            resolver.findResources(query.toString(), Query.JCR_SQL2);

        // Generate the output via recursively adding all fields from each questionnaire.
        JsonObjectBuilder builder = Json.createObjectBuilder();
        Map<String, String> seenTypes = new HashMap<String, String>();
        Map<String, String> seenElements = new HashMap<String, String>();
        while (results.hasNext()) {
            Resource resource = results.next();
            String path = resource.getResourceMetadata().getResolutionPath();
            resource = resolver.resolve(path.concat(DEEP_JSON_SUFFIX));
            JsonObject resourceJson = resource.adaptTo(JsonObject.class);
            this.copyQuestions(resourceJson, getTitle(resourceJson), builder, seenTypes, seenElements);
        }
        return builder.build();
    }

    /**
     * Copies over cards:Question fields from the input JsonObject, optionally handling questions that
     * may already exist in the builder.
     *
     * @param questions A JsonObject (from an cards:Questionnaire or cards:Section) whose fields may be cards:Questions
     * @param builder A JsonObjectBuilder to copy results to
     * @param seenTypes Either a map from field names to dataTypes, or null to disable tracking
     * @param seenElements Either a map from field names to jcr:uuids, or null to disable tracking
     * @return the content matching the query
     */
    private void copyQuestions(JsonObject questions, String questionnaireTitle, JsonObjectBuilder builder,
            Map<String, String> seenTypes, Map<String, String> seenElements)
    {
        // Copy over the keys
        for (String key : questions.keySet()) {
            // Skip over non-questions (non-objects)
            if (questions.get(key).getValueType() != ValueType.OBJECT) {
                continue;
            }
            JsonObject datum = questions.getJsonObject(key);

            // Copy over information from children of sections
            if ("cards:Section".equals(datum.getString("jcr:primaryType"))) {
                this.copyQuestions(datum, questionnaireTitle, builder, seenTypes, seenElements);
            }

            // Copy over information from this object if this is a question
            if ("cards:Question".equals(datum.getString("jcr:primaryType"))) {
                JsonObject amendedDatum = amendWithQuestionnaireTitle(datum, questionnaireTitle);
                copyFields(amendedDatum, key, builder, seenTypes, seenElements);
            }
        }
    }

    /**
     * Copies over every field from the input JsonObject, optionally handling questions that
     * may already exist in the builder.
     *
     * @param question A JsonObject from a cards:Question resource
     * @param key The name of the cards:Question
     * @param builder A JsonObjectBuilder to copy results to
     * @param seenTypes Either a map from field names to dataTypes, or null to disable tracking
     * @param seenElements Either a map from field names to jcr:uuids, or null to disable tracking
     */
    private void copyFields(JsonObject question, String key, JsonObjectBuilder builder, Map<String, String> seenTypes,
            Map<String, String> seenElements)
    {
        if (seenTypes == null) {
            // No map to keep track of dataTypes provided: add blindly to the builder
            builder.add(key, question);
            return;
        }

        if (seenTypes.containsKey(key)) {
            // If this question already exists, make sure that it has the same dataType
            String questionType = question.getString("dataType");
            if (seenTypes.get(key) != questionType) {
                // DIFFERENT -- prepend a slightly differently named version
                String newKey = questionType.concat("|").concat(key);
                seenTypes.put(newKey, questionType);
                seenElements.put(newKey, question.getString(JCR_UUID));
                builder.add(newKey, question);
            } else {
                // SAME -- append our jcr:uuid to the question
                // JsonObjects are immutable, so we need to copy and overwrite the UUID
                JsonObjectBuilder amended = Json.createObjectBuilder();
                for (String amendKey : question.keySet()) {
                    if (amendKey.equals(JCR_UUID)) {
                        // Append our jcr:uuid to the existing one
                        String newUUID = String.format(
                            "%s,%s",
                            seenElements.get(key),
                            question.getString(JCR_UUID)
                        );
                        amended.add(JCR_UUID, newUUID);
                        seenElements.put(key, newUUID);
                    } else {
                        amended.add(amendKey, question.get(amendKey));
                    }
                }
                builder.add(key, amended.build());
            }
        } else {
            // If this question does not exist, just add it
            seenTypes.put(key, question.getString("dataType"));
            seenElements.put(key, question.getString(JCR_UUID));
            builder.add(key, question);
        }
    }

    /**
     * Copies over cards:Question fields from the input JsonObject.
     *
     * @param questions A JsonObject (from an cards:Questionnaire) whose fields may be cards:Questions
     * @param builder A JsonObjectBuilder to copy results to
     */
    private void copyQuestions(final JsonObject questions, final JsonObjectBuilder builder)
    {
        this.copyQuestions(questions, getTitle(questions), builder, null, null);
    }

    /**
     * Retrieves the title of a questionnaire.
     *
     * @param resourceJson The Questionnaire resource, as JsonObject
     * @return the title field, or the name field if title is missing
     */
    private String getTitle(final JsonObject resourceJson)
    {
        String result = resourceJson.getString("title", "");
        if (StringUtils.isBlank(result)) {
            result = resourceJson.getString("@name", "");
        }
        return result;
    }

    /**
     * Adds a questionnaireTitle field to a question JsonObject.
     *
     * @param question The question as a JsonObject
     * @param title The title of the questionnaire the question belongs to
     * @return a new JsonObject with all the fields of question, plus the questionnaireTitle field
     */
    private JsonObject amendWithQuestionnaireTitle(final JsonObject question, final String title)
    {
        JsonObjectBuilder amended = Json.createObjectBuilder();
        // Copy over all the fields
        for (String key : question.keySet()) {
            amended.add(key, question.get(key));
        }
        // Add the questionnaire title
        amended.add("questionnaireTitle", title);
        // Build and return
        return amended.build();
    }
}
