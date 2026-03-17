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

package io.uhndata.cards.locking;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Map;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.locking.api.LockError;
import io.uhndata.cards.locking.api.LockException;
import io.uhndata.cards.locking.api.LockManager;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Subject" },
    methods = { "LOCK", "UNLOCK" })
public class LockServlet extends SlingJakartaAllMethodsServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LockServlet.class);

    private static final long serialVersionUID = 1L;

    private static final String METHOD_LOCK = "LOCK";

    private static final String METHOD_UNLOCK = "UNLOCK";

    private SlingJakartaHttpServletRequest request;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private LockManager lockManager;

    @Override
    protected boolean mayService(SlingJakartaHttpServletRequest request, SlingJakartaHttpServletResponse response)
        throws ServletException, IOException
    {
        // assume the method is known for now
        boolean methodKnown = true;

        String method = request.getMethod();
        if (METHOD_LOCK.equals(method)) {
            handleRequest(request, response, true);
        } else if (METHOD_UNLOCK.equals(method)) {
            handleRequest(request, response, false);
        } else {
            // actually we do not know the method
            methodKnown = false;
        }
        return methodKnown;
    }

    @Override
    protected StringBuffer getAllowedRequestMethods(Map<String, Method> declaredMethods)
    {
        StringBuffer allowBuf = super.getAllowedRequestMethods(declaredMethods);

        allowBuf.append(METHOD_LOCK).append(", ").append(METHOD_UNLOCK);

        return allowBuf;
    }

    public void handleRequest(final SlingJakartaHttpServletRequest request,
        final SlingJakartaHttpServletResponse response,
        final boolean isLockRequest)
        throws IOException, IllegalArgumentException
    {
        this.request = request;

        boolean mustPopResolver = false;
        try {
            this.rrp.push(this.request.getResourceResolver());
            mustPopResolver = true;

            Node requestNode = request.getResource().adaptTo(Node.class);

            if (isLockRequest) {
                this.lockManager.forceLock(requestNode);
            } else {
                this.lockManager.unlock(requestNode);
            }
            writeSuccess(response);
        } catch (LockError e) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                String.format("Cannot %s node: %s", isLockRequest ? "lock" : "unlock", e.getMessage()));
        } catch (LockException e) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                String.format("Unexpected error %s requested node", isLockRequest ? "locking" : "unlocking"));
        } catch (AccessDeniedException e) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Access Denied");
        } catch (RepositoryException e) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Unexpected error accessing the repository");
            LOGGER.error("Unexpected error accessing the repository: {}", e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void writeError(final SlingJakartaHttpServletResponse response, final int statusCode,
        final String errorMessage) throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(statusCode);
        try (Writer out = response.getWriter()) {
            out.append("{\"status\":\"error\",\"error\": \"" + errorMessage + "\"}");
        }
    }

    private void writeSuccess(final SlingJakartaHttpServletResponse response)
        throws IOException, RepositoryException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        try (Writer out = response.getWriter()) {
            final JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("status", "success");
            out.append(result.build().toString());
        }
    }
}
