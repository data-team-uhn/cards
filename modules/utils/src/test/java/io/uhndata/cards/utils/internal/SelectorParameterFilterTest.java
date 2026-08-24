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
package io.uhndata.cards.utils.internal;

import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.uhndata.cards.utils.SelectorUtils;

/**
 * Unit tests for {@link SelectorParameterFilter}.
 *
 * <p>
 * The filter's whole effect is on what {@link SelectorUtils} parses while the chain runs, so that is what the chain
 * mock inspects. Each test also checks the recording is gone afterwards: it lives on a pooled thread, so a leak
 * would apply one request's selectors to whatever that thread served next.
 * </p>
 *
 * @version $Id$
 */
public class SelectorParameterFilterTest
{
    private static final String PATH_INFO = ".data.csv";

    private static final List<String> PATH_SELECTORS = List.of("data", "csv");

    private SelectorParameterFilter filter;

    private SlingJakartaHttpServletRequest request;

    private HttpServletResponse response;

    private FilterChain chain;

    @Before
    public void setUp()
    {
        this.filter = new SelectorParameterFilter();
        this.request = Mockito.mock(SlingJakartaHttpServletRequest.class);
        this.response = Mockito.mock(HttpServletResponse.class);
        this.chain = Mockito.mock(FilterChain.class);
    }

    @After
    public void tearDown()
    {
        // Nothing should be left behind, but a failing test must not take its neighbours down with it
        SelectorUtils.clearRequestSelectors();
    }

    private void selectorParameters(final String... selectors)
    {
        Mockito.when(this.request.getParameterValues(SelectorParameterFilter.SELECTOR_PARAMETER))
            .thenReturn(selectors.length == 0 ? null : selectors);
    }

    /** Asserts what {@link SelectorUtils} parses at the moment the chain is called. */
    private void chainSees(final List<String> expected) throws Exception
    {
        Mockito.doAnswer(invocation -> {
            Assert.assertEquals(expected, SelectorUtils.parseSelectors(PATH_INFO));
            return null;
        }).when(this.chain).doFilter(Mockito.any(ServletRequest.class), Mockito.any(ServletResponse.class));
    }

    @Test
    public void noSelectorParameterRecordsNothing() throws Exception
    {
        selectorParameters();
        chainSees(PATH_SELECTORS);

        this.filter.doFilter(this.request, this.response, this.chain);

        Mockito.verify(this.chain).doFilter(this.request, this.response);
    }

    @Test
    public void aRequestThatIsNotASlingRequestRecordsNothing() throws Exception
    {
        final ServletRequest plain = Mockito.mock(ServletRequest.class);
        chainSees(PATH_SELECTORS);

        this.filter.doFilter(plain, this.response, this.chain);

        Mockito.verify(this.chain).doFilter(plain, this.response);
    }

    @Test
    public void aSelectorParameterIsVisibleToTheParserWhileTheChainRuns() throws Exception
    {
        selectorParameters("deep");
        chainSees(List.of("data", "csv", "deep"));

        this.filter.doFilter(this.request, this.response, this.chain);

        Mockito.verify(this.chain).doFilter(this.request, this.response);
        // And gone once the request is over
        Assert.assertEquals(PATH_SELECTORS, SelectorUtils.parseSelectors(PATH_INFO));
    }

    @Test
    public void aRepeatedParameterContributesEverySelector() throws Exception
    {
        selectorParameters("deep", "-labels");
        chainSees(List.of("data", "csv", "deep", "-labels"));

        this.filter.doFilter(this.request, this.response, this.chain);

        Mockito.verify(this.chain).doFilter(this.request, this.response);
    }

    @Test
    public void theSelectorThatCouldNotBeWrittenInThePathArrivesWhole() throws Exception
    {
        // The CARDS-2898 case: the dots belong to the value, and escaping them in a path needs a backslash, which
        // Jetty refuses
        selectorParameters("dataOption:formSelectors=-dereference.simple.deep");
        Mockito.doAnswer(invocation -> {
            Assert.assertEquals(Map.of("formSelectors", "-dereference.simple.deep"),
                SelectorUtils.parseOptionsToMap("dataOption:", PATH_INFO));
            return null;
        }).when(this.chain).doFilter(Mockito.any(ServletRequest.class), Mockito.any(ServletResponse.class));

        this.filter.doFilter(this.request, this.response, this.chain);

        Mockito.verify(this.chain).doFilter(this.request, this.response);
    }

    @Test
    public void theRecordingIsClearedEvenWhenTheRequestFails() throws Exception
    {
        selectorParameters("deep");
        Mockito.doThrow(new ServletException("boom")).when(this.chain)
            .doFilter(Mockito.any(ServletRequest.class), Mockito.any(ServletResponse.class));

        Assert.assertThrows(ServletException.class,
            () -> this.filter.doFilter(this.request, this.response, this.chain));

        Assert.assertEquals(PATH_SELECTORS, SelectorUtils.parseSelectors(PATH_INFO));
    }
}
