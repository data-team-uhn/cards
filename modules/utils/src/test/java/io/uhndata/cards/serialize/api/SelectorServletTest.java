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
package io.uhndata.cards.serialize.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.junit.Before;
import org.junit.Test;

import io.uhndata.cards.serialize.spi.DataFilterFactory;
import io.uhndata.cards.serialize.spi.ResourceCSVProcessor;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;
import io.uhndata.cards.serialize.spi.SelectorDetails;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SelectorServlet}.
 *
 * @version $Id$
 */
public class SelectorServletTest
{
    private static final String PROCESSORS = "Processors";

    private static final String FILTERS = "Filters";

    private static final String CSV_OPTIONS = "CSV Adapter Options";

    private final SelectorServlet servlet = new SelectorServlet();

    private final SlingJakartaHttpServletRequest request = mock(SlingJakartaHttpServletRequest.class);

    private final SlingJakartaHttpServletResponse response = mock(SlingJakartaHttpServletResponse.class);

    private final StringWriter output = new StringWriter();

    @Before
    public void setUp() throws IllegalAccessException, IOException
    {
        setProcessors(List.of(), List.of(), List.of());
        when(this.response.getWriter()).thenReturn(new PrintWriter(this.output));
    }

    @Test
    public void doGetWritesJsonByDefault() throws IllegalAccessException, IOException
    {
        setProcessors(
            List.of(jsonProcessor(new SelectorDetails("bare", "Bare serialization", true))),
            List.of(filterFactory(new SelectorDetails("status", "Filter by status", "include", "Statuses to keep"))),
            List.of(csvProcessor(new SelectorDetails("labels", "Use labels"))));
        when(this.request.getPathInfo()).thenReturn("/Forms.selectors.json");

        this.servlet.doGet(this.request, this.response);

        verify(this.response).setCharacterEncoding("UTF-8");
        verify(this.response).setContentType("application/json");
        JsonObject result = Json.createReader(new StringReader(this.output.toString())).readObject();
        JsonObject processor = result.getJsonObject(PROCESSORS).getJsonObject("bare");
        assertEquals("Bare serialization", processor.getString("description"));
        assertTrue(processor.getBoolean("isEnabledByDefault"));
        assertFalse(processor.containsKey("options"));
        JsonObject filter = result.getJsonObject(FILTERS).getJsonObject("status");
        assertFalse(filter.containsKey("isEnabledByDefault"));
        assertEquals("Statuses to keep", filter.getJsonObject("options").getString("include"));
        assertEquals("Use labels", result.getJsonObject(CSV_OPTIONS).getJsonObject("labels").getString("description"));
    }

    @Test
    public void doGetGroupsSameNameSelectorsInJsonArray() throws IllegalAccessException, IOException
    {
        setProcessors(
            List.of(jsonProcessor(new SelectorDetails("bare", "First implementation")),
                jsonProcessor(new SelectorDetails("bare", "Second implementation"))),
            List.of(), List.of());
        when(this.request.getPathInfo()).thenReturn("/Forms.selectors.json");

        this.servlet.doGet(this.request, this.response);

        JsonObject result = Json.createReader(new StringReader(this.output.toString())).readObject();
        assertEquals(2, result.getJsonObject(PROCESSORS).getJsonArray("bare").size());
        assertEquals("First implementation",
            result.getJsonObject(PROCESSORS).getJsonArray("bare").getJsonObject(0).getString("description"));
    }

    @Test
    public void doGetWritesMarkdownForMdRequests() throws IllegalAccessException, IOException
    {
        setProcessors(
            List.of(jsonProcessor(
                new SelectorDetails("bare", "Bare serialization\nSecond line", true, "exclude", "What to exclude"))),
            List.of(), List.of());
        when(this.request.getPathInfo()).thenReturn("/Forms.selectors.md");

        this.servlet.doGet(this.request, this.response);

        verify(this.response).setContentType("text/markdown");
        String result = this.output.toString();
        assertTrue(result.contains("## " + PROCESSORS));
        assertTrue(result.contains("## " + FILTERS));
        assertTrue(result.contains("## " + CSV_OPTIONS));
        assertTrue(result.contains("### bare"));
        // Newlines in the description are turned into markdown line breaks
        assertTrue(result.contains("Bare serialization  \nSecond line"));
        assertTrue(result.contains("**Enabled by default**"));
        assertTrue(result.contains("#### Options"));
        assertTrue(result.contains(" - **exclude**: What to exclude"));
    }

    @Test
    public void doGetListsSameNameSelectorsAsMarkdownImplementations() throws IllegalAccessException, IOException
    {
        setProcessors(
            List.of(jsonProcessor(new SelectorDetails("bare", "First implementation")),
                jsonProcessor(new SelectorDetails("bare", "Second implementation", true, "exclude", "The exclusions"))),
            List.of(), List.of());
        when(this.request.getPathInfo()).thenReturn("/Forms.selectors.md");

        this.servlet.doGet(this.request, this.response);

        String result = this.output.toString();
        assertTrue(result.contains("#### Implementations"));
        assertTrue(result.contains("1. First implementation"));
        assertTrue(result.contains("2. Second implementation"));
        // Options of a listed implementation are indented and nested one header level deeper
        assertTrue(result.contains("    ##### Options"));
        assertTrue(result.contains("     - **exclude**: The exclusions"));
    }

    private ResourceJsonProcessor jsonProcessor(final SelectorDetails details)
    {
        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);
        when(processor.getDetails()).thenReturn(details);
        return processor;
    }

    private DataFilterFactory filterFactory(final SelectorDetails... details)
    {
        DataFilterFactory factory = mock(DataFilterFactory.class);
        when(factory.getFilterDetails()).thenReturn(List.of(details));
        return factory;
    }

    private ResourceCSVProcessor csvProcessor(final SelectorDetails... details)
    {
        ResourceCSVProcessor processor = mock(ResourceCSVProcessor.class);
        when(processor.getDetails()).thenReturn(List.of(details));
        return processor;
    }

    private void setProcessors(final List<ResourceJsonProcessor> jsonProcessors,
        final List<DataFilterFactory> filters, final List<ResourceCSVProcessor> csvProcessors)
        throws IllegalAccessException
    {
        FieldUtils.writeField(this.servlet, "allJsonProcessors", jsonProcessors, true);
        FieldUtils.writeField(this.servlet, "allFilters", filters, true);
        FieldUtils.writeField(this.servlet, "allCSVProcessors", csvProcessors, true);
    }
}
