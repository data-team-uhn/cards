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
package io.uhndata.cards.migrators.spi;

import javax.jcr.Session;

import org.osgi.framework.Version;

/**
 * Service interface for data migrators. A data migrator runs when the framework is started, and performs any changes
 * needed to port existing data from the old version to the new version, or to set up any missing details on a new
 * instance.
 *
 * @version $Id$
 * @since 0.9.18
 */
public interface DataMigrator extends Comparable<DataMigrator>
{
    /**
     * The name of this migrator.
     *
     * @return the user readable name of this migrator
     */
    String getName();

    /**
     * The priority of this migrator. Migrators with higher numbers are invoked after those with lower numbers.
     *
     * @return the priority of this migrator, can be any number
     */
    int getPriority();

    /**
     * If this migrator should run based on what version of CARDS was previously run.
     *
     * @param previousVersion The version of CARDs that was run previously, {@code null} if this is the first run
     * @param currentVersion The version of CARDs that is currently running
     * @param session The session that should be used to pull any other data if required
     * @return {@code true} if the migrator should be run
     */
    boolean shouldRun(Version previousVersion, Version currentVersion, Session session);

    /**
     * Change anything that needs to be changed to upgrade from the previous version of CARDS.
     *
     * @param previousVersion The version of CARDs that was run previously, may be {@code null}
     * @param currentVersion The version of CARDs that is currently running
     * @param session The session that should be used to enact any required changes
     */
    void run(Version previousVersion, Version currentVersion, Session session);

    @Override
    default int compareTo(DataMigrator other)
    {
        return this.getPriority() - other.getPriority();
    }
}
