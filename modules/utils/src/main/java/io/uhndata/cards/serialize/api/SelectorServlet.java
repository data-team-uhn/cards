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

package io.uhndata.cards.serialize.api;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.uhndata.cards.serialize.spi.DataFilterFactory;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;
import io.uhndata.cards.serialize.spi.SelectorDetails;
import io.uhndata.cards.serialize.spi.SelectorDetails.SelectorOption;

/**
 * A servlet that lists the available {@code ResourceJsonProcessor}s and {@code DataFilter}s
 * which can be used as selectors for data serializtion or exports.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/ResourceHomepage" }, extensions = {
    "selectors" }, methods = { "GET", "POST" })
public class SelectorServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = 2558430802619674046L;

    /** A list of all available processors. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<ResourceJsonProcessor> allJsonProcessors;

    /** A list of all available processors. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<DataFilterFactory> allFilters;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        response.setCharacterEncoding("UTF-8");

        JsonObject result = Json.createObjectBuilder()
            .add("Processors", getSelectorJson(getProcessorDetails()))
            .add("Filters", getSelectorJson(getFilterDetails()))
            .build();
        final Writer out = response.getWriter();
        if (request.getPathInfo().endsWith(".md")) {
            response.setContentType("text/markdown");
            writeMarkdown(out, result);
        } else {
            response.setContentType("application/json");
            out.write(result.toString());
        }
    }

    /**
     * Get the Details to be output for all ResourceToJsonProcessors.
     */
    private List<SelectorDetails> getProcessorDetails()
    {
        List<SelectorDetails> results = new ArrayList<>();
        for (ResourceJsonProcessor processor : this.allJsonProcessors) {
            results.add(processor.getDetails());
        }
        return results;
    }

    /**
     * Get the Details to be output for all DataFilterFactories.
     */
    private List<SelectorDetails> getFilterDetails()
    {
        List<SelectorDetails> results = new ArrayList<>();
        for (DataFilterFactory filter : this.allFilters) {
            results.addAll(filter.getFilterDetails());
        }
        return results;
    }

    private JsonObjectBuilder getSelectorJson(List<SelectorDetails> selectorDetails)
    {
        Map<String, List<JsonObjectBuilder>> processorDetails = new HashMap<>();
        for (SelectorDetails selector : selectorDetails) {
            List<JsonObjectBuilder> implementations;
            String name = selector.getName();
            if (processorDetails.containsKey(name)) {
                implementations = processorDetails.get(name);
            } else {
                implementations = new ArrayList<>();
            }
            implementations.add(outputSelector(selector));
            processorDetails.put(name, implementations);
        }

        JsonObjectBuilder results = Json.createObjectBuilder();
        processorDetails.forEach((key, value) -> {
            if (value.size() == 1) {
                results.add(key, value.get(0));
            } else {
                JsonArrayBuilder implementations = Json.createArrayBuilder();
                value.forEach(processor -> implementations.add(processor));
                results.add(key, Json.createObjectBuilder().add("Implementations", implementations));
            }
        });

        return results;
    }

    private JsonObjectBuilder outputSelector(SelectorDetails details)
    {
        JsonObjectBuilder result = Json.createObjectBuilder()
            .add("Description", details.getDescription());
        if (details.isEnabledByDefault()) {
            result.add("isEnabledByDefault", true);
        }
        if (details.getOptions().length > 0) {
            JsonObjectBuilder optionsJson = Json.createObjectBuilder();
            for (SelectorOption option : details.getOptions()) {
                optionsJson.add(option.getName(), option.getDescription());
            }
            result.add("Options", optionsJson);
        }
        return result;
    }

    private void writeMarkdown(Writer out, JsonObject result)
        throws IOException
    {
        writeObject(out, result, "#", "");
    }

    private void writeValue(Writer out, JsonValue result, String header, String indentation)
        throws IOException
    {
        switch (result.getValueType()) {
            case OBJECT:
                writeObject(out, result.asJsonObject(), header, indentation);
                break;
            case ARRAY:
                writeArray(out, result.asJsonArray(), header, indentation);
                break;
            default:
                writeProperty(out, result, header, indentation);
                break;
        }
    }

    private void writeObject(Writer out, JsonObject result, String header, String indentation)
        throws IOException
    {
        List<Entry<String, JsonValue>> childArrays = new ArrayList<>();
        List<Entry<String, JsonValue>> childObjects = new ArrayList<>();

        for (Entry<String, JsonValue> entry : result.entrySet()) {
            ValueType type = entry.getValue().getValueType();
            if (type.equals(ValueType.ARRAY)) {
                childArrays.add(entry);
            } else if (type.equals(ValueType.OBJECT)) {
                childObjects.add(entry);
            } else {
                writeProperty(out, entry.getKey(), entry.getValue(), header, indentation);
            }
        }


        for (Entry<String, JsonValue> array : childArrays) {
            out.write(String.format("%s#%s %s:\n", indentation, header, array.getKey()));
            writeArray(out, array.getValue().asJsonArray(), header, indentation);
        }

        childObjects.stream()
            .sorted((Entry<String, JsonValue> first, Entry<String, JsonValue> second)
                -> first.getKey().compareTo(second.getKey()))
            .forEach((Entry<String, JsonValue> object) -> {
                try {
                    out.write(String.format("%s#%s %s:\n", indentation, header, object.getKey()));
                    writeObject(out, object.getValue().asJsonObject(), "#" + header, indentation);
                } catch (IOException e) {
                    // TODO: Should not happen
                }
            });

    }

    private void writeArray(Writer out, JsonArray result, String header, String indentation)
        throws IOException
    {
        for (JsonValue entry : result) {
            out.write(String.format("%s- ", indentation));
            writeValue(out, entry, header, indentation);
        }
    }

    private void writeProperty(Writer out, String key, JsonValue value, String header, String indentation)
        throws IOException
    {
        out.write(String.format("%s**%s**: %s\n", indentation, key, writeString(value.toString(), indentation)));
    }

    private void writeProperty(Writer out, JsonValue value, String header, String indentation)
        throws IOException
    {
        out.write(String.format("%s%s\n", indentation, writeString(value.toString(), indentation)));
    }

    private String writeString(String str, String indentation)
    {
        String newString = str;
        if (newString.startsWith("\"") && newString.endsWith("\"")) {
            newString = newString.substring(1, newString.length() - 1);
        }
        newString = newString.replaceAll("\\\\n", indentation + "\n");
        return newString;
    }
}
