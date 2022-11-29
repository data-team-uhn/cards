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
package io.uhndata.cards.proms.internal.serialize;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Serializes a Form in a format that can be sent to Epic for storage. as a Questionnaire Response.
 * Also flattens a form so that all the answers, regardless of any sections and subsections they may normally be in,
 * are listed in the top level of the JSON. The name of this processor is {@code toEpic}. It is incompatible
 * with the {@code bare} processor.
 *
 * Responses can only be submitted for questionnaires due in the future.
 *
 * Responses can only be submitted for questionnaires that have not been completed yet.
 * We CAN submit questionnaires with an “in-progress” status, but we will need to store the partial responses
 * because we cannot get them back from Epic.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class ToEpicFormProcessor implements ResourceJsonProcessor
{
    private static final String PRIMARY_TYPE_PROP = "jcr:primaryType";

    private static final String VALUE = "value";

    private static final Logger LOGGER = LoggerFactory.getLogger(ToEpicFormProcessor.class);

    @Reference
    private ResourceResolverFactory rrf;

    /**
     * Saves all JSONs of nodes with type {@code cards:Answer}. This maps from the node's name to the JSON. A linked map
     * preserves the order of the answers, as encountered in a top-to-bottom DFS pass through the form.
     */
    private ThreadLocal<Map<String, JsonObject>> childrenJsons = ThreadLocal.withInitial(LinkedHashMap::new);

    @Override
    public String getName()
    {
        return "toEpic";
    }

    @Override
    public int getPriority()
    {
        // This should run last, after the JSON is finalized, since it prevents all following processors from seeing the
        // JSON
        return 100;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        return resource.isResourceType("cards/Form");
    }

    @Override
    public JsonValue processChild(Node node, Node child, JsonValue input, Function<Node, JsonValue> serializeNode)
    {
        // This removes the serialization of answers and sections from their original place, storing the serialization
        // of answers to be added later in the root of the JSON.
        if (input == null) {
            // No serialization coming from other processors, nothing to do
            return null;
        }
        try {
            if (child.isNodeType("cards:Answer")) {
                JsonObject answer = input.asJsonObject();
                final JsonObjectBuilder answersObj = Json.createObjectBuilder();
                /*
                 {
                  "linkId": "<ID of the Question ID Type>|<ID of the question>",
                  "text": "Pain",
                  "answer": [
                    {
                      "valueString": "5"
                    }
                  ]
                }
                */

                // The `linkIds` for questions are not the same ids that are returned in a QuestionnaireResponse.Read
                // request. Here we need the ID of the Question ID Type and the ID of the question. These are custom
                // ids that need to manually be added to the questions when building the surveys in hyperspace.
                //answersObj.add("linkId", "<ID of the Question ID Type>|<ID of the question>");
                JsonObject question = answer.getJsonObject("question");
                String questionId = question.values().stream()
                    .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
                    .map(JsonValue::asJsonObject)
                    .filter(value -> value.containsKey(PRIMARY_TYPE_PROP)
                        && "cards:ExternalLink".equals(value.getString(PRIMARY_TYPE_PROP))
                        && "epic".equals(value.getString("type")))
                    .map(value -> value.getString(VALUE))
                    .findFirst().orElse("");
                answersObj.add("linkId", "<ID of the Question ID Type>|" + questionId);
                answersObj.add("text", question.getString("text", ""));
                final JsonArrayBuilder answers = Json.createArrayBuilder();

                JsonValue value = answer.get("displayedValue");
                if (value == null) {
                    value = answer.get(VALUE);
                }
                if (value == null) {
                    return null;
                }

                final String nodeType = answer.getString(PRIMARY_TYPE_PROP);
                if (ValueType.ARRAY.equals(value.getValueType())) {
                    value.asJsonArray().stream()
                        .forEach(entry -> answers.add(Json.createObjectBuilder()
                                                              .add("valueString", getStringValue(entry, nodeType))
                                                              .build()));
                } else {
                    answers.add(Json.createObjectBuilder()
                                        .add("valueString", getStringValue(value, nodeType))
                                        .build());
                }

                answersObj.add("answer", answers.build());
                this.childrenJsons.get().put(child.getName(), answersObj.build());

                return null;
            } else if (child.isNodeType("cards:AnswerSection")) {
                // Answer sections are completely discarded
                return null;
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
        // All other nodes (except Answer and AnswerSection) are left as is
        return input;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("cards:Form")) {
                final JsonObjectBuilder result = Json.createObjectBuilder();
                result.add("resourceType", "QuestionnaireResponse");
                // The `questionnaire` field obtained from the `instantiatesCanonical` field in the CarePlan response.
                // json.add("questionnaire", "Questionnaire/<FHIR ID>");
                final Node questionnaire = node.getProperty("questionnaire").getNode();
                // Set default to the questionnaire uuid
                String questionnaireId = questionnaire.getIdentifier();
                for (NodeIterator i = questionnaire.getNodes(); i.hasNext();) {
                    Node child = i.nextNode();
                    if ("cards:ExternalLink".equals(child.getPrimaryNodeType().getName())
                        && "epic".equals(child.getProperty("type").getString())) {
                        questionnaireId = child.getProperty(VALUE).getString();
                    }
                }
                result.add("questionnaire", "Questionnaire/" + questionnaireId);

                // The `subject` field references the `reference` field in the CarePlan.
                // `system` is taken from the `reference.type` and `value` is taken from `reference.identifier.value`.
                // The `value` is the CSN ID for an Appointment.
                /*
                 "subject": {
                        "identifier": {
                            // From CarePlan.activity.reference.type
                            "system": "1.2.840.114350.1.13.630.3.7.3.698084.8",
                            // From CarePlan.activity.reference.identifier.value
                            "value": "100010050777"
                        }
                    },
                */
                final JsonObjectBuilder identifier = Json.createObjectBuilder();
                identifier.add("system", "CarePlan.activity.reference.type");
                identifier.add(VALUE, "CarePlan.activity.reference.identifier.value -> CSN ID for an Appointment");
                result.add("subject", Json.createObjectBuilder()
                                        .add("identifier", identifier.build())
                                        .build());
                final String subjectUUID = node.getProperty("subject").getNode().getIdentifier();
                setStatus(result, subjectUUID);

                // Add the answers to the root node
                final JsonArrayBuilder answers = Json.createArrayBuilder();
                this.childrenJsons.get().entrySet().stream()
                    .forEach(entry -> answers.add(entry.getValue()));
                result.add("item", answers.build());

                // Clean the previous JSON processor work
                json.build().keySet().stream()
                    .forEach(key -> json.remove(key));
                // Add new results
                result.build().entrySet().stream()
                    .forEach(entry -> json.add(entry.getKey(), entry.getValue()));
            }
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public void end(Resource resource)
    {
        // Cleanup the state
        this.childrenJsons.remove();
    }

    private String getStringValue(final JsonValue value, final String nodeType)
    {
        if (ValueType.STRING.equals(value.getValueType())) {
            return ((JsonString) value).getString();
        } else if ("cards:PedigreeAnswer".equals(nodeType)) {
            return "yes";
        } else if ("cards:VocabularyAnswer".equals(nodeType)) {
            return StringUtils.substringAfterLast(((JsonString) value).getString(), "/");
        } else {
            return value.toString();
        }
    }

    private void setStatus(final JsonObjectBuilder result, final String subjectUUID)
        throws LoginException
    {
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "ToEpicFormProcessor");
        ResourceResolver serviceResolver = this.rrf.getServiceResourceResolver(parameters);
        if (serviceResolver == null) {
            return;
        }

        // Gather the needed UUIDs to place in the query
        final String visitInformationQuestionnaire =
            (String) serviceResolver.getResource("/Questionnaires/Visit information").getValueMap().get("jcr:uuid");
        final String submittedId = (String) serviceResolver
            .getResource("/Questionnaires/Visit information/surveys_submitted").getValueMap().get("jcr:uuid");

        // Query: get all associated visit forms that are in progress
        final Iterator<Resource> resources = serviceResolver.findResources(String.format(
            // select the data forms
            "select distinct visitInformation.* from [cards:Form] as visitInformation"
                // belonging to a visit
                + "  inner join [cards:Subject] as visitSubject on visitSubject.'jcr:uuid' = visitInformation.subject"
                + "  inner join [cards:Subject] as dataSubject on isdescendantnode(visitSubject, dataSubject)"
                + "  inner join [cards:Answer] as submitted on isdescendantnode(submitted, visitInformation)"
                + " where"
                + "  dataSubject.'jcr:uuid' = '%1$s'"
                // link to the correct Visit Information questionnaire
                + "  and visitInformation.questionnaire = '%2$s'"
                // the visit is not submitted
                + "  and submitted.question = '%3$s'"
                + "  and (submitted.value <> 1 OR submitted.value IS NULL)"
                // exclude the Visit Information questionnaire form itself
                + "  and visitInformation.'jcr:uuid' <> '%2$s'",
                subjectUUID, visitInformationQuestionnaire, submittedId),
            Query.JCR_SQL2);

        // No visit forms in progress
        if (!resources.hasNext()) {
            return;
        }

        // Add the status
        result.add("status", "in-progress");
        return;
    }
}
