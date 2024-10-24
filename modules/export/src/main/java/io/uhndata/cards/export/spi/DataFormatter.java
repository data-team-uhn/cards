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
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.sling.api.resource.ResourceResolver;

import io.uhndata.cards.export.ExportConfigDefinition;

public interface DataFormatter extends DataPipelineStep
{
    ResourceRepresentation format(ResourceIdentifier what, ZonedDateTime startDate,
        ZonedDateTime endDate, ExportConfigDefinition config, ResourceResolver resolver)
        throws RepositoryException;

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
