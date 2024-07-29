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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

/**
 * Base class for adapting a Form resource to a text-based format.
 *
 * @version $Id$
 */
public abstract class AbstractFormToStringSerializer
{
    private static final String SECTION_KEY = "section";
    private static final String QUESTION_KEY = "question";
    private static final String PRIMARY_TYPE_KEY = "jcr:primaryType";
    private static final String UUID_KEY = "jcr:uuid";

    protected String toString(final Resource originalResource)
    {
        // The proper serialization depends on "deep", "dereference" and "labels", but we may allow other JSON
        // processors to be enabled/disabled to further customize the data, so we also append the original selectors
        final String processedPath = originalResource.getPath()
            + originalResource.getResourceMetadata().getResolutionPathInfo() + ".deep.dereference.labels";
        final Resource resource = originalResource.getResourceResolver().resolve(processedPath);

        JsonObject result = resource.adaptTo(JsonObject.class);
        if (result != null) {
            return processForm(result);
        }
        return null;
    }

    /**
     * Converts the JSON representation of a form into a string.
     *
     * @param formJson a form serialized as JSON
     * @return plain text serialization of the form
     */
    private String processForm(final JsonObject formJson)
    {
        StringBuilder result = new StringBuilder();

        // Metadata
        generateMetadata(formJson, result);

        // Answers and sections
        final Map<String, Integer> sectionCounts = new HashMap<>();
        processSection(formJson, result, sectionCounts);

        // All done!
        return result.toString().trim();
    }

    /**
     * Converts a JSON fragment (object) to plain text displaying the form's subject, questionnaire title, and creation
     * date.
     *
     * @param nodeJson a JSON serialization of a node
     * @param result the string builder where the serialization must be appended
     */
    private void generateMetadata(final JsonObject formJson, StringBuilder result)
    {
        formatSubject(getSubjectIdentifier(formJson), result);
        formatTitle(getQuestionnaireTitle(formJson), result);
        formatDate(getCreationDate(formJson), result);
        formatMetadataSeparator(result);
    }

    /**
     * Retrieves the human readable subject full identifier from the form's JSON.
     *
     * @param formJson the JSON serialization of a form
     * @return a full identifier, in the format {@code Subject Identifier} or
     *         {@code Parent Subject Identifier / Child Subject Identifier}
     */
    private String getSubjectIdentifier(final JsonObject formJson)
    {
        return formJson.getJsonObject("subject").getString("fullIdentifier");
    }

    /**
     * Retrieves the form's questionnaire title.
     *
     * @param formJson the JSON serialization of a form
     * @return the questionnaire title, in upper case
     */
    private String getQuestionnaireTitle(final JsonObject formJson)
    {
        return formJson.getJsonObject("questionnaire").getString("title");
    }

    /**
     * Retrieves the form's creation date.
     *
     * @param formJson the JSON serialization of a form
     * @return a date in the {@code YYYY-MM-DD} format
     */
    private String getCreationDate(final JsonObject formJson)
    {
        return StringUtils.substringBefore(formJson.getString("jcr:created"), "T");
    }

    /**
     * Converts a JSON fragment (object) to plain text, if it is a simple answer or an answer section. All other kinds
     * of information are ignored.
     *
     * @param nodeJson a JSON serialization of a node
     * @param result the string builder where the serialization must be appended
     * @param sectionCounts a map keeping track of the number of instances encountered so far for recurrent sections
     */
    private void processElement(final JsonObject nodeJson, final StringBuilder result,
        final Map<String, Integer> sectionCounts)
    {
        final String nodeType = nodeJson.getString(PRIMARY_TYPE_KEY);
        if (FormUtils.ANSWER_SECTION_NODETYPE.equals(nodeType)) {
            processSection(nodeJson, result, sectionCounts);
        } else if (nodeType.startsWith("cards:") && nodeType.endsWith("Answer")) {
            processAnswer(nodeJson, nodeType, result);
        } else if (QuestionnaireUtils.INFORMATION_NODETYPE.equals(nodeType)) {
            processInformation(nodeJson, result);
        }
    }

