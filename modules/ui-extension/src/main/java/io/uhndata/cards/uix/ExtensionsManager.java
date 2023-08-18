/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.uix;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.script.Bindings;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.scripting.sightly.pojo.Use;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A HTL Use-API that lists UI Extensions. To use this API, simply place the following code in a HTL file:
 * <p>
 * <code>
 * &lt;input data-sly-use.em="${'io.uhndata.cards.uix.ExtensionsManager' @ uixp='ExtensionPointName'}"
 *   type="hidden" id="SomeIdentifier" value="${em.enabled}" /&gt;
 * </code>
 * </p>
 * <p>
 * Another way, using the resources themselves instead of the JSON serialization:
 * </p>
 * <p>
 * <code>
 *   &lt;ul data-sly-use.em="${'io.uhndata.cards.uix.ExtensionsManager' @ uixp='ExtensionPointName'}"&gt;
 *     &lt;li data-sly-repeat="${em.listAll}"&gt;${item.name}&lt;/li&gt;
 *   &lt;/ul&gt;
 * </code>
 * </p>
 *
 * @version $Id$
 */
public class ExtensionsManager implements Use
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionsManager.class);

    private final List<Resource> matchingExtensions = new ArrayList<>();

    private ResourceResolver resourceResolver;

    @Override
    public void init(Bindings bindings)
    {
        final String uixp = (String) bindings.get("uixp");
        if (StringUtils.isBlank(uixp)) {
            LOGGER.warn("Invalid usage of the extension manager: required parameter [uixp] missing");
            return;
        }

        this.resourceResolver = (ResourceResolver) bindings.get("resolver");

        try {
            findExtensions(uixp);
        } catch (Exception e) {
            LOGGER.error("Unexpected error while querying extensions: {}", e.getMessage(), e);
        }
    }

    /**
     * Finds all the extensions for the given extension point and collects them in {@link #matchingExtensions}.
     *
     * @param extensionPointId the identifier of an extension point
     */
    private void findExtensions(final String extensionPointId) throws RepositoryException
    {
        LOGGER.debug("Looking for extensions for [{}]", extensionPointId);
        final Iterator<Resource> result = this.resourceResolver.findResources(
            "select n from [cards:Extension] as n where n.'cards:extensionPointId' = '" + extensionPointId
            + "' order by n.'cards:defaultOrder' OPTION (index tag property)",
            "JCR-SQL2");
        result.forEachRemaining(extension -> this.matchingExtensions.add(extension));
        LOGGER.debug("Found [{}] extensions", this.matchingExtensions.size());
    }

    /**
     * Gets all the matching extensions as a serialized JSON array. The extensions are ordered in the preferred display
     * order. This includes disabled extensions, so it should not be used for actually listing the extensions to be
     * displayed, see {@link #getEnabled()}.
     *
     * @return a JsonArray with all the matching extensions
     * @see #getEnabled()
     */
    public String getAll()
    {
        return toString(listAll());
    }

    /**
     * Gets all the matching extensions. The extensions are ordered in the preferred display order. This includes
     * disabled extensions, so it should not be used for actually listing the extensions to be displayed, see
     * {@link #getEnabled()}.
     *
     * @return a list of all the matching extensions
     * @see #getEnabled()
     */
    public List<Resource> listAll()
    {
        return this.matchingExtensions;
    }

    /**
     * Gets the non-disabled matching extensions as a serialized JSON array. The extensions are ordered in the preferred
     * display order.
     *
     * @return a JsonArray with the enabled matching extensions
     */
    public String getEnabled()
    {
        return toString(listEnabled());
    }

    /**
     * Gets the non-disabled matching extensions. The extensions are ordered in the preferred display order.
     *
     * @return a list of the enabled matching extensions
     */
    public List<Resource> listEnabled()
    {
        return this.matchingExtensions.stream()
            .filter(i -> {
                Boolean b = i.getValueMap().get("cards:defaultDisabled", Boolean.class);
                return b == null ? true : !b.booleanValue();
            })
            .collect(Collectors.toList());
    }

    /**
     * Serializes a list of extension resources as a JSON Array.
     *
     * @param extensions the extensions to serialize
     * @return a string representing a JSON Array, with each of the passed extensions in turn serialized as a JSON
     *         Object
     */
    private String toString(final List<Resource> extensions)
    {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        extensions.stream().forEach(extension -> builder.add(extension.adaptTo(JsonObject.class)));
        return builder.build().toString();
    }
}
