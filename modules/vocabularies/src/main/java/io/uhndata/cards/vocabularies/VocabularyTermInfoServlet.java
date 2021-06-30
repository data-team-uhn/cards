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
package io.uhndata.cards.vocabularies;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.LogService;

/**
 * A servlet that extends the normal JSON object of a VocabularyTermNode by replacing IDs with objects containing their
 * name and ID.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/VocabularyTerm" },
    methods = { "GET" },
    selectors = { "info" }
    )
public class VocabularyTermInfoServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -8244429250995709300L;

    private static final String HAS_CHILDREN_PROPERTY = "cards:hasChildren";

    private static final String CHILDREN_PROPERTY = "cards:children";

    /* Copying over every child tends to bloat the response, so we only copy a subset of the data */
    private static final String[] KEYS_TO_COPY = { "identifier", "label", "@path" };

    @Reference
    private LogService logger;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        Resource vocabulary = this.findParentVocabulary(request.getResource());
        ResourceResolver resolver = request.getResourceResolver();
        String includeChildrenStr = request.getParameter("includeChildren");
        String parentPath = vocabulary == null ? request.getResource().getParent().getPath() : vocabulary.getPath();
        boolean includeChildren = StringUtils.isNotBlank(includeChildrenStr) && "true".equals(includeChildrenStr);

        // Our normal output would be ourselves as a JSONObject
        JsonObject json = request.getResource().adaptTo(JsonObject.class);
        response.setContentType("application/json");
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();

            // Loop through our properties
            Set<String> keys = json.keySet();
            for (String key : keys) {
                JsonValue value = json.get(key);
                if (value instanceof JsonArray) {
                    // Any arrays should be iterated through and written
                    jsonGen.write(
                        key,
                        this.processArray((JsonArray) value, vocabulary, resolver, parentPath, includeChildren)
                    );
                } else {
                    // Anything else should be left as-is
                    jsonGen.write(key, value);
                }
            }

            // Check to see if we ourselves have children, and add it to the result
            boolean hasChildren = false;
            Iterator<Resource> children = getChildren(
                request.getResource().adaptTo(JsonObject.class),
                resolver,
                parentPath
                );
            jsonGen.writeStartArray(CHILDREN_PROPERTY);
            while (children.hasNext()) {
                hasChildren = true;
                Resource child = children.next();
                jsonGen.write(populateChildren(child.adaptTo(JsonObject.class), resolver, parentPath));
            }
            jsonGen.writeEnd();
            jsonGen.write(HAS_CHILDREN_PROPERTY, hasChildren);

            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Find the parent vocabulary for the given resource, or null if none is found.
     *
     * @param node The resource whose parent vocabulary we're finding
     * @return The parent vocabulary node, or null if none is found
     */
    private Resource findParentVocabulary(Resource node)
    {
        Resource curNode = node;
        while (curNode != null && !("cards/Vocabulary".equals(curNode.getResourceType())))
        {
            curNode = curNode.getParent();
        }
        return curNode;
    }

    /**
     * Process the given array, following references (if any). Fill in an additional cards:hasChildren field
     * if the node has children.
     *
     * @param array Array to iterate through
     * @param vocab Vocabulary object whose children are the vocabulary terms
     * @param resolver A reference to a ResourceResolver
     * @param parentPath the location of the vocabulary whose children we're searching
     * @param includeChildren whether or not we serialize our children
     * @return The input array, but any strings whose names are vocabulary terms are replaced with the term itself
     */
    private JsonValue processArray(JsonArray array, Resource vocab, ResourceResolver resolver, String parentPath,
        boolean includeChildren)
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (int i = 0; i < array.size(); i++) {
            JsonValue value = array.get(i);

            // If this value is a string, attempt to find it within this vocabulary
            if (value.getValueType() == ValueType.STRING) {
                // Parse out the name of the node, removing colons
                JsonString potentialNameString = (JsonString) value;
                String vocabTermNodeName = potentialNameString.getString();
                vocabTermNodeName = vocabTermNodeName.replaceAll(":", "");

                Resource linkedValue = resolver.getResource(vocab, "./" + vocabTermNodeName);
                if (linkedValue == null) {
                    // We couldn't load the parent resource; leave it as is
                    builder.add(value);
                } else {
                    JsonObject linkedObject = linkedValue.adaptTo(JsonObject.class);
                    // If necessary, we also populate this child's children
                    if (includeChildren) {
                        builder.add(populateChildren(linkedObject, resolver, parentPath));
                    } else {
                        builder.add(linkedObject);
                    }
                }
            } else {
                // This is not a string, leave it as is
                builder.add(value);
            }
        }
        return builder.build();
    }

    /**
     * Add the children of the given node to itself.
     * @param resource The resource whose children we're looking for
     * @param resolver A reference to a resource resolver
     * @param parentPath The path of the vocabulary to search through
     * @return The input resource, with its children added as a property with key CHILDREN_PROPERTY
     */
    private JsonObject populateChildren(JsonObject resource, ResourceResolver resolver, String parentPath)
    {
        // Add child terms
        Iterator<Resource> children = getChildren(resource, resolver, parentPath);

        // Copy the resource but add cards:hasChildren to it
        JsonObjectBuilder objectCopier = Json.createObjectBuilder();
        for (String key : KEYS_TO_COPY) {
            objectCopier.add(key, resource.get(key));
        }
        objectCopier.add(HAS_CHILDREN_PROPERTY, children.hasNext());
        return objectCopier.build();
    }

    /**
     * Determine if the given resource has children or not.
     *
     * @param resource The JsonObject to obtain the children of. Must have a child with key "id"
     * @param resolver A reference to a ResourceResolver to use
     * @param parentPath the location of the vocabulary whose children we're searching
     * @return An iterator over the resource's children
     */
    private Iterator<Resource> getChildren(JsonObject resource, ResourceResolver resolver,
        String parentPath)
    {
        // Check to see if this resource has children
        String oakQuery = String.format(
            "SELECT * FROM [cards:VocabularyTerm] AS a WHERE isdescendantnode(a, '%s') AND a.parents = '%s'"
            + " ORDER BY a.label",
            parentPath,
            resource.getString("identifier"));
        return resolver.findResources(oakQuery, "JCR-SQL2");
    }
}