    /**
     * Converts a JSON serialization of an answer section to plain text. This may add a section title, if present,
     * followed by all the subsections and answers contained in this answer section.
     *
     * @param answerSectionJson a JSON serialization of an answer section
     * @param result the string builder where the serialization must be appended
     * @param sectionCounts a map keeping track of the number of instances encountered so far for recurrent sections
     */
    private void processSection(final JsonObject answerSectionJson, final StringBuilder result,
        final Map<String, Integer> sectionCounts)
    {
        final JsonObject definition;
        if (answerSectionJson.containsKey("questionnaire")
            && answerSectionJson.get("questionnaire").getValueType() == ValueType.OBJECT) {
            definition = answerSectionJson.getJsonObject("questionnaire");
        } else if (answerSectionJson.containsKey(SECTION_KEY)
            && answerSectionJson.get(SECTION_KEY).getValueType() == ValueType.OBJECT) {
            definition = answerSectionJson.getJsonObject(SECTION_KEY);
        } else {
            definition = Json.createObjectBuilder().build();
        }

        final String displayMode = getDisplayMode(SECTION_KEY, answerSectionJson);
        if ("summary".equals(displayMode)) {
            // Do not output summary sections
            return;
        }
        if ("footer".equals(displayMode)) {
            formatSectionSeparator(result);
        }
        final String sectionTitle = getSectionTitle(answerSectionJson, sectionCounts);
        if (StringUtils.isNotBlank(sectionTitle)) {
            formatSectionTitle(sectionTitle, result);
        }
        Stream.concat(
            // Take the answers and answer sections from the form
            answerSectionJson.values().stream()
                .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
                .map(JsonValue::asJsonObject)
                .filter(value -> value.containsKey(PRIMARY_TYPE_KEY)),
            // Take the printable information nodes from the questionnaire
            definition.values().stream()
                .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
                .map(JsonValue::asJsonObject)
                .filter(value -> value.containsKey(PRIMARY_TYPE_KEY))
                .filter(value -> QuestionnaireUtils.INFORMATION_NODETYPE.equals(value.getString(PRIMARY_TYPE_KEY)))
                .filter(value -> value.containsKey(QuestionnaireUtils.INFORMATION_DISPLAY_MODE_PROPERTY)
                    && !"edit".equals(value.getString(QuestionnaireUtils.INFORMATION_DISPLAY_MODE_PROPERTY))))
            .sorted(new DefinitionComparator(definition))
            .forEach(value -> processElement(value, result, sectionCounts));
        if ("header".equals(displayMode)) {
            formatSectionSeparator(result);
        }
    }

    /**
     * Converts a JSON serialization of an answer to plain text. This adds the question, followed by any answers,
     * followed by any notes. If there is no answer and no notes, the whole answer is ignored and nothing is added to
     * the output.
     *
     * @param answerJson a JSON serialization of an answer
     * @param nodeType the type of node, dictating how answers are displayed
     * @param result the string builder where the serialization must be appended
     */
    private void processAnswer(final JsonObject answerJson, final String nodeType, final StringBuilder result)
    {
        final String displayMode = getDisplayMode(QUESTION_KEY, answerJson);
        if (displayMode == null || "hidden".equals(displayMode) || "summary".equals(displayMode)) {
            return;
        }

        final JsonValue value = answerJson.get("displayedValue");
        final String note = answerJson.containsKey("note") ? answerJson.getString("note") : null;

        final String questionText = getQuestionText(answerJson);
        final boolean questionHasText = StringUtils.isNotBlank(questionText);
        if (questionHasText) {
            formatQuestion(questionText, result);
        }

        if (value == null) {
            if (questionHasText) {
                formatAnswer("—", result);
            }
        } else if ("cards:PedigreeAnswer".equals(nodeType)) {
            formatPedigree(((JsonString) value).getString(), result);
        } else {
            processAnswerValue(value, result);
        }
        if (StringUtils.isNotBlank(note)) {
            formatNote(note, result);
        }
    }

    private void processInformation(final JsonObject informationDefinition, final StringBuilder result)
    {
        formatInformation(informationDefinition.getString("text"), result);
    }

    /**
     * Converts a JSON serialization of a textual answer value to plain text.
     *
     * @param value the value to be displayed, can be either a single value or an array of values
     * @param result the string builder where the serialization must be appended
     */
    private void processAnswerValue(final JsonValue value, final StringBuilder result)
    {
        if (ValueType.ARRAY.equals(value.getValueType())) {
            value.asJsonArray().forEach(v -> formatAnswer(((JsonString) v).getString(), result));
        } else {
            formatAnswer(((JsonString) value).getString(), result);
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
        } catch (JsonException | NullPointerException | ClassCastException ex) {
            // Not there, return
        }
        return null;
    }

    /**
     * Retrieves the text of a question, if any. If the question cannot be accessed or doesn't have a text,
     * {@code null} is returned.
     *
     * @param answerJson a JSON serialization of an answer
     * @return the question text, or {@code null}
     */
    private String getQuestionText(final JsonObject answerJson)
    {
        try {
            return answerJson.getJsonObject(QUESTION_KEY).getString("text");
        } catch (JsonException | NullPointerException | ClassCastException ex) {
            // Not there, return
        }
        return null;
    }

