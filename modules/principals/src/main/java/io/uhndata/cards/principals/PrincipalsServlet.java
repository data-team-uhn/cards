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
package io.uhndata.cards.principals;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servlet that lists existing principals. It supports pagination and basic filtering. Depending on the path on which
 * it is invoked, it can either return only users (on {@code /home/users.json}), only groups (on
 * {@code /home/groups.json}), or both (on {@code /home.json}).
 * Users queries support filtering by user type: only users (default), only service users ({@code type=service}),
 * or both ({@code type=all}).
 * <p>
 * This servlet supports the following parameters:
 * </p>
 * <ul>
 * <li><code>filter</code>: a lucene search term, such as "david" or "adm*"; no filter set by default</li>
 * <li><code>offset</code>: a 0-based number representing how many principals to skip; 0 by default</li>
 * <li><code>limit</code>: a number representing how many principals to include at most in the result; 0 by default</li>
 * <li><code>type</code>: a users type filter: "service" for only service users or "all" for all types</li>
 * </ul>
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletPaths(value = { "/home.json", "/home/users.json", "/home/groups.json" })
public class PrincipalsServlet extends SlingSafeMethodsServlet
{
    /**
     * Creates a query for principals matching the request parameters.
     */
    private static final class FilteredPrincipalsQuery implements Query
    {
        private final JackrabbitSession session;

        private final AuthorizableType type;

        private final long limit;

        private final String filter;

        private final String userType;

        private final long offset;

        private FilteredPrincipalsQuery(JackrabbitSession session, AuthorizableType type, long limit, String filter,
            String userType, long offset)
        {
            this.session = session;
            this.type = type;
            this.limit = limit;
            this.filter = filter;
            this.offset = offset;
            this.userType = userType;
        }

        @Override
        public <T> void build(QueryBuilder<T> builder)
        {
            T condition = null;
            // Filter by user type
            try {
                ValueFactory factory = this.session.getValueFactory();
                T serviceUsers = builder.eq("@jcr:primaryType", factory.createValue("rep:SystemUser"));
                if (StringUtils.isBlank(this.userType)) {
                    // The default should stay as it is, only listing real users
                    condition = builder.not(serviceUsers);
                } else {
                    if ("service".equals(this.userType)) {
                        condition = serviceUsers;
                    }
                    // Otherwise include all user types, no conditions
                }
            } catch (RepositoryException e) {
                // This really shouldn't happen
            }

            // Apply the requested filter
            if (StringUtils.isNotBlank(this.filter)) {
                // Full text search in the principal's node
                T filterBy = builder.contains(".", this.filter);
                condition = (condition == null) ? filterBy : builder.and(condition, filterBy);
            }
            builder.setCondition(condition);
            // What type of principal to include
            builder.setSelector(this.type.getAuthorizableClass());
            // Pagination parameters
            // TODO Maybe use the value-bound method instead of fixed pages?
            if (this.limit > 0) {
                builder.setLimit(this.offset, this.limit);
            }
        }
    }

    private static final long serialVersionUID = -1985122718411056384L;

    private static final Logger LOGGER = LoggerFactory.getLogger(PrincipalsServlet.class);

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String filter = request.getParameter("filter");
        String userType = request.getParameter("type");
        final long limit = getLongValueOrDefault(request.getParameter("limit"), 0);
        final long offset = getLongValueOrDefault(request.getParameter("offset"), 0);
        Session session = request.getResourceResolver().adaptTo(Session.class);

