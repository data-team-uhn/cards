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

package ca.sickkids.ccm.lfs.commons.internal;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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
 * AdapterFactory that converts Apache Sling resources to JsonObjects.
 *
 * @version $Id$
 */

@Component(service = { AdapterFactory.class },

    property = {
    "adaptables=org.apache.sling.api.resource.Resource",
    "adapters=javax.json.JsonObject"
    })

public class ResourceToJsonAdapterFactory
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
            // For multi-value properties
            addArray(objectbuilder, name, obj);
        } else if (obj instanceof Calendar) {
            // Corresponding to JCR DATE property
            addCalendar(objectbuilder, name, obj);
        } else if (obj instanceof InputStream) {
            // Corresponding to JCR BINARY property
            addInputStream(objectbuilder, name, obj);
        } else if (obj instanceof BigDecimal) {
            // Corresponding to JCR DECIMAL property
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
            // For multip-value poperties
            addArray(arraybuilder, obj);
        } else if (obj instanceof Calendar) {
            // Corresponding to JCR DATE property
            addCalendar(arraybuilder, obj);
        } else if (obj instanceof InputStream) {
            // Corresponding to JCR BINARY property
            addInputStream(arraybuilder, obj);
        } else if (obj instanceof BigDecimal) {
            // Corresponding to JCR DECIMAL property
            arraybuilder.add((BigDecimal) obj);
        } else if (obj instanceof BigInteger) {
            arraybuilder.add((BigInteger) obj);
        } else {
            addPrimitive(arraybuilder, obj);
        }
    }

    // for object
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

    // for array
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

    // for object
    private void addArray(JsonObjectBuilder objectbuilder, String name, Object obj)
    {
        Object[] objarray = (Object[]) obj;
        JsonArrayBuilder arraybuilder = Json.createArrayBuilder();
        for (Object o : objarray) {
            addArrayElement(arraybuilder, o);
        }
        objectbuilder.add(name, arraybuilder);
    }

    // for array
    private void addArray(JsonArrayBuilder arraybuilder, Object obj)
    {
        Object[] objarray = (Object[]) obj;
        JsonArrayBuilder nestedarraybuilder = Json.createArrayBuilder();
        for (Object o : objarray) {
            addArrayElement(nestedarraybuilder, o);
        }
        arraybuilder.add(arraybuilder);
    }

    // for object
    private void addInputStream(JsonObjectBuilder objectbuilder, String name, Object obj)
    {
        try {
            objectbuilder.add(name, IOUtils.toString((InputStream) obj, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // for array
    private void addInputStream(JsonArrayBuilder arraybuilder, Object obj)
    {
        try {
            arraybuilder.add(IOUtils.toString((InputStream) obj, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // for object
    private void addCalendar(JsonObjectBuilder objectbuilder, String name, Object obj)
    {
        Calendar calendar = (Calendar) obj;
        // format date as year-month-day 'T' hours:minutes:seconds.milliseconds-timezone
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(calendar.getTimeZone());
        objectbuilder.add(name, sdf.format(calendar.getTime()));
    }

    // for array
    private void addCalendar(JsonArrayBuilder arraybuilder, Object obj)
    {
        Calendar calendar = (Calendar) obj;
        // format date as year-month-day 'T' hours:minutes:seconds.milliseconds-timezone
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(calendar.getTimeZone());
        arraybuilder.add(sdf.format(calendar.getTime()));
    }
}
