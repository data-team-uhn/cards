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
package io.uhndata.cards.forms.internal.serialize;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
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

import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Serialize a subject or questionnaire along with its forms. The name of this processor is {@code data}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DataProcessor implements ResourceJsonProcessor
{
    private ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    private ThreadLocal<String> selectors = new ThreadLocal<>();

    private ThreadLocal<String> rootNode = new ThreadLocal<>();

    private ThreadLocal<Map<String, String>> filters = new ThreadLocal<>();

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
        final Map<String, String> filtersMap = new HashMap<>();
        // Split by unescaped dots. A backslash escapes a dot, but two backslashes are just one escaped backslash.
        // Match by:
        // - no preceding backslash, i.e. start counting at the first backslash (?<!\)
        // - an even number of backslashes, i.e. any number of groups of two backslashes (?:\\)*
        // - a literal dot \.
        // Each backslash, except the \., is escaped twice, once as a special escape char inside a Java string, and
        // once as a special escape char inside a RegExp. The one before the dot is escaped only once as a special
        // char inside a Java string, since it must retain its escaping meaning in the RegExp.
        Arrays.asList(this.selectors.get().split("(?<!\\\\)(?:\\\\\\\\)*\\.")).stream()
            .filter(s -> StringUtils.startsWith(s, "dataFilter:"))
            .map(s -> StringUtils.substringAfter(s, "dataFilter:"))
            .forEach(s -> filtersMap.put(StringUtils.substringBefore(s, "="),
                StringUtils.substringAfter(s, "=").replaceAll("\\\\\\.", ".")));
        this.filters.set(filtersMap);

    }

    @Override
    public boolean canProcess(Resource resource)
    {
        return resource.isResourceType("cards/Subject") || resource.isResourceType("cards/Questionnaire");
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            // Only the original subject or questionnaire node will have its data appended
            if (!node.getPath().equals(this.rootNode.get())) {
                return;
            }

            boolean isQuestionnaire = node.isNodeType(QuestionnaireUtils.QUESTIONNAIRE_NODETYPE);
            final String query = generateDataQuery(node, isQuestionnaire);
            Iterator<Resource> forms = this.resolver.get().findResources(query, Query.JCR_SQL2);

            final Map<String, JsonArrayBuilder> formsJsons = new HashMap<>();
            final ResourceResolver currentResolver = this.resolver.get();
            final String currentSelectors = this.selectors.get();
            forms.forEachRemaining(f ->
                storeForm(currentResolver.resolve(f.getPath() + currentSelectors), formsJsons, isQuestionnaire));
            // The data JSONs have been collected, add them to the subject's JSON
            formsJsons.forEach(json::add);
            final JsonObjectBuilder filtersJson = Json.createObjectBuilder();
            this.filters.get().forEach(filtersJson::add);
            json.add("dataFilters", filtersJson);
            json.add("exportDate",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(Calendar.getInstance().getTime()));
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    private void storeForm(final Resource form, final Map<String, JsonArrayBuilder> formsJsons,
        final boolean isQuestionnaire)
    {
        try {
            String questionnaireTitle = "@data";
            if (!isQuestionnaire) {
                final Node questionnaire = form.adaptTo(Node.class).getProperty("questionnaire").getNode();
                questionnaireTitle = questionnaire.getProperty("title").getString();
            }
            final JsonArrayBuilder arrayForQuestionnaire =
                formsJsons.computeIfAbsent(questionnaireTitle, k -> Json.createArrayBuilder());
            arrayForQuestionnaire.add(form.adaptTo(JsonObject.class));
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    private String generateDataQuery(final Node entity, final boolean isQuestionnaire) throws RepositoryException
    {
        final String entityFilter = isQuestionnaire ? "questionnaire" : "subject";
        final StringBuilder result = new StringBuilder("select * from [cards:Form] as n where n." + entityFilter
            + " = '" + entity.getIdentifier() + "'");
        this.filters.get().forEach((key, value) -> {
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
                case "status":
                    result.append(" and n.[statusFlags] = '").append(value).append('\'');
                    break;
                case "statusNot":
                    result.append(" and not n.[statusFlags] = '").append(value).append('\'');
                    break;
                case "modifiedAfter":
                    result.append(" and n.[jcr:lastModified] >= '").append(value).append('\'');
                    break;
                case "modifiedBefore":
                    result.append(" and n.[jcr:lastModified] < '").append(value).append('\'');
                    break;
                default:
                    break;
            }
        });
        result.append(" order by n.'jcr:created' ASC");
        return result.toString();
    }
}
