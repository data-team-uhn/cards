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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

/**
 * AdapterFactory that converts forms to plain text.
 *
 * @version $Id$
 */
@Component(
    service = { AdapterFactory.class },
    property = { "adaptables=org.apache.sling.api.resource.Resource", "adapters=java.lang.String" })
public class FormToTextAdapterFactory
    implements AdapterFactory
{
    @Override
    public <A> A getAdapter(final Object adaptable, final Class<A> type)
    {
        if (adaptable == null) {
            return null;
        }

        final Resource originalResource = (Resource) adaptable;
        if (!originalResource.isResourceType("cards/Form")) {
            return type.cast(originalResource.getPath());
        }

        // The proper serialization depends on "deep", "dereference" and "labels", but we may allow other JSON
        // processors to be enabled/disabled to further customize the data, so we also append the original selectors
        final String processedPath = originalResource.getPath()
            + originalResource.getResourceMetadata().getResolutionPathInfo() + ".deep.dereference.labels";
        final Resource resource = originalResource.getResourceResolver().resolve(processedPath);

        JsonObject result = resource.adaptTo(JsonObject.class);
        if (result != null) {
            return type.cast(processForm(result));
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
        result.append(getSubjectIdentifier(formJson)).append('\n');
        result.append(getQuestionnaireTitle(formJson)).append('\n');
        result.append(getCreationDate(formJson)).append("\n\n\n");

        // Answers and sections
        final Map<String, Integer> sectionCounts = new HashMap<>();
        processSection(formJson, result, sectionCounts);

        // All done!
        return result.toString().trim();
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
        return formJson.getJsonObject("questionnaire").getString("title").toUpperCase(Locale.ROOT);
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
     * of information is ignored.
     *
     * @param nodeJson a JSON serialization of a node
     * @param result the string builder where the serialization must be appended
     * @param sectionCounts a map keeping track of the number of instances encountered so far for recurrent sections
     */
    private void processElement(final JsonObject nodeJson, final StringBuilder result,
        final Map<String, Integer> sectionCounts)
    {
        final String nodeType = nodeJson.getString("jcr:primaryType");
        if ("cards:AnswerSection".equals(nodeType)) {
            processSection(nodeJson, result, sectionCounts);
        } else if ("cards:PedigreeAnswer".equals(nodeType)) {
            processPedigreeAnswer(nodeJson, result);
        } else if (nodeType.startsWith("cards:") && nodeType.endsWith("Answer")) {
            processAnswer(nodeJson, result);
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
        final String sectionTitle = getSectionTitle(answerSectionJson, sectionCounts);
        if (StringUtils.isNotBlank(sectionTitle)) {
            result.append(sectionTitle).append("\n\n");
        }
        answerSectionJson.values().stream()
            .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
            .map(JsonValue::asJsonObject)
            .filter(value -> value.containsKey("jcr:primaryType"))
            .forEach(value -> processElement(value, result, sectionCounts));
    }

    /**
     * Converts a JSON serialization of an answer to plain text. This adds the question, followed by any answers,
     * followed by any notes. If there is no answer and no notes, the whole answer is ignored and nothing is added to
     * the output.
     *
     * @param answerJson a JSON serialization of an answer
     * @param result the string builder where the serialization must be appended
     */
    private void processAnswer(final JsonObject answerJson, final StringBuilder result)
    {
        final JsonValue value = answerJson.get("displayedValue");
        final String note = answerJson.containsKey("note") ? answerJson.getString("note") : null;
        if (value == null && StringUtils.isBlank(note)) {
            return;
        }

        result.append(answerJson.getJsonObject("question").getString("text")).append('\n');
        if (value == null) {
            // Ignore null values, we probably only have notes
        } else if (ValueType.ARRAY.equals(value.getValueType())) {
            value.asJsonArray().forEach(v -> result.append("  ").append(((JsonString) v).getString()).append('\n'));
        } else {
            result.append("  ").append(((JsonString) value).getString()).append('\n');
        }
        if (StringUtils.isNotBlank(note)) {
            result.append("\n  NOTES:\n  ").append(note.replaceAll("\n", "\n  ")).append('\n');
        }
        result.append("\n\n");
    }

    /**
     * Converts a JSON serialization of a pedigree answer to plain text. Normal answer processing through
     * {@link #processAnswer} would include the whole pedigree image SVG source, but that is not a proper user-friendly
     * answer, so instead a simple "Pedigree provided" answer is included in the plain text serialization if indeed a
     * pedigree is included. Otherwise, the whole question is skipped.
     *
     * @param answerJson a JSON serialization of a pedigree answer
     * @param result the string builder where the serialization must be appended
     */
    private void processPedigreeAnswer(final JsonObject answerJson, final StringBuilder result)
    {
        final JsonValue value = answerJson.get("displayedValue");
        final String note = answerJson.containsKey("note") ? answerJson.getString("note") : null;
        if (value == null && StringUtils.isBlank(note)) {
            return;
        }

        result.append(answerJson.getJsonObject("question").getString("text")).append('\n');
        if (value == null) {
            // Ignore null values, we probably only have notes
        } else {
            result.append("  Pedigree provided\n");
        }
        if (StringUtils.isNotBlank(note)) {
            result.append("\n  NOTES:\n  ").append(note.replaceAll("\n", "\n  ")).append('\n');
        }
        result.append("\n\n");
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
            return label.toUpperCase(Locale.ROOT) + getSectionInstanceSuffix(answerSectionJson, sectionCounts);
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
        final boolean recurrent = answerSectionJson.getJsonObject("section").getBoolean("recurrent");
        if (recurrent) {
            final int instanceNumber =
                sectionCounts.compute(answerSectionJson.getJsonObject("section").getString("jcr:uuid"),
                    (k, v) -> v == null ? 1 : v + 1);
            return " #" + instanceNumber;
        }

        return "";
    }
}
