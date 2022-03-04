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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
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

    private static final String CONFIGURATION_NODE = "/apps/cards/config/CopyAnswers";

    private final ThreadLocal<Node> answersToCopy = ThreadLocal.withInitial(() -> null);


    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        // Is there a questionnaire specified?
        String questionnaire = request.getParameter("questionnaire");
        if (questionnaire == null) {
            response.setStatus(403);
            final Writer out = response.getWriter();
            out.write("Questionnaire path has to be specified in the request.");
            return;
        }

        // get configs for additional answers
        final Resource allConfigurations = request.getResourceResolver().getResource(CONFIGURATION_NODE);
        if (allConfigurations != null) {
            final String questionnaireName =
                    request.getResourceResolver().getResource(questionnaire).getName();
            final Resource configuration = allConfigurations.getChild(questionnaireName);
            if (configuration != null) {
                this.answersToCopy.set(configuration.adaptTo(Node.class));
            }
        }

        // return all fields by the given questionnaire
        JsonObject allProperties = getAllFields(request.getResourceResolver(), questionnaire);

        this.answersToCopy.remove();
        // Return the entire thing as a json file
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
        JsonObject questionnaire = resource.adaptTo(JsonObject.class);
        // Copy deep json of questionnaire
        builder.add(questionnaire.getString("@name"), questionnaire);
        // Copy answers from the corresponding configuration file, if any
        copyConfigAnswers(resolver, questionnairePath, builder);
        return builder.build();
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
            // Group questions by questionnaire
            Map<String, List<JsonObject>> result = new HashMap<>();
            Map<String, Resource> questionairesById = new HashMap<>();

            final Node configNode = this.answersToCopy.get();
            final PropertyIterator properties = configNode.getProperties();
            while (properties.hasNext()) {
                final Property property = properties.nextProperty();
                if (property.getType() != PropertyType.REFERENCE) {
                    continue;
                }
                final String path = property.getNode().getPath();
                // We are interested only in questions not from original questionnaire from request
                //   those were captured in previous step with deep json of original questionnaire
                if (!path.startsWith(questionnairePath)) {
                    Resource questionResource = resolver.resolve(path);
                    JsonObject question = questionResource.adaptTo(JsonObject.class);
                    Resource questionnaire = getQuestionnaire(questionResource);
                    String identifier = questionnaire.getValueMap().get("identifier", String.class);
                    List<JsonObject> answers = result.getOrDefault(identifier, new LinkedList<>());
                    answers.add(question);
                    result.put(identifier, answers);
                    questionairesById.put(identifier, questionnaire);
                }
            }

            // Now we can add all config questions by questionnaires
            for (String questionnaireId : result.keySet()) {
                JsonObjectBuilder qbuilder = Json.createObjectBuilder();

                JsonObject questionnaire = questionairesById.get(questionnaireId).adaptTo(JsonObject.class);
                qbuilder.add("jcr:primaryType", questionnaire.getString("jcr:primaryType"));
                qbuilder.add("title", questionnaire.getString("title"));

                // Add all questions
                for (JsonObject question : result.get(questionnaireId)) {
                    qbuilder.add(question.getString("@name"), question);
                }
                builder.add(questionnaire.getString("@name"), qbuilder.build());
            }

        } catch (RepositoryException e) {
            LOGGER.warn("Failed to look for the right answer to copy: {}", e.getMessage(), e);
        }
    }

    private Resource getQuestionnaire(final Resource questionResource)
    {
        Resource result = questionResource;
        while (result != null && !"cards/Questionnaire".equals(result.getResourceType())) {
            result = result.getParent();
        }
        return result;
    }
}
