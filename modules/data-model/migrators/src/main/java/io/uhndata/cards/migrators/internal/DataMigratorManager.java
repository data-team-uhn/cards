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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.migrators.spi.DataMigrator;

@Component
public class DataMigratorManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DataMigratorManager.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.UPDATE)
    private volatile List<DataMigrator> migrators = new ArrayList<>();

    @Activate
    protected void activate()
    {
        LOGGER.info("Starting Data Migrator");

        // Get the session to run any migrators
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "dataMigratorManager");
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(parameters);) {
            final Session session = resolver.adaptTo(Session.class);

            // Get the previously run CARDS version
            String previousVersion = DataMigratorUtils.getPreviousVersion(session);

            // Sort the migrators
            List<DataMigrator> sortedMigrators = new ArrayList<>(this.migrators);
            Collections.sort(sortedMigrators);

            // Run any required migrators
            for (DataMigrator migrator: sortedMigrators) {
                if (migrator.shouldRun(previousVersion, session)) {
                    migrator.run(session);
                }
            }

            resolver.commit();

            LOGGER.info("Completed Data Migrator");
        } catch (LoginException | RepositoryException | PersistenceException e) {
            LOGGER.error("Could not migrate data", e);
        }
    }
}
