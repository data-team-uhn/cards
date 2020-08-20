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
package ca.sickkids.ccm.lfs.statistics;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Stack;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonObjectBuilder;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AdapterFactory that converts Apache Sling resources to JsonObjects. Unlike the standard Sling serialization, this
 * serialization is enhanced by:
 * <ul>
 * <li>using the ISO date/time format</li>
 * <li>adding a {@code @path} property with the absolute path of the node</li>
 * <li>embedding referenced nodes instead of simply displaying the UUID or Path, except for versioning nodes</li>
 * </ul>
 *
 * @version $Id$
 */
@Component(service = { AdapterFactory.class },
    property = {
        "adaptables=org.apache.sling.api.resource.Resource",
        "adapters=javax.json.JsonObject"
})
public class StatisticJsonAdapterFactory
    implements AdapterFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticJsonAdapterFactory.class);

    //String constants for JCR properties
    private static final String PATH_PROP = "@path";
    private static final String NAME_PROP = "@name";

    //String constants for Sling Resource Types
    private static final String STATISTIC_SLINGTYPE = "lfs/Statistic";

    private ThreadLocal<Boolean> statistic = new ThreadLocal<Boolean>()
    {
        @Override
        protected Boolean initialValue()
        {
            return Boolean.FALSE;
        }
    };

    private ThreadLocal<Stack<String>> processedNodes = new ThreadLocal<Stack<String>>()
    {
        @Override
        protected Stack<String> initialValue()
        {
            return new Stack<>();
        }
    };

    @Override
    public <A> A getAdapter(final Object adaptable, final Class<A> type)
    {
        if (adaptable == null) {
            return null;
        }
        final Resource resource = (Resource) adaptable;
        final Node node = resource.adaptTo(Node.class);
        try {
            if (".statistics.json".equals(resource.getResourceMetadata().getResolutionPathInfo())) {
                this.statistic.set(Boolean.TRUE);
            }
            final JsonObjectBuilder result = adapt(node);
            if (result != null) {
                return type.cast(result.build());
            }
        } catch (RepositoryException e) {
            LOGGER.error("Failed to serialize resource [{}] to JSON: {}", resource.getPath(), e.getMessage(), e);
        } finally {
            this.statistic.remove();
        }
        return null;
    }

    /*
     * Serialize only the children whose parent is a Statistic
     */
    private void addChild(Node parent, Node child, JsonObjectBuilder result) throws RepositoryException
    {
        if (this.statistic.get()) {
            String parentPropertyType = parent.getProperty("sling:resourceType").getString();
            if (STATISTIC_SLINGTYPE.equals(parentPropertyType)
                ) {
                result.add(child.getName(), adapt(child));
            }
        }
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private JsonObjectBuilder adapt(final Node node) throws RepositoryException
    {
        if (node == null) {
            return null;
        }

        final JsonObjectBuilder result = Json.createObjectBuilder();
        final boolean alreadyProcessed = this.processedNodes.get().contains(node.getPath());
        try {
            this.processedNodes.get().add(node.getPath());
            if (!alreadyProcessed) {
                final PropertyIterator properties = node.getProperties();
                while (properties.hasNext()) {
                    Property thisProp = properties.nextProperty();
                    addProperty(result, thisProp);
                }
                // Also serialize child nodes
                if (this.statistic.get()) {
                    final NodeIterator children = node.getNodes();
                    while (children.hasNext()) {
                        final Node child = children.nextNode();
                        addChild(node, child, result);
                    }
                }
            }
            // Since the node itself doesn't contain the path and name as properties, we must manually add them.
            result.add(PATH_PROP, node.getPath());
            result.add(NAME_PROP, node.getName());
            return result;
        } catch (RepositoryException e) {
            LOGGER.error("Failed to serialize node [{}] to JSON: {}", node.getPath(), e.getMessage(), e);
        } finally {
            this.processedNodes.get().pop();
        }
        return null;
    }

    /*
     * Adds a property to the serialized JSON data structure
     */
    private void addProperty(final JsonObjectBuilder objectBuilder, final Property property)
        throws RepositoryException
    {
        final String name = property.getName();
        final Value value = property.getValue();

        switch (property.getType()) {
            case PropertyType.DATE:
                addDate(objectBuilder, name, value.getDate());
                break;
            case PropertyType.REFERENCE:
            case PropertyType.PATH:
                final Node node = property.getNode();
                // Reference properties starting with "jcr:" deal with versioning,
                // and the version trees have cyclic references.
                // Also, the node history shouldn't be serialized.
                if (name.startsWith("jcr:")) {
                    objectBuilder.add(name, node.getPath());
                } else {
                    objectBuilder.add(name, adapt(node));
                }
                break;
            default:
                objectBuilder.add(name, value.getString());
                break;
        }
    }

    // for object
    private void addDate(final JsonObjectBuilder objectBuilder, final String name, final Calendar value)
    {
        // Use the ISO 8601 date+time format
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(value.getTimeZone());
        objectBuilder.add(name, sdf.format(value.getTime()));
    }
}
