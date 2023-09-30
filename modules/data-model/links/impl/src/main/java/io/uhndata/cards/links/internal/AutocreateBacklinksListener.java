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

import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.links.api.Link;
import io.uhndata.cards.links.api.Links;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Change listener that watches for new links and creates the backlink to the originating resource if that is required.
 *
 * @version $Id$
 */
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/",
    ResourceChangeListener.CHANGES + "=ADDED"
})
public class AutocreateBacklinksListener implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AutocreateBacklinksListener.class);

    /** Provides access to resources. */
    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private Links linkUtils;

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        changes.forEach(this::handleEvent);
    }

    private void handleEvent(final ResourceChange event)
    {
        final String path = event.getPath();
        // Quick filtering to avoid creating a new session
        if (!path.contains("/cards:links/")) {
            return;
        }
        // Acquire a service session with the right privileges for accessing the repository
        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.resolverFactory.getServiceResourceResolver(null)) {
            this.rrp.push(localResolver);
            mustPopResolver = true;
            // Get the information needed from the triggering resource
            final Session session = localResolver.adaptTo(Session.class);
            if (!session.nodeExists(path)) {
                return;
            }
            final Node linkNode = session.getNode(path);
            if (!linkNode.isNodeType(Link.LINK_NODETYPE)) {
                return;
            }
            final Link link = this.linkUtils.getLink(linkNode);
            if (link.getDefinition().hasBacklink() && link.getBacklink() == null) {
                this.linkUtils.addBacklink(link);
            }
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }
}
