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
package io.uhndata.cards.dataentry.internal.serialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.query.Query;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceCSVProcessor;

/**
 * CSV serializer that can process Questionnaires.
 *
 * @version $Id$
 */
@Component(service = ResourceCSVProcessor.class)
public class QuestionnaireToCsvProcessor implements ResourceCSVProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionnaireToCsvProcessor.class);

    private static final String IDENTIFIER_HEADER = "Identifier";

    private static final String CREATED_HEADER = "Created";

    private static final String PRIMARY_TYPE_PROP = "jcr:primaryType";

    private static final String UUID_PROP = "jcr:uuid";

    @Override
    public boolean canProcess(final Resource resource)
    {
        return resource.isResourceType("cards/Questionnaire");
    }

    @Override
    public String serialize(Resource resource)
    {
        // The proper serialization depends on "deep", "dereference", and "-labels", but we may allow other JSON
        // processors to be enabled/disabled to further customize the data, so we also append the original selectors
        final String processedPath = resource.getPath()
            + resource.getResourceMetadata().getResolutionPathInfo() + ".deep.dereference.-labels";
        JsonObject result = resource.getResourceResolver().resolve(processedPath).adaptTo(JsonObject.class);

        if (result != null) {
            return processQuestionnaire(result, resource.getResourceResolver());
        }
        return null;
    }

    private String processQuestionnaire(final JsonObject questionnaire, final ResourceResolver resolver)
    {
        try {
            final StringBuilder output = new StringBuilder();
            final CSVPrinter csvPrinter = new CSVPrinter(output, CSVFormat.DEFAULT);

            // CSV data strings aggregator where the key is Question uuid that maps to the list of pairs
            // of corresponding answer strings to row number/level in the csv [ <uuid> : List<Pair<int, String>> ]
            final Map<String, Map<Integer, String>> csvData = new LinkedHashMap<>();
            // collect column headers explicitly as labels because csvData maps only questions uuids to answers
            final List<String> columns = new ArrayList<>();
            columns.add(IDENTIFIER_HEADER);

            // Fetch the subject types expected to be for the questionnaire
            if (questionnaire.containsKey("requiredSubjectTypes")) {
                getSubjectTypes(questionnaire.getJsonArray("requiredSubjectTypes"), csvData, columns);
            } else {
                // No specific subject types for this questionnaire, output all known subject types
                getSubjectTypes(resolver, csvData, columns);
            }
            csvData.put(CREATED_HEADER, new HashMap<>());
            columns.add(CREATED_HEADER);

            // get header titles from the questionnaire question objects
            processSectionToHeaderRow(questionnaire, csvData, columns);
            // print header
            csvPrinter.printRecord(columns.toArray());

            // Aggregate form answers to the csvData collector for the CSV output
            if (questionnaire.containsKey("@data")) {
                processFormsToRows(questionnaire.getJsonArray("@data"), csvData, csvPrinter);
            }

            final String result = output.toString();

            csvPrinter.flush();
            csvPrinter.close();
            return result;
        } catch (IOException e) {
            LOGGER.error("Error in CSV export of {} questionnaire", questionnaire.getString("@name"));
        }
        return null;
    }

    private void getSubjectTypes(final ResourceResolver resolver, final Map<String, Map<Integer, String>> csvData,
        final List<String> columns)
    {
        final Iterator<Resource> subjectTypes = resolver.findResources(
            "SELECT st.* FROM [cards:SubjectType] AS st ORDER BY st.[cards:defaultOrder] ASC", Query.JCR_SQL2);
        while (subjectTypes.hasNext()) {
            final ValueMap subjectTypeProperties = subjectTypes.next().getValueMap();
            columns.add(subjectTypeProperties.get("label", String.class).concat(" ID"));
            csvData.put(subjectTypeProperties.get(UUID_PROP, String.class), new HashMap<>());
        }
    }

    private void getSubjectTypes(JsonArray subjectTypesArray, Map<String, Map<Integer, String>> csvData,
        List<String> columns)
    {
        final List<JsonObject> subjectTypes = new ArrayList<>();
        subjectTypesArray.stream().map(JsonValue::asJsonObject)
            .forEach(value -> gatherSubjectTypes(value, subjectTypes));
        subjectTypes.sort((t1, t2) -> t1.getInt("cards:defaultOrder") - t2.getInt("cards:defaultOrder"));
        subjectTypes.forEach(subjectType -> {
            columns.add(subjectType.getString("label").concat(" ID"));
            csvData.put(subjectType.getString(UUID_PROP), new HashMap<>());
        });
    }

    private void gatherSubjectTypes(final JsonObject subjectType, final List<JsonObject> result)
    {
        if (result.stream().noneMatch(s -> s.getString(UUID_PROP).equals(subjectType.getString(UUID_PROP)))) {
            result.add(subjectType);
            if (subjectType.containsKey("parents")) {
                gatherSubjectTypes(subjectType.getJsonObject("parents"), result);
            }
        }
    }

    private void processSectionToHeaderRow(JsonObject questionnaire, Map<String, Map<Integer, String>> csvData,
        List<String> columns)
    {
        questionnaire.values().stream()
            .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
            .map(JsonValue::asJsonObject)
            .filter(value -> value.containsKey(PRIMARY_TYPE_PROP))
            .forEach(value -> processHeaderElement(value, csvData, columns));
    }

    /**
     * Converts a JSON fragment (object) to csv text, if it is a simple question or a section. All other kinds of
     * information are ignored.
     *
     * @param nodeJson a JSON serialization of a node
     * @param csvData data aggregator
     * @param columns a list of column headers
     */
    private void processHeaderElement(final JsonObject nodeJson, Map<String, Map<Integer, String>> csvData,
        List<String> columns)
    {
        final String nodeType = nodeJson.getString(PRIMARY_TYPE_PROP);
        if ("cards:Section".equals(nodeType)) {
            getDisplayMode(nodeJson);
            processSectionToHeaderRow(nodeJson, csvData, columns);
        } else if ("cards:Question".equals(nodeType)) {
            final String displayMode = getDisplayMode(nodeJson);
            if ("hidden".equals(displayMode)) {
                return;
            }

            String label = nodeJson.getString("@name");
            if (nodeJson.containsKey("text")) {
                label = nodeJson.getString("text");
            }
            columns.add(label);
            csvData.put(nodeJson.getString(UUID_PROP), new HashMap<>());
        }
    }

    private void processFormsToRows(JsonArray formsJson, Map<String, Map<Integer, String>> csvData,
        CSVPrinter csvPrinter)
    {
        for (JsonValue form : formsJson) {
            processForm((JsonObject) form, csvData, csvPrinter);
        }
    }

    private void processForm(final JsonObject form, Map<String, Map<Integer, String>> csvData,
        CSVPrinter csvPrinter)
    {
        // Collect information regarding the form subjects and subject parents
        if (form.containsKey("subject")) {
            processFormSubjects(form.getJsonObject("subject"), csvData);
        }
        csvData.get(CREATED_HEADER).put(0, form.getString("jcr:created"));

        // Set the root graph node
        TreeGraph<String> formGraph = new TreeGraph<String>("root", null, null, 0, false, true);

        processFormSection(form, csvData, formGraph);
        Map<String, TreeGraph> result = new LinkedHashMap<String, TreeGraph>();
        formGraph.dfsRecursive(result);

        fillCSVData(formGraph, result, csvData);

        final int levels = csvData.values().stream().map(Map::keySet)
            .flatMap(Set::stream)
            .max(Comparator.comparing(Integer::valueOf))
            .get();

        // Assemble CSV by rows
        for (int i = 0; i <= levels; i++) {
            final List<String> row = new ArrayList<>();
            // First add the form identifier
            row.add(form.getString("@name"));
            // Iterate over the columns; since this is a linked map, the order is always the same as in the header
            for (final Map<Integer, String> answerList : csvData.values()) {
                // Look for the answer with the level <i> in the list of pairs <level,Answer>
                // If none found, we add ""
                row.add(StringUtils.defaultString(answerList.get(i)));
            }
            // print one row for the level i
            try {
                csvPrinter.printRecord(row.toArray());
            } catch (final IOException e) {
                // Should not happen since we're printing to a string
            }
        }

        // Empty csvData for future form
        csvData.values().forEach(Map::clear);
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private void fillCSVData(TreeGraph<String> formGraph, Map<String, TreeGraph> result,
        Map<String, Map<Integer, String>> csvData)
    {
        // Fill in csvData from the tree
        int rowNumber = 0;
        Boolean isRecurring = false;
        Boolean isSameSection = false;
        Boolean isCurrentlyReccurentSection = false;
        String sectionId = formGraph.getUuid();
        int depth = formGraph.getLevel();
        for (final String name : result.keySet()) {
            TreeGraph node = result.get(name);

            if (node.isSection()) {
                isRecurring = node.isRecurrentSection();
                isSameSection = sectionId == node.getUuid();
            }

            int newDepth = node.getLevel();

            Boolean isNewRowPartOne = (newDepth == depth - 1) && (isRecurring && isCurrentlyReccurentSection)
                && isSameSection;
            Boolean isNewRowPartTwo = (newDepth < depth - 1) && (isRecurring && isCurrentlyReccurentSection);

            if (isNewRowPartOne || isNewRowPartTwo) {
                rowNumber++;
            }

            depth = newDepth;
            isCurrentlyReccurentSection = isRecurring;
            if (node.isSection()) {
                sectionId = node.getUuid();
            }

            final Map<Integer, String> answerColumn = csvData.get(node.getUuid());
            if (answerColumn == null) {
                // This is a skipped question, either hidden or in a non-data section
                continue;
            }
            answerColumn.put(rowNumber, (String) node.getData());
        }
    }

    private void processFormSubjects(final JsonObject subjectJson, final Map<String, Map<Integer, String>> csvData)
    {
        final Map<Integer, String> subjectColumn = csvData.get(subjectJson.getJsonObject("type").getString(UUID_PROP));
        if (subjectColumn != null) {
            subjectColumn.put(0, subjectJson.getString("identifier"));
        }
        // Recursively collect all of the subjects parents in the hierarchy
        if (subjectJson.containsKey("parents")) {
            processFormSubjects(subjectJson.getJsonObject("parents"), csvData);
        }
    }

    /**
     * Converts a JSON serialization of an answer section to CSV text.
     *
     * @param answerSectionJson a JSON serialization of an answer section
     * @param csvData data aggregator
     * @param formGraph tree structure
     */
    private void processFormSection(final JsonObject answSectionJson, Map<String, Map<Integer, String>> csvData,
        TreeGraph<String> formGraph)
    {
        answSectionJson.values().stream()
            .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
            .map(JsonValue::asJsonObject)
            .filter(value -> value.containsKey(PRIMARY_TYPE_PROP)
                && ("cards:AnswerSection".equals(value.getString(PRIMARY_TYPE_PROP))
                    || value.getString(PRIMARY_TYPE_PROP).startsWith("cards:")
                        && value.getString(PRIMARY_TYPE_PROP).endsWith("Answer")))
            .forEach(value -> processFormElement(value, csvData, formGraph));
    }

    /**
     * Process node answer to fill in data aggregator.
     *
     * @param nodeJson a JSON serialization of an answer node
     * @param csvData data aggregator
     * @param formGraph tree structure
     */
    private void processFormElement(final JsonObject nodeJson, Map<String, Map<Integer, String>> csvData,
        TreeGraph<String> formGraph)
    {
        int newLevel = formGraph.getLevel() + 1;
        final String nodeName = nodeJson.getString("@name");
        final String nodeType = nodeJson.getString(PRIMARY_TYPE_PROP);
        if ("cards:AnswerSection".equals(nodeType)) {
            Boolean isRecurrent = nodeJson.getJsonObject("section").getBoolean("recurrent");
            final String uuid = nodeJson.getJsonObject("section").getString(UUID_PROP);
            // Add Section node to the tree
            TreeGraph<String> sectionTreeNode = new TreeGraph(nodeName, uuid, null, newLevel, isRecurrent, true);
            formGraph.addChild(sectionTreeNode);
            processFormSection(nodeJson, csvData, sectionTreeNode);
        } else if (nodeType.startsWith("cards:") && nodeType.endsWith("Answer")) {
            final String uuid = nodeJson.getJsonObject("question").getString(UUID_PROP);
            String answer = getAnswerString(nodeJson, nodeType);
            // Add Answer child to the tree
            formGraph.addChild(nodeName, uuid, answer, newLevel, false, false);
        }
    }

    private String getAnswerString(final JsonObject nodeJson, final String nodeType)
    {
        // If `labels` are enabled, grab the `displayedValue`
        JsonValue value = nodeJson.get("displayedValue");
        // In the absence of `displayedValue`, carry on with raw `value`
        if (value == null) {
            value = nodeJson.get("value");
        }
        if (value == null) {
            return "";
        } else if ("cards:PedigreeAnswer".equals(nodeType)) {
            return "yes";
        } else if (ValueType.ARRAY.equals(value.getValueType())) {
            return value.asJsonArray().stream()
                .map(v -> getStringValue(v, nodeType))
                .reduce((result, v) -> result + ";" + v).get();
        } else {
            return getStringValue(value, nodeType);
        }
    }

    private String getStringValue(final JsonValue value, final String nodeType)
    {
        if (ValueType.STRING.equals(value.getValueType())) {
            return ((JsonString) value).getString();
        } else if ("cards:VocabularyAnswer".equals(nodeType)) {
            return StringUtils.substringAfterLast(((JsonString) value).getString(), "/");
        } else {
            return value.toString();
        }
    }

    /**
     * Retrieves the display mode specified for a question or section, if any.
     *
     * @param elementJson a JSON serialization of a question or section
     * @return the display mode as String, e.g. hidden, default, header, footer, summary.
     */
    private String getDisplayMode(final JsonObject elementJson)
    {
        try {
            return (elementJson.getString("displayMode"));
        } catch (JsonException | NullPointerException ex) {
            // Not there, return
        }
        return null;
    }
}
