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
package io.uhndata.cards.spi;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Some utilities for working with JCR nodes.
 *
 * @version $Id$
 */
public abstract class AbstractNodeUtils
{
    /**
     * Check if the given node is of a specific type, either directly as a primary node type, or as a subtype.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @param targetNodeType the required node type, a prefixed name like {@code cards:Form}
     * @return {@code true} if the node is not {@code null} and is of the required type, {@code false} otherwise
     */
    protected boolean isNodeType(final Node node, final String targetNodeType)
    {
        if (node == null) {
            return false;
        }
        try {
            return node.isNodeType(targetNodeType);
        } catch (final RepositoryException e) {
            return false;
        }
    }

    /**
     * Check if the given node is of a specific type, either directly as a primary node type, or as a subtype.
     *
     * @param node the node to check, a Node State, may be {@code null}
     * @param targetNodeType the required node type, a prefixed name like {@code cards:Form}
     * @param session the current JCR session, needed to access information about node types
     * @return {@code true} if the node is not {@code null} and has the required primary node type, {@code false}
     *         otherwise
     */
    protected boolean isNodeType(final NodeState node, final String targetNodeType, final Session session)
    {
        if (session == null) {
            return false;
        }
        final PropertyState primaryType = node.getProperty("jcr:primaryType");
        if (primaryType != null) {
            final String actualNodeType = primaryType.getValue(Type.NAME);
            try {
                if (session.getWorkspace().getNodeTypeManager()
                    .getNodeType(actualNodeType).isNodeType(targetNodeType)) {
                    return true;
                }
            } catch (final RepositoryException e) {
                // Shouldn't happen
            }
        }
        return false;
    }

    /**
     * Retrieve a referenced node, for example an Answer's Question.
     *
     * @param node a JCR node
     * @param property the name of the property holding the reference to the target node
     * @return the target node, or {@code null} if the requested reference is not valid
     */
    protected Node getReferencedNode(final Node node, final String property)
    {
        try {
            return node.getProperty(property).getNode();
        } catch (final RepositoryException e) {
            return null;
        }
    }

    /**
     * Retrieve a referenced node of a given type, for example a form's related subject of type Patient.
     *
     * @param node a JCR node
     * @param relatingProperty the name of the property holding a list of references to other nodes, must be
     *            multi-valued, e.g. {@code relatedSubjects}
     * @param typePath a path to a node type, e.g. {@code /SubjectTypes/Patient}
     * @param typeProperty the name of the property of the related node referencing the type, e.g. {@code type}
     * @return the target node, or {@code null} if the requested reference is not valid or is inaccessible
     */
    protected Node getReferencedNodeOfType(final Node node, final String relatingProperty, final String typePath,
        final String typeProperty)
    {
        try {
            Value[] values = node.getProperty(relatingProperty).getValues();
            for (Value value : values) {
                try {
                    Node referenced = node.getSession().getNodeByIdentifier(value.getString());
                    if (referenced.getProperty(typeProperty).getNode().getPath().equals(typePath)) {
                        return referenced;
                    }
                } catch (Exception e) {
                    // The current user may not have access to all the referenced nodes
                }
            }
        } catch (final RepositoryException e) {
            // We're allowed to just return null in case of exceptions
        }
        return null;
    }

    /**
     * Get the value of a String-like property.
     *
     * @param node a JCR node
     * @param property the name of the property to retrieve
     * @return the value stored in the property, or {@code null} if the requested property is not valid
     */
    protected String getStringProperty(final Node node, final String property)
    {
        try {
            return node.getProperty(property).getString();
        } catch (final RepositoryException e) {
            return null;
        }
    }

    /**
     * Get the value of a String-like property.
     *
     * @param node a node state
     * @param property the name of the property to retrieve
     * @return the value stored in the property, or {@code null} if the requested property is not valid
     */
    protected String getStringProperty(final NodeState node, final String property)
    {
        return node.getProperty(property).getValue(Type.STRING);
    }

    protected Node getNodeByIdentifier(final String identifier, final Session session)
    {
        try {
            if (session == null) {
                return null;
            }
            return session.getNodeByIdentifier(identifier);
        } catch (final RepositoryException e) {
            return null;
        }
    }

    /**
     * Obtain the current session from the resource resolver factory.
     *
     * @param rrp the resource resolver factory service, may be {@code null}
     * @return the current session, or {@code null} if a session may not be obtained
     */
    protected Session getSession(final ThreadResourceResolverProvider rrp)
    {
        if (rrp == null) {
            return null;
        }

        final ResourceResolver rr = rrp.getThreadResourceResolver();
        if (rr == null) {
            return null;
        }
        return rr.adaptTo(Session.class);
    }
}
