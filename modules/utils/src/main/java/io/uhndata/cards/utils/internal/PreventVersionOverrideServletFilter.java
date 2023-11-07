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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Check if a base version was passed in the request, and if it doesn't match the base version of the resource being
 * modified, abort the request.
 *
 * @version $Id$
 */
@Component(service = Filter.class,
    property = {
        "service.ranking:Integer=0",
        "sling.filter.scope=REQUEST",
        "sling.filter.methods=POST",
        "sling.filter.methods=PUT"
    })
public class PreventVersionOverrideServletFilter implements Filter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PreventVersionOverrideServletFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
        // Nothing to do
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException
    {
        String requestBaseVersion = request.getParameter(":baseVersion");
        if (!(request instanceof SlingHttpServletRequest) || requestBaseVersion == null) {
            chain.doFilter(request, response);
            return;
        }
        SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) request;
        try {
            Node node = slingRequest.getResource().adaptTo(Node.class);
            if (node != null && !requestBaseVersion.equals(node.getProperty("jcr:baseVersion").getNode().getPath())) {
                slingRequest.setAttribute("javax.servlet.error.status_code", HttpServletResponse.SC_CONFLICT);
                throw new ServletException(
                    "The answers to this form were modified while you were editing. Please refresh to see the latest data.");
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to determine current resource version: {}", e.getMessage(), e);
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy()
    {
        // Nothing to do
    }
}
