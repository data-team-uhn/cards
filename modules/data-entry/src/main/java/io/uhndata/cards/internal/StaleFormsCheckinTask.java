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

package io.uhndata.cards.internal;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.version.VersionManager;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Periodically check in forms that haven't been modified in more than 30 minutes.
 *
 * @version $Id$
 * @since 0.9.17
 */
public class StaleFormsCheckinTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(StaleFormsCheckinTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    /** For sharing the resource resolver with other services. */
    private final ThreadResourceResolverProvider rrp;

    /**
     * @param resolverFactory a valid ResourceResolverFactory providing access to resources
     * @param rrp sharing the resource resolver with other services
     */
    StaleFormsCheckinTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
    }

    @Override
    public void run()
    {
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "staleFormsCheckin"))) {
            this.rrp.push(resolver);
            mustPopResolver = true;
            final VersionManager vm = resolver.adaptTo(Session.class).getWorkspace().getVersionManager();

            // Query:
            final Iterator<Resource> resources = resolver.findResources(String.format(
                // select the data forms
                "select distinct dataForm.* from [cards:Form] as dataForm"
                    + " where"
                    // form is checked out
                    + " dataForm.[jcr:isCheckedOut] = true"
                    // form is stale for the last 30 minutes
                    + " and dataForm.[jcr:lastCheckedOut] < '%s'",
                ZonedDateTime.now().minusMinutes(30)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx"))),
                Query.JCR_SQL2);
            resources.forEachRemaining(form -> {
                try {
                    vm.checkin(form.getPath());
                } catch (RepositoryException e) {
                    LOGGER.warn("Failed to check in a form that hasn't been modified in more than 30 minutes {}: {}",
                        form.getPath(), e.getMessage());
                }
            });
        } catch (LoginException e) {
            LOGGER.warn("Invalid setup, service rights not set up for checkin stale forms task");
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to checkin forms that haven't been modified in more than 30 minutes: {}",
                e.getMessage());
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }
}
