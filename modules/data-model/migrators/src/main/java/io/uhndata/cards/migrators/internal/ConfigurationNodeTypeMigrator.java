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
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.migrators.spi.DataMigrator;

/**
 * Migrator that gives the public configuration nodes of an upgraded instance the {@code cards:Configuration} type,
 * which is what marks their properties as page metadata, and moves the properties renamed along with it to their new
 * names. The initial content and the repoinit create these nodes with the type, but leave the type of an existing node
 * alone, so an upgraded instance would otherwise publish no metadata at all. The nodes are checked on every start, not
 * only on an upgrade, since a downstream project that overwrites one with its old form recreates it at each update.
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

    /**
     * The new name of each renamed property whose value must survive the rename, since it may be a downstream project's
     * or an administrator's choice. The old {@code Version} is not one: it is the version being upgraded from, and the
     * initial content supplies the current one.
     */
    private static final Map<String, String> NEW_NAMES = Map.of(
        "AppName", "title",
        "PlatformName", "platformName");

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
                final String oldName = entry.getValue();
                if (!oldName.isEmpty() && node.hasProperty(oldName)) {
                    final Property oldProperty = node.getProperty(oldName);
                    if (NEW_NAMES.containsKey(oldName)) {
                        // Replaces the default that the initial content may have just placed under the new name
                        node.setProperty(NEW_NAMES.get(oldName), oldProperty.getValue());
                    }
                    oldProperty.remove();
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
