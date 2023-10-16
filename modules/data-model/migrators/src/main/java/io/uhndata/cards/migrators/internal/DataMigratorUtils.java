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
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.osgi.framework.Version;

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

    public static Version getPreviousVersion(Session session)
    {
        String previousVersion = null;
        try {
            if (session.itemExists(PREV_VERSION_PATH + "/" + VERSION_PROPERTY)) {
                previousVersion = session.getNode(DataMigratorUtils.PREV_VERSION_PATH)
                    .getProperty(DataMigratorUtils.VERSION_PROPERTY).getString();
            } else if (session.nodeExists("/Forms")) {
                final Node forms = session.getNode("/Forms");
                final NodeIterator children = forms.getNodes();
                while (children.hasNext()) {
                    final Node child = children.nextNode();
                    if ("rep:policy".equals(child.getName())) {
                        continue;
                    }
                    // Forms exist, this must be an upgrade from a pre-0.9.18 version
                    previousVersion = "0.1.0";
                    break;
                }
            }
        } catch (RepositoryException e) {
            // Repository is new
        }

        return previousVersion == null ? null : new Version(previousVersion);
    }
}
