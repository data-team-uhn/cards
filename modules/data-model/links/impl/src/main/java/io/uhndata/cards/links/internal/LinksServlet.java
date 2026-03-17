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
package io.uhndata.cards.links.internal;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.links.api.LinkUtils;

/**
 * Servlet that handles resource links.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Resource" },
    extensions = { "links" },
    methods = { "GET", "POST", "DELETE" })
public class LinksServlet extends SlingJakartaAllMethodsServlet
{
    private static final long serialVersionUID = 1337L;

    @Reference
    private LinkUtils links;

    @Override
    public void doGet(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        Node currentNode = request.getResource().adaptTo(Node.class);
        JsonArrayBuilder result = Json.createArrayBuilder();
        response.setContentType("application/json");
        this.links.getLinks(currentNode).forEach(link -> result.add(link.toJson()));
        response.getWriter().print(result.build().toString());
        response.flushBuffer();
    }

    @Override
    public void doPost(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        final Session session = request.getResourceResolver().adaptTo(Session.class);
        final String destinationPath = request.getParameter("destination");
        final String type = request.getParameter("type");
        final String label = request.getParameter("label");
        try {
            if (StringUtils.isBlank(destinationPath) || !session.nodeExists(destinationPath)
                || StringUtils.isBlank(type)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            final Node currentNode = request.getResource().adaptTo(Node.class);
            this.links.addLink(currentNode, session.getNode(destinationPath), type, label);
        } catch (RepositoryException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doDelete(SlingJakartaHttpServletRequest request, SlingJakartaHttpServletResponse response)
        throws ServletException, IOException
    {
        final Session session = request.getResourceResolver().adaptTo(Session.class);
        final String destinationPath = request.getParameter("destination");
        final String type = request.getParameter("type");
        final String label = request.getParameter("label");
        try {
            if (StringUtils.isBlank(destinationPath) || StringUtils.isBlank(type)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            final Node currentNode = request.getResource().adaptTo(Node.class);
            this.links.removeLinks(currentNode, session.getNode(destinationPath), type, label);
        } catch (RepositoryException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
