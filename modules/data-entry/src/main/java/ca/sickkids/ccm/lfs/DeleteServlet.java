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

package ca.sickkids.ccm.lfs;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servlet that tries to delete a given node and returns an explination if deletion is not possible.
 * <p>
 * This servlet supports the following parameters:
 * </p>
 * <ul>
 * <li><tt>path</tt>: a path to the item which should be deleted</li>
 * <li><tt>recursive</tt>: whether nodes which reference the item should be deleted; defaults to false</li>
 * </ul>
 *
 * @version $Id$
 */

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "lfs/QuestionnairesHomepage", "lfs/FormsHomepage", "lfs/SubjectsHomepage",
        "lfs/SubjectTypesHomepage" },
    extensions = { "delete" },
    methods = { "POST" })
public class DeleteServlet extends SlingAllMethodsServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteServlet.class);

    private static final long serialVersionUID = 1L;

    /** The Resource Resolver for the current request. */
    private final ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    /** A list of all nodes traversed by {@code traverseNode}. */
    private final ThreadLocal<ArrayList<Node>> nodesTraversed = new ThreadLocal<>();

    /**
     * Accept and operates on a Node.
     */
    @FunctionalInterface
    private interface NodeConsumer
    {
        /**
         * Operate on a node.
         *
         * @param node the node to be operated on
         * @throws RepositoryException if the operation fails due to repository errors
        */
        void accept(Node node) throws RepositoryException;
    }

    /**
     * Mark a node for deletion upon session save.
     *
     * @param node the node to be deleted
     * @throws RepositoryException if marking the node for deletion fails due to repository errors
     */
    private NodeConsumer deleteNode = (node) -> {
        node.remove();
    };


    /**
     * Add a node to a list of traversed nodes.
     *
     * @param node the node to be listed
     */
    private NodeConsumer traverseNode = (node) -> {
        this.nodesTraversed.get().add(node);
    };

    /**
     * Recursively call a function on all nodes which reference a node and itself.
     *
     * @param node the node to have its referrers and self operated on
     * @param consumer the function to be called on each node
     * @throws RepositoryException if any function call fails due to repository errors
     */
    private void iterateReferrers(
        Node node,
        NodeConsumer consumer
    ) throws RepositoryException
    {
        final PropertyIterator references = node.getReferences();
        while (references.hasNext()) {
            iterateReferrers(references.nextProperty().getParent(), consumer);
        }
        consumer.accept(node);
    }

    /**
     * Add a string listing the number of items found to an array.
     *
     * @param results the array to be added to
     * @param type the type of item found
     * @param nodeCount the number of items of this type found
     */
    private void addNodesToResult(ArrayList<String> results, String type, int nodeCount)
    {
        if (nodeCount > 0) {
            results.add(String.format("%d %s%s", nodeCount, type, (nodeCount == 1 ? "" : "s")));
        }
    }

    /**
     * Add a string listing the number and names of items found to an array.
     *
     * @param results the array to be added to
     * @param type the type of item found
     * @param names the names of each item of this type found
     */
    private void addNodesToResult(ArrayList<String> results, String type, ArrayList<String> names)
    {
        if (names.size() > 0) {
            results.add(String.format("%d %s%s (%s)",
                names.size(),
                type,
                (names.size() == 1 ? "" : "s"), String.join(", ", names)));
        }
    }

    /**
     * Get a string explaining which nodes refer to the node traversed by {@code traverseNode}.
     *
     * @param parentNode the node originally traversed
     * @return a string in the format "2 forms, 1 subject(subjectName)" for all traversed nodes
     */
    private String listReferrersFromTraversal(Node parentNode)
    {
        try {
            int formCount = 0;
            int otherCount = 0;
            ArrayList<String> subjects = new ArrayList<String>();
            ArrayList<String> subjectTypes = new ArrayList<String>();
            ArrayList<String> questionnaires = new ArrayList<String>();

            for (Node n : this.nodesTraversed.get()) {
                if (n.isSame(parentNode)) {
                    continue;
                }
                switch (n.getPrimaryNodeType().getName()) {
                    case "lfs:Form":
                        formCount++;
                        break;
                    case "lfs:Subject":
                        subjects.add(n.getProperty("identifier").getString());
                        break;
                    case "lfs:SubjectType":
                        subjectTypes.add(n.getProperty("label").getString());
                        break;
                    case "lfs:Questionnaire":
                        questionnaires.add(n.getProperty("title").getString());
                        break;
                    default:
                        otherCount++;
                }
            }

            ArrayList<String> results = new ArrayList<String>();
            addNodesToResult(results, "form", formCount);
            addNodesToResult(results, "subject type", subjectTypes);
            addNodesToResult(results, "subject type", subjectTypes);
            addNodesToResult(results, "subject type", questionnaires);
            addNodesToResult(results, "other", otherCount);

            return String.join(", ", results);
        } catch (RepositoryException e) {
            return null;
        }
    }

    /**
     * Send a json response with the provided HTTP response code.
     *
     * @param response the response object to write to
     * @param sc the HTTP response code to send
     */
    private static void sendJsonError(final SlingHttpServletResponse response, int sc)
        throws IOException
    {
        sendJsonError(response, sc, null, null);
    }

    /**
     * Send a json response with the provided HTTP response code.
     *
     * @param response the response object to write to
     * @param sc the HTTP response code to send
     * @param message a message to be sent explaining the error
     */
    private static void sendJsonError(final SlingHttpServletResponse response, int sc, String message)
        throws IOException
    {
        sendJsonError(response, sc, message, null);
    }

    /**
     * Send a json response with the provided HTTP response code.
     *
     * @param response the response object to write to
     * @param sc the HTTP response code to send
     * @param message a message to be sent explaining the error
     * @param exception the exception that lead to the error
     */
    private static void sendJsonError(final SlingHttpServletResponse response, int sc, String message,
        Exception exception)
        throws IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(sc);

        final Writer out = response.getWriter();
        JsonGenerator jsonGen = Json.createGenerator(out);
        jsonGen.writeStartObject()
            .write("status.code", sc);
        if (!StringUtils.isEmpty(message)) {
            jsonGen.write("status.message", message);
        }
        if (exception != null) {
            jsonGen.writeStartObject("error")
                .write("class", exception.getClass().getName())
                .write("message", exception.getMessage())
                .writeEnd();
        }
        jsonGen.writeEnd().close();
        response.setStatus(sc);
    }

    /**
     * Attempt to delete a node. If other nodes refer to this, user will be informed that deletion could not occur.
     *
     * @param response the HTTP response to be used to convey failure to the user
     * @param node the node to attempt deletion
     * @throws IOException if sending an error to the response fails
     * @throws AccessDeniedException if the requesting user does not have permission to delete the node
     * @throws RepositoryException if deletion fails due to a repository error
     */
    private void handleDelete(final SlingHttpServletResponse response, Node node)
        throws IOException, AccessDeniedException, RepositoryException
    {
        // Check if node is referenced by other nodes
        ArrayList<Node> nodeList = new ArrayList<>();
        this.nodesTraversed.set(nodeList);
        iterateReferrers(node, this.traverseNode);

        if (this.nodesTraversed.get().size() == 1) {
            node.remove();
            this.resolver.get().adaptTo(Session.class).save();
        } else {
            // Will not be able to delete node due to references. Inform user.
            String referencedNodes = listReferrersFromTraversal(node);
            sendJsonError(response, SlingHttpServletResponse.SC_CONFLICT, String.format("This item is referenced %s.",
                StringUtils.isEmpty(referencedNodes) ? " by unkown item(s)" : " in " + referencedNodes));
        }
    }

    /**
     * Delete a node and all nodes which reference it.
     *
     * @param node the node to attempt deletion
     * @throws AccessDeniedException if the requesting user does not have permission to delete any node
     * @throws RepositoryException if deletion fails due to a repository error
     */
    private void handleRecursiveDelete(Node node)
        throws AccessDeniedException, RepositoryException
    {
        iterateReferrers(node, this.deleteNode);
        this.resolver.get().adaptTo(Session.class).save();
    }

    @Override
    public void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException
    {
        try {
            final ResourceResolver resourceResolver = request.getResourceResolver();
            this.resolver.set(resourceResolver);

            final String path = request.getParameter("path");
            if (StringUtils.isBlank(path)) {
                throw new IllegalArgumentException("Required parameter \"path\" missing");
            }
            final Boolean recursive = Boolean.parseBoolean(request.getParameter("recursive"));

            LOGGER.error(path);
            Node node = this.resolver.get().getResource(path).adaptTo(Node.class);
            if (recursive) {
                handleRecursiveDelete(node);
            } else {
                handleDelete(response, node);
            }
        } catch (AccessDeniedException e) {
            sendJsonError(response, SlingHttpServletResponse.SC_UNAUTHORIZED);
        } catch (RepositoryException e) {
            LOGGER.error("Unknown RepositoryException trying to delete node: {}", e.getMessage(), e);
            sendJsonError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e);
        } finally {
            this.resolver.remove();
        }
    }
}
