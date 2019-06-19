//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
package ca.sickkids.ccm.lfs.userviewer;

import java.io.IOException;
import java.io.Writer;
import java.security.Principal;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.jackrabbit.api.security.principal.ItemBasedPrincipal;
import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;

/**
 * A servlet that lists existing users.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletPaths(value = { "/home/users" })
public class UsersServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -1985122718411056384L;

    private boolean testMode = true;

    private long getLongValueOrDefault(String stringValue, long defaultValue)
    {
        long value = defaultValue;
        if (!stringValue.isEmpty()) {
            try {
                value = Long.parseLong(stringValue);
            } catch (NumberFormatException exception) {
                value = defaultValue;
            }
        }
        return value;
    }

    private void writeBlankJson(JsonGenerator jsonGen)
    {
        jsonGen.writeStartObject();
        jsonGen.writeEnd();
        jsonGen.flush();
    }

    private void writeUsers(JsonGenerator jsonGen, Session session, String filter/*, long limit, long offset*/)
    {
        PrincipalIterator principals = null;
        try {
            PrincipalManager principalManager = AccessControlUtil.getPrincipalManager(session);
            principals = principalManager.findPrincipals(filter, 3);

            if (principals != null) {
                jsonGen.writeStartArray();
                while (principals.hasNext()) {
                    Principal currentPrincipal = principals.nextPrincipal();
                    // jsonGen.write(currentPrincipal);
                    jsonGen.writeStartObject();
                    jsonGen.write("name", currentPrincipal.getName());
                    jsonGen.write("string", currentPrincipal.toString());
                    if (currentPrincipal instanceof ItemBasedPrincipal) {
                        jsonGen.write("path", ((ItemBasedPrincipal) currentPrincipal).getPath());
                    }
                    jsonGen.writeEnd();
                }
                jsonGen.writeEnd();
            } else {
                jsonGen.writeStartObject();
                jsonGen.write("Status", "No session");
                jsonGen.writeEnd();
                jsonGen.flush();
            }

            // response.getWriter().write(out.toString());

        } catch (RepositoryException re) {
            writeBlankJson(jsonGen);
            throw new SlingException("Error obtaining list of principals", re);
        }
    }

    @Override
    public void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String filter = request.getParameter("filter");
        //long limit = getLongValueOrDefault(request.getParameter("limit"), 10);
        //long offset = getLongValueOrDefault(request.getParameter("offset"), 0);
        Session session = request.getResourceResolver().adaptTo(Session.class);
        Writer out = response.getWriter();
        JsonGenerator jsonGen = Json.createGenerator(out);
        try {
            if (session != null) {
                writeUsers(jsonGen, session, filter);
            } else {
                writeBlankJson(jsonGen);
                // System.out.println("Null session detected. Could not find list of principals.");
            }
        } finally {
            jsonGen.close();
            out.close();
        }

    }
    /*
     * final JsonGenerator w = Json.createGenerator(out); w.writeStartObject(); w.write("Hello", "Hello"); w.writeEnd();
     * w.flush(); w.close();
     */
}
