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
package ca.sickkids.ccm.lfs.serialize.internal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.sickkids.ccm.lfs.serialize.spi.ResourceJsonProcessor;

/**
 * Append the progeny of the given node. This will recursively check for the children of this resource,
 * and their children and so forth. The name of this processor is {@code progeny}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class ProgenyProcessor implements ResourceJsonProcessor
{
    private static final String PROGENY_PROPERTY_KEY = "@progeny";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgenyProcessor.class);

    private ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public String getName()
    {
        return "progeny";
    }

    @Override
    public int getPriority()
    {
        return 11;
    }

    @Override
    public boolean isEnabledByDefault(Resource resource)
    {
        return false;
    }

    @Override
    public void start(Resource resource)
    {
        // We will need the resource resolver to query for forms
        this.resolver.set(resource.getResourceResolver());
    }

    @Override
    public void leave(final Node node, final JsonObjectBuilder json,
        final Function<Node, JsonValue> serializeNode)
    {
        // Add a few properties identifying the resource
        try {
            Map<String, Boolean> seenTypes = new HashMap<String, Boolean>();
            JsonObject progeny = getAllProgeny(node.getPath(), seenTypes, true);
            json.add(PROGENY_PROPERTY_KEY, progeny.getJsonArray(PROGENY_PROPERTY_KEY));
        } catch (RepositoryException e) {
            // Unlikely, and not critical, just make sure the serialization doesn't fail
            LOGGER.warn("Failed to obtain subject type children: {}", e.getMessage(), e);
        }
    }

    /**
     * Get all progeny of the current node.
     *
     * @param subjectNodePath The path to the subject node type whose progeny you are trying to access
     * @param seenNodes A map of nodes that we have seen, to avoid cyclical references
     * @param isRoot Used in recursive calls; if true, will avoid copying over any properties of the given Node
     *     to the returned JsonObject
     * @return All nodes that consider the given SubjectType their parent, and t heir grandchildren and so on
     * @throws RepositoryException If a request to the ResourceResolver fails
     */
    public JsonObject getAllProgeny(String subjectNodePath, Map<String, Boolean> seenNodes, Boolean isRoot)
        throws RepositoryException
    {
        final Resource subjectResource = this.resolver.get().getResource(subjectNodePath);
        final Node subjectNode = subjectResource.adaptTo(Node.class);
        final String query =
            "select n from [lfs:SubjectType] as n where n.'parent' = '" + subjectNode.getIdentifier() + "'";
        final Iterator<Resource> results =
            this.resolver.get().findResources(query.toString(), Query.JCR_SQL2);

        // Add one of each progeny
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        Boolean hasResult = false;
        while (results.hasNext()) {
            hasResult = true;
            Resource resource = results.next();
            Node node = resource.adaptTo(Node.class);
            String path = resource.getResourceMetadata().getResolutionPath();

            // Guard against cyclical references
            if (seenNodes.containsKey(path)) {
                continue;
            }
            seenNodes.put(path, true);

            final JsonObject progeny = this.getAllProgeny(path, seenNodes, false);
            arrayBuilder.add(progeny);
        }

        // Copy over this node's info, if any. An empty root node will need an @progeny child.
        if (hasResult || isRoot) {
            builder.add(PROGENY_PROPERTY_KEY, arrayBuilder.build());
        }

        if (!isRoot) {
            // Copy over the keys
            try
            {
                final Thread serializer = new Thread(() ->
                    copyObjectExceptNulls(builder, subjectResource.adaptTo(JsonObject.class)));
                serializer.start();
                // Wait for the serialization of forms to finish
                serializer.join();
            } catch (InterruptedException e)
            {
                LOGGER.warn("Json adaptation failed: {}", e.getMessage());
            }
        }
        return builder.build();
    }

    /**
     * Copy over a JsonObject's values into the given JsonObjectBuilder, excepting any null-valued properties.
     *
     * @param builder A reference to a JsonObjectBuilder to add key/value pairs to
     * @param toAdd A JsonObject whose values you want to add
     */
    private void copyObjectExceptNulls(JsonObjectBuilder builder, JsonObject toAdd)
    {
        for (Entry<String, JsonValue> entry : toAdd.entrySet()) {
            // Nullness guard which causes the Use API to fail
            if (entry.getValue() == null) {
                continue;
            }
            builder.add(entry.getKey(), entry.getValue());
        }
    }
}
