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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.version.VersionManager;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Subject" },
    methods = { "POST" })
public class LockServlet extends SlingAllMethodsServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LockServlet.class);

    private static final long serialVersionUID = 1L;

    private static final String POST_ACTION_NAME = "action";
    private static final String POST_ACTION_LOCK = "LOCK";
    private static final String POST_ACTION_UNLOCK = "UNLOCK";
    private static final String STATUS_PROPERTY = "statusFlags";
    private static final String LOCKED_FLAG = "LOCKED";
    private static final String LOCK_NODE_PATH = "lock";
    private static final String LOCK_NODE_TYPE = "cards:Lock";

    private SlingHttpServletRequest request;
    private SlingHttpServletResponse response;

    @Reference
    private ResourceResolverFactory resolverFactory;

    /** The nodes that should be checked in. */
    private final ThreadLocal<Set<String>> nodesToCheckin = ThreadLocal.withInitial(() -> new TreeSet<>());

    /** The Resource Resolver for the current request. */
    private final ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    @Override
    public void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, IllegalArgumentException
    {
        this.request = request;
        this.response = response;
        try (ResourceResolver resourceResolver = this.resolverFactory.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "locking"))) {
            this.resolver.set(resourceResolver);
            final Session session = resourceResolver.adaptTo(Session.class);
            final VersionManager versionManager = session.getWorkspace().getVersionManager();

            final Node requestNode = request.getResource().adaptTo(Node.class);
            // TODO: check if user has permission to lock and unlock the request node

            final Node node = session.getNode(requestNode.getPath());

            final String action = request.getParameter(POST_ACTION_NAME);
            final boolean isLockAction;
            if (POST_ACTION_LOCK.equals(action)) {
                isLockAction = true;
            } else if (POST_ACTION_UNLOCK.equals(action)) {
                isLockAction = false;
            } else {
                writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Must specify if the node is to be locked or unlocked");
                return;
            }

            if (handleSubject(node, versionManager, isLockAction, true)) {
                // Node was successfully processed
                session.save();
                for (String path : this.nodesToCheckin.get()) {
                    versionManager.checkin(path);
                }
                writeSuccess(response);
            }

        } catch (final LoginException e) {
            LOGGER.error("Service authorization not granted: {}", e.getMessage());
        } catch (final RepositoryException e) {
            LOGGER.error("Unknown error: {}", e.getMessage(), e);
        }
    }

    private boolean handleSubject(Node node, VersionManager versionManager, boolean isLockAction)
        throws RepositoryException, IOException
    {
        return handleSubject(node, versionManager, isLockAction, false);
    }

    // TODO: Clean up
    @SuppressWarnings({"checkstyle:CyclomaticComplexity"})
    private boolean handleSubject(Node node, VersionManager versionManager, boolean isLockAction, boolean isRoot)
        throws RepositoryException, IOException
    {
        final Set<String> statusFlags = getStatusFlags(node);

        final boolean isLocked = statusFlags.contains(LOCKED_FLAG);
        if (isLocked && !isLockAction) {
            // Trying to unlock a locked subject
            // If there is a locked parent, this node cannot be unlocked
            if (parentIsLocked(node)) {
                writeError(this.response, 409, "A parent subject is locked");
                return false;
            }

            if (node.hasNode(LOCK_NODE_PATH)) {
                if (isRoot) {
                    // Remove the lock node as this node is being directly unlocked
                    node.getNode(LOCK_NODE_PATH).remove();
                } else {
                    // Do not indirectly unlock nodes that have lock nodes.
                    // Halt traversal here but do not prevent other parent/sibling nodes from being processed.
                    return true;
                }
            }

            checkoutIfNeeded(node, versionManager);
            statusFlags.remove(LOCKED_FLAG);
            node.setProperty(STATUS_PROPERTY, statusFlags.toArray(new String[0]));
            handleSubjectChildren(node, versionManager, isLockAction);
        } else if (!isLocked && isLockAction) {
            // Trying to lock an unlocked subject
            checkoutIfNeeded(node, versionManager);

            if (isRoot) {
                String userID = this.request.getResourceResolver().adaptTo(Session.class).getUserID();
                Node lockNode = node.addNode(LOCK_NODE_PATH, LOCK_NODE_TYPE);
                lockNode.setProperty("author", userID);
            }

            statusFlags.add(LOCKED_FLAG);
            node.setProperty(STATUS_PROPERTY, statusFlags.toArray(new String[0]));
            handleSubjectChildren(node, versionManager, isLockAction);
        } else if (isLocked && isLockAction && isRoot) {
            writeError(this.response, 409, "This node is already locked");
            return false;
        }
        return true;
    }

    private void handleForm(Node node, VersionManager versionManager, boolean isLockAction)
        throws RepositoryException
    {
        handleForm(node, versionManager, isLockAction, false);
    }

    private void handleForm(Node node, VersionManager versionManager, boolean isLockAction, boolean isRoot)
        throws RepositoryException
    {
        final Set<String> statusFlags = getStatusFlags(node);

        final boolean isLocked = statusFlags.contains(LOCKED_FLAG);

        if (isLocked && !isLockAction) {
            // Trying to unlock a locked form
            checkoutIfNeeded(node, versionManager);
            statusFlags.remove(LOCKED_FLAG);
            node.setProperty(STATUS_PROPERTY, statusFlags.toArray(new String[0]));
        } else if (!isLocked && isLockAction) {
            // Trying to lock an unlocked form
            checkoutIfNeeded(node, versionManager);
            statusFlags.add(LOCKED_FLAG);
            node.setProperty(STATUS_PROPERTY, statusFlags.toArray(new String[0]));
        }
    }

    private boolean parentIsLocked(Node node)
        throws RepositoryException
    {
        Node parent = node.getParent();
        return parent.isNodeType("cards:Subject") && getStatusFlags(parent).contains(LOCKED_FLAG);
    }

    private Set<String> getStatusFlags(Node node)
        throws RepositoryException
    {
        final Set<String> statusFlags = new TreeSet<>();
        if (node.hasProperty(STATUS_PROPERTY)) {
            for (Value value : node.getProperty(STATUS_PROPERTY).getValues()) {
                statusFlags.add(value.getString());
            }
        }
        return statusFlags;
    }

    private boolean handleSubjectChildren(Node node, VersionManager versionManager, boolean isLockAction)
        throws RepositoryException, IOException
    {
        // Handle child subjects
        final NodeIterator childNodes = node.getNodes();
        while (childNodes.hasNext()) {
            Node childNode = childNodes.nextNode();
            if (childNode.isNodeType("cards:Subject")) {
                boolean result = handleSubject(childNode, versionManager, isLockAction);
                if (!result) {
                    // Error occured, halt
                    return false;
                }
            }
        }

        // Handle forms
        final PropertyIterator references = node.getReferences();
        while (references.hasNext()) {
            Node referenceNode = references.nextProperty().getParent();
            if (referenceNode.isNodeType("cards:Form")) {
                handleForm(referenceNode, versionManager, isLockAction);
            }
        }

        return true;
    }

    private void checkoutIfNeeded(Node node, VersionManager versionManager)
        throws RepositoryException
    {
        final String path = node.getPath();
        if (!versionManager.isCheckedOut(path)) {
            this.nodesToCheckin.get().add(path);
            versionManager.checkout(path);
        }
    }

    private void writeError(final SlingHttpServletResponse response, final int statusCode, final String errorMessage)
        throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(statusCode);
        try (Writer out = response.getWriter()) {
            out.append("{\"status\":\"error\",\"error\": \"" + errorMessage + "\"}");
        }
    }

    private void writeSuccess(final SlingHttpServletResponse response)
        throws IOException, RepositoryException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(SlingHttpServletResponse.SC_OK);
        try (Writer out = response.getWriter()) {
            final JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("status", "success");
            out.append(result.build().toString());
        }
    }
}
