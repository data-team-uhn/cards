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

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceToJsonAdapterFactory.class);

    @Override
    public <A> A getAdapter(Object adaptable, Class<A> type)
    {
        if (adaptable == null) {
            return null;
        }
        Resource resource = (Resource) adaptable;
        ValueMap valuemap = resource.getValueMap();
        JsonObjectBuilder objectbuilder = Json.createObjectBuilder();
        valuemap.forEach((key, value) -> addObject(objectbuilder, key, value));
        return type.cast(objectbuilder.build());
    }

    private void addObject(JsonObjectBuilder objectBuilder, String name, Object value)
    {
        if (value == null) {
            objectBuilder.addNull(name);
        } else if (value instanceof Object[]) {
            // For multi-value properties
            addArray(objectBuilder, name, (Object[]) value);
        } else if (value instanceof Calendar) {
            // Corresponding to JCR DATE property
            addCalendar(objectBuilder, name, (Calendar) value);
        } else if (value instanceof InputStream) {
            // Corresponding to JCR BINARY property
            addInputStream(objectBuilder, name, (InputStream) value);
        } else if (value instanceof BigDecimal) {
            // Corresponding to JCR DECIMAL property
            objectBuilder.add(name, (BigDecimal) value);
        } else if (value instanceof BigInteger) {
            // Also corresponding to JCR DECIMAL property
            objectBuilder.add(name, (BigInteger) value);
        } else {
            addPrimitive(objectBuilder, name, value);
        }
    }

    // for object
    private void addArray(JsonObjectBuilder objectBuilder, String name, Object[] values)
    {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (Object o : values) {
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
            addCalendar(arrayBuilder, (Calendar) value);
        } else if (value instanceof InputStream) {
            // Corresponding to JCR BINARY property
            addInputStream(arrayBuilder, (InputStream) value);
        } else if (value instanceof BigDecimal) {
            // Corresponding to JCR DECIMAL property
            arrayBuilder.add((BigDecimal) value);
        } else if (value instanceof BigInteger) {
            // Also corresponding to JCR DECIMAL property
            arrayBuilder.add((BigInteger) value);
        } else {
            addPrimitive(arrayBuilder, value);
        }
    }

    // for object
    private void addCalendar(JsonObjectBuilder objectBuilder, String name, Calendar value)
    {
        // Use the ISO 8601 date+time format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(value.getTimeZone());
        objectBuilder.add(name, sdf.format(value.getTime()));
    }

    // for array
    private void addCalendar(JsonArrayBuilder arrayBuilder, Calendar value)
    {
        // Use the ISO 8601 date+time format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(value.getTimeZone());
        arrayBuilder.add(sdf.format(value.getTime()));
    }

    // for object
    private void addInputStream(JsonObjectBuilder objectBuilder, String name, InputStream value)
    {
        try {
            // We're supposed to be transforming raw bytes into an Unicode string; ISO 8859-1 is a subset of Unicode
            objectBuilder.add(name, IOUtils.toString(value, StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            LOGGER.warn("Failed to read InputStream: {}", e.getMessage(), e);
        }
    }

    // for array
    private void addInputStream(JsonArrayBuilder arrayBuilder, InputStream value)
    {
        try {
            // We're supposed to be transforming raw bytes into an Unicode string; ISO 8859-1 is a subset of Unicode
            arrayBuilder.add(IOUtils.toString(value, StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            LOGGER.warn("Failed to read InputStream: {}", e.getMessage(), e);
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
