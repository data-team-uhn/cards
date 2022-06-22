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
package io.uhndata.cards.permissions.internal;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.permissions.spi.PermissionsManager;

/**
 * Servlet which handles changing permissions. It processes POST requests on the {@code /Forms} page and subpages,
 * expecting three parameters: {@code :rule} is either "deny" or "allow". {@code :privileges} is a comma-seperated
 * list of privileges to alter, {@code :principal} is the group or user name, {@code :restriction} is a single
 * {@code restriction=value} pair. An optional {@code :remove} can be provided if you wish to delete an entry
 * matching the above instead.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/FormsHomepage", "cards/Form", "cards/Answer" },
    selectors = { "permissions" },
    methods = { "POST" }
    )
public class PermissionsManagerServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -677311195300436475L;

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsManagerServlet.class);

    @Reference
    private PermissionsManager permissionsChangeServiceHandler;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws ServletException
    {
        // Get request parameters
        String rule = request.getParameter(":rule");
        String privilegesText = request.getParameter(":privileges");
        String principalName = request.getParameter(":principal");
        String uri = request.getRequestURI();
        String target = uri.substring(0, uri.indexOf("."));
        String restrictionText = request.getParameter(":restriction");
        String remove = request.getParameter(":remove");
        JackrabbitSession session = (JackrabbitSession) request.getResourceResolver().adaptTo(Session.class);

        // Alter this node's permissions
        try {
            boolean isAllow = parseRule(rule);
            String[] privileges = parsePrivileges(privilegesText);
            Map<String, Value> restrictions = parseRestriction(restrictionText, session.getValueFactory());
            Principal principal = session.getPrincipalManager().getPrincipal(principalName);
            if (remove == null) {
                this.permissionsChangeServiceHandler.addAccessControlEntry(
                    target, isAllow, principal, privileges, restrictions, session);
            } else {
                this.permissionsChangeServiceHandler.removeAccessControlEntry(
                    target, isAllow, principal, privileges, restrictions, session);
            }
            session.save();
        } catch (RepositoryException e) {
            LOGGER.error("Failed to change permissions: {}", e.getMessage(), e);
        }
    }

    /**
     * Parse the rule from input string.
     *
     * @param rule either "allow" or "deny"
     * @return true if the rule is "allow", false if it is "deny"
     * @throws RepositoryException if the rule is null or neither "allow" or "deny"
     */
    private boolean parseRule(String rule) throws RepositoryException
    {
        if (rule == null) {
            throw new IllegalArgumentException("Required parameter \":rule\" missing");
        } else if ("allow".equals(rule)) {
            return true;
        } else if ("deny".equals(rule)) {
            return false;
        } else {
            throw new IllegalArgumentException("\":rule\" must be either 'allow' or 'deny'");
        }
    }

    /**
     * Parse restrictions from input string.
     *
     * @param toParse a string of form name=value, where name is the name of the restriction (e.g. cards:answer) and
     *            value is the value (e.g. 4c32f6cb-ad5d-4e45-ab0a-8d27f5ba8211)
     * @param valueFactory a value factory to work with
     * @return a map with one string-value pair
     */
    private static Map<String, Value> parseRestriction(String toParse, ValueFactory valueFactory)
        throws RepositoryException
    {
        if (toParse == null) {
            throw new IllegalArgumentException("Required parameter \":restriction\" missing");
        }
        final Map<String, Value> restriction = new HashMap<>();
        int splitPos = toParse.indexOf("=");
        String restrictionName = toParse.substring(0, splitPos);
        String restrictionValue = toParse.substring(splitPos + 1);
        restriction.put(restrictionName, valueFactory.createValue(restrictionValue));
        return restriction;
    }

    /**
     * Parse a list of privilege names from a comma-separated string.
     *
     * @param toParse a list of privilege names separated by commas
     * @return an array of strings
     * @throws IllegalArgumentException if the input string is empty or null
     */
    private static String[] parsePrivileges(String toParse)
    {
        if (toParse == null) {
            throw new IllegalArgumentException("Required parameter \":privileges\" missing");
        }
        return toParse.split(",");
    }
}
