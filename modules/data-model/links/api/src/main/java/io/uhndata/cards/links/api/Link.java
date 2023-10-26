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

import javax.jcr.Node;
import javax.json.JsonObject;

/**
 * A Link, a connection from one resource to another.
 *
 * @version $Id$
 * @since 0.9.20
 */
public interface Link
{
    /** The primary node type for a strong Link. */
    String LINK_NODETYPE = "cards:Link";

    /** The Sling resource type of a Link. */
    String LINK_RESOURCE = "cards/Link";

    /** The primary node type for a weak Link. */
    String WEAK_LINK_NODETYPE = "cards:WeakLink";

    /** The Sling resource type of a weak Link. */
    String WEAK_LINK_RESOURCE = "cards/WeakLink";

    /** The name of the property of a Link node that links to the LinkDefinition. */
    String TYPE_PROPERTY = "type";

    /** The name of the property of a Link node that holds the actual link, a reference to another node. */
    String REFERENCE_PROPERTY = "reference";

    /** The name of the property of a Link node that specifies an optional link label. */
    String LABEL_PROPERTY = "label";

    /**
     * Get the JCR node for this link.
     *
     * @return a JCR node
     */
    Node getNode();

    /**
     * Get the definition of this link.
     *
     * @return a link definition object
     */
    LinkDefinition getDefinition();

    /**
     * Get the rendered label for the linked resource.
     *
     * @return a formatted label for the resource
     * @see LinkDefinition#getResourceLabelFormat()
     */
    String getResourceLabel();

    /**
     * Reports if this is a weak or a strong link.
     *
     * @return {@code true} if this is a weak link that may be broken if the linked resource is deleted, {@code false}
     *         if the link forces the resource and the link to be deleted together
     */
    boolean isWeak();

    /**
     * Reports if there is an equivalent backlink from the linked resource to the linked resource.
     *
     * @return {@code true} if there is a backlink, {@code false} otherwise
     */
    boolean isSymmetric();

    /**
     * Retrieve the resource that the link points to.
     *
     * @return a JCR node, may be {@code null} if the linked resource has been deleted
     */
    Node getLinkedResource();

    /**
     * Retrieve the resource that the link belongs to.
     *
     * @return a JCR node
     */
    Node getLinkingResource();

    /**
     * Retrieve the equivalent backlink.
     *
     * @return a Link object, may be {@code null} if a backlink doesn't exist
     */
    Link getBacklink();

    /**
     * Retrieve the (optional) label set for this link.
     *
     * @return a string, empty if no label is set
     */
    String getLabel();

    /**
     * Get a nicer JSON representation of this link.
     *
     * @return a JSON object
     */
    JsonObject toJson();
}
