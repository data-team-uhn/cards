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
package ca.sickkids.ccm.lfs.permissions.internal;

import java.io.IOException;
import java.io.Writer;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.sickkids.ccm.lfs.permissions.spi.PermissionsManager;

/**
 * Servlet which handles changing permissions. It processes POST requests on the {@code /Forms} page and subpages,
 * expecting up to five parameters:
 * - {@code :forSubject} .
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "lfs/Subject", "lfs/FormsHomepage", "lfs/Form", "lfs/Answer" },
    selectors = { "permissions" },
    methods = { "GET" }
    )
public class PermissionsRetrievalServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = 4856192593469712534L;

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsManagerServlet.class);

    @Reference
    private PermissionsManager permissionsChangeServiceHandler;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException
    {
        try {
            String uri = request.getRequestURI();
            String target = uri.substring(0, uri.indexOf("."));
            JackrabbitSession session = (JackrabbitSession) request.getResourceResolver().adaptTo(Session.class);
            String subject = request.getParameter(":forSubject");
            boolean withServiceUser = (StringUtils.isNotBlank(subject) && "/Forms".equals(target)
                && hasSubjectManageRights(session, subject));

            getPolicies(session, target, response, withServiceUser);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to retrieve permissions: {}", e.getMessage(), e);
            if (e instanceof AccessDeniedException) {
                response.sendError(SlingHttpServletResponse.SC_FORBIDDEN);
            } else {
                response.sendError(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }
    }

    private Boolean hasSubjectManageRights(JackrabbitSession session, String subject)
    {
        try {
            AccessControlManager acm = session.getAccessControlManager();
            return acm.hasPrivileges("/Subjects/" + subject,
                new Privilege[] {acm.privilegeFromName("jcr:modifyAccessControl")});
        } catch (RepositoryException e) {
            // Do nothing. Couldn't check permissions, so return default.
        }
        return false;
    }

    /**
     * Send all access control entries for the target to the client.
     * @param session the Jackrabbit session to retrieve permissions from
     * @param target the path of the node to retrieve the access control entries from
     * @param response the http response to output to
     * @throws IOException if outputting to the response fails
     */
    private void getPolicies(JackrabbitSession session, String target, SlingHttpServletResponse response,
        boolean withServiceUser) throws IOException, RepositoryException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            this.permissionsChangeServiceHandler.outputAccessControlEntries(target, jsonGen, session, withServiceUser);
        }
    }
}
