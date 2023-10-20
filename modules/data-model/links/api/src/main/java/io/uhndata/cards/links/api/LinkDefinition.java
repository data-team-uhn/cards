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

import java.util.Set;

import javax.jcr.Node;

/**
 * A Link Definition, settings for connections from one resource to another.
 *
 * @version $Id$
 * @since 0.9.19
 */
public interface LinkDefinition
{
    /** The primary node type for a Link Definition. */
    String LINK_DEFINITION_NODETYPE = "cards:LinkDefinition";

    /** The Sling resource type of a Link Definition. */
    String LINK_DEFINITION_RESOURCE = "cards/LinkDefinition";

    /** The name of the property of a LinkDefinition node that specifies if the link is weak or strong. */
    String WEAK_PROPERTY = "weak";

    /** The name of the property of a LinkDefinition node that specifies the optional link type label. */
    String LABEL_PROPERTY = "label";

    /**
     * The name of the property of a LinkDefinition node that specifies which node types are required for the source
     * node.
     */
    String REQUIRED_SOURCE_TYPES_PROPERTY = "requiredSourceTypes";

    /**
     * The name of the property of a LinkDefinition node that specifies which node types are required for the
     * destination node.
     */
    String REQUIRED_DESTINATION_TYPES_PROPERTY = "requiredDestinationTypes";

    /** The name of the property of a LinkDefinition node that specifies the optional resource display format. */
    String RESOURCE_LABEL_FORMAT_PROPERTY = "resourceLabelFormat";

    /** The name of the property of a LinkDefinition node that specifies if a backlink should be created. */
    String BACKLINK_PROPERTY = "backlink";

    /**
     * The name of the property of a LinkDefinition node that specifies if a backlink must be created, even if access
     * rights would normally prevent write access to it for the current user.
     */
    String FORCE_BACKLINK_PROPERTY = "forceBacklink";

    /**
     * The name of the property of a LinkDefinition node that specifies if a link is only available as a backlink and
     * should not be created manually by users.
     */
    String BACKLINK_ONLY_PROPERTY = "backlinkOnly";

    /**
     * The name of the property of a LinkDefinition node that specifies what happens with the referencing resource when
     * the reference resource is deleted.
     */
    String ON_DELETE_PROPERTY = "onDelete";

    /**
     * What to do when the referenced resource is deleted.
     */
    enum OnDelete
    {
        /** Keep the link as a broken reference. Only valid for weak links. */
        IGNORE,
        /** Only remove the link between resources, keep the source resource in place. */
        REMOVE_LINK,
        /** Also delete the source resource, and any others it may impact. */
        RECURSIVE_DELETE
    }

    /**
     * Get the JCR node for this link definition.
     *
     * @return a JCR node
     */
    Node getNode();

    /**
     * Denotes a weak link, which may be broken when the linked resource gets deleted.
     *
     * @return {@code true} if this link type is weak, {@code false} otherwise
     */
    boolean isWeak();

    /**
     * A user-friendly name for this type of link.
     *
     * @return a short string
     */
    String getLabel();

    /**
     * An optional list of node types that are allowed as valid nodes on which the link can be set. If not set, any type
     * of node can be used.
     *
     * @return a set of node types, e.g. {@code {cards:Form, cards:Subject}}, may be empty
     */
    Set<String> getRequiredSourceTypes();

    /**
     * An optional list of node types that are allowed as valid nodes to link to. If not set, any type of node can be
     * used.
     *
     * @return a set of node types, e.g. {@code {cards:Form, cards:Subject}}, may be empty
     */
    Set<String> getRequiredDestinationTypes();

    /**
     * An optional expression for rendering a better label for the linked resource. If no format is used, the node name
     * is returned when rendering the linked resource of a link. This expression must be a valid JavaScript snippet, and
     * may use the following variables:
     * <ul>
     * <li>label, the optional label set on the link</li>
     * <li>typeNode, the JCR node of the link definition</li>
     * <li>typeLabel, the {@link #getLabel() label} of the link definition</li>
     * <li>destinationNode, the JCR node of the linked resource</li>
     * <li>destinationName, the JCR node name of the linked resource</li>
     * <li>sourceNode, the JCR node of the linking resource</li>
     * <li>sourceName, the JCR node name of the linking resource</li>
     * </ul>
     *
     * @return a string, or {@code null}
     */
    String getResourceLabelFormat();

    /**
     * Check if a backlink is set for this link type.
     *
     * @return {@code true} if there is a backlink set
     * @see #getBacklink()
     */
    boolean hasBacklink();

    /**
     * Retrieve the link definition for the backlink that must be created for every link of this type. It may be
     * {@code null} if no backlink is set.
     *
     * @return a link definition, or {@code null}
     */
    LinkDefinition getBacklink();

    /**
     * Check if a backlink must be created, even if access rights would normally prevent write access to it for the
     * current user. If a backlink is not forced and the current user does not have the right to create it, then a
     * backlink will not be created.
     *
     * @return {@code true} if the backlink will be created by a service user instead of the current user
     */
    boolean isBacklinkForced();

    /**
     * Check if this link type is only available as a backlink and should not be created manually by users.
     *
     * @return {@code true} if this type of link should not be offered to users as a valid link type
     */
    boolean isBacklinkOnly();

    /**
     * Retrieve the policy to use when the linked resource is deleted.
     *
     * @return a deletion policy
     * @see OnDelete
     */
    OnDelete getOnDeletePolicy();
}
