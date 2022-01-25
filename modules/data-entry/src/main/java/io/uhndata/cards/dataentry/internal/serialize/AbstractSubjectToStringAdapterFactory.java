/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional insubjectation
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

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

/**
 * Base class for adapting a Subject resource to a text-based format.
 *
 * @version $Id$
 */
public abstract class AbstractSubjectToStringAdapterFactory
    implements AdapterFactory
{
    private ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    @Override
    public <A> A getAdapter(final Object adaptable, final Class<A> type)
    {
        if (adaptable == null) {
            return null;
        }

        final Resource originalResource = (Resource) adaptable;
        if (!originalResource.isResourceType("cards/Subject")) {
            return type.cast(originalResource.getPath());
        }

        this.resolver.set(originalResource.getResourceResolver());

        // The proper serialization depends on "deep", "dereference" and "labels", but we may allow other JSON
        // processors to be enabled/disabled to further customize the data, so we also append the original selectors
        final String subjectPath = originalResource.getPath()
            + originalResource.getResourceMetadata().getResolutionPathInfo();
        final Resource resource = this.resolver.get().resolve(subjectPath + ".deep.dereference.labels");

        JsonObject result = resource.adaptTo(JsonObject.class);
        if (result != null) {
            return type.cast(processSubject(result, subjectPath));
        }
        return null;
    }

    /**
     * Converts the JSON representation of a subject into a string.
     * @param subjectJson a JSON serialization of a node
     * @param subjectPath processed subject path
     *
     * @param subjectJson a subject serialized as JSON
     * @return plain text serialization of the subject
     */
    private String processSubject(final JsonObject subjectJson, String subjectPath)
    {
        StringBuilder result = new StringBuilder();
        generateMetadata(subjectJson, result);
        processSubjectForms(subjectJson, result, subjectPath);
        return result.toString().trim();
    }

    /**
     * Converts a JSON fragment (object) to plain text displaying the subject parent, id and creation date.
     *
     * @param subjectJson a JSON serialization of a node
     * @param result the string builder where the serialization must be appended
     */
    private void generateMetadata(final JsonObject subjectJson, StringBuilder result)
    {
        String parent = getSubjectFullIdentifier(subjectJson);
        if (parent != null) {
            formatMetadata(getSubjectFullIdentifier(subjectJson), result);
        }
        formatMetadata("\n", result);
        formatType(getSubjectType(subjectJson), result);
        formatMetadata(getSubjectIdentifier(subjectJson), result);
        formatMetadata("\n", result);
        formatMetadata(getCreationDate(subjectJson), result);
        formatMetadata("\n", result);
    }

    /**
     * Retrieves the human readable subject's parent full identifier from the subject's JSON.
     *
     * @param subjectJson the JSON serialization of a subject
     * @return a full parent identifier, in the format {@code Subject Identifier} or
     *         {@code Parent Subject Identifier / Child Subject Identifier} or null
     */
    private String getSubjectFullIdentifier(final JsonObject subjectJson)
    {
        JsonObject parent = subjectJson.getJsonObject("parents");
        if (parent != null) {
            return parent.getString("fullIdentifier");
        }
        return null;
    }

    /**
     * Retrieves the subject identifier from the subject's JSON.
     *
     * @param subjectJson the JSON serialization of a subject
     * @return an identifier, in the format {@code Subject Identifier}
     */
    private String getSubjectIdentifier(final JsonObject subjectJson)
    {
        return subjectJson.getString("identifier");
    }

    /**
     * Retrieves the subject's type.
     *
     * @param subjectJson the JSON serialization of a subject
     * @return the type, in upper case
     */
    private String getSubjectType(final JsonObject subjectJson)
    {
        return subjectJson.getJsonObject("type").getString("@name");
    }

    /**
     * Retrieves the subject's creation date.
     *
     * @param subjectJson the JSON serialization of a subject
     * @return a date in the {@code YYYY-MM-DD} format
     */
    private String getCreationDate(final JsonObject subjectJson)
    {
        return StringUtils.substringBefore(subjectJson.getString("jcr:created"), "T");
    }

    private void processSubjectForms(final JsonObject subjectJson, StringBuilder result, String processedPath)
    {
        final Resource resource = this.resolver.get().resolve(processedPath + ".data.deep.json");

        JsonObject data = resource.adaptTo(JsonObject.class);
        if (result != null) {
            data.values().stream()
            .filter(value -> ValueType.ARRAY.equals(value.getValueType()))
            .map(JsonValue::asJsonArray)
            .forEach(value -> processQuestionnaireArray(value, result));
        }
    }

    private void processQuestionnaireArray(JsonArray data, StringBuilder result)
    {
        data.stream()
        .filter(value -> ValueType.OBJECT.equals(value.getValueType()))
        .map(JsonValue::asJsonObject)
        .filter(value -> value.containsKey("jcr:primaryType"))
            .forEach(value -> processForm(value, result));
    }

    private void processForm(final JsonObject nodeJson, final StringBuilder result)
    {
        final String nodeType = nodeJson.getString("jcr:primaryType");
        if (!"cards:Form".equals(nodeType)) {
            return;
        }
        // Process form
        String formPath = nodeJson.getString("@path");
        final Resource formResource = this.resolver.get().resolve(formPath + ".deep.dereference.labels");

        formatForm(formResource, result);
    }

    abstract void formatMetadata(String metadata, StringBuilder result);

    abstract void formatType(String metadata, StringBuilder result);

    abstract void formatForm(Resource resource, StringBuilder result);
}
