/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.links.api;

import java.util.Collection;

import javax.jcr.Node;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Basic utilities for working with Links between resources.
 *
 * @version $Id$
 * @since 0.9.20
 */
public interface LinkUtils
{
    /** The repository location where link definitions are stored. */
    String LINK_DEFINITIONS_PATH = "/apps/cards/LinkDefinitions/";

    /** The child node name where the links for a resource are stored. */
    String LINKS_CONTAINER = "cards:links";

    /** The primary node type for the Links container. */
    String LINKS_NODETYPE = "cards:Links";

    /**
     * Load a link from its underlying JCR node.
     *
     * @param linkNode a JCR node, must be of type {@code cards:Link} or a subtype of it
     * @return a link object, or {@code null} if reading the node fails
     * @throws IllegalArgumentException if the provided node is not a link
     */
    @Nullable
    Link getLink(@NotNull Node linkNode) throws IllegalArgumentException;

    /**
     * Retrieve all the links from a resource or link node.
     *
     * @param source the node to get links from
     * @return a collection of links, may be empty
     */
    @NotNull
    Collection<Link> getLinks(@NotNull Node source);

    /**
     * Retrieve all the links from a resource of a desired link type.
     *
     * @param source the node to get links from
     * @param type the type of link to filter by, the name of the node holding the link definition, or a JCR path to it
     * @return a collection of links, may be empty
     */
    @NotNull
    Collection<Link> getLinksOfType(@NotNull Node source, @NotNull String type);

    /**
     * Retrieve all the links from a resource of a desired link type.
     *
     * @param source the node to get links from
     * @param type the node holding the link definition to filter by
     * @return a collection of links, may be empty
     */
    @NotNull
    Collection<Link> getLinksOfType(@NotNull Node source, @NotNull Node type);

    /**
     * Retrieve all the links to a resource.
     *
     * @param source the node to get links pointing to
     * @return a collection of links, may be empty
     */
    @NotNull
    Collection<Link> getBacklinks(@NotNull Node source);

    /**
     * Create a new link between two resources.
     *
     * @param source the node to put the link on
     * @param destination the node that the link points to
     * @param type the type of link to create, the name of the node holding the link definition, or a JCR path to it
     * @param label an optional label for the new link
     * @return the newly created link, or an existing link if there is one with the exact same parameters
     */
    @NotNull
    Link addLink(@NotNull Node source, @NotNull Node destination, @NotNull String type,
        @Nullable String label);

    /**
     * Create a new link between two resources.
     *
     * @param source the node to put the link on
     * @param destination the node that the link points to
     * @param type the type of link to create
     * @param label an optional label for the new link
     * @return the newly created link, or an existing link if there is one with the exact same parameters
     */
    @NotNull
    Link addLink(@NotNull Node source, @NotNull Node destination, @NotNull Node type,
        @Nullable String label);

    /**
     * Create a new link between two resources.
     *
     * @param source the node builder to put the link on, usually a newly created node
     * @param destination the node that the link points to
     * @param type the type of link to create
     * @param label an optional label for the new link
     */
    void addLink(@NotNull NodeBuilder source, @NotNull Node destination, @NotNull Node type,
        @Nullable String label);

    /**
     * Add a backlink for the original link, if it is missing.
     *
     * @param original an existing link
     * @return {@code true} if a backlink was created or already existed, {@code false} otherwise
     */
    boolean addBacklink(@NotNull Link original);

    /**
     * Delete a link.
     *
     * @param link the JCR node holding the link to delete, must be of type {@code cards:Link} or {@code cards:WeakLink}
     * @return {@code true} if the link was deleted, {@code false} if the operation failed
     */
    boolean removeLink(@NotNull Node link);

    /**
     * Delete a link.
     *
     * @param link the JCR node holding the link to delete, must be of type {@code cards:Link} or {@code cards:WeakLink}
     * @param removeBacklinks if {@code true}, delete any backlinks from the desination to source based on the node type
     * @return {@code true} if the link was deleted, {@code false} if the operation failed
     */
    boolean removeLink(@NotNull Node link, boolean removeBacklinks);

