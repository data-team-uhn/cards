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
import java.util.Stack;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
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
public class ResourceToJsonAdapterFactory
    implements AdapterFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceToJsonAdapterFactory.class);

    private static final String JCR_PRIMARY_TYPE_PROP = "jcr:primaryType";
    private static final String PATH_PROP = "@path";
    private static final String JCR_CREATED_PROP = "jcr:created";
    private static final String JCR_CREATED_BY_PROP = "jcr:createdBy";
    private static final String QUESTIONNAIRE_PROP = "questionnaire";
    private static final String TITLE_PROP = "title";
    private static final String TEXT_PROP = "text";
    private static final String VALUE_PROP = "value";
    private static final String NOTE_PROP = "note";
    private static final String QUESTION_PROP = "question";
    private static final String SECTION_PROP = "section";
    private static final String LABEL_PROP = "label";

    private ThreadLocal<Boolean> deep = new ThreadLocal<Boolean>()
    {
        @Override
        protected Boolean initialValue()
        {
            return Boolean.FALSE;
        }
    };

    private ThreadLocal<Boolean> simple = new ThreadLocal<Boolean>()
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
            if (".deep.json".equals(resource.getResourceMetadata().getResolutionPathInfo())) {
                this.deep.set(Boolean.TRUE);
            }
            if (".simple.json".equals(resource.getResourceMetadata().getResolutionPathInfo())) {
                this.simple.set(Boolean.TRUE);
            }
            final JsonObjectBuilder result = adapt(node);
            if (result != null) {
                return type.cast(result.build());
            }
        } catch (RepositoryException e) {
            LOGGER.error("Failed to serialize resource [{}] to JSON: {}", resource.getPath(), e.getMessage(), e);
        } finally {
            this.deep.remove();
            this.simple.remove();
        }
        return null;
    }

    private void handleChild(Node parent, Node child, JsonObjectBuilder result) throws RepositoryException
    {
        if (this.deep.get()) {
            result.add(child.getName(), adapt(child));
        } else if (this.simple.get()) {
            String parentPropertyType = parent.getProperty("sling:resourceType").getString();
            if (!"lfs/Questionnaire".equals(parentPropertyType)
                && !"lfs/Question".equals(parentPropertyType)
                && !"lfs/Section".equals(parentPropertyType)
                ) {
                result.add(child.getName(), adapt(child));
            }
        }
    }

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
                    if (this.simple.get()) {
                        //String primaryType = node.getProperty("jcr:primaryType").getString();
                        String slingResourceSuperType = node.getProperty("sling:resourceSuperType").getString();
                        String slingResourceType = node.getProperty("sling:resourceType").getString();
                        conditionalAddProperty(slingResourceSuperType, slingResourceType, result, thisProp);
                    } else {
                        addProperty(result, thisProp);
                    }
                }
                // If this is a deep serialization, also serialize child nodes
                if (this.deep.get() || this.simple.get()) {
                    final NodeIterator children = node.getNodes();
                    while (children.hasNext()) {
                        final Node child = children.nextNode();
                        handleChild(node, child, result);
                    }
                }
            }
            // Since the node itself doesn't contain the path as a property, we must manually add it.
            result.add(PATH_PROP, node.getPath());
            return result;
        } catch (RepositoryException e) {
            LOGGER.error("Failed to serialize node [{}] to JSON: {}", node.getPath(), e.getMessage(), e);
        } finally {
            this.processedNodes.get().pop();
        }
        return null;
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private void conditionalAddProperty(String slingResourceSuperType,
        String slingResourceType, final JsonObjectBuilder ob,
        final Property prop) throws RepositoryException
    {
        if ("lfs/Form".equals(slingResourceType)) {
            if (JCR_PRIMARY_TYPE_PROP.equals(prop.getName())
                || JCR_CREATED_PROP.equals(prop.getName())
                || JCR_CREATED_BY_PROP.equals(prop.getName())
                || QUESTIONNAIRE_PROP.equals(prop.getName())
                ) {
                addProperty(ob, prop);
            }
        } else if ("lfs/Questionnaire".equals(slingResourceType)) {
            if (JCR_PRIMARY_TYPE_PROP.equals(prop.getName())
                || TITLE_PROP.equals(prop.getName())
                || PATH_PROP.equals(prop.getName())
                ) {
                addProperty(ob, prop);
            }
        } else if ("lfs/Question".equals(slingResourceType)) {
            if (JCR_PRIMARY_TYPE_PROP.equals(prop.getName())
                || TEXT_PROP.equals(prop.getName())
                || PATH_PROP.equals(prop.getName())
                ) {
                addProperty(ob, prop);
            }
        } else if ("lfs/Answer".equals(slingResourceSuperType)) {
            if (JCR_PRIMARY_TYPE_PROP.equals(prop.getName())
                || VALUE_PROP.equals(prop.getName())
                || NOTE_PROP.equals(prop.getName())
                || PATH_PROP.equals(prop.getName())
                ) {
                addProperty(ob, prop);
            }
            if (QUESTION_PROP.equals(prop.getName())) {
                addProperty(ob, prop);
            }
        } else if ("lfs/AnswerSection".equals(slingResourceType)) {
            if (JCR_PRIMARY_TYPE_PROP.equals(prop.getName())) {
                addProperty(ob, prop);
            }
            if (SECTION_PROP.equals(prop.getName())) {
                addProperty(ob, prop);
            }
            if (PATH_PROP.equals(prop.getName())) {
                addProperty(ob, prop);
            }
        } else if ("lfs/Section".equals(slingResourceType)) {
            if (JCR_PRIMARY_TYPE_PROP.equals(prop.getName())) {
                addProperty(ob, prop);
            }
            if (LABEL_PROP.equals(prop.getName())) {
                addProperty(ob, prop);
            }
            if (PATH_PROP.equals(prop.getName())) {
                addProperty(ob, prop);
            }
        } else {
            addProperty(ob, prop);
        }
    }

    private void addProperty(final JsonObjectBuilder objectBuilder, final Property property) throws RepositoryException
    {
        if (property.isMultiple()) {
            addMultiValuedProperty(objectBuilder, property);
        } else {
            addSingleValuedProperty(objectBuilder, property);
        }
    }

    private void addSingleValuedProperty(final JsonObjectBuilder objectBuilder, final Property property)
        throws RepositoryException
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
                addDate(objectBuilder, name, value.getDate());
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

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private void addMultiValuedProperty(final JsonObjectBuilder objectBuilder, final Property property)
        throws RepositoryException
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
                    addDate(arrayBuilder, value.getDate());
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
                    final Node node = property.getSession().getNodeByIdentifier(value.getString());
                    // Reference properties starting with "jcr:" deal with versioning,
                    // and the version trees have cyclic references.
                    // Also, the node history shouldn't be serialized.
                    if (name.startsWith("jcr:")) {
                        arrayBuilder.add(node.getPath());
                    } else {
                        arrayBuilder.add(adapt(node));
                    }
                    break;
                case PropertyType.PATH:
                    final String path = value.getString();
                    final Node referenced =
                        path.charAt(0) == '/' ? property.getSession().getNode(path)
                            : property.getParent().getNode(path);
                    objectBuilder.add(name, adapt(referenced));
                    break;
                default:
                    arrayBuilder.add(value.getString());
                    break;
            }
        }
        objectBuilder.add(name, arrayBuilder);
    }

    // for object
    private void addDate(final JsonObjectBuilder objectBuilder, final String name, final Calendar value)
    {
        // Use the ISO 8601 date+time format
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(value.getTimeZone());
        objectBuilder.add(name, sdf.format(value.getTime()));
    }

    // for array
    private void addDate(final JsonArrayBuilder arrayBuilder, final Calendar value)
    {
        // Use the ISO 8601 date+time format
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(value.getTimeZone());
        arrayBuilder.add(sdf.format(value.getTime()));
    }

    // for object
    private void addInputStream(final JsonObjectBuilder objectBuilder, final String name, final InputStream value)
    {
        try {
            // We're supposed to be transforming raw bytes into an Unicode string; ISO 8859-1 is a subset of Unicode
            objectBuilder.add(name, IOUtils.toString(value, StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            LOGGER.warn("Failed to read InputStream: {}", e.getMessage(), e);
        }
    }

    // for array
    private void addInputStream(final JsonArrayBuilder arrayBuilder, final InputStream value)
    {
        try {
            // We're supposed to be transforming raw bytes into an Unicode string; ISO 8859-1 is a subset of Unicode
            arrayBuilder.add(IOUtils.toString(value, StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            LOGGER.warn("Failed to read InputStream: {}", e.getMessage(), e);
        }
    }
}
