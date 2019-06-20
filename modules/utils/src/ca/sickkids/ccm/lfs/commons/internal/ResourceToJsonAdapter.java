//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.

package ca.sickkids.ccm.lfs.commons.internal;

import java.util.function.BiConsumer;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;

@Component(service = { AdapterFactory.class }, property = {
AdapterFactory.ADAPTABLE_CLASSES + "=org.apache.sling.api.resource.Resource",
AdapterFactory.ADAPTER_CLASSES + "=javax.json.JsonObject"
})

public class ResourceToJsonAdapter implements AdapterFactory
{
    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(Object adaptable, Class<AdapterType> type)
    {
        Resource resource = (Resource) adaptable;
        if (resource != null) {
            ValueMap valuemap = resource.getValueMap();
            JsonObjectBuilder objectbuilder = Json.createObjectBuilder();

            BiConsumer<String, Object> biconsumer = (key, value) -> {
                if (value == null) {
                    objectbuilder.addNull(key);
                } else if (value instanceof String) {
                    objectbuilder.add(key, (String) value);
                } else if (value instanceof Boolean) {
                    objectbuilder.add(key, (boolean) value);
                } else if (value instanceof Double) {
                    objectbuilder.add(key, (double) value);
                } else if (value instanceof Integer) {
                    objectbuilder.add(key, (int) value);
                } else if (value instanceof Long) {
                    objectbuilder.add(key, (long) value);
                } else {
                    objectbuilder.add(key, value.toString());
                }
            };

            valuemap.forEach(biconsumer);

            JsonObject jsonobject = objectbuilder.build();

            return (AdapterType) jsonobject;
        }
        return null;
    }

    public static void main(String[] args)
    {
        System.out.println("HSFJL");
    }
}
