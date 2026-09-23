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

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.migrators.spi.DataMigrator;

/**
 * Migrator that gives the public configuration nodes of an upgraded instance the {@code cards:Configuration} type,
 * which is what marks their properties as page metadata, and removes the properties that were renamed along with it.
 * The initial content and the repoinit create these nodes with the type, but leave the type of an existing node alone,
 * so an upgraded instance would otherwise publish no metadata at all.
 *
 * @version $Id$
 * @since 0.9.42
 */
@Component(immediate = true)
public class ConfigurationNodeTypeMigrator implements DataMigrator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationNodeTypeMigrator.class);

    private static final String CONFIGURATION_TYPE = "cards:Configuration";

    /** Each public configuration node, mapped to the property it held under its old name, or to an empty string. */
    private static final Map<String, String> NODES = Map.of(
        "/libs/cards/conf/AppName", "AppName",
        "/libs/cards/conf/PlatformName", "PlatformName",
        "/libs/cards/conf/Version", "Version",
        "/libs/cards/conf/AppVersion", "",
        "/libs/cards/conf/LoginPage", "",
        "/libs/cards/conf/ThemeColor", "",
        "/libs/cards/conf/Media", "");

    @Override
    public String getName()
    {
        return "ConfigurationNodeTypeMigrator";
    }

    @Override
    public boolean shouldRun(final Version previousVersion, final Version currentVersion, final Session session)
    {
        return NODES.entrySet().stream().anyMatch(node -> needsMigration(session, node.getKey(), node.getValue()));
    }

    @Override
    public void run(final Version previousVersion, final Version currentVersion, final Session session)
    {
        try {
            for (Map.Entry<String, String> entry : NODES.entrySet()) {
                if (!session.nodeExists(entry.getKey())) {
                    continue;
                }
                final Node node = session.getNode(entry.getKey());
                if (!node.isNodeType(CONFIGURATION_TYPE)) {
                    node.setPrimaryType(CONFIGURATION_TYPE);
                }
                if (!entry.getValue().isEmpty() && node.hasProperty(entry.getValue())) {
                    node.getProperty(entry.getValue()).remove();
                }
            }
            session.save();
        } catch (RepositoryException e) {
            LOGGER.error("Failed to give the configuration nodes their node type: {}", e.getMessage(), e);
        }
    }

    private static boolean needsMigration(final Session session, final String path, final String oldProperty)
    {
        try {
            if (!session.nodeExists(path)) {
                return false;
            }
            final Node node = session.getNode(path);
            return !node.isNodeType(CONFIGURATION_TYPE) || (!oldProperty.isEmpty() && node.hasProperty(oldProperty));
        } catch (RepositoryException e) {
            return false;
        }
    }
}
