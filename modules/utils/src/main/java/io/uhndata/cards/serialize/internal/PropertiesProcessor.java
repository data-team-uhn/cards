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
package io.uhndata.cards.serialize.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonValue;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Serialize all node properties. The name of this processor is {@code properties}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class PropertiesProcessor implements ResourceJsonProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesProcessor.class);

    @Override
    public String getName()
    {
        return "properties";
    }

    @Override
    public int getPriority()
    {
        return 0;
    }

    @Override
    public boolean isEnabledByDefault(Resource resource)
    {
        return true;
    }

    @Override
    public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        // By default this should be the base serializer for properties, but in case someone wants special serialization
        // for a property, leave the previous value unmodified. For example, to skip serializing extra large binary
        // content, a processor with a lower priority may already replace the content with a download link.
        if (input != null) {
            return input;
        }
        // The default is to simply serialize all properties
        try {
            return serializeProperty(property);
        } catch (RepositoryException e) {
            // TODO Auto-generated catch block
            LOGGER.warn("Failed to serialize property {}: {}", property, e.getMessage(), e);
            return null;
        }
    }

    private JsonValue serializeProperty(final Property property) throws RepositoryException
    {
        if (property.isMultiple()) {
            return serializeMultiValuedProperty(property);
        } else {
            return serializeSingleValuedProperty(property);
        }
    }

    private JsonValue serializeSingleValuedProperty(final Property property)
        throws RepositoryException
    {
        final Value value = property.getValue();
        JsonValue result = null;

        switch (property.getType()) {
            case PropertyType.BINARY:
                result = serializeInputStream(value.getBinary().getStream());
                break;
            case PropertyType.BOOLEAN:
                result = value.getBoolean() ? JsonValue.TRUE : JsonValue.FALSE;
                break;
            case PropertyType.DATE:
                result = serializeDate(value.getDate());
                break;
            case PropertyType.DOUBLE:
                result = Json.createValue(value.getDouble());
                break;
            case PropertyType.LONG:
                result = Json.createValue(value.getLong());
                break;
            case PropertyType.DECIMAL:
                // Send as string to prevent losing precision
            default:
                result = Json.createValue(value.getString());
                break;
        }
        return result;
    }

    private JsonValue serializeMultiValuedProperty(final Property property)
        throws RepositoryException
    {
        final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

        for (Value value : property.getValues()) {
            switch (property.getType()) {
                case PropertyType.BINARY:
                    arrayBuilder.add(serializeInputStream(value.getBinary().getStream()));
                    break;
                case PropertyType.BOOLEAN:
                    arrayBuilder.add(value.getBoolean());
                    break;
                case PropertyType.DATE:
                    arrayBuilder.add(serializeDate(value.getDate()));
                    break;
                case PropertyType.DOUBLE:
                    arrayBuilder.add(value.getDouble());
                    break;
                case PropertyType.LONG:
                    arrayBuilder.add(value.getLong());
                    break;
                case PropertyType.DECIMAL:
                    // Send decimals as string to prevent losing precision
                default:
                    arrayBuilder.add(value.getString());
                    break;
            }
        }
        return arrayBuilder.build();
    }

    private JsonValue serializeDate(final Calendar value)
    {
        // Use the ISO 8601 date+time format
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(value.getTimeZone());
        return Json.createValue(sdf.format(value.getTime()));
    }

    private JsonValue serializeInputStream(final InputStream value)
    {
        try {
            // We're supposed to be transforming raw bytes into an Unicode string; ISO 8859-1 is a subset of Unicode
            return Json.createValue(IOUtils.toString(value, StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            LOGGER.warn("Failed to read InputStream: {}", e.getMessage(), e);
        }
        return JsonValue.NULL;
    }
}
