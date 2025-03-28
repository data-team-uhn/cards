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
package io.uhndata.cards.export.spi;

import java.time.ZonedDateTime;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.ResourceResolver;

import io.uhndata.cards.export.ExportConfigDefinition;

/**
 * The first step of the data export is finding the resources to be exported. Implementations of this service will do
 * that, usually by querying the repository for resources modified during the target interval, and returning a list of
 * {@link ResourceIdentifier resource identifiers}.
 *
 * @version $Id$
 * @since 0.9.26
 */
public interface DataRetriever extends DataPipelineStep
{
    /**
     * Find resources to export, with a relevant change within the given time frame, according to the given export
     * configuration.
     *
     * @param config the export process configuration, which may hold further customization for the resources to find in
     *            the {@link ExportConfigDefinition#retrieverParameters()} settings
     * @param startDate the start date to consider for relevant changes, inclusive
     * @param endDate the end date to consider for relevant changes, exclusive
     * @param resolver a valid resource resolver with access to the data
     * @return a list of matching resources to export
     * @throws RepositoryException if accessing the data fails
     */
    List<ResourceIdentifier> getResourcesToExport(ExportConfigDefinition config, ZonedDateTime startDate,
        ZonedDateTime endDate, ResourceResolver resolver) throws RepositoryException;
}
