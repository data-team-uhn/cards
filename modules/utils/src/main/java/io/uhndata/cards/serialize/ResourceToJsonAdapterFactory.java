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
package io.uhndata.cards.serialize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * AdapterFactory that converts Apache Sling resources to JsonObjects. This is just a shell, the actual implementation
 * of the serialization process is implemented by implementations of the {@link ResourceJsonProcessor} service. To
 * configure the serialization process, include serializer names in the resource selectors. This can be accomplished by
 * appending them in the request URL for a resource, for example
 * <code>http://server.example/path/to/resource.deep.simple.json</code>, or by appending them in the resource path when
 * using {@code resourceResolver.resolve}, for example
 * {@code resourceResolver.resolve("/path/to/resource.deep.simple")}. A few processors are
 * {@link ResourceJsonProcessor#isEnabledByDefault(Resource) enabled by default}, for example the {@code properties},
 * {@code identify}, and {@code dereference} processors; to disable them, use their name prefixed by {@code -} in the
 * selectors, e.g. {@code /path/to/resource.-dereference.json}.
 *
 * @version $Id$
 */
@Component(
    service = { AdapterFactory.class },
    property = { "adaptables=org.apache.sling.api.resource.Resource", "adapters=javax.json.JsonObject" })
public class ResourceToJsonAdapterFactory
    implements AdapterFactory
{
    /** Logging helper. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceToJsonAdapterFactory.class);

    /** A list of all available processors. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<ResourceJsonProcessor> allProcessors;

    @Override
    public <A> A getAdapter(final Object adaptable, final Class<A> type)
    {
        if (adaptable == null) {
            return null;
        }
        final Resource resource = (Resource) adaptable;
        final Node node = resource.adaptTo(Node.class);
        if (node == null) {
            return null;
        }

        // The list of processors that are enabled for the current resource serialization.
        List<ResourceJsonProcessor> enabledProcessors = setupProcessors(resource);

        // To prevent infinite recursion in case of circular references among nodes, keep track of the nodes processed
        // so far down the stack.
        Stack<String> processedNodes = new Stack<>();

        start(resource, enabledProcessors);
        JsonValue result = serializeNode(node, enabledProcessors, processedNodes);
        end(resource, enabledProcessors);
        if (result != null) {
            return type.cast(result);
        }
        return null;
    }

    /**
     * Serializes a Node into a JSON value. Usually this will be a JSON object listing its items, but to avoid
     * recursion, it is also possible to be just the node's path as a simple string.
     *
     * @param node the node to serialize
     * @return a JSON value, either a JsonObject or a JsonString
     */
    private JsonValue serializeNode(final Node node, final List<ResourceJsonProcessor> enabledProcessors,
        final Stack<String> processedNodes)
    {
        if (node == null) {
            return null;
        }

        try {
            final boolean alreadyProcessed = processedNodes.contains(node.getPath());
            processedNodes.add(node.getPath());
            if (!alreadyProcessed) {
                final JsonObjectBuilder result = Json.createObjectBuilder();
                enterNode(node, result, enabledProcessors, processedNodes);
                processProperties(node, result, enabledProcessors, processedNodes);
                processChildren(node, result, enabledProcessors, processedNodes);
                leaveNode(node, result, enabledProcessors, processedNodes);
                return result.build();
            }
            // If the node has already been processed, only include its path in the output
            return Json.createValue(node.getPath());
        } catch (RepositoryException e) {
            LOGGER.error("Failed to serialize node [{}] to JSON: {}", node, e.getMessage(), e);
        } finally {
            processedNodes.pop();
        }
        return null;
    }

    /**
     * Prepare the serialization of a resource by invoking {@link ResourceJsonProcessor#start} in all enabled
     * processors.
     *
     * @param resource the resource being serialized
     */
    private void start(final Resource resource, final List<ResourceJsonProcessor> enabledProcessors)
    {
        enabledProcessors.forEach(p -> p.start(resource));
    }

    /**
     * Prepare the serialization of a node by invoking {@link ResourceJsonProcessor#enter} in all enabled processors.
     *
     * @param node the node to serialize
     * @param json the JSON being built
     */
    private void enterNode(final Node node, final JsonObjectBuilder json,
        final List<ResourceJsonProcessor> enabledProcessors, final Stack<String> processedNodes)
    {
        enabledProcessors.forEach(p -> p.enter(node, json, n -> serializeNode(n, enabledProcessors, processedNodes)));
    }

    /**
     * Serialize the properties of a node into a {@code JsonObjectBuilder} by invoking
     * {@link ResourceJsonProcessor#processProperty} in all enabled processors.
     *
     * @param node the node to serialize
     * @param json the JSON being built
     * @throws RepositoryException if accessing the repository fails
     */
    private void processProperties(final Node node, final JsonObjectBuilder json,
        final List<ResourceJsonProcessor> enabledProcessors, final Stack<String> processedNodes)
        throws RepositoryException
    {
        final PropertyIterator properties = node.getProperties();
        while (properties.hasNext()) {
            Property thisProp = properties.nextProperty();
            JsonValue value = null;
            for (ResourceJsonProcessor p : enabledProcessors) {
                value =
                    p.processProperty(node, thisProp, value, n -> serializeNode(n, enabledProcessors, processedNodes));
            }
            if (value != null) {
                json.add(thisProp.getName(), value);
            }
        }
    }

    /**
     * Serialize the children of a node into a {@code JsonObjectBuilder} by invoking
     * {@link ResourceJsonProcessor#processChild} in all enabled processors.
     *
     * @param node the node to serialize
     * @param json the JSON being built
     * @throws RepositoryException if accessing the repository fails
     */
    private void processChildren(final Node node, final JsonObjectBuilder json,
        final List<ResourceJsonProcessor> enabledProcessors, final Stack<String> processedNodes)
        throws RepositoryException
    {
        final NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            final Node child = children.nextNode();
            JsonValue value = null;
            for (ResourceJsonProcessor p : enabledProcessors) {
                value = p.processChild(node, child, value, n -> serializeNode(n, enabledProcessors, processedNodes));
            }
            if (value != null) {
                json.add(child.getName(), value);
            }
        }
    }

    /**
     * Further enhance the JSON representing a node after all its properties and children have been serialized by
     * invoking {@link ResourceJsonProcessor#leave} in all enabled processors.
     *
     * @param node the node to serialize
     * @param json the JSON being built
     */
    private void leaveNode(final Node node, final JsonObjectBuilder json,
        final List<ResourceJsonProcessor> enabledProcessors, final Stack<String> processedNodes)
    {
        enabledProcessors.forEach(p -> p.leave(node, json, n -> serializeNode(n, enabledProcessors, processedNodes)));
    }

    /**
     * Clean up after the serialization of a resource by invoking {@link ResourceJsonProcessor#end} in all enabled
     * processors.
     *
     * @param resource the resource that was serialized
     */
    private void end(final Resource resource, final List<ResourceJsonProcessor> enabledProcessors)
    {
        enabledProcessors.forEach(p -> p.end(resource));
    }

    /**
     * Compute the list of enabled processors using the resource's type and selectors. This method must be invoked only
     * once at the start of the serialization process for a resource, and it will set the {@link #enabledProcessors}
     * field.
     *
     * @param resource the resource to serialize
     */
    private List<ResourceJsonProcessor> setupProcessors(final Resource resource)
    {
        // Compute the list of requested processor names:
        // These are enabled by default
        final List<String> defaults = this.allProcessors.stream().filter(p -> p.isEnabledByDefault(resource))
            .map(ResourceJsonProcessor::getName).collect(Collectors.toList());
        // These have been requested
        final List<String> requestedProcessors =
            // Split by unescaped dots. A backslash escapes a dot, but two backslashes are just one escaped backslash.
            // Match by:
            // - no preceding backslash, i.e. start counting at the first backslash (?<!\)
            // - an even number of backslashes, i.e. any number of groups of two backslashes (?:\\)*
            // - a literal dot \.
            // Each backslash, except the \., is escaped twice, once as a special escape char inside a Java string, and
            // once as a special escape char inside a RegExp. The one before the dot is escaped only once as a special
            // char inside a Java string, since it must retain its escaping meaning in the RegExp.
            new ArrayList<>(resource.getResourceMetadata().getResolutionPathInfo() != null
                ? Arrays
                    .asList(resource.getResourceMetadata().getResolutionPathInfo().split("(?<!\\\\)(?:\\\\\\\\)*\\."))
                : defaults);
        // Add the defaults, if not already selected and not explicitly excluded
        for (String def : defaults) {
            if (!requestedProcessors.contains(def) && !requestedProcessors.contains("-" + def)) {
                requestedProcessors.add(def);
            }
        }

        // Build the enabled list using the requested names
        final List<ResourceJsonProcessor> enabled = this.allProcessors.stream()
            .filter(p -> requestedProcessors.contains(p.getName()))
            .filter(p -> p.canProcess(resource))
            .collect(Collectors.toList());
        enabled.sort((o1, o2) -> o1.getPriority() - o2.getPriority());

        return enabled;
    }
}
