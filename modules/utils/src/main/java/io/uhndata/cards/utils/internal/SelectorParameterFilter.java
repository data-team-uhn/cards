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

import java.io.IOException;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.utils.SelectorUtils;

/**
 * Lets a caller pass serialization selectors as {@code selector} query parameters instead of writing them into the path.
 * For security, the request path cannot contain special characters like slash and backslash, and while dots are allowed,
 * in order to be used inside a selector instead of being interpreted as a separator between selectors they would need to
 * be escaped. A query parameter does not have any constraints, but Sling's own selector parsing only works on the path.
 * This filter records the selectors passed as query parameters for the duration of the request into the
 * {@link io.uhndata.cards.utils.SelectorUtils} helper.
 *
 * @version $Id$
 */
@Component(service = Filter.class,
    property = {
        // Ahead of any filter that reads selectors itself, so that all of them see the same ones
        "service.ranking:Integer=1000",
        "sling.filter.scope=REQUEST",
        "sling.filter.methods=GET",
        "sling.filter.methods=HEAD"
    })
public class SelectorParameterFilter implements Filter
{
    /** The query parameter carrying one extra selector; repeat it to add more. */
    static final String SELECTOR_PARAMETER = "selector";

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException
    {
        // Nothing to do
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
        throws IOException, ServletException
    {
        final String[] extras = request instanceof SlingJakartaHttpServletRequest slingRequest
            ? slingRequest.getParameterValues(SELECTOR_PARAMETER) : null;
        if (extras == null || extras.length == 0) {
            chain.doFilter(request, response);
            return;
        }
        try {
            SelectorUtils.setRequestSelectors(List.of(extras));
            chain.doFilter(request, response);
        } finally {
            // Sling serves a request on a pooled thread, so leaving these behind would apply them to whatever it
            // serves next
            SelectorUtils.clearRequestSelectors();
        }
    }

    @Override
    public void destroy()
    {
        // Nothing to do
    }
}
