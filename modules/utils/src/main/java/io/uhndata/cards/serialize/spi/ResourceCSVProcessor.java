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

import org.apache.sling.api.resource.Resource;

/**
 * A service that can help serialize a node to CSV text. Implementations of this interface will be invoked by
 * {@link io.uhndata.cards.serialize.ResourceToCSVAdapterFactory} when serializing a resource as {@code CSVString}. When
 * serializing a resource, each enabled processors will be asked if they {@link #canProcess(Resource) can process} the
 * resource, and the first one to answer {@code true} will have it's {@link #serialize(Resource)} method invoked with
 * the resource as a parameter.
 *
 * @version $Id$
 */
public interface ResourceCSVProcessor
{
    /**
     * Checks if the given resource can be serialized by this processor. This method is only invoked for the top level
     * resource being serialized, not for each of its children/descendants. If this method returns {@code true}, this
     * processor will be the only one invoked to serialize the resource. The default implementation returns
     * {@code false} for all resources, implementations must override it to select which resources can be processed.
     *
     * @param resource the resource being serialized
     * @return {@code true} if this processor can be serialize this resource, {@code false} otherwise
     */
    default boolean canProcess(final Resource resource)
    {
        return false;
    }

    /**
     * Called for serializing a resource to CSV.
     *
     * @param resource the resource to serialize
     * @return the resource serialization as CSV text, may be empty
     */
    String serialize(Resource resource);
}
