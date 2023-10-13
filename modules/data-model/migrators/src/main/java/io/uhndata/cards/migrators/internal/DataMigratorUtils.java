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

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

public final class DataMigratorUtils
{
    static final String PREV_VERSION_NAME = "PrevVersion";

    static final String PREV_VERSION_PATH = "/libs/cards/conf/PrevVersion";

    static final String VERSION_PROPERTY = "Version";

    static final String VERSION_PATH = "/libs/cards/conf/Version";

    /**
     * Hide the utility class constructor.
     */
    private DataMigratorUtils()
    {
    }

    public static String getVersion(Session session) throws RepositoryException
    {
        return session.getNode(VERSION_PATH).getProperty(VERSION_PROPERTY).getString();
    }

    public static String getPreviousVersion(Session session) throws RepositoryException
    {
        String previousVersion = "";
        try {
            previousVersion = session.getNode(DataMigratorUtils.PREV_VERSION_PATH)
                .getProperty(DataMigratorUtils.VERSION_PROPERTY).getString();
        } catch (PathNotFoundException e) {
            // No previous version
            previousVersion = null;
        }

        return previousVersion;
    }
}
