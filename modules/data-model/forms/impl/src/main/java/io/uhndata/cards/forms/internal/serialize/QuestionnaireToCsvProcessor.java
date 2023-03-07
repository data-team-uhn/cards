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
package io.uhndata.cards.forms.internal.serialize;

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
    private static final String LAST_MODIFIED_HEADER = "Last modified";

    private static final String PRIMARY_TYPE_PROP = "jcr:primaryType";

    private static final String UUID_PROP = "jcr:uuid";

    @Override
    public boolean canProcess(final Resource resource)
    {
        return resource.isResourceType("cards/Questionnaire");
    }

    @Override
    public String serialize(final Resource resource)
    {
        // The proper serialization depends on "deep", "dereference", and "-labels", but we may allow other JSON
        // processors to be enabled/disabled to further customize the data, so we also append the original selectors
        String resolutionPathInfo = resource.getResourceMetadata().getResolutionPathInfo();
        final String processedPath = resource.getPath() + (resolutionPathInfo != null ? resolutionPathInfo : "")
                + ".deep.dereference.-labels";
        final JsonObject result = resource.getResourceResolver().resolve(processedPath).adaptTo(JsonObject.class);

        if (result != null) {
            return processQuestionnaire(result, resource.getResourceResolver(),
                (resolutionPathInfo != null ? resolutionPathInfo : ""));
        }
        return null;
    }

    private String processQuestionnaire(final JsonObject questionnaire, final ResourceResolver resolver,
        final String resolutionPathInfo)
    {
        try {
            final StringBuilder output = new StringBuilder();
            CSVFormat format = CSVFormat.DEFAULT;
            if (resolutionPathInfo.endsWith(".tsv")) {
                format = CSVFormat.TDF;
            }
            final CSVPrinter csvPrinter = new CSVPrinter(output, format);

            // CSV data aggregator mapping Question UUIDs to pairs of corresponding row number to answer in the csv
            // [ question : [ row# : answer ] ]
            final Map<String, Map<Integer, String>> csvData = new LinkedHashMap<>();
            // Collect column headers explicitly as labels because csvData maps only questions uuids to answers
            final List<String> columns = new ArrayList<>();
            columns.add(IDENTIFIER_HEADER);

            final List<String> rawColumns = new ArrayList<>();
            rawColumns.add("@name");

            // Fetch the subject types expected to be for the questionnaire
            if (questionnaire.containsKey("requiredSubjectTypes")) {
                getSubjectTypes(questionnaire.getJsonArray("requiredSubjectTypes"), csvData, columns, rawColumns);
            } else {
                // No specific subject types for this questionnaire, output all known subject types
                getSubjectTypes(resolver, csvData, columns, rawColumns);
            }
            csvData.put(CREATED_HEADER, new HashMap<>());
            csvData.put(LAST_MODIFIED_HEADER, new HashMap<>());
            columns.add(CREATED_HEADER);
            rawColumns.add("jcr:created");
            columns.add(LAST_MODIFIED_HEADER);
            rawColumns.add("jcr:lastModified");

            // Get header titles from the questionnaire question objects
            processSectionToHeaderRow(questionnaire, csvData, columns, rawColumns);
            // Print header
            if (!resolutionPathInfo.contains("-csvHeader:labels")) {
                csvPrinter.printRecord(columns);
            }
            if (resolutionPathInfo.contains("csvHeader:raw")) {
                csvPrinter.printRecord(rawColumns);
            }

            // Aggregate form answers to the csvData collector for the CSV output
            if (questionnaire.containsKey("@data")) {
                processFormsToRows(questionnaire.getJsonArray("@data"), csvData, csvPrinter);
            }

            // All done, flush, close and return the CSV
            csvPrinter.close(true);
            return output.toString();
        } catch (final IOException e) {
            LOGGER.error("Error in CSV export of {} questionnaire", questionnaire.getString("@name"));
        }
        return null;
    }

    private void getSubjectTypes(final ResourceResolver resolver, final Map<String, Map<Integer, String>> csvData,
        final List<String> columns, final List<String> rawColumns)
    {
        final Iterator<Resource> subjectTypes = resolver.findResources(
            "SELECT st.* FROM [cards:SubjectType] AS st ORDER BY st.[cards:defaultOrder] ASC", Query.JCR_SQL2);
        while (subjectTypes.hasNext()) {
            final Resource subjectType = subjectTypes.next();
            final ValueMap subjectTypeProperties = subjectType.getValueMap();
            columns.add(subjectTypeProperties.get("label", String.class).concat(" ID"));
            rawColumns.add(subjectType.getPath());
            csvData.put(subjectTypeProperties.get(UUID_PROP, String.class), new HashMap<>());
        }
    }

    private void getSubjectTypes(final JsonArray subjectTypesArray, final Map<String, Map<Integer, String>> csvData,
        final List<String> columns, final List<String> rawColumns)
    {
        final List<JsonObject> subjectTypes = new ArrayList<>();
        subjectTypesArray.stream().map(JsonValue::asJsonObject)
            .forEach(value -> gatherSubjectTypes(value, subjectTypes));
        subjectTypes.sort((t1, t2) -> t1.getInt("cards:defaultOrder") - t2.getInt("cards:defaultOrder"));
        subjectTypes.forEach(subjectType -> {
            columns.add(subjectType.getString("label").concat(" ID"));
            rawColumns.add(subjectType.getString("@path"));
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

    private void processSectionToHeaderRow(final JsonObject questionnaire,
        final Map<String, Map<Integer, String>> csvData,
        final List<String> columns, final List<String> rawColumns)
    {
        questionnaire.values().stream()
            .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
            .map(JsonValue::asJsonObject)
            .filter(value -> value.containsKey(PRIMARY_TYPE_PROP))
            .forEach(value -> processHeaderElement(value, csvData, columns, rawColumns));
    }

    /**
     * Converts a JSON fragment (object) to csv text, if it is a simple question or a section. All other kinds of
     * information are ignored.
     *
     * @param nodeJson a JSON serialization of a node
     * @param csvData data aggregator
     * @param columns a list of column headers
     */
    private void processHeaderElement(final JsonObject nodeJson, final Map<String, Map<Integer, String>> csvData,
        final List<String> columns, final List<String> rawColumns)
    {
        final String nodeType = nodeJson.getString(PRIMARY_TYPE_PROP);
        if ("cards:Section".equals(nodeType)) {
            processSectionToHeaderRow(nodeJson, csvData, columns, rawColumns);
        } else if ("cards:Question".equals(nodeType)) {
            String label = nodeJson.getString("@name");
            rawColumns.add(label);
            if (nodeJson.containsKey("text")) {
                label = nodeJson.getString("text");
            }
            columns.add(label);
            csvData.put(nodeJson.getString(UUID_PROP), new HashMap<>());
        }
    }

    private void processFormsToRows(final JsonArray formsJson, final Map<String, Map<Integer, String>> csvData,
        final CSVPrinter csvPrinter)
    {
        for (final JsonValue form : formsJson) {
            processForm((JsonObject) form, csvData, csvPrinter);
        }
    }

    private void processForm(final JsonObject form, final Map<String, Map<Integer, String>> csvData,
        final CSVPrinter csvPrinter)
    {
        // Collect information regarding the form subjects and subject parents
        if (form.containsKey("subject")) {
            processFormSubjects(form.getJsonObject("subject"), csvData);
        }
        csvData.get(CREATED_HEADER).put(0, form.getString("jcr:created"));
        csvData.get(LAST_MODIFIED_HEADER).put(0, form.getString("jcr:lastModified"));

        // Compute on which row each answer is supposed to be.
        // Without repeatable sections, this would be easy, since everything in a flat form is on the same row.
        // Repeatable sections complicate things since more than one row is needed for the same form.
        // Multiple nested repeatable sections complicate things even more, since the number of rows needed for a
        // subsection may be different from the number of rows needed for a sibling/cousin subsection.
        //
        // Each element has:
        // - a number of levels, which is the maximum number of repeats of its subsections
        // - a height, which is the total number of rows it needs to display itself and all its children
        // - a starting row number, 0-based, which is where the element starts outputting its data
        //
        // A single question has only one level and height of 1.
        // An empty section has nothing to display, so its height/levels is 0.
        // A section's number of levels is max(count(same element instances)).
        // Each level's height is max(height(instance on the level).
        // A section's height is sum(height(level))
        //
        // First, extract the tree structure of the form into a separate structure where we will compute
        // levels/height/start row.
        // Second, going bottom-up, compute the number of levels and height of each element.
        // Third, going top-down, we assign the starting row of each element.
        // Last, we build the CSV rows, copying data from the form on the correct row number.

        // Step one, extract the tree structure of the form.
        final TreeGraph formGraph = new TreeGraph(null, null, true);
        copyFormSection(form, formGraph);

        // Step two, compute the number of levels and height of each element.
        formGraph.computeHeight();

        // Step three, assign starting row numbers
        formGraph.assignStartingRow(0);

        // Step four, copy data from the treegraph into the CSV
        // Tabulate data, transforming the tree into a grid [ question : [ row# : answer ] ]
        formGraph.tabulateData(csvData);
        // Get the maximum number of rows
        final int levels = csvData.values().stream().map(Map::keySet)
            .flatMap(Set::stream)
            .max(Comparator.comparing(Integer::valueOf))
            .get();
        // Assemble and print the CSV row by row
        for (int level = 0; level <= levels; ++level) {
            final List<String> row = new ArrayList<>();
            // First add the form identifier
            row.add(form.getString("@name"));
            // Iterate over the columns; since this is a linked map, the order is always the same as in the header
            for (final Map<Integer, String> answerList : csvData.values()) {
                // Look for the answer with the level <i> in the list of pairs <level,Answer>
                // If none found, we add ""
                row.add(StringUtils.defaultString(answerList.get(level)));
            }
            // Print one row for the level
            try {
                csvPrinter.printRecord(row.toArray());
            } catch (final IOException e) {
                // Should not happen since we're printing to a string
            }
        }

        // Empty csvData for next form
        // By emptying just the inner maps instead of the whole outer map, we preserve the initial order of the columns
        csvData.values().forEach(Map::clear);
    }

    /**
     * Recursively extract the form's subjects and add them to the gathered data.
     *
     * @param subjectJson a JSON serialization of an answer
     * @param data the gathered data
     */
    private void processFormSubjects(final JsonObject subjectJson, final Map<String, Map<Integer, String>> data)
    {
        final Map<Integer, String> subjectColumn = data.get(subjectJson.getJsonObject("type").getString(UUID_PROP));
        if (subjectColumn != null) {
            subjectColumn.put(0, subjectJson.getString("identifier"));
        }
        // Recursively collect all of the subjects parents in the hierarchy
        if (subjectJson.containsKey("parents")) {
            processFormSubjects(subjectJson.getJsonObject("parents"), data);
        }
    }

    /**
     * Adds all the relevant children of an AnswerSection to a TreeGraph.
     *
     * @param answerSectionJson a JSON serialization of an answer section
     * @param formGraph tree structure corresponding to the answer section under which to add all the children
     */
    private void copyFormSection(final JsonObject answerSectionJson, final TreeGraph formGraph)
    {
        answerSectionJson.values().stream()
            .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
            .map(JsonValue::asJsonObject)
            .filter(value -> value.containsKey(PRIMARY_TYPE_PROP)
                && ("cards:AnswerSection".equals(value.getString(PRIMARY_TYPE_PROP))
                    || value.getString(PRIMARY_TYPE_PROP).startsWith("cards:")
                        && value.getString(PRIMARY_TYPE_PROP).endsWith("Answer")))
            .forEach(value -> copyFormElement(value, formGraph));
    }

    /**
     * Add a form element, either an AnswerSection or an Answer, to the TreeGraph. If the element is an AnswerSection,
     * recursively process its children as well.
     *
     * @param nodeJson a JSON serialization of a node
     * @param formGraph tree structure under which to add the element
     */
    private void copyFormElement(final JsonObject nodeJson, final TreeGraph formGraph)
    {
        final String nodeType = nodeJson.getString(PRIMARY_TYPE_PROP);
        if ("cards:AnswerSection".equals(nodeType)) {
            final String uuid = nodeJson.getJsonObject("section").getString(UUID_PROP);
            // Add Section node to the tree
            final TreeGraph sectionTreeNode = new TreeGraph(uuid, null, true);
            formGraph.addChild(sectionTreeNode);
            copyFormSection(nodeJson, sectionTreeNode);
        } else if (nodeType.startsWith("cards:") && nodeType.endsWith("Answer")) {
            final String uuid = nodeJson.getJsonObject("question").getString(UUID_PROP);
            final String answer = getAnswerString(nodeJson, nodeType);
            // Add Answer child to the tree
            formGraph.addChild(new TreeGraph(uuid, answer, false));
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
}
