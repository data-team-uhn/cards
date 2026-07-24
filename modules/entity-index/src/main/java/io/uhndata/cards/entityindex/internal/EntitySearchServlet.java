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
package io.uhndata.cards.entityindex.internal;

import java.io.IOException;
import java.io.Writer;
import java.util.Locale;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingJakartaSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.entityindex.EntityIndexer;
import io.uhndata.cards.entityindex.IndexFields;
import io.uhndata.cards.entityindex.SearchCondition;
import io.uhndata.cards.entityindex.SearchQuery;
import io.uhndata.cards.entityindex.SearchResults;

/**
 * A servlet running searches against the {@link EntityIndexer entity index} instead of JCR queries, accessible as
 * {@code /Forms.entitysearch.json}. It accepts the same filtering request parameters as the {@code .paginate}
 * servlet, so any number of answer filters are evaluated in a single fast index lookup instead of a JCR JOIN query:
 * <ul>
 * <li><code>filternames</code>, <code>filtercomparators</code>, <code>filtervalues</code>, <code>filtertypes</code>:
 * per-answer filters; the name is the UUID of a question, the path of a question relative to
 * {@code /Questionnaires}, or one of the special values {@code cards:Questionnaire}, {@code cards:Subject},
 * {@code cards:Created}, {@code cards:CreatedBy}, {@code cards:LastModified}, {@code cards:LastModifiedBy},
 * {@code statusFlags}</li>
 * <li><code>filterempty</code>, <code>filternotempty</code>: questions that must (not) be unanswered</li>
 * <li><code>filter</code>: a full text filter over the whole entity content</li>
 * <li><code>lucene</code>: a native Lucene query using the flattened field naming convention</li>
 * <li><code>offset</code>, <code>limit</code>, <code>req</code>, <code>descending</code>,
 * <code>includeallstatus</code>, <code>resourceSelectors</code>: as in the {@code .paginate} servlet</li>
 * <li><code>sortby</code>: a question UUID or path to sort by, instead of the creation date</li>
 * </ul>
 * <p>
 * Results are resolved through the requesting user's session, so entities the user cannot read are never returned;
 * the reported total may however include such entities, in which case it is marked as approximate.
 * </p>
 *
 * @version $Id$
 * @since 0.9.41
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/FormsHomepage" },
    selectors = { "entitysearch" })
public class EntitySearchServlet extends SlingJakartaSafeMethodsServlet
{
    private static final long serialVersionUID = 2558430185869017677L;

    private static final Logger LOGGER = LoggerFactory.getLogger(EntitySearchServlet.class);

    private static final int MAX_SCANNED_HITS = 100000;

    private static final String SUBJECT_IDENTIFIER = "cards:Subject";

    private static final String QUESTIONNAIRE_IDENTIFIER = "cards:Questionnaire";

    private static final String INCOMPLETE_FLAG = "INCOMPLETE";

    private static final String STATUS_FLAGS = "statusFlags";

    /** A field to filter on, resolved to its index field name and value type. */
    private static final class ResolvedField
    {
        private final String field;

        private final SearchCondition.Type type;

        ResolvedField(final String field, final SearchCondition.Type type)
        {
            this.field = field;
            this.type = type;
        }
    }

    @Reference
    private transient EntityIndexer indexer;

    @Override
    public void doGet(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        try {
            final long limit = NumberUtils.toLong(request.getParameter("limit"), 10);
            final long offset = NumberUtils.toLong(request.getParameter("offset"), 0);
            final SearchQuery query = buildQuery(request, offset, limit);
            final SearchResults results = this.indexer.search(query);
            writeResponse(request, response, results, offset, limit);
        } catch (final IllegalArgumentException e) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter()
                .write("{\"error\":\"" + StringUtils.defaultString(e.getMessage()).replace('"', '\'') + "\"}");
        } catch (final IOException e) {
            LOGGER.warn("Failed to search the entity index: {}", e.getMessage(), e);
            response.setStatus(500);
        }
    }

    private SearchQuery buildQuery(final SlingJakartaHttpServletRequest request, final long offset, final long limit)
    {
        final Session session = request.getResourceResolver().adaptTo(Session.class);
        final SearchQuery query = new SearchQuery();
        addValueFilters(request, session, query);
        addValuelessFilters(request, session, query, "filterempty", SearchCondition.Operator.IS_EMPTY);
        addValuelessFilters(request, session, query, "filternotempty", SearchCondition.Operator.IS_NOT_EMPTY);
        addStatusFilter(request, query);
        query.withFulltext(request.getParameter("filter"));
        query.withNativeQuery(request.getParameter("lucene"));
        addSort(request, session, query);
        final long maxHits = Math.min(MAX_SCANNED_HITS, offset + Math.max(0, limit) * 10 + 1);
        query.withMaxHits((int) maxHits);
        return query;
    }

    private void addValueFilters(final SlingJakartaHttpServletRequest request, final Session session,
        final SearchQuery query)
    {
        final String[] names = request.getParameterValues("filternames");
        if (names == null) {
            return;
        }
        final String[] values = request.getParameterValues("filtervalues");
        final String[] types = request.getParameterValues("filtertypes");
        final String[] comparators = request.getParameterValues("filtercomparators");
        final boolean missing = values == null || types == null || comparators == null;
        if (missing || names.length != values.length || names.length != types.length
            || names.length != comparators.length) {
            throw new IllegalArgumentException(
                "Invalid request, the same number of filter names, values, types and comparators must be provided");
        }
        for (int i = 0; i < names.length; ++i) {
            if (StringUtils.isBlank(names[i])) {
                continue;
            }
            final ResolvedField field = resolveField(names[i], types[i], session);
            query.withCondition(new SearchCondition(field.field,
                SearchCondition.Operator.fromSymbol(comparators[i]), values[i], field.type));
        }
    }

    private void addValuelessFilters(final SlingJakartaHttpServletRequest request, final Session session,
        final SearchQuery query, final String parameter, final SearchCondition.Operator operator)
    {
        final String[] names = request.getParameterValues(parameter);
        if (names == null) {
            return;
        }
        for (final String name : names) {
            if (StringUtils.isBlank(name)) {
                continue;
            }
            final ResolvedField field = resolveField(name, null, session);
            query.withCondition(new SearchCondition(field.field, operator, null, field.type));
        }
    }

    private void addStatusFilter(final SlingJakartaHttpServletRequest request, final SearchQuery query)
    {
        final boolean includeAllStatus = Boolean.parseBoolean(request.getParameter("includeallstatus"));
        final boolean statusExplicitlyFiltered = query.getConditions().stream()
            .anyMatch(c -> IndexFields.STATUS_FLAGS.equals(c.getField()));
        if (!includeAllStatus && !statusExplicitlyFiltered) {
            query.withCondition(new SearchCondition(IndexFields.STATUS_FLAGS, SearchCondition.Operator.NEQ,
                INCOMPLETE_FLAG, SearchCondition.Type.TEXT));
        }
    }

    private void addSort(final SlingJakartaHttpServletRequest request, final Session session, final SearchQuery query)
    {
        final boolean descending = Boolean.parseBoolean(request.getParameter("descending"));
        final String sortBy = request.getParameter("sortby");
        if (StringUtils.isBlank(sortBy)) {
            query.sortBy(null, true, descending);
            return;
        }
        final ResolvedField field = resolveField(sortBy, null, session);
        query.sortBy(field.field, field.type != SearchCondition.Type.TEXT
            && field.type != SearchCondition.Type.REFERENCE, descending);
    }

    /**
     * Map a filter name from the request to an index field and value type. The name may be one of the special
     * {@code cards:*} values, the path of a question relative to {@code /Questionnaires}, or a question UUID.
     *
     * @param name the filter name from the request
     * @param type the value type from the request, may be blank to look it up from the question definition
     * @param session the requesting user's session, used to look up question definitions
     * @return the resolved field
     */
    private ResolvedField resolveField(final String name, final String type, final Session session)
    {
        return switch (name) {
            case QUESTIONNAIRE_IDENTIFIER -> new ResolvedField(IndexFields.QUESTIONNAIRE,
                SearchCondition.Type.REFERENCE);
            case SUBJECT_IDENTIFIER -> new ResolvedField(IndexFields.RELATED_SUBJECTS,
                SearchCondition.Type.REFERENCE);
            case "cards:Created" -> new ResolvedField(IndexFields.CREATED, SearchCondition.Type.DATE);
            case "cards:CreatedBy" -> new ResolvedField(IndexFields.CREATED_BY, SearchCondition.Type.TEXT);
            case "cards:LastModified" -> new ResolvedField(IndexFields.LAST_MODIFIED, SearchCondition.Type.DATE);
            case "cards:LastModifiedBy" -> new ResolvedField(IndexFields.LAST_MODIFIED_BY,
                SearchCondition.Type.TEXT);
            case STATUS_FLAGS -> new ResolvedField(IndexFields.STATUS_FLAGS, SearchCondition.Type.TEXT);
            case null, default -> resolveQuestion(name, type, session);
        };
    }

    private ResolvedField resolveQuestion(final String name, final String type, final Session session)
    {
        String dataType = type;
        String uuid = name;
        try {
            final Node question = name.indexOf('/') != -1
                ? session.getNode("/Questionnaires/" + name)
                : session.getNodeByIdentifier(name);
            uuid = question.getIdentifier();
            if (StringUtils.isBlank(dataType) && question.hasProperty("dataType")) {
                dataType = question.getProperty("dataType").getString();
            }
        } catch (final RepositoryException e) {
            LOGGER.debug("Cannot resolve filter name [{}]: {}", name, e.getMessage());
        }
        return new ResolvedField(uuid, mapDataType(dataType));
    }

    private SearchCondition.Type mapDataType(final String dataType)
    {
        return switch (StringUtils.defaultString(dataType).toLowerCase(Locale.ROOT)) {
            case "long", "boolean" -> SearchCondition.Type.LONG;
            case "double", "decimal" -> SearchCondition.Type.DOUBLE;
            case "date" -> SearchCondition.Type.DATE;
            case null, default -> SearchCondition.Type.TEXT;
        };
    }

    private void writeResponse(final SlingJakartaHttpServletRequest request,
        final SlingJakartaHttpServletResponse response, final SearchResults results, final long offset,
        final long limit) throws IOException
    {
        final String selectors =
            (request.getParameter("resourceSelectors") == null ? "" : "." + request.getParameter("resourceSelectors"))
                .replaceAll("\\.\\.", ".");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            jsonGen.writeStartArray("rows");
            long readable = 0;
            long returned = 0;
            boolean skipped = false;
            for (final String path : results.getPaths()) {
                if (returned >= limit && !skipped) {
                    // The page is full and no entity was hidden so far, so the index total is exact,
                    // no need to keep checking the remaining results
                    break;
                }
                final Resource resource = request.getResourceResolver().resolve(path + selectors);
                final JsonObject json = resource.adaptTo(JsonObject.class);
                if (json == null) {
                    // The current user is not allowed to see this entity
                    skipped = true;
                    continue;
                }
                ++readable;
                if (readable > offset && returned < limit) {
                    jsonGen.write(json);
                    ++returned;
                }
            }
            jsonGen.writeEnd();
            jsonGen.write("req", StringUtils.defaultString(request.getParameter("req")));
            jsonGen.write("offset", offset);
            jsonGen.write("limit", limit);
            jsonGen.write("returnedrows", returned);
            jsonGen.write("totalrows", skipped ? readable : results.getTotalMatches());
            jsonGen.write("totalIsApproximate",
                skipped || results.getPaths().size() < results.getTotalMatches());
            jsonGen.write("searchtimems", results.getSearchTimeMillis());
            jsonGen.writeEnd().flush();
        }
    }
}
