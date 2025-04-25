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
package io.uhndata.cards.locking.internal;

import java.util.Collections;
import java.util.Map;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.locking.api.LockManager;
import io.uhndata.cards.permissions.spi.PermissionsManager;

@Component(immediate = true, property = "service.ranking:Integer=300")
public class LockRepositoryInitializer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LockRepositoryInitializer.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private PermissionsManager permissionsManager;

    @Activate
    protected void activate(ComponentContext context)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "locking"))) {
            JackrabbitSession session = (JackrabbitSession) resolver.adaptTo(Session.class);
            Authorizable trustedUser = session.getUserManager().getAuthorizable("TrustedUsers");

            if (trustedUser != null && trustedUser.isGroup()) {
                String repWrite = "rep:write";
                ValueFactory valueFactory = session.getValueFactory();

                // Add permissions for trusted users if that user group exists
                // Deny write permissions to locked forms
                this.permissionsManager.addAccessControlEntry(
                    "/Forms",
                    false,
                    trustedUser.getPrincipal(),
                    new String[] { repWrite },
                    Collections.singletonMap("cards:locked", valueFactory.createValue("")),
                    session);
                // Deny write permissions to locked subjects
                this.permissionsManager.addAccessControlEntry(
                    "/Subjects",
                    false,
                    trustedUser.getPrincipal(),
                    new String[] { repWrite },
                    Collections.singletonMap("cards:locked", valueFactory.createValue("")),
                    session);
                // Deny write permissions to form lock reference properties
                this.permissionsManager.addAccessControlEntry(
                    "/Forms",
                    false,
                    trustedUser.getPrincipal(),
                    new String[] { repWrite },
                    Collections.singletonMap("rep:ntNames",
                        valueFactory.createValue(LockManager.LOCK_PROPERTY, PropertyType.NAME)),
                    session);
                // Deny write permissions to subject lock reference properties
                this.permissionsManager.addAccessControlEntry(
                    "/Subjects",
                    false,
                    trustedUser.getPrincipal(),
                    new String[] { repWrite },
                    Collections.singletonMap("rep:ntNames",
                        valueFactory.createValue(LockManager.LOCK_PROPERTY, PropertyType.NAME)),
                    session);
                session.save();
            }
        } catch (LoginException e) {
            LOGGER.error("Could not initialize locking permissions", e);
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error initializing locking permissions", e);
        }


    }
}
