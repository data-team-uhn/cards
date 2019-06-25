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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.function.BiConsumer;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.commons.io.IOUtils;
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
    @Override
    @SuppressWarnings("unchecked")
    public <A> A getAdapter(Object adaptable, Class<A> type)
    {
        Resource resource = (Resource) adaptable;
        if (resource != null) {
            ValueMap valuemap = resource.getValueMap();
            JsonObjectBuilder objectbuilder = Json.createObjectBuilder();

            BiConsumer<String, Object> biconsumer = (key, value) -> {
                addObject(objectbuilder, key, value);
            };

            valuemap.forEach(biconsumer);

            JsonObject jsonobject = objectbuilder.build();

            return (A) jsonobject;
        }
        return null;
    }

    private void addObject(JsonObjectBuilder objectbuilder, String name, Object obj)
    {
        if (obj == null) {
            objectbuilder.addNull(name);
        } else if (obj instanceof Object[]) {
            addArray(objectbuilder, name, obj);
        } else if (obj instanceof GregorianCalendar) {
            addGregorianCalendar(objectbuilder, name, obj);
        } else if (obj instanceof InputStream) {
            addInputStream(objectbuilder, name, obj);
        } else if (obj instanceof BigDecimal) {
            objectbuilder.add(name, (BigDecimal) obj);
        } else if (obj instanceof BigInteger) {
            objectbuilder.add(name, (BigInteger) obj);
        } else {
            addPrimitive(objectbuilder, name, obj);
        }
    }

    private void addArrayElement(JsonArrayBuilder arraybuilder, Object obj)
    {
        if (obj == null) {
            arraybuilder.addNull();
        } else if (obj instanceof Object[]) {
            addArray(arraybuilder, obj);
        } else if (obj instanceof GregorianCalendar) {
            addGregorianCalendar(arraybuilder, obj);
        } else if (obj instanceof InputStream) {
            addInputStream(arraybuilder, obj);
        } else if (obj instanceof BigDecimal) {
            arraybuilder.add((BigDecimal) obj);
        } else if (obj instanceof BigInteger) {
            arraybuilder.add((BigInteger) obj);
        } else {
            addPrimitive(arraybuilder, obj);
        }
    }

    /*
     * private void addPrimitive(JsonObjectBuilder objectbuilder, String name, Object obj) { else {
     * objectbuilder.add(name, obj.toString()); } } private void addPrimitive(JsonArrayBuilder arraybuilder, Object obj)
     * { else { arraybuilder.add(obj.toString()); } }
     */

    private void addPrimitive(JsonObjectBuilder objectbuilder, String name, Object obj)
    {
        if (obj instanceof Boolean) {
            objectbuilder.add(name, (boolean) obj);
        } else if (obj instanceof Integer) {
            objectbuilder.add(name, (int) obj);
        } else if (obj instanceof Long) {
            objectbuilder.add(name, (long) obj);
        } else if (obj instanceof Double) {
            objectbuilder.add(name, (double) obj);
        } else {
            objectbuilder.add(name, obj.toString());
        }
    }

    private void addPrimitive(JsonArrayBuilder arraybuilder, Object obj)
    {
        if (obj instanceof Boolean) {
            arraybuilder.add((boolean) obj);
        } else if (obj instanceof Integer) {
            arraybuilder.add((int) obj);
        } else if (obj instanceof Long) {
            arraybuilder.add((long) obj);
        } else if (obj instanceof Double) {
            arraybuilder.add((double) obj);
        } else {
            arraybuilder.add(obj.toString());
        }
    }

    private void addArray(JsonObjectBuilder objectbuilder, String name, Object obj)
    {
        Object[] objarray = (Object[]) obj;
        JsonArrayBuilder arraybuilder = Json.createArrayBuilder();
        for (Object o : objarray) {
            addArrayElement(arraybuilder, o);
        }
        objectbuilder.add(name, arraybuilder);
    }

    private void addArray(JsonArrayBuilder arraybuilder, Object obj)
    {
        Object[] objarray = (Object[]) obj;
        JsonArrayBuilder nestedarraybuilder = Json.createArrayBuilder();
        for (Object o : objarray) {
            addArrayElement(nestedarraybuilder, o);
        }
        arraybuilder.add(arraybuilder);
    }

    private void addInputStream(JsonObjectBuilder objectbuilder, String name, Object obj)
    {
        try {
            objectbuilder.add(name, IOUtils.toString((InputStream) obj, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void addInputStream(JsonArrayBuilder arraybuilder, Object obj)
    {
        try {
            arraybuilder.add(IOUtils.toString((InputStream) obj, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void addGregorianCalendar(JsonObjectBuilder objectbuilder, String name, Object obj)
    {
        GregorianCalendar calendar = (GregorianCalendar) obj;
        SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(calendar.getTimeZone());
        objectbuilder.add(name, sdf.format(calendar.getTime()));
    }

    private void addGregorianCalendar(JsonArrayBuilder arraybuilder, Object obj)
    {
        GregorianCalendar calendar = (GregorianCalendar) obj;
        SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(calendar.getTimeZone());
        arraybuilder.add(sdf.format(calendar.getTime()));
    }
}
