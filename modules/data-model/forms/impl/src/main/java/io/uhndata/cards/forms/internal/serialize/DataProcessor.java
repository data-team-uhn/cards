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
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.serialize.DataFilters;
import io.uhndata.cards.serialize.DataFiltersParser;
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

    // Max depth level of children whose data will be included in the output
    private ThreadLocal<Object> displayLevel = ThreadLocal.withInitial(() -> 0);

    private ThreadLocal<DataFilters> filters = new ThreadLocal<>();

    private ThreadLocal<Map<String, String>> options = new ThreadLocal<>();

    // Node uuid and its type, either questionnaire or subject
    private ThreadLocal<Map<String, String>> uuidsWithEntityFilter = ThreadLocal.withInitial(HashMap::new);

    @Reference
    private DataFiltersParser filtersParser;

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

        this.filters.set(this.filtersParser.parseFilters(this.selectors.get()));

        final Map<String, String> optionsMap = new HashMap<>();
        Arrays.asList(this.selectors.get().split("(?<!\\\\)(?:\\\\\\\\)*\\.")).stream()
            .filter(s -> StringUtils.startsWith(s, "dataOption:"))
            .map(s -> StringUtils.substringAfter(s, "dataOption:"))
            .forEach(s -> optionsMap.put(StringUtils.substringBefore(s, "="),
                StringUtils.substringAfter(s, "=").replaceAll("\\\\\\.", ".")));
        this.options.set(optionsMap);

        setDisplayLevel(optionsMap.get("descendantData"));
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
            // Only the original subject or questionnaire node or their children will have its data appended
            if (!checkNodeToBeSubNodeAndNodeDepthLevel(node)) {
                return;
            }

            boolean isQuestionnaire = node.isNodeType(QuestionnaireUtils.QUESTIONNAIRE_NODETYPE);
            this.uuidsWithEntityFilter.get().put(node.getIdentifier(), isQuestionnaire ? "questionnaire" : "subject");

            if (!node.getPath().equals(this.rootNode.get())) {
                return;
            }

            final String query = generateDataQuery(node.getIdentifier());
            Iterator<Resource> forms = this.resolver.get().findResources(query, Query.JCR_SQL2);

            final Map<String, JsonArrayBuilder> formsJsons = new HashMap<>();
            final ResourceResolver currentResolver = this.resolver.get();

            final String currentSelectors = getCurrentSelectors();
            forms.forEachRemaining(f -> {
                storeForm(currentResolver.resolve(f.getPath() + currentSelectors), formsJsons, isQuestionnaire);
            });
            // The data JSONs have been collected, add them to the subject's JSON
            formsJsons.forEach(json::add);
            final JsonArrayBuilder filtersJson = Json.createArrayBuilder();
            final JsonObjectBuilder optionsJson = Json.createObjectBuilder();
            this.filters.get().getFilters().forEach(f -> filtersJson.add(f.toString()));
            this.options.get().forEach(optionsJson::add);
            json.add("dataFilters", filtersJson);
            json.add("dataOptions", optionsJson);
            json.add("exportDate",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(Calendar.getInstance().getTime()));
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    private String getCurrentSelectors()
    {
        if (this.options.get().containsKey("formSelectors")) {
            return "." + this.options.get().get("formSelectors") + ".json";
        }
        return this.selectors.get();
    }

    @Override
    public void end(Resource resource)
    {
        this.displayLevel.remove();
        this.uuidsWithEntityFilter.remove();
    }

    private void setDisplayLevel(String descendantDataValue)
    {
        if (NumberUtils.isParsable(descendantDataValue)) {
            this.displayLevel.set(Integer.parseInt(descendantDataValue));
        } else if ("true".equals(descendantDataValue)) {
            this.displayLevel.set(true);
        }
    }

    private boolean checkNodeToBeSubNodeAndNodeDepthLevel(Node currentNode) throws RepositoryException
    {
        if (!(currentNode.getPath().equals(this.rootNode.get())
            || currentNode.getPath().startsWith(this.rootNode.get() + "/"))) {
            return false;
        }

        if (this.displayLevel.get().equals(true)) {
            return true;
        }

        // The depth level is equal the number of "/" in path of node without the root path prefix
        final String pathWithoutRoot = currentNode.getPath().replace(this.rootNode.get(), "");
        final int depthLevel = StringUtils.countMatches(pathWithoutRoot, "/");
        return depthLevel <= (int) this.displayLevel.get();
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

    private String generateDataQuery(String currentNodeIdentifier) throws RepositoryException
    {
        StringBuilder result = new StringBuilder("select form.* from [cards:Form] as form");
        result.append(this.filters.get().getExtraQuerySelectors());
        result.append(" where (form."
            + this.uuidsWithEntityFilter.get().get(currentNodeIdentifier) + " = '" + currentNodeIdentifier + "'");
        this.uuidsWithEntityFilter.get().remove(currentNodeIdentifier);
        this.uuidsWithEntityFilter.get()
            .forEach((key, value) -> result.append(" or form.").append(value).append(" = '").append(key).append("'"));
        result.append(")");
        result.append(this.filters.get().getExtraQueryConditions());
        result.append(" order by form.'jcr:created' ASC");
        result.append(" OPTION (index tag cards)");
        return result.toString();
    }
}
