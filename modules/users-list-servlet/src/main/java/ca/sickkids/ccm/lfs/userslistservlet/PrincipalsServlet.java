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
package ca.sickkids.ccm.lfs.userslistservlet;

import java.io.IOException;
import java.io.Writer;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableType;
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
public class PrincipalsServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -1985122718411056384L;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String filter = request.getParameter("filter");
        final long limit = getLongValueOrDefault(request.getParameter("limit"), 10);
        final long offset = getLongValueOrDefault(request.getParameter("offset"), 0);
        Session session = request.getResourceResolver().adaptTo(Session.class);

        try (Writer out = response.getWriter(); JsonGenerator jsonGen = Json.createGenerator(out)) {
            if (session != null) {
                writeUsers(jsonGen, session, filter, offset, limit);
            } else {
                writeBlankJson(jsonGen);
            }
        }
    }

    private void writeUsers(JsonGenerator jsonGen, Session session, String filter, long offset, long limit)
    {
        jsonGen.writeStartArray();
        Query q = new Query()
        {
            @Override
            public <T> void build(QueryBuilder<T> builder)
            {
                if (org.apache.commons.lang3.StringUtils.isNotBlank(filter)) {
                    builder.setCondition(builder.contains(".", filter));
                }
                builder.setSelector(AuthorizableType.USER.getAuthorizableClass());
                builder.setLimit(offset, limit);
            }
        };
        try {
            final UserManager userManager = ((JackrabbitSession) session).getUserManager();
            userManager.findAuthorizables(q).forEachRemaining(u -> writeUser(jsonGen, (User) u));
        } catch (RepositoryException e) {
            // TODO
        }
        jsonGen.writeEnd();
    }

    private long getLongValueOrDefault(final String stringValue, final long defaultValue)
    {
        long value = defaultValue;
        try {
            value = Long.parseLong(stringValue);
        } catch (NumberFormatException exception) {
            value = defaultValue;
        }
        return value;
    }

    private void writeBlankJson(final JsonGenerator jsonGen)
    {
        jsonGen.writeStartArray();
        jsonGen.writeEnd();
        jsonGen.flush();
    }

    private void writeUser(JsonGenerator jsonGen, User u)
    {
        jsonGen.writeStartObject();
        try {
            jsonGen.write("name", u.getID());
            jsonGen.write("principalName", u.getPrincipal().getName());
            jsonGen.write("isAdmin", u.isAdmin());
            jsonGen.write("isDisabled", u.isDisabled());
            if (u.isDisabled()) {
                jsonGen.write("disabledReason", u.getDisabledReason());
            }
            jsonGen.write("isSystem", u.isSystemUser());
            jsonGen.writeStartArray("declaredMemberOf");
            u.declaredMemberOf().forEachRemaining(g -> writeGroupSummary(jsonGen, g));
            jsonGen.writeEnd();
            jsonGen.writeStartArray("memberOf");
            u.memberOf().forEachRemaining(g -> writeGroupSummary(jsonGen, g));
            jsonGen.writeEnd();
            jsonGen.write("path", u.getPath());
            writeProperties(jsonGen, u);
        } catch (RepositoryException e) {
            // TODO
        }
        jsonGen.writeEnd();
    }

    private void writeGroupSummary(JsonGenerator jsonGen, Group g)
    {
        jsonGen.writeStartObject();
        try {
            jsonGen.write("name", g.getID());
            jsonGen.write("path", g.getPath());
        } catch (RepositoryException e) {
            // TODO
        }
        jsonGen.writeEnd();
    }

    private void writeProperties(JsonGenerator jsonGen, Authorizable u)
    {
        jsonGen.writeStartObject("properties");
        try {
            u.getPropertyNames().forEachRemaining(propertyName -> {
                try {
                    Value[] values = u.getProperty(propertyName);
                    if (values.length == 1 && values[0] != null) {
                        jsonGen.write(propertyName, values[0].toString());
                    } else {
                        jsonGen.writeStartArray(propertyName);
                        for (Value v : values) {
                            jsonGen.write(v.toString());
                        }
                        jsonGen.writeEnd();
                    }
                } catch (RepositoryException e) {
                    // TODO
                }
            });
        } catch (RepositoryException e) {
            // TODO
        }
        jsonGen.writeEnd();
    }
}
