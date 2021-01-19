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
package ca.sickkids.ccm.lfs;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servlet that lists the subject types that whose ancestor is the given subject type.
 * e.g. If there's a Tumor Region subject type whose parent is a Tumor whose parent is a Patient,
 * a progeny search for Patient will return both Tumor Regions and Tumors.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "lfs/SubjectType" },
    selectors = { "progeny" })
public class ProgenyServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = 2558430802619674046L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgenyServlet.class);

    private static final String DEEP_JSON_SUFFIX = ".deep.json";

    private static final String JCR_UUID = "jcr:uuid";

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        // Get the subject type specified
        String subjectPath = request.getResource().getPath();

        // If a questionnaire is specified, return all fields by the given questionnaire
        // Otherwise, we return all questionnaires under this node that are visible by the user
        Map<String, Boolean> seenTypes = new HashMap<String, Boolean>();
        try {
            JsonObject subjectProgeny = this.getAllProgeny(request.getResourceResolver(), subjectPath, seenTypes);

            // Return the entire thing as a json file, except join together fields that have the same
            // name and type
            final Writer out = response.getWriter();
            out.write(subjectProgeny.toString());
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to get progeny of {}: {}", subjectPath, e.getMessage(), e);
        }
    }

    // Get all progeny of the current node
    public JsonObject getAllProgeny(ResourceResolver resolver, String subjectNodePath, Map<String, Boolean> seenNodes)
        throws RepositoryException
    {
        final Resource subjectResource = resolver.getResource(subjectNodePath);
        final Node subjectNode = subjectResource.adaptTo(Node.class);
        final String query =
            "select n from [lfs:SubjectType] as n where n.'parent' = '" + subjectNode.getIdentifier() + "'";
        final Iterator<Resource> results =
            resolver.findResources(query.toString(), Query.JCR_SQL2);

        // Add one of each progeny
        JsonObjectBuilder builder = Json.createObjectBuilder();
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        while (results.hasNext()) {
            Resource resource = results.next();
            Node node = resource.adaptTo(Node.class);
            String path = resource.getResourceMetadata().getResolutionPath();

            // Guard against cyclical references
            if (seenNodes.containsKey(path)) {
                continue;
            }
            seenNodes.put(path, true);

            resource = resolver.resolve(path.concat(DEEP_JSON_SUFFIX));

            JsonObject progeny = this.getAllProgeny(resolver, path, seenNodes);
            //builder.add(node.getIdentifier(), progeny);
            arrayBuilder.add(progeny);
        }

        // Copy over this node's info
        builder.add("progeny", arrayBuilder.build());
        this.copyObjectExceptProgeny(builder, subjectResource.adaptTo(JsonObject.class));
        return builder.build();
    }

    // Copy the object
    private void copyObjectExceptProgeny(JsonObjectBuilder builder, JsonObject toAdd)
    {
        // Copy over the keys
        for (Entry<String, JsonValue> entry : toAdd.entrySet()) {
            if ("progeny".equals(entry.getKey())) {
                continue;
            }
            builder.add(entry.getKey(), entry.getValue());
        }
    }
}
