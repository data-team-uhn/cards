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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.Servlet;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.uhndata.cards.serialize.spi.DataFilterFactory;
import io.uhndata.cards.serialize.spi.ResourceCSVProcessor;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;
import io.uhndata.cards.serialize.spi.SelectorDetails;
import io.uhndata.cards.serialize.spi.SelectorDetails.SelectorOption;

/**
 * A servlet that lists the available {@code ResourceJsonProcessor}s, {@code DataFilter}s and
 * {@code ResourceCSVProcessor}s which can be used as selectors for data serializtion or exports.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/ResourceHomepage" }, extensions = {
    "selectors" }, methods = { "GET", "POST" })
public class SelectorServlet extends SlingJakartaSafeMethodsServlet
{
    private static final long serialVersionUID = 2558430802619674046L;

    /** A list of all available JsonProcessors. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<ResourceJsonProcessor> allJsonProcessors;

    /** A list of all available DataFilterFactories. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<DataFilterFactory> allFilters;

    /** A list of all available CSV processors. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<ResourceCSVProcessor> allCSVProcessors;

    @Override
    public void doGet(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        response.setCharacterEncoding("UTF-8");

        /* A map containing:
         * - All the types of selectors, keyed by displayed name, ordered by insertion order
         *   - A map of all selectors of this type, keyed and ordered by name
         *     - A list of selector details that use this name
         */
        Map<String, Map<String, List<SelectorDetails>>> allDetails = new LinkedHashMap<>();
        allDetails.put("Processors", getProcessorDetails());
        allDetails.put("Filters", getFilterDetails());
        allDetails.put("CSV Adapter Options", getCSVDetails());

        final Writer out = response.getWriter();
        if (request.getPathInfo().endsWith(".md")) {
            response.setContentType("text/markdown");
            writeMarkdown(out, allDetails);
        } else {
            response.setContentType("application/json");
            writeJson(out, allDetails);
        }
    }

    /**
     * Get the Details to be output for all ResourceToJsonProcessors.
     */
    private Map<String, List<SelectorDetails>> getProcessorDetails()
    {
        Map<String, List<SelectorDetails>> results = new TreeMap<>();
        for (ResourceJsonProcessor processor : this.allJsonProcessors) {
            addSelectorDetails(results, processor.getDetails());
        }
        return results;
    }

    /**
     * Get the Details to be output for all DataFilterFactories.
     *
     * @return a sorted map of all selector details, keyed and ordered by selector name.
     *         The value for each entry will be a list of all selector details with a given name.
     */
    private Map<String, List<SelectorDetails>> getFilterDetails()
    {
        return addDetailsFromLists(this.allFilters.stream().map(p -> p.getFilterDetails()));
    }

    /**
     * Get the Details to be output for all ResourceCSVProcessors.
     *
     * @return a sorted map of all selector details, keyed and ordered by selector name.
     *         The value for each entry will be a list of all selector details with a given name.
     */
    private Map<String, List<SelectorDetails>> getCSVDetails()
    {
        return addDetailsFromLists(this.allCSVProcessors.stream().map(p -> p.getDetails()));
    }

    /**
     * Add the details from multiple lists of details.
     *
     * @param stream A stream containing all the lists of details to pull details from
     * @return a sorted map of all selector details, keyed and ordered by selector name.
     *         The value for each entry will be a list of all selector details with a given name.
     */
    private Map<String, List<SelectorDetails>> addDetailsFromLists(Stream<List<SelectorDetails>> stream)
    {
        Map<String, List<SelectorDetails>> results = new TreeMap<>();
        stream.forEach(detailsList -> {
            for (SelectorDetails details : detailsList) {
                addSelectorDetails(results, details);
            }
        });
        return results;
    }

    /**
     * Add the SelectorDetails to the output map.
     * If there is already an entry for the selector's name, add it to that list.
     * Otherwise, create a new list containing this SelectorDetails and insert it into the map.
     *
     * @param results The map to add the details to
     * @param details The {@code SelectorDetails} to add
     */
    private void addSelectorDetails(Map<String, List<SelectorDetails>> results, SelectorDetails details)
    {
        String name = details.getName();
        if (results.containsKey(name)) {
            results.get(name).add(details);
        } else {
            List<SelectorDetails> newArray = new ArrayList<>();
            newArray.add(details);
            results.put(name, newArray);
        }
    }

    /**
     * Output all the selector details in a markdown format.
     *
     * @param out The writer to output the details to
     * @param allDetails All the details that should be output
     * @throws IOException If writing the data failed
     */
    private void writeMarkdown(Writer out, Map<String, Map<String, List<SelectorDetails>>> allDetails)
        throws IOException
    {
        for (Entry<String, Map<String, List<SelectorDetails>>> typeDetails : allDetails.entrySet()) {
            writeMarkdownTypeDetails(out, typeDetails.getKey(), typeDetails.getValue());
        }
    }

    /**
     * Output all the selector details for a specific selector type in a markdown format.
     *
     * @param out The writer to output the details to
     * @param type The name of the selector type
     * @param typeDetails All the details that should be output as belonging to the specified type
     * @throws IOException If writing the data failed
     */
    private void writeMarkdownTypeDetails(Writer out, String type, Map<String, List<SelectorDetails>> typeDetails)
        throws IOException
    {
        out.write("## " + type + "\n");
        for (Entry<String, List<SelectorDetails>> details : typeDetails.entrySet()) {
            writeMarkdownDetailsList(out, details.getKey(), details.getValue());
        }
    }

    /**
     * Output a set of selector details with a given name.
     * If there is just one entry in the list, output it as is.
     * If there are multiple entries, output them as an ordered list.
     *
     * @param out The writer to output the details to
     * @param name The name of the specified selector
     * @param detailsList The list of details to output
     * @throws IOException If writing the data failed
     */
    private void writeMarkdownDetailsList(Writer out, String name, List<SelectorDetails> detailsList)
        throws IOException
    {
        out.write("### " + name + "\n");
        if (detailsList.size() == 1) {
            // Just 1 selector with this name. Simple output
            writeMarkdownDetails(out, detailsList.get(0), -1);
        } else {
            // Multiple selectors with this name. Output as an ordered list of implementations
            out.write("#### Implementations\n");
            for (int i = 0; i < detailsList.size(); i++) {
                writeMarkdownDetails(out, detailsList.get(i), i);
            }
        }
    }

    /**
     * Output the information about a given selector.
     * Outputs the description, options (if available) and if the selector is enabled by default.
     *
     * @param out The writer to output the details to
     * @param details The details about the desired selector
     * @param index The index of the selector in a list of implementations.
     *              If negative, output without being a part of a list.
     *              If 0 or positive, output as the index+1 entry in an ordered list.
     * @throws IOException If writing the data failed
     */
    private void writeMarkdownDetails(Writer out, SelectorDetails details, int index)
        throws IOException
    {
        // If this has a non-negative index, this is a part of a list.
        boolean indented = index >= 0;
        // Set up the indent string to maintain the desired list indentation if this is a part of a list.
        String indent = indented ? "    " : "";

        // Write the description of the selector.
        // - If this is a part of an ordered list of implementations, prepend the description with the 1 based index.
        // - Replace all escaped newlines with a non escaped newline
        // - Prepend newlines with 2 spaces to make sure they are displayed as a line break in markdown viewers
        // - Add the desired indentation after newlines to maintain the correct list indentation
        out.write((indented ? String.format("%d. ", index + 1) : "")
            + details.getDescription().replaceAll("\\\n", "  \n" + indent)
            + "  \n");

        if (details.isEnabledByDefault()) {
            out.write(indent + "**Enabled by default**  \n");
        }
        SelectorOption[] options = details.getOptions();
        if (options.length > 0) {
            // If the option is in a list of implementations, the header tree will be one level deeper due to
            // the "Implmenations" header.
            out.write(indent + (indented ? "#" : "") + "#### Options  \n");
            for (SelectorOption option : options) {
                // Append each option as an unordered list item
                // e.g. " - **exclude**: description of how to use the exclude option"
                // Replace any newlines as detailed earlier with descriptions but with one level of extra indentation
                // due to the options list
                out.write(String.format("%s - **%s**: %s  \n",
                    indent,
                    option.getName().replaceAll("\\\n", "  \n    " + indent),
                    option.getDescription().replaceAll("\\\n", "  \n    " + indent)));
            }
        }
    }

    /**
     * Output all the selector details in Json format.
     *
     * @param out The writer to output the details to
     * @param allDetails All the details that should be output
     * @throws IOException If writing the data failed
     */
    private void writeJson(Writer out, Map<String, Map<String, List<SelectorDetails>>> allDetails)
        throws IOException
    {
        JsonObjectBuilder result = Json.createObjectBuilder();
        for (Entry<String, Map<String, List<SelectorDetails>>> typeDetails : allDetails.entrySet()) {
            // Add the type of selector and all relevant details
            // e.g. Processor or Filter
            result.add(typeDetails.getKey(), getJsonTypeDetails(typeDetails.getValue()));
        }
        out.write(result.build().toString());
    }

    /**
     * Get the details about all selectors of a type in a Json format.
     *
     * @param typeDetails All the details that should be converted to Json
     * @return a Json object containing all the provided details, keyed by selector name
     */
    private JsonObjectBuilder getJsonTypeDetails(Map<String, List<SelectorDetails>> typeDetails)
    {
        JsonObjectBuilder result = Json.createObjectBuilder();
        for (Entry<String, List<SelectorDetails>> detailsEntry : typeDetails.entrySet()) {
            List<SelectorDetails> detailsList = detailsEntry.getValue();
            if (detailsList.size() > 1) {
                // Multiple implementations with the same name. Output as an array of selector objects
                JsonArrayBuilder implementations = Json.createArrayBuilder();
                for (SelectorDetails details : detailsList) {
                    implementations.add(getJsonDetails(details));
                }
                result.add(detailsEntry.getKey(), implementations);
            } else {
                // Just one implementation. Output as an object
                result.add(detailsEntry.getKey(), getJsonDetails(detailsEntry.getValue().get(0)));
            }
        }
        return result;
    }

    /**
     * Output the data about a given selector.
     * e.g.
     *
     * <code>
     * {
     *     description: "the description"
     *     isEnabledByDefault: true or undefined
     *     options: Object or undefined {
     *         option name: "option description"
     *     }
     * }
     * </code>
     *
     * @param details The selector details that should be output
     * @return A json object containing all the provided details
     */
    private JsonObjectBuilder getJsonDetails(SelectorDetails details)
    {
        JsonObjectBuilder result = Json.createObjectBuilder()
            .add("description", details.getDescription());
        if (details.isEnabledByDefault()) {
            result.add("isEnabledByDefault", true);
        }
        SelectorOption[] options = details.getOptions();
        if (options.length > 0) {
            JsonObjectBuilder optionsJson = Json.createObjectBuilder();
            for (SelectorOption option : options) {
                optionsJson.add(option.getName(), option.getDescription());
            }
            result.add("options", optionsJson);
        }
        return result;
    }
}
