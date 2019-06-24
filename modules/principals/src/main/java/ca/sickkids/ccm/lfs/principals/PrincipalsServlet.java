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
package ca.sickkids.ccm.lfs.principals;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
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
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.LogService;

/**
 * A servlet that lists existing principals. It supports pagination and basic filtering. Depending on the path on which
 * it is invoked, it can either return only users (on {@code /home/users.json}), only groups (on
 * {@code /home/groups.json}), or both (on {@code /home.json}).
 * <p>
 * This servlet supports the following parameters:
 * </p>
 * <ul>
 * <li><tt>filter</tt>: a lucene search term, such as "david" or "adm*"; no filter set by default</li>
 * <li><tt>offset</tt>: a 0-based number representing how many principals to skip; 0 by default</li>
 * <li><tt>limit</tt>: a number representing how many principals to include at most in the result; 10 by default</li>
 * </ul>
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletPaths(value = { "/home.json", "/home/users.json", "/home/groups.json" })
public class PrincipalsServlet extends SlingSafeMethodsServlet
{
    private static final class FilteredPrincipalsQuery implements Query
    {
        private final JackrabbitSession session;

        private final AuthorizableType type;

        private final long limit;

        private final String filter;

        private final long offset;

        private FilteredPrincipalsQuery(JackrabbitSession session, AuthorizableType type, long limit, String filter,
            long offset)
        {
            this.session = session;
            this.type = type;
            this.limit = limit;
            this.filter = filter;
            this.offset = offset;
        }

        @Override
        public <T> void build(QueryBuilder<T> builder)
        {
            T condition = null;
            // Ignore system users
            // TODO Maybe this should be optional?
            try {
                condition = builder.not(
                    builder.eq("@jcr:primaryType", this.session.getValueFactory().createValue("rep:SystemUser")));
            } catch (RepositoryException e) {
                // This really shouldn't happen
            }
            // Apply the requested filter
            if (StringUtils.isNotBlank(this.filter)) {
                // Full text search in the principal's node
                condition = builder.and(condition, builder.contains(".", this.filter));
            }
            builder.setCondition(condition);
            // What type of principal to include
            builder.setSelector(this.type.getAuthorizableClass());
            // Pagination parameters
            // TODO Maybe use the value-bound method instead of fixed pages?
            builder.setLimit(this.offset, this.limit);
        }
    }

    private static final long serialVersionUID = -1985122718411056384L;

    @Reference
    private LogService logger;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String filter = request.getParameter("filter");
        final long limit = getLongValueOrDefault(request.getParameter("limit"), 10);
        final long offset = getLongValueOrDefault(request.getParameter("offset"), 0);
        Session session = request.getResourceResolver().adaptTo(Session.class);

        try (Writer out = response.getWriter(); JsonGenerator jsonGen = Json.createGenerator(out)) {
            if (session == null || !(session instanceof JackrabbitSession)) {
                writeBlankJson(jsonGen);
            } else {
                AuthorizableType type;
                switch (request.getRequestPathInfo().getResourcePath()) {
                    case "/home/users.json":
                        type = AuthorizableType.USER;
                        break;
                    case "/home/groups.json":
                        type = AuthorizableType.GROUP;
                        break;
                    default:
                        type = AuthorizableType.AUTHORIZABLE;
                }
                writePrincipals(jsonGen, queryPrincipals((JackrabbitSession) session, type, filter, offset, limit));
            }
        } catch (RepositoryException e) {
            this.logger.log(LogService.LOG_ERROR, "Failed to query the repository for principals: " + e.getMessage());
        }
    }

    /**
     * Query the list of principals according to the filter and paging parameters.
     *
     * @param session the current JCR session
     * @param type the type of principals to include in the results
     * @param filter an optional filter, in the lucene search term syntax
     * @param offset how many results to skip
     * @param limit how many results to include at most
     * @return the matching authorizables, may be empty
     * @throws RepositoryException if the query fails
     */
    private Iterator<Authorizable> queryPrincipals(final JackrabbitSession session, final AuthorizableType type,
        final String filter, final long offset, final long limit) throws RepositoryException
    {
        final Query q = new FilteredPrincipalsQuery(session, type, limit, filter, offset);
        final UserManager userManager = session.getUserManager();
        return userManager.findAuthorizables(q);
    }

    /**
     * Serialize a list of authorizables as JSON.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param principals the list of authorizables to serialize
     */
    private void writePrincipals(final JsonGenerator jsonGen, final Iterator<Authorizable> principals)
    {
        jsonGen.writeStartArray();
        principals.forEachRemaining(authorizable -> writeAuthorizable(jsonGen, authorizable));
        jsonGen.writeEnd().flush();
    }

    /**
     * Serialize a user or group as JSON.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param authorizable the authorizable to serialize
     */
    private void writeAuthorizable(final JsonGenerator jsonGen, final Authorizable authorizable)
    {
        jsonGen.writeStartObject();
        try {
            if (authorizable instanceof User) {
                writeUser(jsonGen, (User) authorizable);
            } else if (authorizable instanceof Group) {
                writeGroup(jsonGen, (Group) authorizable);
            }
        } catch (RepositoryException e) {
            jsonGen.write("error", e.getMessage());
        }
        jsonGen.writeEnd();
    }

    /**
     * Serialize a user as JSON.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param user the user to serialize
     * @throws RepositoryException if an underlying repository operation fails
     */
    private void writeUser(final JsonGenerator jsonGen, final User user) throws RepositoryException
    {
        jsonGen.write("name", user.getID());
        jsonGen.write("type", "user");
        jsonGen.write("principalName", user.getPrincipal().getName());
        jsonGen.write("isAdmin", user.isAdmin());
        jsonGen.write("isDisabled", user.isDisabled());
        if (user.isDisabled()) {
            jsonGen.write("disabledReason", user.getDisabledReason());
        }
        jsonGen.write("isSystem", user.isSystemUser());
        jsonGen.writeStartArray("declaredMemberOf");
        user.declaredMemberOf().forEachRemaining(g -> writeGroupSummary(jsonGen, g));
        jsonGen.writeEnd();
        jsonGen.writeStartArray("memberOf");
        user.memberOf().forEachRemaining(g -> writeGroupSummary(jsonGen, g));
        jsonGen.writeEnd();
        jsonGen.write("path", user.getPath());
        writeProperties(jsonGen, user);
    }

    /**
     * Serialize a group as JSON.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param group the group to serialize
     * @throws RepositoryException if an underlying repository operation fails
     */
    private void writeGroup(final JsonGenerator jsonGen, final Group group) throws RepositoryException
    {
        jsonGen.write("name", group.getID());
        jsonGen.write("type", "group");
        jsonGen.write("principalName", group.getPrincipal().getName());
        jsonGen.writeStartArray("declaredMemberOf");
        group.declaredMemberOf().forEachRemaining(g -> writeGroupSummary(jsonGen, g));
        jsonGen.writeEnd();
        jsonGen.writeStartArray("memberOf");
        group.memberOf().forEachRemaining(g -> writeGroupSummary(jsonGen, g));
        jsonGen.writeEnd();
        jsonGen.write("path", group.getPath());
        jsonGen.write("members", sizeOf(group.getMembers()));
        jsonGen.write("declaredMembers", sizeOf(group.getDeclaredMembers()));
        writeProperties(jsonGen, group);
    }

    /**
     * Write a short summary about a group as JSON.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param group the group to serialize
     */
    private void writeGroupSummary(final JsonGenerator jsonGen, final Group group)
    {
        jsonGen.writeStartObject();
        try {
            jsonGen.write("name", group.getID());
            jsonGen.write("path", group.getPath());
        } catch (RepositoryException e) {
            jsonGen.write("error", e.getMessage());
        }
        jsonGen.writeEnd();
    }

    /**
     * Serialize all non-standard properties of an authorizable as JSON.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param authorizable the user or group whose properties should be serialized
     */
    private void writeProperties(final JsonGenerator jsonGen, final Authorizable authorizable)
    {
        jsonGen.writeStartObject("properties");
        try {
            authorizable.getPropertyNames().forEachRemaining(propertyName -> {
                try {
                    Value[] values = authorizable.getProperty(propertyName);
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
                    jsonGen.write("error", e.getMessage());
                }
            });
        } catch (RepositoryException e) {
            jsonGen.write("error", e.getMessage());
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
        jsonGen.writeStartArray().writeEnd().flush();
    }

    private long sizeOf(final Iterator<?> i)
    {
        long size = 0;
        while (i.hasNext()) {
            ++size;
            i.next();
        }
        return size;
    }
}
