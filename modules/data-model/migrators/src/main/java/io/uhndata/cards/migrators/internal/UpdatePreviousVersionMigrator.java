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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.migrators.spi.DataMigrator;

@Component
public class UpdatePreviousVersionMigrator implements DataMigrator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdatePreviousVersionMigrator.class);

    @Override
    public String getName()
    {
        return "UpdatePreviousVersionMigrator";
    }

    @Override
    public int getPriority()
    {
        // Always run last
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean shouldRun(Version previousVersion, Version currentVersion, Session session)
    {
        return !currentVersion.equals(previousVersion);
    }

    @Override
    public void run(Version previousVersion, Version currentVersion, Session session)
    {
        try {
            // Update the previous CARDS version to the current version
            Node node = session.getNode("/libs/cards/conf");
            if (node.hasNode(DataMigratorUtils.PREV_VERSION_NAME)) {
                node = node.getNode(DataMigratorUtils.PREV_VERSION_NAME);
            } else {
                node = node.addNode(DataMigratorUtils.PREV_VERSION_NAME);
            }

            node.setProperty(DataMigratorUtils.VERSION_PROPERTY, currentVersion.toString());
        } catch (RepositoryException e) {
            // Should not happen
            LOGGER.error("Failed to update previous version", e);
        }
    }
}
