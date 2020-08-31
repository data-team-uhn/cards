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
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
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
 * - {@code :action} is on of "get", "add" or "remove".
 * - {@code :rule} is either "deny" or "allow". Used for actions add or remove.
 * - {@code :privileges} is a comma-seperated list of privileges to alter. Used for actions add or remove.
 * - {@code :principal} is the group or user name. Used for actions add or remove.
 * - {@code :restriction} is a single {@code restriction=value} pair. Used for actions add or remove.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "lfs/Subject", "lfs/FormsHomepage", "lfs/Form", "lfs/Answer" },
    selectors = { "permissions" },
    methods = { "POST" }
    )
public class PermissionsManagerServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -677311195300436475L;

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsManagerServlet.class);

    @Reference
    private PermissionsManager permissionsChangeServiceHandler;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException
    {
        // Get request parameters
        String action = request.getParameter(":action");
        String uri = request.getRequestURI();
        String target = uri.substring(0, uri.indexOf("."));
        JackrabbitSession session = (JackrabbitSession) request.getResourceResolver().adaptTo(Session.class);

        String restrictionText = request.getParameter(":restrictions");
        try {
            Map<String, Value> restrictions = parseRestriction(restrictionText, session.getValueFactory());
            Value subject = restrictions.get("lfs:subject");
            Boolean useServiceUser = (subject != null && "/Forms".equals(target));

            if (useServiceUser && hasSubjectPermissions(session, subject.getString())) {
                Map<String, Object> param = new HashMap<String, Object>();
                param.put(ResourceResolverFactory.SUBSERVICE, "forms");
                session = (JackrabbitSession) this.resolverFactory.getServiceResourceResolver(param)
                    .adaptTo(Session.class);
            }

            switch (action) {
                case "add":
                case "remove":
                    this.editRule(session, target, request, action, restrictions);
                    break;
                case "get":
                    getPolicies(session, target, response);
                    break;
                default:
                    throw new IllegalArgumentException("\":action\" must be on of 'get', 'allow' or 'deny'");
            }
        } catch (LoginException | RepositoryException e) {
            LOGGER.error("Failed to change permissions: {}", e.getMessage(), e);
        }
    }

    private Boolean hasSubjectPermissions(JackrabbitSession session, String subject)
    {
        try {
            Privilege[] privileges = session.getAccessControlManager().getPrivileges("/Subjects/" + subject);
            for (Privilege privilege : privileges) {
                if ("jcr:modifyAccessControl".equals(privilege.getName())) {
                    return true;
                }
            }
            return false;
        } catch (RepositoryException e) {
            return false;
        }
    }

    /**
     * Add or remove an access control entry to the target with the details specified in the request.
     * @param session the Jackrabbit session to modify permissions for
     * @param target the path of the node to set the access control entries for
     * @param request the http request to retrieve parameters from
     * @param action the action to perform: either "add" or "remove"
     */
    private void editRule(JackrabbitSession session, String target, SlingHttpServletRequest request, String action,
        Map<String, Value> restrictions)
    {
        String rule = request.getParameter(":rule");
        String privilegesText = request.getParameter(":privileges");
        String principalName = request.getParameter(":principal");

        // Alter this node's permissions
        try {
            boolean isAllow = parseRule(rule);
            Privilege[] privileges = parsePrivileges(privilegesText, session.getAccessControlManager());
            Principal principal = session.getPrincipalManager().getPrincipal(principalName);
            if ("add".equals(action)) {
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
     * Send all access control entries for the target to the client.
     * @param session the Jackrabbit session to retrieve permissions from
     * @param target the path of the node to retrieve the access control entries from
     * @param response the http response to output to
     * @throws IOException if outputting to the response fails
     */
    private void getPolicies(JackrabbitSession session, String target, SlingHttpServletResponse response)
        throws IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            AccessControlManager acm = session.getAccessControlManager();
            JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(acm, target);

            jsonGen.writeStartObject();
            if (acl != null) {
                writePolicies(jsonGen, acl);
            }
            jsonGen.writeEnd().flush();
        } catch (RepositoryException e) {
            LOGGER.error("Failed to retrieve permissions: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert an access control list into a json output.
     * @param jsonGen The stream used to build the json
     * @param acl the access control list to convert
     * @throws RepositoryException if an error occurs traversing the access list
     */
    private void writePolicies(JsonGenerator jsonGen, JackrabbitAccessControlList acl) throws RepositoryException
    {
        jsonGen.writeStartArray("policies");
        // Find the necessary AccessControlEntry to remove
        JackrabbitAccessControlEntry[] entries = (JackrabbitAccessControlEntry[]) acl.getAccessControlEntries();
        for (JackrabbitAccessControlEntry entry : entries) {
            jsonGen.writeStartObject()
                .write("allow", (entry.isAllow() ? "allow" : "deny"))
                .write("principal", entry.getPrincipal().getName())
                .writeStartArray("privileges");

            Privilege[] privileges = entry.getPrivileges();
            for (Privilege privilege : privileges) {
                jsonGen.write(privilege.getName());
            }

            jsonGen.writeEnd().writeStartArray("restrictions");
            String[] restrictions = entry.getRestrictionNames();
            for (String restriction : restrictions) {
                jsonGen.write(restriction);
            }

            jsonGen.writeEnd().writeEnd();
        }
        jsonGen.writeEnd();
    }

    /**
     * Parse the rule from input string.
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
     * Parse privileges from input string.
     * @param toParse a comma delimited list of privileges
     * @param acm a reference to the AccessControlManager
     * @return an array of Privileges
     * @throws AccessControlException if no privilege with the specified name exists.
     * @throws RepositoryException if another error occurs
     */
    private static Privilege[] parsePrivileges(String toParse, AccessControlManager acm)
            throws AccessControlException, RepositoryException
    {
        // Typecheck the parameters
        if (toParse == null) {
            throw new IllegalArgumentException("Required parameter \":privileges\" missing");
        }
        String[] privilegeNames = toParse.split(",");
        Privilege[] retval = new Privilege[privilegeNames.length];
        for (int i = 0; i < privilegeNames.length; i++)
        {
            retval[i] = acm.privilegeFromName(privilegeNames[i]);
        }
        return retval;
    }

    /**
     * Parse restrictions from input string.
     * @param toParse a string of form name=value, where name is the name of the restriction (e.g.
     *        lfs:answer) and value is the value (e.g. 4c32f6cb-ad5d-4e45-ab0a-8d27f5ba8211)
     * @param valueFactory a value factory to work with
     * @return a map with one string-value pair
     */
    private static Map<String, Value> parseRestriction(String toParse, ValueFactory valueFactory)
        throws RepositoryException
    {
        final Map<String, Value> restriction = new HashMap<>();
        if (toParse != null && toParse.indexOf("=") >= 0) {
            int splitPos = toParse.indexOf("=");
            String restrictionName = toParse.substring(0, splitPos);
            String restrictionValue = toParse.substring(splitPos + 1);
            restriction.put(restrictionName, valueFactory.createValue(restrictionValue));
        }
        return restriction;
    }
}
