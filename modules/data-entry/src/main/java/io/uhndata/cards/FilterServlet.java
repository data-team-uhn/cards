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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue.ValueType;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterServlet.class);

    private static final long serialVersionUID = 2558430802619674046L;

    private static final String DEEP_JSON_SUFFIX = ".deep.json";

    private static final String JCR_UUID = "jcr:uuid";

    private static final String CONFIGURATION_NODE = "/apps/cards/config/CopyAnswers";

    private final ThreadLocal<List<Node>> answersToCopy = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        // Is there a questionnaire specified?
        String questionnaire = request.getParameter("questionnaire");
        String homepagePath = request.getResource().getPath();

        // get configs for additional answers
        final Resource allConfigurations = request.getResourceResolver().getResource(CONFIGURATION_NODE);
        if (allConfigurations != null) {
            final Iterator<Resource> configurations = allConfigurations.getChildren().iterator();
            while (configurations.hasNext()) {
                this.answersToCopy.get().add(configurations.next().adaptTo(Node.class));
            }
        }

        // If a questionnaire is specified, return all fields by the given questionnaire
        // Otherwise, we return all questionnaires under this node that are visible by the user
        JsonObject allProperties = questionnaire == null
            ? getAllFieldsFromAllQuestionnaires(request.getResourceResolver(), homepagePath)
            : getAllFields(request.getResourceResolver(), questionnaire);

        this.answersToCopy.remove();
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
        this.copyConfigAnswers(resolver, questionnairePath, builder);
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
                + parentPath + "') and n.'sling:resourceSuperType' = 'cards/Resource'");
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
            this.copyQuestions(resource.adaptTo(JsonObject.class), builder, seenTypes, seenElements);
            this.copyConfigAnswers(resolver, path, builder);
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
    private void copyQuestions(JsonObject questions, JsonObjectBuilder builder, Map<String, String> seenTypes,
            Map<String, String> seenElements)
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
                this.copyQuestions(datum, builder, seenTypes, seenElements);
            }

            // Copy over information from this object if this is a question
            if ("cards:Question".equals(datum.getString("jcr:primaryType"))) {
                copyFields(datum, key, builder, seenTypes, seenElements);
            }
        }
    }

    /**
     * Copies over every field from the input JsonObject, optionally handling questions that
     * may already exist in the builder.
     *
     * @param question A JsonObject from an cards:Question
     * @param key The name of the cards:Question
     * @param builder A JsonObjectBuilder to copy results to
     * @param seenTypes Either a map from field names to dataTypes, or null to disable tracking
     * @param seenElements Either a map from field names to jcr:uuids, or null to disable tracking
     * @return the content matching the query
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
     * @return the content matching the query
     */
    private void copyQuestions(JsonObject questions, JsonObjectBuilder builder)
    {
        this.copyQuestions(questions, builder, null, null);
    }

    /**
     * Copies over fields from another questionnaires configured to be added to the questionnaire JSON form.
     *
     * @param resolver a reference to a ResourceResolver
     * @param questionnairePath the path to the questionnaire to compare
     * @param builder A JsonObjectBuilder to copy results to
     */
    private void copyConfigAnswers(ResourceResolver resolver, String questionnairePath, JsonObjectBuilder builder)
    {
        if (this.answersToCopy.get() == null) {
            return;
        }
        try {
            for (Node configNode : this.answersToCopy.get()) {
                final PropertyIterator properties = configNode.getProperties();
                while (properties.hasNext()) {
                    final Property property = properties.nextProperty();
                    if (property.getType() != PropertyType.REFERENCE) {
                        continue;
                    }
                    final String path = property.getNode().getPath();
                    if (!path.startsWith(questionnairePath)) {
                        JsonObject question = resolver.resolve(path).adaptTo(JsonObject.class);
                        builder.add(question.getString("@name"), question);
                    }
                }
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to look for the right answer to copy: {}", e.getMessage(), e);
        }
    }
}
