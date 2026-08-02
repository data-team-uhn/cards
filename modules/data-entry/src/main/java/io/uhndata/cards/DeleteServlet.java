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
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.OnParentVersionAction;
import javax.jcr.version.VersionManager;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
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
    resourceTypes = { "cards/Item" },
    methods = { "DELETE" })
public class DeleteServlet extends SlingJakartaAllMethodsServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteServlet.class);

    private static final long serialVersionUID = 1L;

    /** The node that was requested to be deleted. */
    private final ThreadLocal<Node> nodeToDelete = new ThreadLocal<>();

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
     * The paths whose referrers were already chased in the current phase, so that reference cycles, e.g. two
     * resources holding mutual RECURSIVE_DELETE links to each other, terminate instead of overflowing the stack.
     */
    private final ThreadLocal<Set<String>> referrersChased = ThreadLocal.withInitial(TreeSet::new);

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
    public void doDelete(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws ServletException, IOException
    {
        try {
            final ResourceResolver resourceResolver = request.getResourceResolver();
            final Node node = request.getResource().adaptTo(Node.class);
            if (node == null) {
                sendJsonError(response, SlingJakartaHttpServletResponse.SC_NOT_FOUND, "Not a repository item");
                return;
            }
            this.nodeToDelete.set(node);
            final Session session = resourceResolver.adaptTo(Session.class);

            final boolean recursive = Boolean.parseBoolean(request.getParameter("recursive"));

            boolean proceed = true;
            if (recursive) {
                handleRecursiveDeleteChildren(node);
            } else {
                proceed = handleDelete(response, node);
            }
            if (proceed) {
                removeMarkedNodes(session);
            }
            // Otherwise the deletion was refused and the explanation already sent; nothing may be saved
        } catch (AccessDeniedException e) {
            LOGGER.error("AccessDeniedException trying to delete node: {}", e.getMessage(), e);
            sendJsonError(response, request.getRemoteUser() == null ? SlingJakartaHttpServletResponse.SC_UNAUTHORIZED
                : SlingJakartaHttpServletResponse.SC_FORBIDDEN);
        } catch (RepositoryException e) {
            LOGGER.error("Unknown RepositoryException trying to delete node: {}", e.getMessage(), e);
            sendJsonError(response, SlingJakartaHttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e);
        } finally {
            // Cleanup state to free memory
            this.nodeToDelete.remove();
            this.nodesTraversed.remove();
            this.nodesToDelete.remove();
            this.childNodesDeleted.remove();
            this.referrersChased.remove();
        }
    }

    /**
     * Remove all the nodes marked for deletion, checking out their versionable ancestors to avoid version
     * conflict issues, and checking them back in after saving.
     *
     * @param session the current session
     * @throws RepositoryException if the removal fails due to a repository error
     */
    private void removeMarkedNodes(final Session session) throws RepositoryException
    {
        final VersionManager versionManager = session.getWorkspace().getVersionManager();
        final Set<String> nodesToCheckin = new TreeSet<>();
        // Delete all of our pending nodes, checking out the parent to avoid version conflict issues
        for (final Node n : this.nodesToDelete.get()) {
            final Node versionableAncestor = findVersionableAncestor(n);
            if (versionableAncestor != null && !versionableAncestor.isCheckedOut()) {
                nodesToCheckin.add(versionableAncestor.getPath());
                versionManager.checkout(versionableAncestor.getPath());
            }
            n.remove();
        }

        session.save();

        // Check each parent back in, unless it was itself deleted by this same operation
        for (final String versionablePath : nodesToCheckin) {
            if (session.nodeExists(versionablePath)) {
                versionManager.checkin(versionablePath);
            }
        }
    }

    /**
     * Attempt to delete a node. If other nodes refer to it, user will be informed that deletion could not occur.
     *
     * @param response the HTTP response to be used to convey failure to the user
     * @param node the node to attempt deletion
     * @return {@code true} if the deletion may proceed, {@code false} if it was refused and the explanation was
     *         already sent
     * @throws IOException if sending an error to the response fails
     * @throws AccessDeniedException if the requesting user does not have permission to delete the node
     * @throws RepositoryException if deletion fails due to a repository error
     */
    private boolean handleDelete(final SlingJakartaHttpServletResponse response, final Node node)
        throws IOException, AccessDeniedException, RepositoryException
    {
        // Check if this node or its children are referenced by other nodes
        iterateChildren(node, this.traverseReferences, true);

        if (this.nodesTraversed.get().size() == 0) {
            this.deleteNode.accept(node);
        } else {
            String referencedNodes = listReferrersFromTraversal();
            if (referencedNodes.length() > 0) {
                // Will not be able to delete node due to references. Inform user.
                sendJsonError(response, SlingJakartaHttpServletResponse.SC_CONFLICT,
                    String.format("This item is referenced in %s.", referencedNodes));
                return false;
            }
            // References were found but they are not references that need user prompting to delete.
            // Do not inform user, just delete.
            // The traversal above already chased everybody's referrers; the deletion phase must chase them again
            this.referrersChased.get().clear();
            handleRecursiveDeleteChildren(node);
        }
        return true;
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
     * Recursively call a function on all nodes which reference a node, and on the node itself. Each node's
     * referrers are only chased once per phase, so reference cycles between resources dragging each other into
     * the deletion terminate.
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
        if (!this.referrersChased.get().add(node.getPath())) {
            return;
        }
        @SuppressWarnings("unchecked")
        final Iterator<Property> references =
            IteratorUtils.chainedIterator(node.getReferences(), node.getWeakReferences());
        final String rootPath = this.nodeToDelete.get().getPath();
        while (references.hasNext()) {
            final Property reference = references.next();
            final Node referrer = reference.getParent();
            final String path = referrer.getPath();
            if (path.equals(rootPath) || path.startsWith(rootPath + "/")) {
                // This a reference within the subtree to delete, ignore it
                continue;
            }

            if (handleLinks(reference, referrer, consumer)) {
                iterateReferrers(referrer, consumer, true);
            }
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
     * Process a link node and any relevant parent nodes based on the link deletion setting.
     *
     * @param reference the property through which the link references the node being deleted
     * @param node a JCR node, may be a {@code cards:Link} node
     * @param consumer the function to be called on any nodes that should also be processed
     * @return {@code true} if processing the node should continue as expected, since this is not a link node, or
     *         {@code false} if this was a link and was already processed by this method
     * @throws RepositoryException if any function call fails due to repository errors
     */
    private boolean handleLinks(Property reference, Node node, NodeConsumer consumer) throws RepositoryException
    {
        if (!node.isNodeType("cards:Link")) {
            return true;
        }
        if ("type".equals(reference.getName())) {
            // The deleted node is the link's own definition, and a link cannot outlive its definition; the
            // definition's deletion policy describes what to do when the link's target is deleted, so it must
            // not be consulted here, or a RECURSIVE_DELETE definition would take all its linking resources down
            // with it
            consumer.accept(node);
            return false;
        }
        // When processing a link for deletion or checking if deletion is possible,
        // some link nodes specify that their parent resource should be deleted or checked as well.
        String onDelete;
        try {
            onDelete = node.getProperty("type").getNode().getProperty("onDelete").getString();
        } catch (RepositoryException e) {
            LOGGER.warn("Cannot resolve the deletion policy of link {}, defaulting to only removing the link",
                node.getPath(), e);
            onDelete = "REMOVE_LINK";
        }
        if ("RECURSIVE_DELETE".equals(onDelete)) {
            // This setting requires that the linking resource also be deleted
            // Go two levels above to find the actual resource, since link are stored under
            // {@code <resource>/cards:links/<link>}
            iterateChildren(node.getParent().getParent(), consumer, false);
            iterateReferrers(node.getParent().getParent(), consumer, true);
        } else if ("IGNORE".equals(onDelete) && node.isNodeType("cards:WeakLink")) {
            // Keep the link as a broken reference; only a weak link may outlive its target
        } else {
            if ("IGNORE".equals(onDelete)) {
                LOGGER.warn("Hard link {} cannot ignore the deletion of its target, removing the link instead",
                    node.getPath());
            }
            // Just delete the link itself, keeping the linking resource intact; this is also the fallback for
            // unrecognized policy values
            consumer.accept(node);
        }
        return false;
    }

    /**
     * Get a string explaining which nodes refer to the node traversed by parent node. A node that cannot be
     * described, e.g. because an expected property is missing, is only counted among the "other" items, so one
     * odd node does not degrade the whole explanation.
     *
     * @return a string in the format "2 forms, 1 subject(subjectName)" for all traversed nodes, or an empty
     *         string if there are no nodes needing confirmation
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity", "checkstyle:JavaNCSS"})
    private String listReferrersFromTraversal()
    {
        int formCount = 0;
        int answerSectionCount = 0;
        int answerCount = 0;
        int otherCount = 0;
        List<String> subjects = new ArrayList<>();
        List<String> subjectTypes = new ArrayList<>();
        List<String> questionnaires = new ArrayList<>();

        for (Node n : this.nodesTraversed.get()) {
            try {
                switch (n.getPrimaryNodeType().getName()) {
                    case "cards:Form":
                        formCount++;
                        break;
                    case "cards:AnswerSection":
                        answerSectionCount++;
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
                    case "cards:Links":
                    case "cards:Link":
                    case "cards:WeakLink":
                        // Do not add links to the user's prompt:
                        // The user will be prompted to delete the parent resource if it is being deleted
                        // or the link should be deleted silently
                        break;
                    case null, default:
                        if ("cards/Answer".equals(n.getProperty("sling:resourceSuperType").getString())) {
                            answerCount++;
                        } else {
                            otherCount++;
                        }
                }
            } catch (RepositoryException e) {
                LOGGER.warn("Failed to describe a node blocking a deletion: {}", e.getMessage(), e);
                otherCount++;
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
        addNodesToResult(results, "subject", subjects);
        addNodesToResult(results, "subject type", subjectTypes);
        addNodesToResult(results, "questionnaire", questionnaires);
        addNodesToResult(results, "other", otherCount);

        return stringArrayToList(results);
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
     *
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
    private static void sendJsonError(final SlingJakartaHttpServletResponse response, int sc)
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
    private static void sendJsonError(final SlingJakartaHttpServletResponse response, int sc, String message)
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
    private static void sendJsonError(final SlingJakartaHttpServletResponse response, int sc, String message,
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
        if (n == null || n.getDepth() == 0 || isNonVersionable(n)) {
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

    private boolean isNonVersionable(final Node n) throws RepositoryException
    {
        return n.getDefinition().getOnParentVersion() == OnParentVersionAction.IGNORE;
    }

    /**
     * Node does not implement {@code equals} and {@code hashCode}, so in order to properly detect if two Node instances
     * reference the same node, we need an explicit comparator that compares the two node paths. This comparator may
     * throw {@code NullPointerException} if any of the nodes to compare are null.
     */
    private static final class NodeComparator implements Comparator<Node>, Serializable
    {
        private static final long serialVersionUID = 1L;

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
