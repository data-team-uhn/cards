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
package io.uhndata.cards.links.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.version.VersionManager;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.links.api.Link;
import io.uhndata.cards.links.api.LinkDefinition;
import io.uhndata.cards.links.api.LinkUtils;
import io.uhndata.cards.spi.AbstractNodeUtils;

/**
 * Basic utilities for working with Form data.
 *
 * @version $Id$
 */
@Component
public final class LinkUtilsImpl extends AbstractNodeUtils implements LinkUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUtilsImpl.class);

    @Reference
    private ResourceResolverFactory rrf;

    @Reference
    private ScriptEngineManager scriptManager;

    @Override
    public Link getLink(final Node linkNode)
    {
        try {
            if (!linkNode.isNodeType(Link.LINK_NODETYPE)) {
                throw new IllegalArgumentException("Not a link");
            }
            return new LinkImpl(linkNode);
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to read link: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Collection<Link> getLinks(Node source)
    {
        final Collection<Link> result = new ArrayList<>();
        try {
            if (!source.hasNode(LINKS_CONTAINER)) {
                return result;
            }
            final NodeIterator children = source.getNode(LINKS_CONTAINER).getNodes();
            while (children.hasNext()) {
                result.add(new LinkImpl(children.nextNode()));
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to retrieve links from {}: {}", source, e.getMessage(), e);
        }
        return result;
    }

    @Override
    public Collection<Link> getBacklinks(Node source)
    {
        final Collection<Link> result = new ArrayList<>();
        try {
            NodeIterator links = source.getSession().getWorkspace().getQueryManager()
                .createQuery("SELECT l.* FROM [cards:Link] AS l WHERE l.reference = '" + source.getIdentifier() + "'",
                    Query.JCR_SQL2)
                .execute().getNodes();
            while (links.hasNext()) {
                result.add(new LinkImpl(links.nextNode()));
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to retrieve backlinks to {}: {}", source, e.getMessage(), e);
        }
        return result;
    }

    @Override
    public Link addLink(Node source, Node destination, String type, String label) throws IllegalArgumentException
    {
        try {
            return addLink(source, destination,
                source.getSession().getNode(type.startsWith("/") ? type : LINK_DEFINITIONS_PATH + type),
                label);
        } catch (RepositoryException e) {
            throw new IllegalArgumentException("Unknown link type: " + type);
        }
    }

    @Override
    public Link addLink(Node source, Node destination, Node type, String label)
    {
        try {
            final Link existingLink = getLink(source, destination, type, label);
            if (existingLink != null) {
                return existingLink;
            }
            final LinkDefinition typeDefinition = new LinkDefinitionImpl(type);
            final Node linksContainer = getLinksContainer(source);
            final boolean isWeak = (typeDefinition.isWeak());
            final Node newLink = linksContainer.addNode(UUID.randomUUID().toString(),
                isWeak ? Link.WEAK_LINK_NODETYPE : Link.LINK_NODETYPE);
            newLink.setProperty(Link.TYPE_PROPERTY, type);
            newLink.setProperty(Link.REFERENCE_PROPERTY,
                source.getSession().getValueFactory().createValue(destination, isWeak));
            if (!StringUtils.isEmpty(label)) {
                newLink.setProperty(Link.LABEL_PROPERTY, label);
            }
            source.getSession().save();
            final Link result = new LinkImpl(newLink);
            addBacklink(result);
            return result;
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to create link of type {} from {} to {}: {}", type, source, destination,
                e.getMessage(), e);
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void addLink(NodeBuilder source, Node destination, Node type, String label)
    {
        try {
            final LinkDefinition typeDefinition = new LinkDefinitionImpl(type);
            final NodeBuilder linksContainer = getLinksContainer(source);
            final boolean isWeak = (typeDefinition.isWeak());
            final NodeBuilder newLink = linksContainer.child(UUID.randomUUID().toString());
            newLink.setProperty("jcr:primaryType", isWeak ? Link.WEAK_LINK_NODETYPE : Link.LINK_NODETYPE, Type.NAME);
            newLink.setProperty(Link.TYPE_PROPERTY, type.getIdentifier(), Type.REFERENCE);
            newLink.setProperty(Link.REFERENCE_PROPERTY, destination.getIdentifier(),
                isWeak ? Type.WEAKREFERENCE : Type.REFERENCE);
            if (!StringUtils.isEmpty(label)) {
                newLink.setProperty(Link.LABEL_PROPERTY, label, Type.STRING);
            }
            // Links created from a commit editor cannot add a backlink, since during a commit we cannot do another
            // commit; we'll have to let the backlink autocreation step in and create it, even though a backlink isn't
            // forced
        } catch (IllegalArgumentException | RepositoryException e) {
            LOGGER.warn("Failed to create link of type {} from {} to {}: {}", type, source, destination,
                e.getMessage(), e);
            throw new IllegalArgumentException();
        }
    }

    private Link getLink(Node source, Node destination, Node type, String label) throws RepositoryException
    {
        NodeIterator links = getLinksContainer(source).getNodes();
        while (links.hasNext()) {
            Node link = links.nextNode();
            if (link.getProperty(Link.TYPE_PROPERTY).getNode().isSame(type)
                && link.getProperty(Link.REFERENCE_PROPERTY).getString().equals(destination.getIdentifier())
                && hasSameLabel(link, label)) {
                return new LinkImpl(link);
            }
        }
        return null;
    }

    private boolean hasSameLabel(Node link, String label) throws RepositoryException
    {
        if (StringUtils.isEmpty(label)) {
            return !link.hasProperty(Link.LABEL_PROPERTY)
                || StringUtils.isEmpty(link.getProperty(Link.LABEL_PROPERTY).getString());
        }
        return link.hasProperty(Link.LABEL_PROPERTY)
            && label.equals(link.getProperty(Link.LABEL_PROPERTY).getString());
    }

    @Override
    public boolean addBacklink(Link original)
    {
        if (!original.getDefinition().hasBacklink()) {
            return false;
        }
        if (original.getDefinition().isBacklinkForced()) {
            try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(null)) {
                final Session session = resolver.adaptTo(Session.class);
                addLink(session.getNode(original.getLinkedResource().getPath()),
                    session.getNode(original.getLinkingResource().getPath()),
                    session.getNode(original.getDefinition().getBacklink().getNode().getPath()), original.getLabel());
            } catch (LoginException e) {
                LOGGER.error("Missing service user registration: {}", e.getMessage());
                return false;
            } catch (RepositoryException e) {
                LOGGER.warn("Failed to create backlink: {}", e.getMessage());
            }
        } else {
            try {
                if ("cards-links-manager".equals(original.getLinkingResource().getSession().getUserID())) {
                    // This is not a forced link, and the original user is no longer in the session.
                    // TODO: try to impersonate the original user who created the link
                }
            } catch (RepositoryException e) {
                // Not expected
            }
        }
        return addLink(original.getLinkedResource(), original.getLinkingResource(),
            original.getDefinition().getBacklink().getNode(), original.getLabel()) != null;
    }

    @Override
    public boolean removeLink(Node link)
    {
        try {
            if (!link.isNodeType(Link.LINK_NODETYPE)) {
                return false;
            }
            link.remove();
            link.getSession().save();
            return true;
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to delete link {}: {}", link, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean removeLinks(Node source, Node destination, String type)
    {
        return removeLinks(source, destination, type, null);
    }

    @Override
    public boolean removeLinks(Node source, Node destination, String type, String label)
    {
        try {
            return removeLinks(source, destination, source.getSession().getNode(LINK_DEFINITIONS_PATH + type), label);
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to delete link of type {} from {} to {}: {}", type, source, destination,
                e.getMessage(), e);
            throw new IllegalArgumentException();
        }
    }

    @Override
    public boolean removeLinks(Node source, Node destination, Node type)
    {
        return removeLinks(source, destination, type, null);
    }

    @Override
    public boolean removeLinks(Node source, Node destination, Node type, String label)
    {
        final List<Link> matchingLinks = getLinks(source).stream().filter(link -> {
            try {
                return link.getDefinition().getNode().isSame(type);
            } catch (RepositoryException e) {
                return false;
            }
        }).filter(link -> {
            try {
                return link.getLinkedResource() != null && destination.isSame(link.getLinkedResource());
            } catch (RepositoryException e) {
                return false;
            }
        }).filter(link -> label == null || StringUtils.equals(label, link.getLabel()))
            .collect(Collectors.toList());

        return !matchingLinks.isEmpty()
            && matchingLinks.stream().map(link -> removeLink(link.getNode())).reduce(true, Boolean::logicalAnd);
    }

    private Node getLinksContainer(final Node resource) throws RepositoryException
    {
        if (resource.hasNode(LINKS_CONTAINER)) {
            return resource.getNode(LINKS_CONTAINER);
        }
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(null)) {
            final Session session = resolver.adaptTo(Session.class);
            final VersionManager manager = session.getWorkspace().getVersionManager();
            final boolean wasCheckedOut = manager.isCheckedOut(resource.getPath());
            if (!wasCheckedOut) {
                manager.checkout(resource.getPath());
            }
            session.getNode(resource.getPath()).addNode(LINKS_CONTAINER, LINKS_NODETYPE);
            session.save();
            if (!wasCheckedOut) {
                manager.checkin(resource.getPath());
            }
            resource.refresh(true);
            return resource.getNode(LINKS_CONTAINER);
        } catch (LoginException e) {
            LOGGER.error("Missing service user registration: {}", e.getMessage());
            return null;
        }
    }

    private NodeBuilder getLinksContainer(final NodeBuilder resource)
    {
        if (resource.hasChildNode(LINKS_CONTAINER)) {
            return resource.getChildNode(LINKS_CONTAINER);
        }
        final NodeBuilder result = resource.child(LINKS_CONTAINER);
        result.setProperty("jcr:primaryType", LINKS_NODETYPE, Type.NAME);
        return result;
    }

    private final class LinkDefinitionImpl implements LinkDefinition
    {
        private Node definition;

        LinkDefinitionImpl(final Node definition)
        {
            this.definition = definition;
        }

        @Override
        public Node getNode()
        {
            return this.definition;
        }

        @Override
        public String getLabel()
        {
            try {
                return this.definition.hasProperty(LABEL_PROPERTY)
                    ? this.definition.getProperty(LABEL_PROPERTY).getString() : this.definition.getName();
            } catch (RepositoryException e) {
                LOGGER.warn("Failed to access link definition label: {}", e.getMessage(), e);
                try {
                    return this.definition.getName();
                } catch (RepositoryException e1) {
                    return "";
                }
            }
        }

        @Override
        public Set<String> getRequiredSourceTypes()
        {
            return getRequiredTypes(REQUIRED_SOURCE_TYPES_PROPERTY);
        }

        private Set<String> getRequiredTypes(final String propertyName)
        {
            Set<String> result = new HashSet<>();
            try {
                if (this.definition.hasProperty(propertyName)) {
                    for (Value v : this.definition.getProperty(propertyName).getValues()) {
                        result.add(v.getString());
                    }
                }
            } catch (IllegalStateException | RepositoryException e) {
                LOGGER.warn("Unexpected error reading the list of accepted link source types: {}", e.getMessage(), e);
            }

            return result;
        }

        @Override
        public Set<String> getRequiredDestinationTypes()
        {
            return getRequiredTypes(REQUIRED_DESTINATION_TYPES_PROPERTY);
        }

        @Override
        public boolean isWeak()
        {
            try {
                return this.definition.hasProperty(LinkDefinition.WEAK_PROPERTY)
                    && this.definition.getProperty(LinkDefinition.WEAK_PROPERTY).getBoolean();
            } catch (RepositoryException e) {
                return false;
            }
        }

        @Override
        public String getResourceLabelFormat()
        {
            try {
                return this.definition.hasProperty(RESOURCE_LABEL_FORMAT_PROPERTY)
                    ? this.definition.getProperty(RESOURCE_LABEL_FORMAT_PROPERTY).getString() : null;
            } catch (RepositoryException e) {
                LOGGER.warn("Invalid resource label format setting on {}", this.definition);
                return null;
            }
        }

        @Override
        public boolean hasBacklink()
        {
            try {
                return this.definition.hasProperty(BACKLINK_PROPERTY);
            } catch (RepositoryException e) {
                return false;
            }
        }

        @Override
        public LinkDefinition getBacklink()
        {
            try {
                if (this.definition.hasProperty(BACKLINK_PROPERTY)) {
                    return new LinkDefinitionImpl(this.definition.getProperty(BACKLINK_PROPERTY).getNode());
                }
            } catch (RepositoryException e) {
                LOGGER.warn("Invalid backlink setting on {}", this.definition);
            }
            return null;
        }

        @Override
        public boolean isBacklinkForced()
        {
            try {
                return this.definition.hasProperty(FORCE_BACKLINK_PROPERTY)
                    ? this.definition.getProperty(FORCE_BACKLINK_PROPERTY).getBoolean() : false;
            } catch (RepositoryException e) {
                LOGGER.warn("Invalid forceBacklink setting on {}", this.definition);
                return false;
            }
        }

        @Override
        public boolean isBacklinkOnly()
        {
            try {
                return this.definition.hasProperty(BACKLINK_ONLY_PROPERTY)
                    ? this.definition.getProperty(BACKLINK_ONLY_PROPERTY).getBoolean() : false;
            } catch (RepositoryException e) {
                LOGGER.warn("Invalid backlinkOnly setting on {}", this.definition);
                return false;
            }
        }

        @Override
        public OnDelete getOnDeletePolicy()
        {
            try {
                return OnDelete.valueOf(this.definition.getProperty(ON_DELETE_PROPERTY).getString());
            } catch (RepositoryException e) {
                LOGGER.warn("Invalid OnDelete policy on {}", this.definition);
                return OnDelete.REMOVE_LINK;
            }
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LinkDefinitionImpl)) {
                return false;
            }
            try {
                return this.definition.isSame(((LinkDefinitionImpl) other).definition);
            } catch (RepositoryException e) {
                return false;
            }
        }

        @Override
        public int hashCode()
        {
            try {
                return this.definition.getPath().hashCode();
            } catch (RepositoryException e) {
                return super.hashCode();
            }
        }
    }

    private final class LinkImpl implements Link
    {
        private final Node link;

        private final Node source;

        private final Node destination;

        private final Node type;

        LinkImpl(final Node link) throws RepositoryException
        {
            this.link = link;
            this.source = link.getParent().getParent();
            Node tmp = null;
            try {
                tmp = link.getProperty(REFERENCE_PROPERTY).getNode();
            } catch (ItemNotFoundException e) {
                // Broken link
            }
            this.destination = tmp;
            this.type = link.getProperty(TYPE_PROPERTY).getNode();
        }

        @Override
        public Node getNode()
        {
            return this.link;
        }

        @Override
        public String getLabel()
        {
            try {
                return this.link.hasProperty(LABEL_PROPERTY) ? this.link.getProperty(LABEL_PROPERTY).getString() : "";
            } catch (RepositoryException e) {
                return "";
            }
        }

        @Override
        public boolean isWeak()
        {
            try {
                return this.link.isNodeType(WEAK_LINK_NODETYPE);
            } catch (RepositoryException e) {
                return false;
            }
        }

        @Override
        public boolean isSymmetric()
        {
            try {
                return this.type.hasProperty("backLink");
            } catch (RepositoryException e) {
                return false;
            }
        }

        @Override
        public Link getBacklink()
        {
            if (this.destination == null) {
                return null;
            }
            return LinkUtilsImpl.this.getLinks(this.destination).stream()
                .filter(this::isReverse)
                .findFirst().orElse(null);
        }

        @Override
        public Node getLinkedResource()
        {
            return this.destination;
        }

        @Override
        public Node getLinkingResource()
        {
            return this.source;
        }

        @Override
        public LinkDefinition getDefinition()
        {
            return new LinkDefinitionImpl(this.type);
        }

        @Override
        public String getResourceLabel()
        {
            if (this.destination == null) {
                return "inaccessible resource";
            }
            try {
                if (StringUtils.isBlank(this.getDefinition().getResourceLabelFormat())) {
                    return this.getLinkedResource().getName();
                }
                final String format = this.getDefinition().getResourceLabelFormat();
                final ScriptEngine engine = LinkUtilsImpl.this.scriptManager.getEngineByName("JavaScript");
                final Bindings env = engine.createBindings();
                env.put("label", this.getLabel());
                env.put("typeNode", this.getDefinition());
                env.put("typeLabel", this.getDefinition().getLabel());
                env.put("destinationNode", this.getLinkedResource());
                env.put("destinationName", this.getLinkedResource().getName());
                env.put("sourceNode", this.getLinkingResource());
                env.put("sourceName", this.getLinkingResource().getName());
                Object result = engine
                    .eval("(function(){" + (format.contains("return ") ? format : "return " + format) + "})()", env);

                return String.valueOf(result);
            } catch (RepositoryException | ScriptException e) {
                LOGGER.warn("Failed to compute link resource label: {}", e.getMessage());
                try {
                    return this.getLinkedResource().getName();
                } catch (RepositoryException e1) {
                    return String.valueOf(this.destination);
                }
            }
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LinkImpl)) {
                return false;
            }
            try {
                Link otherLink = (Link) other;
                return this.getLinkedResource().isSame(otherLink.getLinkedResource())
                    && this.getLinkingResource().isSame(otherLink.getLinkingResource())
                    && this.getDefinition().equals(otherLink.getDefinition())
                    && this.getLabel().equals(otherLink.getLabel());
            } catch (RepositoryException e) {
                return false;
            }
        }

        public boolean isReverse(Link other)
        {
            if (!this.getDefinition().hasBacklink() && !other.getDefinition().hasBacklink()) {
                return false;
            }
            try {
                return other.getLinkedResource() != null
                    && this.getLinkedResource().isSame(other.getLinkingResource())
                    && this.getLinkingResource().isSame(other.getLinkedResource())
                    && (this.getDefinition().hasBacklink()
                        ? this.getDefinition().getBacklink().equals(other.getDefinition())
                        : other.getDefinition().getBacklink().equals(this.getDefinition()));
            } catch (RepositoryException e) {
                return false;
            }
        }

        @Override
        public int hashCode()
        {
            try {
                return Objects.hash(this.type.getPath(), this.source.getPath(), this.destination.getPath(),
                    this.getLabel());
            } catch (RepositoryException e) {
                return super.hashCode();
            }
        }

        @Override
        public JsonObject toJson()
        {
            JsonObjectBuilder result = Json.createObjectBuilder()
                .add("type", this.getDefinition().getLabel());
            try {
                result.add("to",
                    this.getLinkedResource() != null ? this.getLinkedResource().getPath() : "inaccessible resource");
            } catch (RepositoryException e) {
                result.add("to", String.valueOf(this.getLinkedResource()));
            }
            result.add("label", this.getLabel())
                .add("resourceLabel", this.getResourceLabel())
                .add("weak", this.isWeak());
            return result.build();
        }
    }
}
