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

package io.uhndata.cards.internal.importer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query the Clarity server every so often to obtain all of the visits & patients that have appeared
 * throughout the day. This will patch over patient & visit information forms.
 *
 * @version $Id$
 */
public class ClarityImportTask implements Runnable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClarityImportTask.class);

    private static final String SQL_URL = "localhost:1433";

    /* TODO: Replace all of these with a config task. */
    private final Map<String, Node> columnToQuestion;

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    ClarityImportTask(final ResourceResolverFactory resolverFactory, Node mapping)
    {
        this.resolverFactory = resolverFactory;

        // Convert our input mapping node to a mapping from column->question
        this.columnToQuestion = new HashMap<String, Node>();

        // Implement the mapping
        try {
            PropertyIterator properties = mapping.getProperties();
            while (properties.hasNext()) {
                Property property = properties.nextProperty();
                int type = property.getType();
                if (type != PropertyType.REFERENCE && type != PropertyType.WEAKREFERENCE
                    && type != PropertyType.PATH) {
                    continue;
                }

                this.columnToQuestion.put(property.getName(), property.getNode());
            }
        } catch (RepositoryException e) {
            LOGGER.error("Error reading mapping: {}", e.getMessage(), e);
        }
    }

    @Override
    public void run()
    {
        String connectionUrl =
            "jdbc:sqlserver://localhost:1433;"
            + "database=path" + System.getenv("SQL_USERNAME") + ";"
            + "user=" + System.getenv("SQL_USERNAME") + ";"
            + "password=" + System.getenv("SQL_PASSWORD") + ";"
            + "encrypt=true;"
            + "trustServerCertificate=false;"
            + "loginTimeout=30;";

        // Connect via SQL to the server
        // Perform the query
        try (Connection connection = DriverManager.getConnection(connectionUrl);
            Statement statement = connection.createStatement();) {
            statement.execute("SELECT * from PatientSurvey WHERE CAST(LoadTime AS DATA) = CAST(GETDATE() AS DATE);");
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to SQL: {}", e.getMessage(), e);
        }
    }
}