        try (Writer out = response.getWriter()) {
            JsonGenerator jsonGen = Json.createGenerator(out);
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
                jsonGen.writeStartObject();
                // The magic number 8 is the prefix length for the protocol, https://
                long[] principalCounts =
                    writePrincipals(jsonGen,
                        queryPrincipals((JackrabbitSession) session, type, filter, userType, 0, Long.MAX_VALUE),
                        request.getRequestURL().substring(0, request.getRequestURL().indexOf("/", 8))
                            + request.getContextPath(), offset, limit);
                writeSummary(jsonGen, request, filter, offset, limit,
                    principalCounts[0], principalCounts[1]);
                jsonGen.writeEnd().flush();
            }
        } catch (RepositoryException e) {
            LOGGER.error("Failed to query the repository for principals: {}", e.getMessage(), e);
        }
    }

    /**
     * Query the list of principals according to the filter and paging parameters.
     *
     * @param session the current JCR session
     * @param type the type of principals to include in the results
     * @param filter an optional filter, in the lucene search term syntax
     * @param userType a user type filter whether to include only users, service users or both
     * @param offset how many results to skip
     * @param limit how many results to include at most
     * @return the matching authorizables, may be empty
     * @throws RepositoryException if the query fails
     */
    private Iterator<Authorizable> queryPrincipals(final JackrabbitSession session, final AuthorizableType type,
        final String filter, final String userType, final long offset, final long limit) throws RepositoryException
    {
        final Query q = new FilteredPrincipalsQuery(session, type, limit, filter, userType, offset);
        final UserManager userManager = session.getUserManager();
        return userManager.findAuthorizables(q);
    }

    /**
     * Write metadata about the request and response. This includes the request parameters, the number of returned and
     * total matching principals, and pagination links.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param request the current request
     * @param filter the requested filter, may be {@code null}
     * @param offset the requested offset, may be the default value of {0}
     * @param limit the requested limit, may be the default value of {10}
     * @param returnedPrincipals the number of matching principals included in the response, may be {@code 0} if no
     *            principals were returned
     * @param totalMatchingPrincipals the total number of accessible principals matching the request filters, may be
     *            {@code 0} if no principals match the filters, or the current user cannot access the principals
     */
    private void writeSummary(final JsonGenerator jsonGen, final SlingHttpServletRequest request, final String filter,
        final long offset, final long limit, final long returnedPrincipals, final long totalMatchingPrincipals)
    {
        jsonGen.write("req", request.getParameter("req"));
        jsonGen.write("filter", filter);
        jsonGen.write("offset", offset);
        jsonGen.write("limit", limit);
        jsonGen.write("returnedrows", returnedPrincipals);
        jsonGen.write("totalrows", totalMatchingPrincipals);
    }

    /**
     * Serialize a list of authorizables as JSON.
     *
     * For the purposes of the servlet, it is intended that a query for all of the principals matching the filter to be
     * made using this servlet so that the total number of matching principals can be recorded for pagination purposes.
     * The method will only write the Authorizables that are allowed by the offset and limit to the JSON however.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param principals the list of authorizables to serialize
     * @param urlPrefix an URL prefix for the server, used for computing an URL for accessing a principal
     * @param offset the requested offset, may be the default value of {0}
     * @param limit the requested limit, may be the default value of {0}
     * @return a long array of length 2 where the element at index [0] is the number of matching principals included
     *            in the response, and the element at index [1] is the total number of accessible principals
     *            matching the requested filters
     */
    private long[] writePrincipals(final JsonGenerator jsonGen, final Iterator<Authorizable> principals,
        final String urlPrefix, final long offset, final long limit)
    {
        long[] principalCount = new long[2];
        principalCount[0] = 0;
        principalCount[1] = 0;

        long offsetCounter = offset < 0 ? 0 : offset;
        long limitCounter = limit < 0 ? 0 : limit;

        jsonGen.writeStartArray("rows");

        while (principals.hasNext()) {
            Authorizable authorizable = principals.next();
            if (offsetCounter > 0) {
                --offsetCounter;
            } else {
                if (limit > 0) {
                    if (limitCounter > 0) {
                        writeAuthorizable(jsonGen, authorizable, urlPrefix);
                        --limitCounter;
                        ++principalCount[0];
                    }
                } else {
                    writeAuthorizable(jsonGen, authorizable, urlPrefix);
                }
            }
            ++principalCount[1];
        }

        jsonGen.writeEnd();

        return principalCount;
    }

    /**
     * Serialize a user or group as JSON.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param authorizable the authorizable to serialize
     * @param urlPrefix an URL prefix for the server, used for computing an URL for accessing the principal
     */
    private void writeAuthorizable(final JsonGenerator jsonGen, final Authorizable authorizable, final String urlPrefix)
    {
        jsonGen.writeStartObject();
        try {
            jsonGen.write("name", authorizable.getID());
            jsonGen.write("principalName", authorizable.getPrincipal().getName());
            jsonGen.write("type", authorizable.isGroup() ? "group" : "user");
            jsonGen.writeStartArray("declaredMemberOf");
            authorizable.declaredMemberOf().forEachRemaining(g -> writeGroupSummary(jsonGen, g));
            jsonGen.writeEnd();
            jsonGen.writeStartArray("memberOf");
            authorizable.memberOf().forEachRemaining(g -> writeGroupSummary(jsonGen, g));
            jsonGen.writeEnd();
            jsonGen.write("path", authorizable.getPath());
            jsonGen.write("href", urlPrefix + authorizable.getPath());
            if (authorizable instanceof User) {
                writeUser(jsonGen, (User) authorizable);
            } else if (authorizable instanceof Group) {
                writeGroup(jsonGen, (Group) authorizable);
            }
            writeProperties(jsonGen, authorizable);
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
        jsonGen.write("isAdmin", user.isAdmin());
        jsonGen.write("isDisabled", user.isDisabled());
        if (user.isDisabled()) {
            jsonGen.write("disabledReason", user.getDisabledReason());
        }
        jsonGen.write("isSystem", user.isSystemUser());
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
        jsonGen.write("members", sizeOf(group.getMembers()));
        jsonGen.write("declaredMembers", sizeOf(group.getDeclaredMembers()));
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
