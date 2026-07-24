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
package io.uhndata.cards.entityindex.internal;

import java.io.IOException;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import jakarta.servlet.Servlet;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.entityindex.EntityIndexer;

/**
 * An administrative servlet triggering a full rebuild of the {@link EntityIndexer entity index}, accessible as
 * {@code POST /Forms.reindexEntities.json}. Only users with write access to the repository root, i.e.
 * administrators, may trigger a rebuild. The rebuild runs in the background; the response only acknowledges that it
 * started.
 *
 * @version $Id$
 * @since 0.9.41
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/FormsHomepage" },
    selectors = { "reindexEntities" },
    methods = { "POST" })
public class EntityIndexReindexServlet extends SlingJakartaAllMethodsServlet
{
    private static final long serialVersionUID = -5993065639789743661L;

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityIndexReindexServlet.class);

    @Reference
    private transient EntityIndexer indexer;

    @Override
    public void doPost(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        response.setContentType("application/json");
        try {
            final Session session = request.getResourceResolver().adaptTo(Session.class);
            if (session == null || !session.hasPermission("/", Session.ACTION_SET_PROPERTY)) {
                response.setStatus(403);
                response.getWriter().write("{\"status\":\"forbidden\"}");
                return;
            }
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed to check permissions for reindexing: {}", e.getMessage(), e);
            response.setStatus(500);
            return;
        }
        final Thread reindexer = new Thread(this.indexer::reindexAll, "entity-index-rebuild");
        reindexer.setDaemon(true);
        reindexer.start();
        response.setStatus(202);
        response.getWriter().write("{\"status\":\"reindexing\"}");
    }
}