    /**
     * Delete all the links matching the parameters. This will remove all links from the source to the destination of
     * the requested type, regardless of labels.
     *
     * @param source the node to delete links from
     * @param destination the linked node, or {@code null} to match links to any destination
     * @param type the type of link to delete, the name of the node holding the link definition, or a JCR path to it
     * @return {@code true} if all matching links were deleted, {@code false} if no links were found or deleting any of
     *         the links failed
     */
    boolean removeLinks(@NotNull Node source, @Nullable Node destination, @NotNull String type);

    /**
     * Delete all the links matching the parameters. If {@code null} is passed as the label, remove all links from the
     * source to the destination of the requested type, regardless of labels. Use an empty string to only delete links
     * that have no label.
     *
     * @param source the node to delete links from
     * @param destination the linked node, or {@code null} to match links to any destination
     * @param type the type of link to delete, the name of the node holding the link definition, or a JCR path to it
     * @param label the label of the link to delete, {@code null} to delete all links, an empty string to only delete
     *            links with no label, or a specific label to match
     * @return {@code true} if all matching links were deleted, {@code false} if no links were found or deleting any of
     *         the links failed
     */
    boolean removeLinks(@NotNull Node source, @Nullable Node destination, @NotNull String type,
        @Nullable String label);

    /**
     * Delete all the links matching the parameters. If {@code null} is passed as the label, remove all links from the
     * source to the destination of the requested type, regardless of labels. Use an empty string to only delete links
     * that have no label.
     *
     * @param source the node to delete links from
     * @param destination the linked node, or {@code null} to match links to any destination
     * @param type the type of link to delete, the name of the node holding the link definition, or a JCR path to it
     * @param label the label of the link to delete, {@code null} to delete all links, an empty string to only delete
     *            links with no label, or a specific label to match
     * @param removeBacklinks if {@code true}, delete any backlinks from the desination to source based on the node type
     * @return {@code true} if all matching links were deleted, {@code false} if no links were found or deleting any of
     *         the links failed
     */
    boolean removeLinks(@NotNull Node source, @Nullable Node destination, @NotNull String type,
        @Nullable String label, boolean removeBacklinks);

    /**
     * Delete all the links matching the parameters. This will remove all links from the source to the destination of
     * the requested type, regardless of labels.
     *
     * @param source the node to delete links from
     * @param destination the linked node, or {@code null} to match links to any destination
     * @param type the type of link to delete
     * @return {@code true} if all matching links were deleted, {@code false} if no links were found or deleting any of
     *         the links failed
     */
    boolean removeLinks(@NotNull Node source, @Nullable Node destination, @NotNull Node type);

    /**
     * Delete all the links matching the parameters. If {@code null} is passed as the label, remove all links from the
     * source to the destination of the requested type, regardless of labels. Use an empty string to only delete links
     * that have no label.
     *
     * @param source the node to delete links from
     * @param destination the linked node, or {@code null} to match links to any destination
     * @param type the type of link to delete
     * @param label the label of the link to delete, {@code null} to delete all links, an empty string to only delete
     *            links with no label, or a specific label to match
     * @return {@code true} if all matching links were deleted, {@code false} if no links were found or deleting any of
     *         the links failed
     */
    boolean removeLinks(@NotNull Node source, @Nullable Node destination, @NotNull Node type,
        @Nullable String label);

    /**
     * Delete all the links matching the parameters. If {@code null} is passed as the label, remove all links from the
     * source to the destination of the requested type, regardless of labels. Use an empty string to only delete links
     * that have no label.
     *
     * @param source the node to delete links from
     * @param destination the linked node, or {@code null} to match links to any destination
     * @param type the type of link to delete
     * @param label the label of the link to delete, {@code null} to delete all links, an empty string to only delete
     *            links with no label, or a specific label to match
     * @param removeBacklinks if {@code true}, delete any backlinks from the desination to source based on the node type
     * @return {@code true} if all matching links were deleted, {@code false} if no links were found or deleting any of
     *         the links failed
     */
    boolean removeLinks(@NotNull Node source, @Nullable Node destination, @NotNull Node type,
        @Nullable String label, boolean removeBacklinks);
}
