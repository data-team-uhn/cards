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

package main.java.ca.sickkids.ccm.lfs.commons.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.BiConsumer;

import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;

/**
 * Adapter to adapt resources to Json.
 *
 * @version $Id$
 */

@Component(service = { AdapterFactory.class },
    /*
     * property = { AdapterFactory.ADAPTABLE_CLASSES + "=org.apache.sling.api.resource.Resource",
     * AdapterFactory.ADAPTER_CLASSES + "=javax.json.JsonObject" }
     */
    property = {
    "adaptables=org.apache.sling.api.resource.Resource",
    "adapters=javax.json.JsonObject"
    })

public class ResourceToJsonAdapter
    implements AdapterFactory
{
    private boolean reachProperty;

    @Override
    @SuppressWarnings("unchecked")
    public <A> A getAdapter(Object adaptable, Class<A> type)
    {
        Resource resource = (Resource) adaptable;
        if (resource != null) {
            ValueMap valuemap = resource.getValueMap();
            JsonObjectBuilder objectbuilder = Json.createObjectBuilder();

            BiConsumer<String, Object> biconsumer = (key, value) -> {
                addObjectField(objectbuilder, key, value);
            };

            valuemap.forEach(biconsumer);

            objectbuilder.add("reachedProperty", this.reachProperty);
            JsonObject jsonobject = objectbuilder.build();

            return (A) jsonobject;
        }
        return null;
    }

    @SuppressWarnings("checkstyle:cyclomaticcomplexity")
    private void addObjectField(JsonObjectBuilder objectbuilder, String name, Object obj)
    {
        if (obj == null) {
            objectbuilder.addNull(name);
        } else if (obj instanceof Property) {
            addObjectFieldProperty(objectbuilder, name, (Property) obj);
        } else if (obj instanceof Integer) {
            objectbuilder.add(name, (int) obj);
        } else if (obj instanceof Long) {
            objectbuilder.add(name, (long) obj);
        } else if (obj instanceof Double) {
            objectbuilder.add(name, (double) obj);
        } else if (obj instanceof Boolean) {
            objectbuilder.add(name, (boolean) obj);
        } else if (obj instanceof String) {
            objectbuilder.add(name, (String) obj);
        } else if (obj instanceof BigDecimal) {
            objectbuilder.add(name, (BigDecimal) obj);
        } else if (obj instanceof BigInteger) {
            objectbuilder.add(name, (BigInteger) obj);
        } else if (obj instanceof Object[]) {
            Object[] objarray = (Object[]) obj;
            JsonArrayBuilder arraybuilder = Json.createArrayBuilder();
            for (Object o : objarray) {
                addArrayField(arraybuilder, o);
            }
            objectbuilder.add(name, arraybuilder);
        } else {
            objectbuilder.add(name, obj.toString());
        }
    }

    private void addObjectFieldProperty(JsonObjectBuilder objectbuilder, String name, Property obj)
    {
        this.reachProperty = true;
        try {
            Value value = obj.getValue();
            int type = obj.getType();
            if (type == PropertyType.BOOLEAN) {
                objectbuilder.add(name, value.getBoolean());
            } else if (type == PropertyType.LONG) {
                objectbuilder.add(name, value.getLong());
            } else if (type == PropertyType.DOUBLE) {
                objectbuilder.add(name, value.getDouble());
            } else if (type == PropertyType.DECIMAL) {
                objectbuilder.add(name, value.getDecimal());
            } else {
                objectbuilder.add(name, value.getString());
            }
        } catch (ValueFormatException v) {
            try {
                Value[] values = obj.getValues();
                JsonArrayBuilder arraybuilder = Json.createArrayBuilder();
                for (Value i : values) {
                    addArrayField(arraybuilder, i);
                }
                objectbuilder.add(name, arraybuilder);
            } catch (RepositoryException e) {
                // TODO
                e.printStackTrace();
            }
        } catch (RepositoryException e) {
            // TODO
            e.printStackTrace();
        }
    }

    @SuppressWarnings("checkstyle:cyclomaticcomplexity")
    private void addArrayField(JsonArrayBuilder arraybuilder, Object obj)
    {
        if (obj == null) {
            arraybuilder.addNull();
        } else if (obj instanceof Property) {
            addArrayFieldProperty(arraybuilder, (Property) obj);
        } else if (obj instanceof Integer) {
            arraybuilder.add((int) obj);
        } else if (obj instanceof Long) {
            arraybuilder.add((long) obj);
        } else if (obj instanceof Double) {
            arraybuilder.add((double) obj);
        } else if (obj instanceof Boolean) {
            arraybuilder.add((boolean) obj);
        } else if (obj instanceof String) {
            arraybuilder.add((String) obj);
        } else if (obj instanceof BigDecimal) {
            arraybuilder.add((BigDecimal) obj);
        } else if (obj instanceof BigInteger) {
            arraybuilder.add((BigInteger) obj);
        } else if (obj instanceof Object[]) {
            Object[] objarray = (Object[]) obj;
            JsonArrayBuilder nestedarraybuilder = Json.createArrayBuilder();
            for (Object o : objarray) {
                addArrayField(nestedarraybuilder, o);
            }
            arraybuilder.add(arraybuilder);
        } else {
            arraybuilder.add(obj.toString());
        }
    }

    private void addArrayFieldProperty(JsonArrayBuilder arraybuilder, Property obj)
    {
        this.reachProperty = true;
        try {
            Value value = obj.getValue();
            int type = obj.getType();
            if (type == PropertyType.BOOLEAN) {
                arraybuilder.add(value.getBoolean());
            } else if (type == PropertyType.LONG) {
                arraybuilder.add(value.getLong());
            } else if (type == PropertyType.DOUBLE) {
                arraybuilder.add(value.getDouble());
            } else if (type == PropertyType.DECIMAL) {
                arraybuilder.add(value.getDecimal());
            } else {
                arraybuilder.add(value.getString());
            }
        } catch (ValueFormatException v) {
            try {
                Value[] values = obj.getValues();
                JsonArrayBuilder nestedarraybuilder = Json.createArrayBuilder();
                for (Value i : values) {
                    addArrayField(nestedarraybuilder, i);
                }
                arraybuilder.add(nestedarraybuilder);
            } catch (RepositoryException e) {
                // TODO
                e.printStackTrace();
            }
        } catch (RepositoryException e) {
            // TODO
            e.printStackTrace();
        }
    }
}
