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
package io.uhndata.cards.serialize.spi;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;

import io.uhndata.cards.serialize.ResourceToJsonAdapterFactory;

/**
 * A service that can help serialize a node to JSON. Implementations of this interface will be invoked by
 * {@link ResourceToJsonAdapterFactory} when serializing a resource as JSON.
 * <p>
 * Each implementation has a {@link #getName() name} that can be used to enable or disable it. Selecting which modules
 * to use when serializing a resource is done through the resource's selectors. This can be accomplished by appending
 * them in the request URL for a resource, for example <tt>http://server.example/path/to/resource.deep.simple.json</tt>,
 * or by appending them in the resource path when using {@code ResourceResolver#resolve}, for example
 * {@code resourceResolver.resolve("/path/to/resource.deep.simple")}. Some implementations are
 * {@link #isEnabledByDefault(Resource) enabled by default} for all, or just some resources, like the {@code properties}
 * processor that serializes all property names. To disable some of these default processors, prefix their name with
 * {@code -} in the list of selectors, for example {@code /path/to/resource.-dereference.json}.
 * </p>
 * <p>
 * The enabled processors will be called one after another, in ascending order of their {@link #getPriority() priority},
 * receiving the serialized value computed by the previous processor, starting with {@code null}. If, at the end of the
 * process, the result is {@code null}, then that property or child is not included in the output. To serialize a
 * property or child as {@code null}, return {@code JsonValue.NULL}. The name under which a property or child is
 * serialized is its actual name by default, and this cannot be changed by the {@link #processProperty} or
 * {@link #processChild} methods. Changing the key names, or adding extra keys in the JSON, can be accomplished using the
 * {@link #leave} method.
 * </p>
 * <p>
 * For each serialized node, first all its properties are serialized using {@link #processProperty}, then all its
 * children using {@link #processChild}, then the output is post-processed using {@link #leave}.
 * </p>
 *
 * @version $Id$
 */
public interface ResourceJsonProcessor
{
    /**
     * The name that can be used to enable or disable this processor.
     *
     * @return the name of this processors, a simple string
     */
    String getName();

    /**
     * The priority of this processor. Processors with higher numbers are invoked after those with lower numbers,
     * receiving their output as an input. Priority {@code 0} is considered the base priority, where the properties and
     * children are serialized using a default method. Use higher numbers if you want to post-process the default
     * serialization, or negative numbers if you want to provide a different base serialization.
     *
     * @return the priority of this processor, can be any number
     */
    int getPriority();

    /**
     * Checks if the given resource can be serialized by this processor. This method is only invoked for the top level
     * resource being serialized, not for each of its children/descendants. If this method returns {@code true}, this
     * processor may be invoked, if selected. If this method returns {@code false}, this processor will not be invoked
     * when serializing the resource, even if explicitly requested. The default implementation returns {@code true} for
     * all resources, implementations must override it to select which resources can be processed.
     *
     * @param resource the resource being serialized
     * @return {@code true} if this processor can be invoked when serializing this resource, {@code false} otherwise
     */
    default boolean canProcess(final Resource resource)
    {
        return true;
    }

    /**
     * Reports whether this processor should be enabled by default for the given resource, even if not explicitly
     * requested. The default implementation returns {@code false}.
     *
     * @param resource the resource being serialized
     * @return {@code true} if this processor should be invoked when serializing the resource, even if not requested,
     *         {@code false} otherwise
     */
    default boolean isEnabledByDefault(final Resource resource)
    {
        return false;
    }

    /**
     * Called at the start of the serialization process for the topmost resource, in case the current processor needs to
     * initialize some temporary state.
     *
     * @param resource the resource being serialized
     */
    default void start(final Resource resource)
    {
        return;
    }

    /**
     * Called when a node's serialization begin. The processors will receive as input the JSON representation for the
     * node, starting with an empty JsonObjectBuilder, and modified by the previous processors, and any changes done to
     * it will be seen by further processors. The default implementation simply returns, leaving the input unmodified.
     *
     * @param node the node being serialized, may be other than the top resource
     * @param input the JSON representation computed by the previous processors, may be an empty object but must not be
     *            {@code null}
     * @param serializeNode a function that can be invoked to serialize a new node, receiving a Node as input, and
     *            returning a JSON representation
     */
    default void enter(final Node node, final JsonObjectBuilder input, final Function<Node, JsonValue> serializeNode)
    {
        return;
    }

    /**
     * Called for each property of a serialized node, allows processing the JSON serialization of a property. The first
     * processor invoked will receive {@code null} as the JSON input, and each subsequent processor being invoked
     * receives the output of the previous one. If at the end of the process the outcome is {@code null}, then the
     * property is skipped from the parent node's serialization. Any processor may add, modify, or discard a
     * representation, only the outcome of the last processor is taken into consideration when serializing the parent
     * node. The default implementation simply returns the input unmodified.
     *
     * @param node the node whose property is serialized, may be other than the top resource
     * @param property the property being serialized
     * @param input the JSON representation computed by the previous processors, may be {@code null}
     * @param serializeNode a function that can be invoked to serialize a new node, receiving a Node as input, and
     *            returning a JSON representation
     * @return a JSON representation for the property value, may be {@code null} if the property should be skipped, or
     *         any simple or complex JSON value, including arrays or objects
     */
    default JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        return input;
    }

    /**
     * Called for each child node of a serialized node, allows processing the JSON serialization of a child node. The
     * first processor invoked will receive {@code null} as the JSON input, and each subsequent processor being invoked
     * receives the output of the previous one. If at the end of the process the outcome is {@code null}, then the child
     * node is skipped from the parent node's serialization. Any processor may add, modify, or discard a representation,
     * only the outcome of the last processor is taken into consideration when serializing the parent node. The default
     * implementation simply returns the input unmodified.
     *
     * @param node the node whose child is serialized, may be other than the top resource
     * @param child the node being serialized
     * @param input the JSON representation computed by the previous processors, may be {@code null}
     * @param serializeNode a function that can be invoked to serialize a new node, receiving a Node as input, and
     *            returning a JSON representation
     * @return a JSON representation for the child node, may be {@code null} if the child should be skipped, or any
     *         simple or complex JSON value, including arrays or objects
     */
    default JsonValue processChild(final Node node, final Node child, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        return input;
    }

    /**
     * Called at the end of node's serialization, allows further processing of the JSON serialization of a node. The
     * processor will receive the JSON representation computed using {@link #enter}, {@link #processProperty} and
     * {@link #processChild} as the JSON input, and any changes done to it will be seen by further processors. The
     * default implementation simply returns, leaving the input unmodified.
     *
     * @param node the node being serialized, may be other than the top resource
     * @param json the JSON representation computed by the previous processors, may be an empty object but must not be
     *            {@code null}
     * @param serializeNode a function that can be invoked to serialize a new node, receiving a Node as input, and
     *            returning a JSON representation
     */
    default void leave(final Node node, final JsonObjectBuilder json, final Function<Node, JsonValue> serializeNode)
    {
        return;
    }

    /**
     * Called at the end of the serialization process, in case the current processor needs to clean up any temporary
     * state.
     *
     * @param resource the resource being serialized
     */
    default void end(final Resource resource)
    {
        return;
    }
}
