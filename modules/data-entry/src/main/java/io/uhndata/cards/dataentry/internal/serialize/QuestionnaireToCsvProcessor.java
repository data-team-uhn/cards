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
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.dataentry.internal.serialize.labels.ResourceCSVProcessor;

/**
 * CSV serializer that can process Questionnaires.
 *
 * @version $Id$
 */
@Component(service = ResourceCSVProcessor.class)
public class QuestionnaireToCsvProcessor implements ResourceCSVProcessor
{
    @Override
    public boolean canProcess(final Resource resource)
    {
        return resource.isResourceType("cards/Questionnaire");
    }

    @Override
    public String serialize(Resource resource)
    {
        // The proper serialization depends on "deep", "dereference" and "labels", but we may allow other JSON
        // processors to be enabled/disabled to further customize the data, so we also append the original selectors
        final String processedPath = resource.getPath()
            + resource.getResourceMetadata().getResolutionPathInfo() + ".deep.dereference.labels";
        JsonObject result = resource.getResourceResolver().resolve(processedPath).adaptTo(JsonObject.class);

        if (result != null) {
            return processQuestionnaire(result);
        }
        return null;
    }

    private String processQuestionnaire(JsonObject questionnaire)
    {
        try {
            final StringWriter stringWriter = new StringWriter();
            CSVPrinter csvPrinter = new CSVPrinter(stringWriter, CSVFormat.DEFAULT);

            // CSV data strings aggregator where the key is Question uuid that maps to the list of
            // answers strings to form scv
            final Map<String, List<String>> csvData = new LinkedHashMap<>();
            csvData.put("Patient ID", new ArrayList<>());
            csvData.put("Created", new ArrayList<>());

            // collect column headers
            List<String> columns = new ArrayList<>();
            columns.add("Patient ID");
            columns.add("Created");

            // get header titles from the questionnaire question objects
            processSectionToHeaderRow(questionnaire, csvData, columns);
            // print header
            csvPrinter.printRecord((Object[]) columns.toArray(String[]::new));

            if (questionnaire.containsKey("data")) {
                processFormsToRows(questionnaire.getJsonArray("data"), csvData);
            }

            // Generate CSV output String
            // Assemble by rows
            for (int i = 0; i < csvData.get("Patient ID").size(); i++) {
                // iterate over the columns
                List<String> row = new ArrayList<>();
                for (String uuid : csvData.keySet()) {
                    row.add(csvData.get(uuid).get(i));
                }
                // print row
                csvPrinter.printRecord((Object[]) row.toArray(String[]::new));
            }

            final String result = stringWriter.toString();

            csvPrinter.flush();
            csvPrinter.close();
            return result;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private void processSectionToHeaderRow(JsonObject questionnaire, Map<String, List<String>> csvData,
        List<String> columns)
    {
        questionnaire.values().stream()
            .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
            .map(JsonValue::asJsonObject)
            .filter(value -> value.containsKey("jcr:primaryType"))
            .forEach(value -> processHeaderElement(value, csvData, columns));
    }

    /**
     * Converts a JSON fragment (object) to csv text, if it is a simple question or a section. All other kinds
     * of information are ignored.
     *
     * @param nodeJson a JSON serialization of a node
     * @param csvData data aggregator
     * @param columns a list of column headers
     */
    private void processHeaderElement(final JsonObject nodeJson, Map<String, List<String>> csvData,
        List<String> columns)
    {
        final String nodeType = nodeJson.getString("jcr:primaryType");
        if ("cards:Section".equals(nodeType)) {
            final String displayMode = getDisplayMode("section", nodeJson);
            if (!"default".equals(displayMode)) {
                // Do not output summary, headers or footers sections
                return;
            }
            processSectionToHeaderRow(nodeJson, csvData, columns);
        } else if ("cards:Question".equals(nodeType)) {
            final String displayMode = getDisplayMode("question", nodeJson);
            if ("hidden".equals(displayMode)) {
                return;
            }

            String label = nodeJson.getString("@name");
            if (nodeJson.containsKey("text")) {
                label = nodeJson.getString("text");
            }
            columns.add(label);
            csvData.put(nodeJson.getString("jcr:uuid"), new ArrayList<>());
        }
    }

    private void processFormsToRows(JsonArray formsJson, Map<String, List<String>> csvData)
    {
        formsJson.stream()
            .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
            .map(JsonValue::asJsonObject)
            .forEach(form -> processForm(form, csvData));
    }

    private void processForm(final JsonObject form, Map<String, List<String>> csvData)
    {
        if (form.containsKey("subject")) {
            JsonObject subject = form.getJsonObject("subject");
            csvData.get("Patient ID").add(subject.getString("identifier"));
        }
        csvData.get("Created").add(form.getString("jcr:created"));
        processFormSection(form, csvData);
    }

    /**
     * Converts a JSON serialization of an answer section to CSV text.
     *
     * @param answerSectionJson a JSON serialization of an answer section
     * @param csvData the string builder where the serialization must be appended
     */
    private void processFormSection(final JsonObject answerSectionJson, Map<String, List<String>> csvData)
    {
        answerSectionJson.values().stream()
            .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
            .map(JsonValue::asJsonObject)
            .filter(value -> value.containsKey("jcr:primaryType"))
            .forEach(value -> processFormElement(value, csvData));
        //if (section.hasProperty("recurrent") && section.getProperty("recurrent").getBoolean())
    }

    /**
     * Process answer to fill in data aggregator.
     *
     * @param nodeJson a JSON serialization of an answer node
     * @param csvData data aggregator
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private void processFormElement(final JsonObject nodeJson, Map<String, List<String>> csvData)
    {
        final String nodeType = nodeJson.getString("jcr:primaryType");
        if ("cards:AnswerSection".equals(nodeType) || "cards:Section".equals(nodeType)) {
            processFormSection(nodeJson, csvData);
        } else if (nodeType.startsWith("cards:") && nodeType.endsWith("Answer")) {

            String uuid = nodeJson.getJsonObject("question").getString("jcr:uuid");
            List<String> answerColumn = csvData.get(uuid);

            final JsonValue value = nodeJson.get("value");
            if (value == null) {
                // Fallback to "displayedValue"
                final JsonValue displayedValue = nodeJson.get("displayedValue");
                if (displayedValue == null) {
                    answerColumn.add("");
                } else {
                    answerColumn.add(((JsonObject) displayedValue).getString("displayedValue"));
                }
            } else if ("cards:PedigreeAnswer".equals(nodeType) || "cards:FileAnswer".equals(nodeType)) {
                answerColumn.add("yes");
            } else {
                if (ValueType.ARRAY.equals(value.getValueType())) {
                    final String answer = value.asJsonArray().stream()
                                                             .map(v -> ((JsonString) v).getString())
                                                             .reduce((result, v) -> result + ";" + v).get();
                    answerColumn.add(answer);
                } else if (ValueType.STRING.equals(value.getValueType())) {
                    answerColumn.add(((JsonString) value).getString());
                } else {
                    answerColumn.add(value.toString());
                }
            }
        }
    }

    /**
     * Retrieves the display mode specified for a section, if any.
     *
     * @param element a kind of questionnaire element: question or section
     * @param answerElementJson a JSON serialization of an answer or answer section
     * @return the display mode as String, e.g. hidden, default, header, footer, summary.
     */
    private String getDisplayMode(final String element, final JsonObject answerElementJson)
    {
        try {
            return ((JsonString) answerElementJson.getValue("/" + element + "/displayMode")).getString();
        } catch (JsonException | NullPointerException ex) {
            // Not there, return
        }
        return null;
    }
}
