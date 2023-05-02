/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.statistics;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * A servlet for querying Statistics that returns a JSON object containing values for the x and y axes.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Statistic", "cards/StatisticsHomepage" },
    selectors = { "query" },
    methods = { "POST" })
public class StatisticQueryServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = 2558430802619674046L;

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticQueryServlet.class);

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final String VALUE_PROP = "value";

    private static final String LABEL_PROP = "displayedValue";

    private static final String VALUE_NOT_SPECIFIED = "Not specified";

    // xValueDictionary is used to cache the map from answer displayed values to raw values for the variable
    private final ThreadLocal<Map<String, String>> xValueDictionary = new ThreadLocal<>();

    // splitValueDictionary is used to cache the map from answer displayed values to raw values for the split variable
    private final ThreadLocal<Map<String, String>> splitValueDictionary = new ThreadLocal<>();

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<ResourceJsonProcessor> allProcessors;

    private final ThreadLocal<List<ResourceJsonProcessor>> labelProcessors = new ThreadLocal<>();

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws IOException
    {
        Map<String, String> arguments = parseArguments(request);

        try {
            // Obtain the labels processors and sort them by priority
            // They will enable aggregating the stats data by displayedValue
            List<ResourceJsonProcessor> processors = this.allProcessors.stream()
                .filter(p -> "labels".equals(p.getName())).collect(Collectors.toList());
            processors.sort((o1, o2) -> o1.getPriority() - o2.getPriority());
            this.labelProcessors.set(processors);

            ResourceResolver resolver = request.getResourceResolver();
            JsonObjectBuilder builder = buildResponse(resolver, arguments);

            // Write the output
            response.setContentType("application/json;charset=UTF-8");
            final Writer out = response.getWriter();
            out.write(builder.build().toString());

        } catch (RepositoryException e) {
            LOGGER.error("Failed to obtain statistic: {}", e.getMessage(), e);
        }
    }

    /**
     * Parse out the arguments given in the POST request into a string map.
     *
     * @param request the POST request made to this servlet
     * @return map of arguments to their values
     */
    protected Map<String, String> parseArguments(SlingHttpServletRequest request) throws IOException
    {
        JsonParser parser = Json.createParser(request.getInputStream());
        Map<String, String> retVal = new HashMap<>();
        while (parser.hasNext()) {
            Event event = parser.next();
            if (event == JsonParser.Event.KEY_NAME) {
                String key = parser.getString();
                event = parser.next();
                retVal.put(key, parser.getString());
            }
        }

        return retVal;
    }

    /**
     * Builds the statistic data accordong to the provided arguments.
     *
     * @param resolver the resolver used for accessing the contributing resources
     * @param arguments the statistic specification
     * @return builder a JsonObjectBuilder containing the computed counts and the metadata (labels, time generated)
     */
    private JsonObjectBuilder buildResponse(ResourceResolver resolver, Map<String, String> arguments)
        throws RepositoryException
    {
        // Steps to returning the calculated statistic:
        // Grab the question that has data for the given x-axis (xVar)
        Session session = resolver.adaptTo(Session.class);
        Node xVar = session.getNode(arguments.get("x-label"));
        String xLabel = xVar.getProperty("text").getString();
        this.xValueDictionary.set(new HashMap<>());

        // Grab the type of subjects being counted for the y-axis (yVar)
        // Answers will be filtered based on whether or not their form's subject is of this SubjectType
        Node ySubjectType = session.getNode(arguments.get("y-label"));
        String yLabel = ySubjectType.getProperty("label").getString();

        // Start building the response:

        // Add inputs and time generated to the output JSON
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("timeGenerated", SIMPLE_DATE_FORMAT.format(new Date()));
        builder.add("name", arguments.get("name"));
        builder.add("x-label", xLabel);
        builder.add("y-label", yLabel);

        // Grab all answers that have this question filled out, and the split var (if it exists)
        boolean isSplit = arguments.containsKey("splitVar");

        if (isSplit) {
            Node splitVar = session.getNode(arguments.get("splitVar"));
            String splitLabel = splitVar.getProperty("text").getString();
            this.splitValueDictionary.set(new HashMap<>());

            Map<Resource, String> data = new LinkedHashMap<>();
            Map<String, Map<Resource, String>> dataById = null;

            data = getAnswersWithType(data, "x", xVar, resolver);
            data = getAnswersWithType(data, "split", splitVar, resolver);
            // filter if splitVar exists
            dataById = filterAnswersWithType(data, ySubjectType);

            // Add the x and split counts to the builder
            builder.add("split-label", splitLabel);
            addDataSplit(dataById, builder);

            // Clean up split variable value dictionary
            this.splitValueDictionary.remove();
        } else {
            Iterator<Resource> answers = getAnswers(xVar, resolver);
            answers = filterAnswersToSubjectType(answers, ySubjectType);

            // Add the x counts to the builder
            addData(answers, builder);
        }

        // Clean up split variable value dictionary
        this.xValueDictionary.remove();

        return builder;
    }

    /**
     * Split: Get all answers that have a given question filled out.
     *
     * @param data the map that the return values will be added to
     * @param type given type (x or split)
     * @param question The question node that the answers is to
     * @param resolver Reference to the resource resolver
     * @return map containing all question nodes and their given type
     */
    private Map<Resource, String> getAnswersWithType(Map<Resource, String> data, String type, Node question,
        ResourceResolver resolver) throws RepositoryException
    {
        Iterator<Resource> answers = getAnswers(question, resolver);

        while (answers.hasNext()) {
            Resource answer = answers.next();
            data.put(answer, type);
        }

        return data;
    }

    /**
     * Get all answers that have a given question filled out.
     *
     * @param question The question node that the answers is to
     * @param resolver Reference to the resource resolver
     * @return a Resource iterator over all nodes corresponding to answers to the given question
     */
    private Iterator<Resource> getAnswers(Node question, ResourceResolver resolver)
        throws RepositoryException
    {
        final StringBuilder query =
            // We select all answers that answer our question
            new StringBuilder("select n from [" + getAnswerNodeType(question) + "] as n where n.'question'='"
                + question.getIdentifier() + "' order by n.'value' desc");
        return resolver.findResources(query.toString(), Query.JCR_SQL2);
    }

    /**
     * Obtain the answer node type based on the dataType specified in the question definition.
     *
     * @param question The question node
     * @return A string "cards:____Answer" (e.g., cards:TextAnswer, cards:LongAnswer) or "cards:Answer" if obtaining the
     *     type fails
     */
    private String getAnswerNodeType(Node question)
    {
        String nodeType = "";
        try {
            final String dataTypeString = question.getProperty("dataType").getString();
            nodeType = StringUtils.capitalize(dataTypeString);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to obtain answer node type: {}", e.getMessage(), e);
        }
        return "cards:" + nodeType + "Answer";
    }

    /**
     * Split: Filter the given iterator of resources to only include resources whose parent is a Form, whose Subject's
     * type is equal to the given subjectType.
     *
     * @param data Iterator of resources
     * @param subjectType Subject type of the subject for the answer's form
     * @return The filtered iterator
     */
    private Map<String, Map<Resource, String>> filterAnswersWithType(Map<Resource, String> data, Node subjectType)
        throws RepositoryException
    {
        Iterator<Map.Entry<Resource, String>> entries = data.entrySet().iterator();

        Map<String, Map<Resource, String>> newData = new LinkedHashMap<>();

        String correctType = subjectType.getIdentifier();

        // filter out answers without correct subject type
        while (entries.hasNext()) {
            Map.Entry<Resource, String> answer = entries.next();
            // get form node
            Node formNode = getFormNode(answer.getKey().adaptTo(Node.class));
            // get parent subject
            Node formSubject = formNode.getProperty("subject").getNode();

            boolean foundAnswer = false;
            while (formSubject.getDepth() > 0) {
                if (formSubject.hasProperty("type")
                    && formSubject.getProperty("type").getNode().getIdentifier().equals(correctType)) {
                    // if it is the correct type, add to new map
                    String uuid = formSubject.getIdentifier();

                    // this should create a nested hashmap <subjectID, <answer node, string of either "x" or "split">>
                    if (newData.containsKey(uuid)) {
                        // if it does include uuid already
                        newData.get(uuid).put(answer.getKey(), answer.getValue());
                    } else {
                        // if does not already include uuid
                        Map<Resource, String> subjectData = new LinkedHashMap<>();
                        subjectData.put(answer.getKey(), answer.getValue());
                        newData.put(uuid, subjectData);
                    }
                    foundAnswer = true;
                    break;
                }
                formSubject = formSubject.getParent();
            }

            if (!foundAnswer) {
                LOGGER.warn("Could not find answer for node: " + formNode.getIdentifier());
            }
        }

        return newData;
    }

    /**
     * Split: Aggregate the counts.
     *
     * @param xVar X variable to use
     * @param splitVar Variable to split on
     * @param counts Map of {SubjectID, {Split variable label, count}}
     * @return map of {x var, {split var, count}}
     */
    private Map<String, Map<String, Integer>> aggregateSplitCounts(Resource xVar, Resource splitVar,
        Map<String, Map<String, Integer>> counts) throws RepositoryException
    {
        Map<String, Integer> innerCount = new LinkedHashMap<>();

        // We can't count anything without an x variable
        if (xVar == null) {
            return counts;
        }

        Node splitAnswer = splitVar.adaptTo(Node.class);
        List<String> splitValues = getAnswerValues(splitAnswer, this.splitValueDictionary.get());

        Node xAnswer = xVar.adaptTo(Node.class);
        List<String> values = getAnswerValues(xAnswer, this.xValueDictionary.get());
        Iterator<String> it = values.iterator();
        while (it.hasNext()) {
            String xValue = it.next();
            Iterator<String> split = splitValues.iterator();
            while (split.hasNext()) {
                String splitValue = split.next();
                // if x value and split value already exist
                if (counts.containsKey(xValue) && counts.get(xValue).containsKey(splitValue)) {
                    counts.get(xValue).put(splitValue, counts.get(xValue).get(splitValue) + 1);
                } else if (counts.containsKey(xValue) && !counts.get(xValue).containsKey(splitValue)) {
                    // if x value already exists, but not split value - create and set to 1
                    counts.get(xValue).put(splitValue, 1);
                } else {
                    // else, create both and set to 1
                    innerCount.put(splitValue, 1);
                    counts.put(xValue, innerCount);
                }
            }
        }

        return counts;
    }

    /**
     * Split: add aggregated data to object builder, to be displayed.
     *
     * @param data Aggregated data
     * @param builder The object builder for output
     */
    private void addDataSplit(Map<String, Map<Resource, String>> data, JsonObjectBuilder builder)
        throws RepositoryException
    {
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();

        try {
            for (Map.Entry<String, Map<Resource, String>> entries : data.entrySet()) {
                Resource splitVar = null;

                // First, find the split variable
                for (Map.Entry<Resource, String> entry : entries.getValue().entrySet()) {
                    if ("split".equals(entry.getValue())) {
                        splitVar = entry.getKey();
                        break;
                    }
                }

                // Then, call aggregate split counts once for each x variable
                for (Map.Entry<Resource, String> entry : entries.getValue().entrySet()) {
                    if ("x".equals(entry.getValue())) {
                        counts = aggregateSplitCounts(entry.getKey(), splitVar, counts);
                    }
                }
            }
        } catch (Exception e) {
            // Do nothing
        }

        JsonObjectBuilder outerBuilder = Json.createObjectBuilder();
        for (Map.Entry<String, Map<String, Integer>> t : counts.entrySet()) {
            String key = t.getKey();

            JsonObjectBuilder keyBuilder = Json.createObjectBuilder();

            for (Map.Entry<String, Integer> e : t.getValue().entrySet()) {
                // inner object
                keyBuilder.add(e.getKey(), e.getValue());
            }
            // outer object
            outerBuilder.add(key, keyBuilder.build());
        }
        builder.add("data", outerBuilder.build());

        // Add value->raw value maps for nice display and filter generation on the frontend
        builder.add("xValueDictionary", buildMapAsJson(this.xValueDictionary.get()));
        builder.add("splitValueDictionary", buildMapAsJson(this.splitValueDictionary.get()));
    }

    /**
     * No Split: Filter the given iterator of resources to only include resources whose parent is a Form, and whose
     * Subject (or an ancestor)'s type is equal to the given subjectType.
     *
     * @param answers The iterator of answers to filter
     * @param subjectType A subjectType to filter for
     * @return An iterator of Resources that are Nodes of answers for the given subjectType
     */
    private Iterator<Resource> filterAnswersToSubjectType(Iterator<Resource> answers, Node subjectType)
        throws RepositoryException
    {
        Deque<Resource> newAnswers = new LinkedList<>();
        String correctType = subjectType.getIdentifier();
        while (answers.hasNext()) {
            Resource answer = answers.next();
            Node answerParent = getFormNode(answer.adaptTo(Node.class));
            Node answerSubjectType = answerParent.getProperty("subject").getNode().getProperty("type").getNode();

            while (answerSubjectType.getDepth() > 0) {
                if (answerSubjectType.getIdentifier().equals(correctType)) {
                    newAnswers.push(answer);
                    break;
                }

                // Check the parent instead
                answerSubjectType = answerSubjectType.getParent();
            }
        }
        return newAnswers.iterator();
    }

    /**
     * No Split: Aggregate the given Iterator of answers to a map of counts for each unique value.
     *
     * @param answers An iterator of cards:Answer objects
     * @return A map of values -> counts
     */
    private Map<String, Integer> aggregateCounts(Iterator<Resource> answers) throws RepositoryException
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        while (answers.hasNext()) {
            Node answer = answers.next().adaptTo(Node.class);
            List<String> values = getAnswerValues(answer, this.xValueDictionary.get());
            Iterator<String> it = values.iterator();
            while (it.hasNext()) {
                String value = it.next();
                // If this already exists in our counts dict, we add 1 to its value
                // Otherwise, set it to 1 count
                if (counts.containsKey(value)) {
                    counts.put(value, counts.get(value) + 1);
                } else {
                    counts.put(value, 1);
                }
            }
        }
        return counts;
    }

    /**
     * Obtain the answer values as a list, regardless whether it is single or multi valued.
     *
     * @param answer The cards:Answer node
     * @param valueDictionary a Map that contains all value -> raw value pairs encountered in any of the answers to
     *     a specific variable (either x or split) used for generating the current statistic
     * @return A list of strings
     */
    private List<String> getAnswerValues(Node answer, Map<String, String> valueDictionary)
    {
        List<String> values = new LinkedList<>();
        // Call label processors to populate displayedValue
        JsonObjectBuilder builder = Json.createObjectBuilder();
        this.labelProcessors.get().forEach(p -> p.leave(answer, builder, null));
        // Now the json has the displayedValue if a value exists
        JsonObject answerJson = builder.build();
        JsonValue jsonValue = answerJson.get(LABEL_PROP);
        try {
            if (!answer.hasProperty(VALUE_PROP)) {
                recordEmptyAnswerValue(values, valueDictionary);
                return values;
            }
            Property rawValue = answer.getProperty(VALUE_PROP);
            if (jsonValue == null) {
                recordEmptyAnswerValue(values, valueDictionary);
            } else if (jsonValue.getValueType() == ValueType.ARRAY) {
                JsonArray jsonArray = jsonValue.asJsonArray();
                if (jsonArray.size() == 0) {
                    recordEmptyAnswerValue(values, valueDictionary);
                } else {
                    Value[] rawValues = rawValue.getValues();
                    for (int i = 0; i < jsonArray.size(); ++i) {
                        recordAnswerValue(values, valueDictionary, jsonArray.getString(i), rawValues[i].getString());
                    }
                }
            } else {
                recordAnswerValue(values, valueDictionary, answerJson.getString(LABEL_PROP), rawValue.getString());
            }
        } catch (ClassCastException | RepositoryException e) {
            LOGGER.error("Value could not be processed for question: {}", e.getMessage(), e);
            if (values.size() == 0) {
                recordEmptyAnswerValue(values, valueDictionary);
            }
        }
        return values;
    }

    /**
     * Records the "empty value" into a certain answer's value list and the statistic's overall value dictionary.
     *
     * @param values a List of strings holding all values for a particular answer
     * @param valueDictionary a Map that contains all value -> raw value pairs encountered in any of the answers to
     *     a specific variable (either x or split) used for generating the current statistic
     */
    private void recordEmptyAnswerValue(List<String> values, Map<String, String> valueDictionary)
    {
        recordAnswerValue(values, valueDictionary, VALUE_NOT_SPECIFIED, "");
    }

    /**
     * Records an answer value into a certain answer's value list and the statistic's overall value dictionary.
     *
     * @param values a List of strings holding all values for a particular answer
     * @param valueDictionary a Map that contains all value -> raw value pairs encountered in any of the answers to
     *     a specific variable (either x or split) used for generating the current statistic
     * @param value The value to record
     * @param rawValue The corresponding raw value, recorder together with value in the dictionary
     */
    private void recordAnswerValue(List<String> values, Map<String, String> valueDictionary, String value,
        String rawValue)
    {
        values.add(value);
        valueDictionary.put(value, rawValue);
    }

    /**
     * No Split: Add the counts to the data object.
     *
     * @param answers Counts object to add
     * @param builder Data object to add to
     */
    private void addData(Iterator<Resource> answers, JsonObjectBuilder builder) throws RepositoryException
    {
        // Aggregate our counts
        Map<String, Integer> counts = aggregateCounts(answers);

        // Convert our HashMap into a JsonObject
        JsonObjectBuilder dataBuilder = Json.createObjectBuilder();
        Iterator<String> keysMap = counts.keySet().iterator();
        while (keysMap.hasNext()) {
            String key = keysMap.next();
            dataBuilder.add(key, counts.get(key));
        }
        builder.add("data", dataBuilder.build());

        // Add value->label maps for nice display on the frontend)
        builder.add("xValueDictionary", buildMapAsJson(this.xValueDictionary.get()));
    }

    /**
     * Build a JsonObject from a map.
     *
     * @param map A String->String map
     * @return JsonObject the same data as a json object
     */
    private JsonObject buildMapAsJson(Map<String, String> map)
    {
        // Convert the Map into a JsonObject
        JsonObjectBuilder builder = Json.createObjectBuilder();
        Iterator<String> keysMap = map.keySet().iterator();
        while (keysMap.hasNext()) {
            String key = keysMap.next();
            builder.add(key, map.get(key));
        }
        return builder.build();
    }

    /**
     * Get the parent node type of a given cards:Answer node.
     *
     * @param answer A node corresponding to an cards:Answer
     * @return A node corresponding to the parent subject type
     */
    public Node getFormNode(Node answer)
    {
        try {
            // Recursively go through our parents until we find a cards:Form node
            // If we somehow reach the top level, return an error
            Node answerParent = answer.getParent();
            while (answerParent != null && answerParent.getDepth() != 0
                && !"cards:Form".equals(answerParent.getPrimaryNodeType().getName())) {
                answerParent = answerParent.getParent();
            }

            // If we never find a form by going upwards, this cards:Answer is malformed
            if (answerParent.getDepth() == 0) {
                LOGGER.warn("Tried to obtain the parent Form for node {} but failed to find one", answer.getPath());
                return null;
            }

            return answerParent;
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to access Form: {}", e.getMessage());
            return null;
        }
    }
}
