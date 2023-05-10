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

package io.uhndata.cards;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;
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
 * A servlet that tries to delete a given node and returns an explanation if deletion is not possible.
 * <p>
 * This servlet supports the following parameters:
 * </p>
 * <ul>
 * <li><code>recursive</code>: whether nodes which reference the item should be deleted; defaults to false</li>
 * </ul>
 *
 * @version $Id$
 */

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Resource" },
    methods = { "DELETE" })
public class DeleteServlet extends SlingAllMethodsServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteServlet.class);

    private static final long serialVersionUID = 1L;

    /** The node that was requested to be deleted. */
    private final ThreadLocal<Node> nodeToDelete = new ThreadLocal<>();

    /** The Resource Resolver for the current request. */
    private final ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    /** A list of all nodes traversed by {@code traverseNode}. */
    private final ThreadLocal<Set<Node>> nodesTraversed =
        ThreadLocal.withInitial(() -> new TreeSet<>(new NodeComparator()));

    /** A set of all nodes that should be deleted. */
    private final ThreadLocal<Set<Node>> nodesToDelete =
        ThreadLocal.withInitial(() -> new TreeSet<>(new NodeComparator()));

    /** A set of all nodes that are children of nodes in {@code nodesToDelete}. */
    private final ThreadLocal<Set<Node>> childNodesDeleted =
        ThreadLocal.withInitial(() -> new TreeSet<>(new NodeComparator()));

    /**
     * A function that operates on a {@link Node}. As opposed to a simple {@code Consumer}, it can forward a
     * {@code RepositoryException} encountered while processing a {@code Node}.
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
     */
    private NodeConsumer deleteNode = (node) -> {
        // Keep track of each child node we've already deleted
        boolean isChild = nodeSetContains(this.childNodesDeleted.get(), node) != null;
        boolean alreadyDeleting = nodeSetContains(this.nodesToDelete.get(), node) != null;
        if (!isChild && !alreadyDeleting) {
            this.nodesToDelete.get().add(node);
        }
        this.iterateChildren(node, this.markChildNodeDeleted, false);
    };

    /**
     * Add a node to a list of traversed nodes.
     */
    private NodeConsumer traverseNode = (node) -> {
        this.nodesTraversed.get().add(node);
    };

    /**
     * Traverse through the references of the node.
     */
    private NodeConsumer traverseReferences = (node) -> {
        iterateReferrers(node, this.traverseNode, false);
    };

    private NodeConsumer markChildNodeDeleted = (node) -> {
        this.childNodesDeleted.get().add(node);

        // Attempting to delete this node will fail -- remove it
        // contains() appears to not be working, so we'll unroll the equality here
        Node toRemove = nodeSetContains(this.nodesToDelete.get(), node);
        if (toRemove != null) {
            this.nodesToDelete.get().remove(toRemove);
        }
    };

    /**
     * Determine whether or not a set contains a particular node.
     * This is necessary because contains() seems to fail to recognize the same node
     * that was obtained in two different ways.
     *
     * @param includeRoot whether or not to include the root node as a child
     */
    private Node nodeSetContains(Set<Node> set, Node node)
        throws RepositoryException
    {
        for (Node n : set) {
            if (n.getPath().equals(node.getPath())) {
                return n;
            }
        }

        return null;
    }

    @Override
    public void doDelete(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException
    {
        try {
            final ResourceResolver resourceResolver = request.getResourceResolver();
            final Node node = request.getResource().adaptTo(Node.class);
            this.nodeToDelete.set(node);
            this.resolver.set(resourceResolver);
            final Session session = resourceResolver.adaptTo(Session.class);
            final VersionManager versionManager = session.getWorkspace().getVersionManager();
            final Set<Node> nodesToCheckin = new TreeSet<>(new NodeComparator());

            final Boolean recursive = Boolean.parseBoolean(request.getParameter("recursive"));

            if (recursive) {
                handleRecursiveDeleteChildren(node);
            } else {
                handleDelete(response, node);
            }

            // Delete all of our pending nodes, checking out the parent to avoid version conflict issues
            for (final Node n : this.nodesToDelete.get()) {
                final Node versionableAncestor = findVersionableAncestor(n);
                if (versionableAncestor != null && !versionableAncestor.isCheckedOut()) {
                    nodesToCheckin.add(versionableAncestor);
                    versionManager.checkout(versionableAncestor.getPath());
                }
                n.remove();
            }

            session.save();

            // Check each parent back in
            for (final Node versionableNode : nodesToCheckin) {
                versionManager.checkin(versionableNode.getPath());
            }
        } catch (AccessDeniedException e) {
            LOGGER.error("AccessDeniedException trying to delete node: {}", e.getMessage(), e);
            sendJsonError(response, request.getRemoteUser() == null ? SlingHttpServletResponse.SC_UNAUTHORIZED
                : SlingHttpServletResponse.SC_FORBIDDEN);
        } catch (RepositoryException e) {
            LOGGER.error("Unknown RepositoryException trying to delete node: {}", e.getMessage(), e);
            sendJsonError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e);
        } finally {
            // Cleanup state to free memory
            this.resolver.remove();
            this.nodeToDelete.remove();
            this.nodesTraversed.remove();
            this.nodesToDelete.remove();
            this.childNodesDeleted.remove();
        }
    }

    /**
     * Attempt to delete a node. If other nodes refer to it, user will be informed that deletion could not occur.
     *
     * @param response the HTTP response to be used to convey failure to the user
     * @param node the node to attempt deletion
     * @throws IOException if sending an error to the response fails
     * @throws AccessDeniedException if the requesting user does not have permission to delete the node
     * @throws RepositoryException if deletion fails due to a repository error
     */
    private void handleDelete(final SlingHttpServletResponse response, final Node node)
        throws IOException, AccessDeniedException, RepositoryException
    {
        // Check if this node or its children are referenced by other nodes
        iterateChildren(node, this.traverseReferences, true);

        if (this.nodesTraversed.get().size() == 0) {
            this.deleteNode.accept(node);
            this.resolver.get().adaptTo(Session.class).save();
        } else {
            String referencedNodes = listReferrersFromTraversal();
            // Will not be able to delete node due to references. Inform user.
            sendJsonError(response, SlingHttpServletResponse.SC_CONFLICT, String.format("This item is referenced %s.",
                StringUtils.isEmpty(referencedNodes) ? "by unknown item(s)" : "in " + referencedNodes));
        }
    }

    /**
     * Delete the children of a node and all nodes which reference its children, as well as the node itself.
     *
     * @param node the node to attempt deletion
     * @throws AccessDeniedException if the requesting user does not have permission to delete any node
     * @throws RepositoryException if deletion fails due to a repository error
     */
    private void handleRecursiveDeleteChildren(Node node)
        throws AccessDeniedException, RepositoryException
    {
        final NodeIterator childNodes = node.getNodes();
        while (childNodes.hasNext()) {
            handleRecursiveDeleteChildren(childNodes.nextNode());
        }
        handleRecursiveDelete(node);
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
        iterateChildren(node, this.deleteNode, false);
        iterateReferrers(node, this.deleteNode);
    }

    /**
     * Recursively call a function on all nodes which reference a node, and on the node itself.
     *
     * @param node the node to have its referrers and self operated on
     * @param consumer the function to be called on each node
     * @param includeRoot if true, the consumer will be called on the node itself in addition to its referrers
     * @throws RepositoryException if any function call fails due to repository errors
     */
    private void iterateReferrers(
        Node node,
        NodeConsumer consumer,
        boolean includeRoot
    ) throws RepositoryException
    {
        final PropertyIterator references = node.getReferences();
        final String rootPath = this.nodeToDelete.get().getPath();
        while (references.hasNext()) {
            final Node referrer = references.nextProperty().getParent();
            final String path = referrer.getPath();
            if (path.equals(rootPath) || path.startsWith(rootPath + "/")) {
                // This a reference within the subtree to delete, ignore it
                continue;
            }
            iterateReferrers(referrer, consumer, true);
            iterateParentIfRequired(referrer, consumer);
        }

        if (includeRoot) {
            consumer.accept(node);
        }
    }

    /**
     * Recursively call a function on all progeny of a node.
     *
     * @param node the node to have its referrers and self operated on
     * @param consumer the function to be called on each node
     * @param includeRoot if true, the consumer will be called on the node itself in addition to its referrers
     * @throws RepositoryException if any function call fails due to repository errors
     */
    private void iterateChildren(
        Node node,
        NodeConsumer consumer,
        boolean includeRoot
    ) throws RepositoryException
    {
        final NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            iterateChildren(children.nextNode(), consumer, true);
        }

        if (includeRoot) {
            consumer.accept(node);
        }
    }

    /**
     * Recursively call a function on all nodes which reference a node, and on the node itself.
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
        iterateReferrers(node, consumer, true);
    }

    /**
     * Recursively call a function all children of the current node's parent if required.
     *
     * @param node the node to have its referrers and self operated on
     * @param consumer the function to be called on each node
     * @throws RepositoryException if any function call fails due to repository errors
     */
    private void iterateParentIfRequired(
        Node node,
        NodeConsumer consumer
    ) throws RepositoryException
    {
        if (node.hasProperty("recursiveDeleteParent") && node.getProperty("recursiveDeleteParent").getBoolean()) {
            iterateChildren(node.getParent(), consumer, true);
        }
    }

    /**
     * Get a string explaining which nodes refer to the node traversed by parent node.
     *
     * @return a string in the format "2 forms, 1 subject(subjectName)" for all traversed nodes
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity", "checkstyle:ExecutableStatementCount", "checkstyle:JavaNCSS"})
    private String listReferrersFromTraversal()
    {
        try {
            int formCount = 0;
            int answerSectionCount = 0;
            int formReferenceCount = 0;
            int answerCount = 0;
            int otherCount = 0;
            List<String> subjects = new ArrayList<>();
            List<String> subjectTypes = new ArrayList<>();
            List<String> questionnaires = new ArrayList<>();

            for (Node n : this.nodesTraversed.get()) {
                switch (n.getPrimaryNodeType().getName()) {
                    case "cards:Form":
                        formCount++;
                        break;
                    case "cards:AnswerSection":
                        answerSectionCount++;
                        break;
                    case "cards:FormReference":
                        // If the form reference leads to the parent form being deleted,
                        // don't list the form reference as a deleted item as it's parent form
                        // will be listed instead.
                        if (!n.hasProperty("recursiveDeleteParent")
                            || !n.getProperty("recursiveDeleteParent").getBoolean()) {
                            formReferenceCount++;
                        }
                        break;
                    case "cards:Subject":
                        subjects.add(n.getProperty("identifier").getString());
                        break;
                    case "cards:SubjectType":
                        subjectTypes.add(n.getProperty("label").getString());
                        break;
                    case "cards:Questionnaire":
                        questionnaires.add(n.getProperty("title").getString());
                        break;
                    default:
                        if ("cards/Answer".equals(n.getProperty("sling:resourceSuperType").getString())) {
                            answerCount++;
                        } else {
                            otherCount++;
                        }
                }
            }

            List<String> results = new ArrayList<>();
            if (formCount > 0) {
                addNodesToResult(results, "form", formCount);
            } else if (answerSectionCount > 0) {
                addNodesToResult(results, "answer section", answerSectionCount);
            } else if (answerCount > 0) {
                addNodesToResult(results, "answer", answerCount);
            }
            if (formReferenceCount > 0) {
                addNodesToResult(results, "form reference", formReferenceCount);
            }
            addNodesToResult(results, "subject", subjects);
            addNodesToResult(results, "subject type", subjectTypes);
            addNodesToResult(results, "questionnaire", questionnaires);
            addNodesToResult(results, "other", otherCount);

            return stringArrayToList(results);
        } catch (RepositoryException e) {
            return null;
        }
    }

    /**
     * Add a string listing the number of items found to an array.
     *
     * @param results the array to be added to
     * @param type the type of item found
     * @param nodeCount the number of items of this type found
     */
    private void addNodesToResult(List<String> results, String type, int nodeCount)
    {
        if (nodeCount > 0) {
            results.add(String.format("%d %s", nodeCount, toPlural(type, nodeCount)));
        }
    }

    /**
     * Add a string listing the number and names of items found to an array.
     *
     * @param results the array to be added to
     * @param type the type of item found
     * @param names the names of each item of this type found
     */
    private void addNodesToResult(List<String> results, String type, List<String> names)
    {
        if (names.size() > 0) {
            results.add(String.format("%d %s (%s)",
                names.size(),
                toPlural(type, names.size()),
                stringArrayToList(names)));
        }
    }

    /**
     * Transform a word from singular to plural form.
     * @param word the word in singular form
     * @param count word count
     * @return the correct form of the word for the given count
     */
    private String toPlural(String word, int count)
    {
        String result;
        if (count == 1) {
            result = word;
        } else {
            // TODO: Handle irregular plurals
            result = String.format("%ss", word);
        }
        return result;
    }

    /**
     * Convert a list of strings to a readable comma and "and" separated string.
     *
     * @param results the strings to combine
     * @return a string in the format "string1, ..., stringN-1 and stringN" or an empty string
     */
    private String stringArrayToList(List<String> results)
    {
        String result;
        if (results.size() > 1) {
            String start;
            String end;
            if (results.size() > 13) {
                start = String.join(", ", results.subList(0, 10));
                end = String.format("%d others", results.size() - 10);
            } else {
                start = String.join(", ", results.subList(0, results.size() - 1));
                end = results.get(results.size() - 1);
            }
            result = String.format("%s and %s", start, end);
        } else if (results.size() == 1) {
            result = results.get(0);
        } else {
            result = "";
        }

        return result;
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
     * Send a json response with the provided HTTP response code and error message.
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
     * Send a json response with the provided HTTP response code and error message.
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
     * Finds and returns an ancestor of the given node that is versionable, if any.
     *
     * @param n a node
     * @return an ancestor node of the input node that is versionable, or {@code null} if no such ancestor exists
     * @throws RepositoryException if accessing the repository fails
     */
    private Node findVersionableAncestor(final Node n) throws RepositoryException
    {
        // Abort early if no ancestor is accessible
        if (n == null || n.getDepth() == 0) {
            return null;
        }

        Node ancestor = n.getParent();
        while (ancestor.getDepth() > 0 && !ancestor.isNodeType("mix:versionable")) {
            try {
                ancestor = ancestor.getParent();
            } catch (AccessDeniedException e) {
                // The parent is inaccessible to us
                return null;
            }
        }

        if (ancestor.isNodeType("mix:versionable")) {
            return ancestor;
        }
        return null;
    }

    /**
     * Node does not implement {@code equals} and {@code hashCode}, so in order to properly detect if two Node instances
     * reference the same node, we need an explicit comparator that compares the two node paths. This comparator may
     * throw {@code NullPointerException} if any of the nodes to compare are null.
     */
    private static class NodeComparator implements Comparator<Node>
    {
        @Override
        public int compare(Node o1, Node o2)
        {
            try {
                return o1.getPath().compareTo(o2.getPath());
            } catch (RepositoryException e) {
                return 0;
            }
        }
    }
}
