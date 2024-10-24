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

package io.uhndata.cards.export.internal.formatters;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

import javax.jcr.RepositoryException;
import javax.json.JsonObject;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataFormatter;

@Component(immediate = true, service = DataFormatter.class)
public class JSONDataFormatter implements DataFormatter
{
    @Override
    public String getName()
    {
        return "json";
    }

    @Override
    public ResourceRepresentation format(final ResourceIdentifier what,
        final ZonedDateTime startDate, final ZonedDateTime endDate,
        final ExportConfigDefinition config, final ResourceResolver resolver)
        throws RepositoryException
    {
        Resource r = resolver.resolve(what.getExportPath() + ".json");
        JsonObject serialization = r.adaptTo(JsonObject.class);

        return new ResourceRepresentation(what,
            new ByteArrayInputStream(serialization.toString().getBytes(StandardCharsets.UTF_8)),
            "application/json",
            getContentsSummary(what, config, resolver));
    }
}
