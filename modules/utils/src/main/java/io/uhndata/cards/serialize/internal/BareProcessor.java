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
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Simplify serialization for all resource types by removing all technical properties, renaming {@code "jcr:created"} to
 * simply {@code "created"}, and storing file attachments in a {@code "content"} property. The name of this processor is
 * {@code bare}.
 *
 * @version $Id$
 */
@Component(immediate = true, scope = ServiceScope.SINGLETON)
public class BareProcessor implements ResourceJsonProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BareProcessor.class);

    private ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    @Override
    public String getName()
    {
        return "bare";
    }

    @Override
    public int getPriority()
    {
        return 90;
    }

    @Override
    public void start(Resource resource)
    {
        this.depth.set(0);
    }

    @Override
    public void enter(Node node, JsonObjectBuilder input, Function<Node, JsonValue> serializeNode)
    {
        this.depth.set(this.depth.get() + 1);
    }

    @Override
    public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        if (property == null) {
            return null;
        }
        try {
            final String name = property.getName();
            JsonValue result = input;
            result = removeTechnicalProperties(name, result, "sling:");
            result = removeTechnicalProperties(name, result, "jcr:");
            result = removeProperty(name, result, "form");
            return result;
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    @Override
    public JsonValue processChild(Node node, Node child, JsonValue input, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (child.getName().startsWith("jcr:")) {
                return null;
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
        return input;
    }

    @Override
    public void leave(final Node node, final JsonObjectBuilder json,
        final Function<Node, JsonValue> serializeNode)
    {
        this.depth.set(this.depth.get() - 1);
        addCreationDate(node, json);
        addLastModifiedDate(node, json);
        addFileContent(node, json);
    }

    private JsonValue removeTechnicalProperties(final String propertyName, final JsonValue input,
        final String prefix)
        throws RepositoryException
    {
        if (propertyName.startsWith(prefix)) {
            return null;
        }
        return input;
    }

    private JsonValue removeProperty(final String propertyName, final JsonValue input,
        final String name)
        throws RepositoryException
    {
        if (propertyName.equals(name)) {
            return null;
        }
        return input;
    }

    private void addCreationDate(final Node node, final JsonObjectBuilder json)
    {
        if (this.depth.get() == 0) {
            try {
                if (node.hasProperty("jcr:created")) {
                    json.add("created", serializeDate(node.getProperty("jcr:created").getDate()));
                }
            } catch (RepositoryException e) {
                // Should't happen, and fixing the date is not that critical
            }
        }
    }

    private void addLastModifiedDate(final Node node, final JsonObjectBuilder json)
    {
        if (this.depth.get() == 0) {
            try {
                if (node.hasProperty("jcr:lastModified")) {
                    json.add("lastModified", serializeDate(node.getProperty("jcr:lastModified").getDate()));
                }
            } catch (RepositoryException e) {
                // Should't happen, and fixing the date is not that critical
            }
        }
    }

    private void addFileContent(final Node node, final JsonObjectBuilder json)
    {
        try {
            if (node.isNodeType("nt:file")) {
                Node resourceNode = null;
                for (NodeIterator children = node.getNodes(); children.hasNext();) {
                    final Node child = children.nextNode();
                    if (child.isNodeType("nt:resource")) {
                        resourceNode = child;
                        break;
                    }
                }
                if (resourceNode != null && resourceNode.hasProperty("jcr:data")) {
                    json.add("content",
                        serializeInputStream(resourceNode.getProperty("jcr:data").getBinary().getStream()));
                }
            }
        } catch (RepositoryException e) {
            // Should't happen, and fixing the date is not that critical
        }
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
            return Json.createValue(IOUtils.toString(value, StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            LOGGER.warn("Failed to read InputStream: {}", e.getMessage(), e);
        }
        return JsonValue.NULL;
    }
}
