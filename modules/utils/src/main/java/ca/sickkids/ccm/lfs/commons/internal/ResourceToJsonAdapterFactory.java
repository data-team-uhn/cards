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

    private void addObject(JsonObjectBuilder objectBuilder, String name, Object value)
    {
        if (value == null) {
            objectBuilder.addNull(name);
        } else if (value instanceof Object[]) {
            // For multi-value properties
            addArray(objectBuilder, name, value);
        } else if (value instanceof Calendar) {
            // Corresponding to JCR DATE property
            addCalendar(objectBuilder, name, value);
        } else if (value instanceof InputStream) {
            // Corresponding to JCR BINARY property
            addInputStream(objectBuilder, name, value);
        } else if (value instanceof BigDecimal) {
            // Corresponding to JCR DECIMAL property
            objectBuilder.add(name, (BigDecimal) value);
        } else if (value instanceof BigInteger) {
            objectBuilder.add(name, (BigInteger) value);
        } else {
            addPrimitive(objectBuilder, name, value);
        }
    }

    // for object
    private void addArray(JsonObjectBuilder objectBuilder, String name, Object values)
    {
        Object[] objarray = (Object[]) values;
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (Object o : objarray) {
            addArrayElement(arrayBuilder, o);
        }
        objectBuilder.add(name, arrayBuilder);
    }

    private void addArrayElement(JsonArrayBuilder arrayBuilder, Object value)
    {
        if (value == null) {
            arrayBuilder.addNull();
        } else if (value instanceof Calendar) {
            // Corresponding to JCR DATE property
            addCalendar(arrayBuilder, value);
        } else if (value instanceof InputStream) {
            // Corresponding to JCR BINARY property
            addInputStream(arrayBuilder, value);
        } else if (value instanceof BigDecimal) {
            // Corresponding to JCR DECIMAL property
            arrayBuilder.add((BigDecimal) value);
        } else if (value instanceof BigInteger) {
            arrayBuilder.add((BigInteger) value);
        } else {
            addPrimitive(arrayBuilder, value);
        }
    }

    // for object
    private void addCalendar(JsonObjectBuilder objectBuilder, String name, Object value)
    {
        Calendar calendar = (Calendar) value;
        // format date as year-month-day 'T' hours:minutes:seconds.milliseconds-timezone
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(calendar.getTimeZone());
        objectBuilder.add(name, sdf.format(calendar.getTime()));
    }

    // for array
    private void addCalendar(JsonArrayBuilder arrayBuilder, Object value)
    {
        Calendar calendar = (Calendar) value;
        // format date as year-month-day 'T' hours:minutes:seconds.milliseconds-timezone
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(calendar.getTimeZone());
        arrayBuilder.add(sdf.format(calendar.getTime()));
    }

    // for object
    private void addInputStream(JsonObjectBuilder objectBuilder, String name, Object value)
    {
        try {
            objectBuilder.add(name, IOUtils.toString((InputStream) value, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // for array
    private void addInputStream(JsonArrayBuilder arrayBuilder, Object value)
    {
        try {
            arrayBuilder.add(IOUtils.toString((InputStream) value, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // for object
    private void addPrimitive(JsonObjectBuilder objectBuilder, String name, Object value)
    {
        if (value instanceof Boolean) {
            objectBuilder.add(name, (boolean) value);
        } else if (value instanceof Integer) {
            objectBuilder.add(name, (int) value);
        } else if (value instanceof Long) {
            objectBuilder.add(name, (long) value);
        } else if (value instanceof Double) {
            objectBuilder.add(name, (double) value);
        } else {
            objectBuilder.add(name, value.toString());
        }
    }

    // for array
    private void addPrimitive(JsonArrayBuilder arrayBuilder, Object value)
    {
        if (value instanceof Boolean) {
            arrayBuilder.add((boolean) value);
        } else if (value instanceof Integer) {
            arrayBuilder.add((int) value);
        } else if (value instanceof Long) {
            arrayBuilder.add((long) value);
        } else if (value instanceof Double) {
            arrayBuilder.add((double) value);
        } else {
            arrayBuilder.add(value.toString());
        }
    }
}
