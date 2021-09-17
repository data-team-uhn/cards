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
import java.util.LinkedList;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final String VALUE_PROP = "value";

    @SuppressWarnings({"checkstyle:ExecutableStatementCount", "checkstyle:CyclomaticComplexity", "checkstyle:JavaNCSS"})
    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws IOException
    {
        // set input variables
        JsonParser parser = Json.createParser(request.getInputStream());
        String statisticName = null;
        String xVariable = null;
        String yVariable = null;
        String splitVariable = null;
        while (parser.hasNext()) {
            Event event = parser.next();
            if (event == JsonParser.Event.KEY_NAME) {
                String key = parser.getString();
                event = parser.next();
                if ("name".equals(key)) {
                    statisticName = parser.getString();
                }
                if ("x-label".equals(key)) {
                    xVariable = parser.getString();
                }
                if ("y-label".equals(key)) {
                    yVariable = parser.getString();
                }
                if ("splitVar".equals(key)) {
                    splitVariable = parser.getString();
                }
            }
        }

        try {
            // Steps to returning the calculated statistic:
            // Grab the question that has data for the given x-axis (xVar)
            Session session = request.getResourceResolver().adaptTo(Session.class);
            Node question = session.getNode(xVariable);

            Iterator<Resource> answers = null;
            Map<Resource, String> data = new HashMap<>();
            Map<String, Map<Resource, String>> dataById = null;

            // Filter those answers based on whether or not their form's subject is of the correct SubjectType (yVar)
            Node correctSubjectType = session.getNode(yVariable);

            // Grab all answers that have this question filled out, and the split var (if it exists)
            if (splitVariable != null) {
                Node split = session.getNode(splitVariable);
                data = getAnswersWithType(data, "x", question, request.getResourceResolver());
                data = getAnswersWithType(data, "split", split, request.getResourceResolver());
                // filter if splitVar exists
                dataById = filterAnswersWithType(data, correctSubjectType);
            } else {
                // filter if split does not exist
                final StringBuilder query =
                    // We select all answers that answer our question
                    new StringBuilder("select n from [cards:Answer] as n where n.'question'='"
                        + question.getIdentifier() + "'");
                answers = request.getResourceResolver().findResources(query.toString(), Query.JCR_SQL2);
                answers = filterAnswersToSubjectType(answers, correctSubjectType);
            }

            String xLabel = question.getProperty("text").getString();
            String yLabel = correctSubjectType.getProperty("label").getString();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            String date = simpleDateFormat.format(new Date());

            // Add inputs and time generated to the output JSON
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("timeGenerated", date);
            builder.add("name", statisticName);
            builder.add("x-label", xLabel);
            builder.add("y-label", yLabel);
            if (splitVariable != null) {
                Node split = session.getNode(splitVariable);
                String splitLabel = split.getProperty("text").getString();
                builder.add("split-label", splitLabel);
                addDataSplit(dataById, builder);
            } else {
                addData(answers, builder);
            }

            // Write the output
            final Writer out = response.getWriter();
            out.write(builder.build().toString());

        } catch (RepositoryException e) {
            LOGGER.error("Failed to obtain statistic: {}", e.getMessage(), e);
        }
    }

    /**
     * Split: Get all answers that have a given question filled out.
     * @param data the map that the return values will be added to
     * @param type given type (x or split)
     * @param question The question node that the answers is to
     * @param resolver Reference to the resource resolver
     * @return map containing all question nodes and their given type
     */
    private Map<Resource, String> getAnswersWithType(Map<Resource, String> data, String type, Node question,
        ResourceResolver resolver) throws RepositoryException
    {
        final StringBuilder query =
            // We select all answers that answer our question
            new StringBuilder("select n from [cards:Answer] as n where n.'question'='"
                + question.getIdentifier() + "'");
        Iterator<Resource> answers = resolver.findResources(query.toString(), Query.JCR_SQL2);

        while (answers.hasNext()) {
            Resource answer = answers.next();
            data.put(answer, type);
        }

        return data;
    }

    /**
     * Split: Filter the given iterator of resources to only include resources whose parent is a Form, whose
     * Subject's type is equal to the given subjectType.
     * @param data Iterator of resources
     * @param subjectType Subject type of the subject for the answer's form
     * @return The filtered iterator
     */
    private Map<String, Map<Resource, String>> filterAnswersWithType(Map<Resource, String> data, Node subjectType)
        throws RepositoryException
    {
        Iterator<Map.Entry<Resource, String>> entries = data.entrySet().iterator();

        Map<String, Map<Resource, String>> newData = new HashMap<>();

        String correctType = subjectType.getIdentifier();

        // filter out answers without correct subject type
        while (entries.hasNext()) {
            Map.Entry<Resource, String> answer = entries.next();
            // get form node
            Node formNode = getParentNode(answer.getKey().adaptTo(Node.class));
            // get parent subject
            Node formSubject = formNode.getProperty("subject").getNode();

            boolean foundAnswer = false;
            while (formSubject.getDepth() > 0) {
                if (formSubject.getProperty("type").getNode().getIdentifier().equals(correctType)) {
                    // if it is the correct type, add to new map
                    String uuid = formSubject.getIdentifier();

                    //this should create a nested hashmap <subjectID, <answer node, string of either "x" or "split">>
                    if (newData.containsKey(uuid)) {
                        // if it does include uuid already
                        newData.get(uuid).put(answer.getKey(), answer.getValue());
                    } else {
                        // if does not already include uuid
                        Map<Resource, String> subjectData = new HashMap<>();
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
     * @param xVar X variable to use
     * @param splitVar Variable to split on
     * @param counts Map of {SubjectID, {Split variable label, count}}
     * @return map of {x var, {split var, count}}
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity"})
    private Map<String, Map<String, Integer>> aggregateSplitCounts(Resource xVar, Resource splitVar,
        Map<String, Map<String, Integer>> counts) throws RepositoryException
    {
        Map<String, Integer> innerCount = new HashMap<>();
        Map<String, String> splitLabels = null;

        // We can't count anything without an x variable
        if (xVar == null) {
            return counts;
        }

        try {
            Node xAnswer = xVar.adaptTo(Node.class);
            String xValue = xAnswer.getProperty(VALUE_PROP).getString();

            String splitLabel = "Undefined";
            if (splitVar != null) {
                Node splitAnswer = splitVar.adaptTo(Node.class);
                if (splitAnswer.hasProperty(VALUE_PROP)) {
                    String splitValue = splitAnswer.getProperty(VALUE_PROP).getString();
                    if (splitLabels == null) {
                        splitLabels = getAnswerOptionLabels(splitAnswer.getProperty("question").getNode());
                    }
                    splitLabel = splitLabels.containsKey(splitValue) ? splitLabels.get(splitValue) : splitValue;
                }
            }

            // if x value and split value already exist
            if (counts.containsKey(xValue) && counts.get(xValue).containsKey(splitLabel)) {
                counts.get(xValue).put(splitLabel, counts.get(xValue).get(splitLabel) + 1);
            } else if (counts.containsKey(xValue) && !counts.get(xValue).containsKey(splitLabel)) {
                // if x value already exists, but not split value - create and set to 1
                counts.get(xValue).put(splitLabel, 1);
            } else {
                // else, create both and set to 1
                innerCount.put(splitLabel, 1);
                counts.put(xValue, innerCount);
            }
        } catch (PathNotFoundException e) {
            LOGGER.error("Value does not exist for question: {}", e.getMessage(), e);
        }

        return counts;
    }

    /**
     * Get the label that should be used for a given split answer node.
     * @param splitVar Split variable to use.
     * @param splitLabels Map of split variables to their desired labels. This will be filled out if null.
     * @return Split variable's user-readable label.
     */
    private String getSplitValue(Resource splitVar) throws RepositoryException
    {
        if (splitVar == null) {
            return null;
        }

        Node splitAnswer = splitVar.adaptTo(Node.class);
        if (!splitAnswer.hasProperty(VALUE_PROP)) {
            return null;
        }

        return splitAnswer.getProperty(VALUE_PROP).getString();
    }

    /**
     * For a question node, get a map from answer option values to their labels.
     * @param questionNode the question whose answer options we want the label map for
     * @return map of {value, label} for the given question
     */
    private Map<String, String> getAnswerOptionLabels(Node questionNode) throws RepositoryException
    {
        Map<String, String> splitLabels = new HashMap<>();
        NodeIterator childNodes = questionNode.getNodes();
        while (childNodes.hasNext()) {
            Node childNode = childNodes.nextNode();

            // Ensure this is an AnswerType node
            if (!"cards:AnswerOption".equals(childNode.getProperty("jcr:primaryType").getString())) {
                continue;
            }

            // Ensure that the child has both a value and a label
            if (childNode.getProperty(VALUE_PROP) == null || childNode.getProperty("label") == null) {
                continue;
            }

            splitLabels.put(childNode.getProperty(VALUE_PROP).getString(), childNode.getProperty("label").getString());
        }

        return splitLabels;
    }

    /**
     * Split: add aggregated data to object builder, to be displayed.
     * @param data Aggregated data
     * @param builder The object builder for output
     */
    private void addDataSplit(Map<String, Map<Resource, String>> data, JsonObjectBuilder builder)
        throws RepositoryException
    {
        Map<String, Map<String, Integer>> counts = new HashMap<>();

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

        JsonObjectBuilder outerBuilder = Json.createObjectBuilder();
        for (Map.Entry<String, Map<String, Integer>> t:counts.entrySet()) {
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
    }

    /**
     * No Split: Get all answers that have a given question filled out.
     *
     * @param question The question node that the answers is to
     * @param resolver A reference to a ResourceResolver
     * @return An iterator of Resources that are Nodes of answers to the given question
     */
    private Iterator<Resource> getAnswersToQuestion(Node question, ResourceResolver resolver) throws RepositoryException
    {
        final StringBuilder query =
            // We select all answers that answer our question
            new StringBuilder("select n from [cards:Answer] as n where n.'question'='"
                + question.getIdentifier() + "'");
        return resolver.findResources(query.toString(), Query.JCR_SQL2);
    }

    /**
     * No Split: Filter the given iterator of resources to only include resources whose parent is a Form, and
     * whose Subject (or an ancestor)'s type is equal to the given subjectType.
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
            Node answerParent = getParentNode(answer.adaptTo(Node.class));
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
        Map<String, Integer> counts = new HashMap<>();
        while (answers.hasNext()) {
            Node answer = answers.next().adaptTo(Node.class);

            try {
                String value = answer.getProperty(VALUE_PROP).getString();
                // If this already exists in our counts dict, we add 1 to its value
                // Otherwise, set it to 1 count
                if (counts.containsKey(value)) {
                    counts.put(value, counts.get(value) + 1);
                } else {
                    counts.put(value, 1);
                }
            } catch (PathNotFoundException e) {
                LOGGER.error("Value does not exist for question: {}", e.getMessage(), e);
                continue;
            }
        }
        return counts;
    }

    /**
     * No Split: Add the counts to the data object.
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
    }

    /**
     * Get the parent node type of a given cards:Answer node.
     *
     * @param node A node corresponding to an cards:Answer
     * @return A node corresponding to the parent subject type
     */
    public Node getParentNode(Node node) throws RepositoryException
    {
        // Recursively go through our parents until we find a cards:Form node
        // If we somehow reach the top level, return an error
        Node answerParent = node.getParent();
        while (!"cards:Form".equals(answerParent.getPrimaryNodeType().getName()) && answerParent.getDepth() != 0) {
            answerParent = answerParent.getParent();
        }

        // If we never find a form by going upwards, this cards:Answer is malformed
        if (answerParent.getDepth() == 0) {
            String error = String.format("Tried to obtain the parent cards:Form for node {} but failed to find one",
                node.getPath());
            throw new RepositoryException(error);
        }

        return answerParent;
    }
}
