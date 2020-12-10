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
package ca.sickkids.ccm.lfs.dataentry.internal.serialize;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;

import ca.sickkids.ccm.lfs.serialize.spi.ResourceJsonProcessor;

/**
 * Serialize a subject along with its forms. The name of this processor is {@code data}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DataSubjectProcessor implements ResourceJsonProcessor
{
    private ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    private ThreadLocal<String> selectors = new ThreadLocal<>();

    private ThreadLocal<String> rootNode = new ThreadLocal<>();

    @Override
    public String getName()
    {
        return "data";
    }

    @Override
    public int getPriority()
    {
        return 90;
    }

    @Override
    public void start(Resource resource)
    {
        // We will need the resource resolver to query for forms
        this.resolver.set(resource.getResourceResolver());
        // We want to forward the selectors to the forms serialization as well
        this.selectors.set(resource.getResourceMetadata().getResolutionPathInfo());
        // We only serialize data for the serialized subject, not other nodes
        this.rootNode.set(resource.getPath());
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        return resource.isResourceType("lfs/Subject");
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            // Only the original subject node will have its data appended
            if (!node.getPath().equals(this.rootNode.get())) {
                return;
            }
            Iterator<Resource> forms = this.resolver.get().findResources(generateDataQuery(node), Query.JCR_SQL2);
            final Map<String, JsonArrayBuilder> formsJsons = new HashMap<>();

            // Since the adaptTo serialization process uses ThreadLocal variables, we need to serialize other resources
            // in a separate thread in order to not pollute the current state. We must extract the needed state from the
            // current ThreadLocals to be used in the sub-thread.
            final ResourceResolver currentResolver = this.resolver.get();
            final String currentSelectors = this.selectors.get();
            final Thread serializer = new Thread(() -> forms
                .forEachRemaining(f -> storeForm(currentResolver.resolve(f.getPath() + currentSelectors), formsJsons)));
            serializer.start();
            // Wait for the serialization of forms to finish
            serializer.join();
            // Now the data JSONs should be available, add them to the subject's JSON
            formsJsons.forEach(json::add);
        } catch (RepositoryException | InterruptedException e) {
            // Really shouldn't happen
        }
    }

    private void storeForm(final Resource form, final Map<String, JsonArrayBuilder> formsJsons)
    {
        try {
            final Node questionnaire = form.adaptTo(Node.class).getProperty("questionnaire").getNode();
            final String questionnaireTitle = questionnaire.getProperty("title").getString();
            final JsonArrayBuilder arrayForQuestionnaire =
                formsJsons.computeIfAbsent(questionnaireTitle, k -> Json.createArrayBuilder());
            arrayForQuestionnaire.add(form.adaptTo(JsonObject.class));
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    private String generateDataQuery(final Node subject) throws RepositoryException
    {
        final StringBuilder result =
            new StringBuilder("select * from [lfs:Form] as n where n.subject = '" + subject.getIdentifier() + "'");
        Arrays.asList(this.selectors.get().split("\\.")).stream().filter(s -> s.contains(":")).forEach(f -> {
            final String key = StringUtils.substringBefore(f, ":");
            final String value = StringUtils.substringAfter(f, ":");
            switch (key) {
                case "createdAfter":
                    result.append(" and n.[jcr:created] >= '").append(value).append('\'');
                    break;
                case "createdBefore":
                    result.append(" and n.[jcr:created] < '").append(value).append('\'');
                    break;
                case "createdBy":
                    result.append(" and n.[jcr:createdBy] = '").append(value).append('\'');
                    break;
                default:
                    break;
            }
        });
        return result.toString();
    }
}
