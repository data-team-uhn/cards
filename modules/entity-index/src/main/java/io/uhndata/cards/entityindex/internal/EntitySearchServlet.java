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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.ItemNotFoundException;
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
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.entityindex.EntityIndexer;
import io.uhndata.cards.entityindex.IndexFields;
import io.uhndata.cards.entityindex.SearchCondition;
import io.uhndata.cards.entityindex.SearchQuery;
import io.uhndata.cards.entityindex.SearchResults;

/**
 * A servlet running searches against the {@link EntityIndexer entity indexes} instead of JCR queries, accessible as
 * {@code /Forms.entitysearch.json} and {@code /Subjects.entitysearch.json}. It accepts the same filtering request
 * parameters as the {@code .paginate} servlet, so any number of answer filters are evaluated in fast index lookups
 * instead of JCR JOIN queries:
 * <ul>
 * <li><code>filternames</code>, <code>filtercomparators</code>, <code>filtervalues</code>, <code>filtertypes</code>:
 * per-answer filters; the name is the UUID of a question, the path of a question relative to
 * {@code /Questionnaires}, or one of the special values {@code cards:Questionnaire}, {@code cards:Subject},
 * {@code cards:Created}, {@code cards:CreatedBy}, {@code cards:LastModified}, {@code cards:LastModifiedBy},
 * {@code statusFlags}</li>
 * <li><code>filterempty</code>, <code>filternotempty</code>: questions that must (not) be unanswered</li>
 * <li><code>filter</code>: a full text filter over the whole entity content</li>
 * <li><code>lucene</code>: a native Lucene query using the flattened field naming convention</li>
 * <li><code>joinnames</code>, <code>joincomparators</code>, <code>joinvalues</code>, <code>jointypes</code>:
 * conditions on another form sharing a related subject with the results</li>
 * <li><code>offset</code>, <code>limit</code>, <code>req</code>, <code>descending</code>,
 * <code>includeallstatus</code>, <code>resourceSelectors</code>: as in the {@code .paginate} servlet</li>
 * <li><code>sortby</code>: a question UUID or path to sort by, instead of the creation date</li>
 * </ul>
 * <p>
 * When searching subjects, question filters are grouped by their questionnaire and evaluated as joins against the
 * forms index: each group must be matched by a single form belonging to the subject, mirroring the JOIN queries of
 * the {@code .paginate} servlet. Results are resolved through the requesting user's session, so entities the user
 * cannot read are never returned; the reported total may however include such entities, in which case it is marked
 * as approximate.
 * </p>
 *
 * @version $Id$
 * @since 0.9.41
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/FormsHomepage", "cards/SubjectsHomepage" },
    selectors = { "entitysearch" })
public class EntitySearchServlet extends SlingJakartaSafeMethodsServlet
{
    private static final long serialVersionUID = 2558430185869017677L;

    private static final Logger LOGGER = LoggerFactory.getLogger(EntitySearchServlet.class);

    private static final int MAX_SCANNED_HITS = 100000;

    private static final String SUBJECT_IDENTIFIER = "cards:Subject";

    private static final String QUESTIONNAIRE_IDENTIFIER = "cards:Questionnaire";

    private static final String INCOMPLETE_FLAG = "INCOMPLETE";

    /** The root of the index that cross-entity joins are evaluated against. */
    private static final String JOINED_ENTITY_ROOT = "/Forms";

    /** A field to filter on, resolved to its index field name, value type, and owning questionnaire. */
    private static final class ResolvedField
    {
        private final String field;

        private final SearchCondition.Type type;

        /** The questionnaire the field belongs to, {@code null} for entity-level fields. */
        private final String questionnaire;

        ResolvedField(final String field, final SearchCondition.Type type, final String questionnaire)
        {
            this.field = field;
            this.type = type;
            this.questionnaire = questionnaire;
        }
    }

    /** A parsed condition, together with the questionnaire it targets, if any. */
    private static final class ParsedCondition
    {
        private final SearchCondition condition;

        private final String questionnaire;

        ParsedCondition(final SearchCondition condition, final String questionnaire)
        {
            this.condition = condition;
            this.questionnaire = questionnaire;
        }
    }

    /** The known entity indexes, keyed by their entity root path. */
    private final Map<String, EntityIndexer> indexes = new ConcurrentHashMap<>();

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC,
        unbind = "unbindIndex")
    void bindIndex(final EntityIndexer index, final Map<String, Object> properties)
    {
        final Object root = properties.get("entity.root");
        if (root instanceof String rootPath) {
            this.indexes.put(rootPath, index);
        }
    }

    void unbindIndex(final EntityIndexer index, final Map<String, Object> properties)
    {
        final Object root = properties.get("entity.root");
        if (root instanceof String rootPath) {
            this.indexes.remove(rootPath, index);
        }
    }

    @Override
    public void doGet(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        try {
            final EntityIndexer index = this.indexes.get(request.getResource().getPath());
            if (index == null) {
                response.setStatus(501);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"No entity index is configured for this resource\"}");
                return;
            }
            final long limit = NumberUtils.toLong(request.getParameter("limit"), 10);
            final long offset = NumberUtils.toLong(request.getParameter("offset"), 0);
            final SearchQuery query = buildQuery(request, index, offset, limit);
            final SearchResults results = index.search(query);
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

    private SearchQuery buildQuery(final SlingJakartaHttpServletRequest request, final EntityIndexer index,
        final long offset, final long limit)
    {
        final Session session = request.getResourceResolver().adaptTo(Session.class);
        final boolean subjectMode = request.getResource().isResourceType("cards/SubjectsHomepage");
        final SearchQuery query = new SearchQuery();
        final List<ParsedCondition> conditions = new ArrayList<>();
        conditions.addAll(parseConditions(request, "filter", session, subjectMode));
        conditions.addAll(parseValuelessConditions(request, "filterempty", SearchCondition.Operator.IS_EMPTY,
            session, subjectMode));
        conditions.addAll(parseValuelessConditions(request, "filternotempty", SearchCondition.Operator.IS_NOT_EMPTY,
            session, subjectMode));
        distributeConditions(query, conditions, index, subjectMode);
        final List<ParsedCondition> joins = parseConditions(request, "join", session, subjectMode);
        if (!joins.isEmpty()) {
            query.withSubjectJoin(joins.stream().map(c -> c.condition).toList(), joinSource(index));
        }
        if (!subjectMode) {
            addStatusFilter(request, query);
        }
        query.withFulltext(request.getParameter("filter"));
        query.withNativeQuery(request.getParameter("lucene"));
        addSort(request, session, query, subjectMode);
        final long maxHits = Math.min(MAX_SCANNED_HITS, offset + Math.max(0, limit) * 10 + 1);
        query.withMaxHits((int) maxHits);
        return query;
    }

    /**
     * Route the parsed conditions into the query. Entity-level conditions apply directly. Conditions on questions
     * apply directly when searching forms; when searching subjects they are grouped by questionnaire, and each group
     * becomes a join against the forms index: a single form of the subject must match all the conditions in the
     * group.
     *
     * @param query the query being built
     * @param conditions the parsed conditions
     * @param index the index being searched
     * @param subjectMode whether subjects are being searched rather than forms
     */
    private void distributeConditions(final SearchQuery query, final List<ParsedCondition> conditions,
        final EntityIndexer index, final boolean subjectMode)
    {
        final Map<String, List<SearchCondition>> joinGroups = new LinkedHashMap<>();
        for (final ParsedCondition parsed : conditions) {
            if (subjectMode && parsed.questionnaire != null) {
                joinGroups.computeIfAbsent(parsed.questionnaire, k -> new ArrayList<>()).add(parsed.condition);
            } else {
                query.withCondition(parsed.condition);
            }
        }
        for (final List<SearchCondition> group : joinGroups.values()) {
            query.withSubjectJoin(group, joinSource(index));
        }
    }

    /**
     * The source index for cross-entity joins: the index of {@value #JOINED_ENTITY_ROOT}, unless that is already the
     * searched index itself.
     *
     * @param searched the index being searched
     * @return the join source, {@code null} when the join is evaluated against the searched index itself
     * @throws IllegalArgumentException if the joined index is not available
     */
    private EntityIndexer joinSource(final EntityIndexer searched)
    {
        final EntityIndexer source = this.indexes.get(JOINED_ENTITY_ROOT);
        if (source == null) {
            throw new IllegalArgumentException("The index for " + JOINED_ENTITY_ROOT + " is not available");
        }
        return source == searched ? null : source;
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

    private void addSort(final SlingJakartaHttpServletRequest request, final Session session,
        final SearchQuery query, final boolean subjectMode)
    {
        final boolean descending = Boolean.parseBoolean(request.getParameter("descending"));
        final String sortBy = request.getParameter("sortby");
        if (StringUtils.isNotBlank(sortBy)) {
            final ResolvedField field = resolveField(sortBy, null, session, subjectMode);
            // Subjects cannot be sorted by answers, since the answers are in separate form documents
            if (field.questionnaire == null || !subjectMode) {
                query.sortBy(field.field, field.type != SearchCondition.Type.TEXT
                    && field.type != SearchCondition.Type.REFERENCE, descending);
                return;
            }
        }
        query.sortBy(null, true, descending);
    }

    /**
     * Map a filter name from the request to an index field and value type. The name may be one of the special
     * {@code cards:*} values, the path of a question relative to {@code /Questionnaires}, or a question UUID.
     *
     * @param name the filter name from the request
     * @param type the value type from the request, may be blank to look it up from the question definition
     * @param session the requesting user's session, used to look up question definitions
     * @param subjectMode whether subjects are being searched rather than forms
     * @return the resolved field
     */
    private ResolvedField resolveField(final String name, final String type, final Session session,
        final boolean subjectMode)
    {
        return switch (name) {
            case QUESTIONNAIRE_IDENTIFIER -> new ResolvedField(IndexFields.QUESTIONNAIRE,
                SearchCondition.Type.REFERENCE, null);
            case SUBJECT_IDENTIFIER -> new ResolvedField(
                subjectMode ? IndexFields.UUID : IndexFields.RELATED_SUBJECTS,
                SearchCondition.Type.REFERENCE, null);
            case "cards:Created" -> new ResolvedField(IndexFields.CREATED, SearchCondition.Type.DATE, null);
            case "cards:CreatedBy" -> new ResolvedField(IndexFields.CREATED_BY, SearchCondition.Type.TEXT, null);
            case "cards:LastModified" -> new ResolvedField(IndexFields.LAST_MODIFIED, SearchCondition.Type.DATE,
                null);
            case "cards:LastModifiedBy" -> new ResolvedField(IndexFields.LAST_MODIFIED_BY,
                SearchCondition.Type.TEXT, null);
            case "statusFlags" -> new ResolvedField(IndexFields.STATUS_FLAGS, SearchCondition.Type.TEXT, null);
            case null, default -> resolveQuestion(name, type, session);
        };
    }

    private ResolvedField resolveQuestion(final String name, final String type, final Session session)
    {
        try {
            final SearchCondition resolved = SearchCondition.forQuestion(session, name, "=", null);
            return new ResolvedField(resolved.getField(),
                StringUtils.isBlank(type) ? resolved.getType() : SearchCondition.typeForData(type),
                findQuestionnaire(session.getNodeByIdentifier(resolved.getField())));
        } catch (final RepositoryException e) {
            LOGGER.debug("Cannot resolve filter name [{}]: {}", name, e.getMessage());
            return new ResolvedField(name, SearchCondition.typeForData(type), null);
        }
    }

    /**
     * Find the questionnaire that a question belongs to.
     *
     * @param question a question node
     * @return the uuid of the ancestor questionnaire, or {@code null} if there isn't one
     */
    private String findQuestionnaire(final Node question)
    {
        try {
            Node parent = question;
            while (parent.getDepth() > 0) {
                parent = parent.getParent();
                if (parent.isNodeType(QUESTIONNAIRE_IDENTIFIER)) {
                    return parent.getIdentifier();
                }
            }
        } catch (final ItemNotFoundException e) {
            // Reached the root, no questionnaire found
        } catch (final RepositoryException e) {
            LOGGER.debug("Failed to find the questionnaire of {}: {}", question, e.getMessage());
        }
        return null;
    }

    /**
     * Parse a group of conditions from a set of four aligned request parameters: {@code <prefix>names},
     * {@code <prefix>comparators}, {@code <prefix>values} and {@code <prefix>types}.
     *
     * @param request the current request
     * @param prefix the parameter name prefix, {@code filter} or {@code join}
     * @param session the requesting user's session
     * @param subjectMode whether subjects are being searched rather than forms
     * @return the parsed conditions, may be empty
     * @throws IllegalArgumentException if the parameters are not aligned
     */
    private List<ParsedCondition> parseConditions(final SlingJakartaHttpServletRequest request, final String prefix,
        final Session session, final boolean subjectMode)
    {
        final List<ParsedCondition> result = new ArrayList<>();
        final String[][] parameters = alignedParameters(request, prefix);
        if (parameters == null) {
            return result;
        }
        final String[] names = parameters[0];
        final String[] comparators = parameters[1];
        final String[] values = parameters[2];
        final String[] types = parameters[3];
        for (int i = 0; i < names.length; ++i) {
            if (StringUtils.isBlank(names[i])) {
                continue;
            }
            final ResolvedField field = resolveField(names[i], types[i], session, subjectMode);
            final SearchCondition condition = new SearchCondition(field.field,
                SearchCondition.Operator.fromSymbol(comparators[i]), values[i], field.type);
            // A filter on the questionnaire itself groups together with the questions of that questionnaire
            final String questionnaire =
                QUESTIONNAIRE_IDENTIFIER.equals(names[i]) ? values[i] : field.questionnaire;
            result.add(new ParsedCondition(condition, questionnaire));
        }
        return result;
    }

    /**
     * Fetch and validate the four aligned condition parameters for the given prefix.
     *
     * @param request the current request
     * @param prefix the parameter name prefix, {@code filter} or {@code join}
     * @return the names, comparators, values and types arrays, or {@code null} when no names are given
     * @throws IllegalArgumentException if the parameters are not aligned
     */
    private String[][] alignedParameters(final SlingJakartaHttpServletRequest request, final String prefix)
    {
        final String[] names = request.getParameterValues(prefix + "names");
        if (names == null) {
            return null;
        }
        final String[] values = request.getParameterValues(prefix + "values");
        final String[] types = request.getParameterValues(prefix + "types");
        final String[] comparators = request.getParameterValues(prefix + "comparators");
        final boolean missing = values == null || types == null || comparators == null;
        if (missing || names.length != values.length || names.length != types.length
            || names.length != comparators.length) {
            throw new IllegalArgumentException("Invalid request, the same number of " + prefix
                + " names, values, types and comparators must be provided");
        }
        return new String[][] { names, comparators, values, types };
    }

    private List<ParsedCondition> parseValuelessConditions(final SlingJakartaHttpServletRequest request,
        final String parameter, final SearchCondition.Operator operator, final Session session,
        final boolean subjectMode)
    {
        final List<ParsedCondition> result = new ArrayList<>();
        final String[] names = request.getParameterValues(parameter);
        if (names == null) {
            return result;
        }
        for (final String name : names) {
            if (StringUtils.isBlank(name)) {
                continue;
            }
            final ResolvedField field = resolveField(name, null, session, subjectMode);
            result.add(new ParsedCondition(new SearchCondition(field.field, operator, null, field.type),
                field.questionnaire));
        }
        return result;
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
