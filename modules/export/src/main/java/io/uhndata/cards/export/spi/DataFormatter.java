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
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import io.uhndata.cards.export.ExportConfigDefinition;

/**
 * The second step of the data export is serializing the resources to be exported into a byte stream. Implementations of
 * this service will do that, usually by {@link Resource#adaptTo(Class) adapting} each {@link ResourceIdentifier
 * resource identifier} to a format.
 *
 * @version $Id$
 * @since 0.9.26
 */
public interface DataFormatter extends DataPipelineStep
{
    /**
     * Serialize a resource into the desired representation.
     *
     * @param what the resource to serialize
     * @param startDate the requested start date for the export time frame
     * @param endDate the requested end date for the export time frame
     * @param config the export process configuration, which may hold further customization for the serialization
     *            process in the {@link ExportConfigDefinition#formatterParameters()} settings
     * @param resolver a valid resource resolver with access to the data
     * @return a resource representation with a valid input stream holding the data
     * @throws RepositoryException if accessing the data fails
     */
    ResourceRepresentation format(ResourceIdentifier what, ZonedDateTime startDate,
        ZonedDateTime endDate, ExportConfigDefinition config, ResourceResolver resolver)
        throws RepositoryException;

    /**
     * Helper method for extracting a listing of sub-resources contained within the serialization, useful when the
     * export produces an aggregate of related data, e.g. modified forms for a subject.
     *
     * @param what the identifier for the resource being exported
     * @param config the export process configuration
     * @param resolver a valid resource resolver with access to the data
     * @return a list of JCR paths to secondary resources contained within the representation
     * @see ResourceRepresentation#getDataContents()
     */
    default List<String> getContentsSummary(ResourceIdentifier what, ExportConfigDefinition config,
        ResourceResolver resolver)
    {
        return resolver.resolve(what.getExportPath() + ".identify.-properties.-dereference.json")
            .adaptTo(JsonObject.class)
            .values()
            .stream()
            .filter(v -> v.getValueType() == ValueType.ARRAY)
            .map(JsonValue::asJsonArray)
            .flatMap(JsonArray::stream)
            .filter(v -> v.getValueType() == ValueType.OBJECT)
            .map(JsonValue::asJsonObject)
            .filter(v -> v.containsKey("@path"))
            .map(v -> v.getString("@path"))
            .collect(Collectors.toList());
    }
}