    /**
     * Retrieves the label of an answer section, if any. For recurrent sections, a {@code #number} is also appended to
     * mark each instance. If the section doesn't have a label, {@code null} is returned.
     *
     * @param answerSectionJson a JSON serialization of an answer section
     * @param sectionCounts a map keeping track of the number of instances encountered so far for recurrent sections
     * @return a label in the format {@code SECTION LABEL}, {@code SECTION LABEL #1}, or {@code null}
     */
    private String getSectionTitle(final JsonObject answerSectionJson, final Map<String, Integer> sectionCounts)
    {
        try {
            String label = ((JsonString) answerSectionJson.getValue("/section/label")).getString();
            return label + getSectionInstanceSuffix(answerSectionJson, sectionCounts);
        } catch (JsonException | NullPointerException ex) {
            // Not there, return
        }
        return null;
    }

    /**
     * Retrieves the suffix to use for a recurrent section, if needed. If the section is not recurrent, an empty string
     * is returned. If the section is recurrent, an increasing counter is returned, starting from {@code 1} for the
     * first instance.
     *
     * @param answerSectionJson a JSON serialization of an answer section
     * @param sectionCounts a map keeping track of the number of instances encountered so far for recurrent sections
     * @return a string in the format {@code " #1"} (with a leading blank space), or an empty string if the section is
     *         not recurrent
     */
    private String getSectionInstanceSuffix(final JsonObject answerSectionJson,
        final Map<String, Integer> sectionCounts)
    {
        final boolean recurrent = answerSectionJson.getJsonObject(SECTION_KEY).getBoolean("recurrent");
        if (recurrent) {
            final int instanceNumber =
                sectionCounts.compute(answerSectionJson.getJsonObject(SECTION_KEY).getString(UUID_KEY),
                    (k, v) -> v == null ? 1 : v + 1);
            return " #" + instanceNumber;
        }

        return "";
    }

    abstract void formatSubject(String metadata, StringBuilder result);

    abstract void formatTitle(String metadata, StringBuilder result);

    abstract void formatDate(String metadata, StringBuilder result);

    abstract void formatMetadataSeparator(StringBuilder result);

    abstract void formatSectionTitle(String title, StringBuilder result);

    abstract void formatSectionSeparator(StringBuilder result);

    abstract void formatQuestion(String question, StringBuilder result);

    abstract void formatEmptyAnswer(StringBuilder result);

    abstract void formatAnswer(String answer, StringBuilder result);

    abstract void formatPedigree(String value, StringBuilder result);

    abstract void formatNote(String note, StringBuilder result);

    void formatInformation(String text, StringBuilder result)
    {
        // By default we don't include Information blocks in text mode
    }

    class DefinitionComparator implements Comparator<JsonObject>
    {
        private final List<String> definitionUuids;

        DefinitionComparator(final JsonObject definition)
        {
            this.definitionUuids = definition.values().stream()
                .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
                .map(JsonValue::asJsonObject)
                .filter(value -> value.containsKey(UUID_KEY))
                .map(value -> value.getString(UUID_KEY))
                .collect(Collectors.toList());
        }

        @Override
        public int compare(JsonObject json1, JsonObject json2)
        {
            final String uuid1 = this.getDefinitionUuid(json1);
            final String uuid2 = this.getDefinitionUuid(json2);

            if (this.definitionUuids.contains(uuid1) && this.definitionUuids.contains(uuid2)) {
                return this.definitionUuids.indexOf(uuid1) - this.definitionUuids.indexOf(uuid2);
            } else {
                return 0;
            }
        }

        private String getDefinitionUuid(JsonObject json)
        {
            String uuid = "";
            if (json.containsKey(SECTION_KEY)) {
                uuid = json.get(SECTION_KEY).getValueType() == ValueType.OBJECT
                    ? json.getJsonObject(SECTION_KEY).getString(UUID_KEY) : json.getString(SECTION_KEY);
            } else if (json.containsKey(QUESTION_KEY)) {
                uuid = json.get(QUESTION_KEY).getValueType() == ValueType.OBJECT
                    ? json.getJsonObject(QUESTION_KEY).getString(UUID_KEY) : json.getString(QUESTION_KEY);
            } else if (QuestionnaireUtils.INFORMATION_NODETYPE.equals(json.getString(PRIMARY_TYPE_KEY))) {
                uuid = json.getString(UUID_KEY);
            }
            return uuid;
        }
    }
}
