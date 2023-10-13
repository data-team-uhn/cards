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
package io.uhndata.cards.migrators.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.Version;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.migrators.spi.DataMigrator;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@Component
public class DataMigratorManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DataMigratorManager.class);

    private Version previousVersion;

    private Version version;

    private boolean activated;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, bind = "migratorAdded", unbind = "migratorRemoved")
    private volatile List<DataMigrator> migrators = new ArrayList<>();

    @Activate
    protected void activate(ComponentContext context)
    {
        LOGGER.info("Starting Data Migrator");
        this.version = context.getBundleContext().getBundle().getVersion();

        // Get the session to run any migrators
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "dataMigratorManager");
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(parameters);) {
            final Session session = resolver.adaptTo(Session.class);
            this.rrp.push(resolver);
            mustPopResolver = true;

            // Get the previously run CARDS version
            this.previousVersion = DataMigratorUtils.getPreviousVersion(session);

            this.activated = true;

            // Sort the migrators
            List<DataMigrator> sortedMigrators = new ArrayList<>(this.migrators);
            Collections.sort(sortedMigrators);

            // Run any required migrators
            for (DataMigrator migrator : sortedMigrators) {
                runMigrator(migrator, session);
            }

            resolver.commit();

            LOGGER.info("Completed Data Migrator");
        } catch (LoginException | RepositoryException | PersistenceException e) {
            LOGGER.error("Could not migrate data", e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    protected void migratorAdded(final DataMigrator migrator)
    {
        if (!this.activated) {
            // Not yet activated: all current migrators will be run when that happens so no need to run this one now
            return;
        }

        LOGGER.info("Running newly added migrator {}", migrator.getName());
        // Get the session to run any migrators
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "dataMigratorManager");
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(parameters);) {
            final Session session = resolver.adaptTo(Session.class);
            this.rrp.push(resolver);
            mustPopResolver = true;

            runMigrator(migrator, session);

            resolver.commit();
        } catch (LoginException | PersistenceException e) {
            LOGGER.error("Could not run newly added migrator", e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    protected void runMigrator(final DataMigrator migrator, final Session session)
    {
        if (migrator.shouldRun(this.previousVersion, this.version, session)) {
            LOGGER.info("Running migrator {}", migrator.getName());
            migrator.run(this.previousVersion, this.version, session);
        }
    }

    protected void migratorRemoved(final DataMigrator migrator)
    {
        // Do nothing: We are running migrators on creation
    }
}
