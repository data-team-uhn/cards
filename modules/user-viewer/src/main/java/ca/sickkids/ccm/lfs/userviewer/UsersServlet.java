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

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
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

    @Override
    public void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Writer out = response.getWriter();

        try {
            /*
             * String filter = request.getParameter("filter"); long limit =
             * getLongValueOrDefault(request.getParameter("limit"), 10); long offset =
             * getLongValueOrDefault(request.getParameter("offset"), 0); PrincipalProvider provider =
             * EmptyPrincipalProvider.INSTANCE; PrincipalManagerImpl userManager = new PrincipalManagerImpl(provider);
             * PrincipalIterator userIterator = userManager.findPrincipals(filter, true, 3, offset, limit); // for the
             * int parameter, use: 1 for non-group principals only, 2 for group principals only, 3 for all
             */
            // Test Json Generation
            final JsonGenerator w = Json.createGenerator(out);
            w.writeStartObject();

            w.write("Hello", "Hello");
            w.writeEnd();
            w.flush();
            w.close();
        } finally {
            out.close();
        }
    }
}
