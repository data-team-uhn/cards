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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
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
        Node node = resource.adaptTo(Node.class);
        JsonObjectBuilder result = Json.createObjectBuilder();
        try {
            PropertyIterator properties = node.getProperties();
            while (properties.hasNext()) {
                addProperty(result, properties.nextProperty());
            }
            return type.cast(result.build());
        } catch (RepositoryException e) {
            LOGGER.error("Failed to serialize resource to JSON: {}", e.getMessage(), e);
        }
        return null;
    }

    private void addProperty(JsonObjectBuilder objectBuilder, Property property) throws RepositoryException
    {
        if (property.isMultiple()) {
            addMultiValuedProperty(objectBuilder, property);
        } else {
            addSingleValuedProperty(objectBuilder, property);
        }
    }

    private void addSingleValuedProperty(JsonObjectBuilder objectBuilder, Property property) throws RepositoryException
    {
        final String name = property.getName();
        final Value value = property.getValue();

        switch (property.getType()) {
            case PropertyType.BINARY:
                addInputStream(objectBuilder, name, value.getBinary().getStream());
                break;
            case PropertyType.BOOLEAN:
                objectBuilder.add(name, value.getBoolean());
                break;
            case PropertyType.DATE:
                addCalendar(objectBuilder, name, value.getDate());
                break;
            case PropertyType.DECIMAL:
                objectBuilder.add(name, value.getDecimal());
                break;
            case PropertyType.DOUBLE:
                objectBuilder.add(name, value.getDouble());
                break;
            case PropertyType.LONG:
                objectBuilder.add(name, value.getLong());
                break;
            case PropertyType.REFERENCE:
            case PropertyType.PATH:
                objectBuilder.add(name, property.getNode().getPath());
                break;
            default:
                objectBuilder.add(name, value.getString());
                break;
        }
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private void addMultiValuedProperty(JsonObjectBuilder objectBuilder, Property property) throws RepositoryException
    {
        final String name = property.getName();
        final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

        for (Value value : property.getValues()) {
            switch (property.getType()) {
                case PropertyType.BINARY:
                    addInputStream(arrayBuilder, value.getBinary().getStream());
                    break;
                case PropertyType.BOOLEAN:
                    arrayBuilder.add(value.getBoolean());
                    break;
                case PropertyType.DATE:
                    addCalendar(arrayBuilder, value.getDate());
                    break;
                case PropertyType.DECIMAL:
                    arrayBuilder.add(value.getDecimal());
                    break;
                case PropertyType.DOUBLE:
                    arrayBuilder.add(value.getDouble());
                    break;
                case PropertyType.LONG:
                    arrayBuilder.add(value.getLong());
                    break;
                case PropertyType.REFERENCE:
                    arrayBuilder.add(
                        property.getSession().getNodeByIdentifier(value.getString()).getPath());
                    break;
                case PropertyType.PATH:
                    final String path = value.getString();
                    final Node referenced =
                        path.charAt(0) == '/' ? property.getSession().getNode(path)
                            : property.getParent().getNode(path);
                    objectBuilder.add(name, referenced.getPath());
                    break;
                default:
                    arrayBuilder.add(value.getString());
                    break;
            }
        }
        objectBuilder.add(name, arrayBuilder);
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
}
