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
 * <li><code>fieldnames</code>, <code>fieldcomparators</code>, <code>fieldvalues</code>: fixed filters on entity
 * properties, as baked into the table URLs (e.g. {@code questionnaire}, {@code subject}, {@code type},
 * {@code statusFlags}); same as {@code filter*} but without an explicit type, which is resolved from the field</li>
 * <li><code>filtergroups</code>, <code>fieldgroups</code>: optional group ids aligned with the filter/field names;
 * conditions sharing a non-empty group are ORed together, distinct groups and ungrouped conditions are ANDed.
 * Supported comparators include {@code ILIKE} and {@code NOT ILIKE}, case-insensitive {@code LIKE} matches</li>
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

    /** The hard cap on how many results are ever scanned for one request, whatever the requested page or total. */
    private static final long MAX_SCANNED_HITS = 10000;

    /** A batch is this many times the page size; the default scan looks ahead about one batch past the page. */
    private static final int QUERY_SIZE_MULTIPLIER = 10;

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

    /**
     * Entity-level filter names that map directly to an index metadata field, regardless of the searched entity. The
     * subject filters ({@code cards:Subject} and the raw {@code subject}) are resolved separately since they depend on
     * whether subjects or forms are being searched. Both the special {@code cards:*} names and the raw node property
     * names sent by the frontend's fixed {@code field} filters are accepted.
     */
    private static final Map<String, ResolvedField> METADATA_FIELDS = Map.of(
        QUESTIONNAIRE_IDENTIFIER, new ResolvedField(IndexFields.QUESTIONNAIRE, SearchCondition.Type.REFERENCE, null),
        "questionnaire", new ResolvedField(IndexFields.QUESTIONNAIRE, SearchCondition.Type.REFERENCE, null),
        "cards:Created", new ResolvedField(IndexFields.CREATED, SearchCondition.Type.DATE, null),
        "cards:CreatedBy", new ResolvedField(IndexFields.CREATED_BY, SearchCondition.Type.TEXT, null),
        "cards:LastModified", new ResolvedField(IndexFields.LAST_MODIFIED, SearchCondition.Type.DATE, null),
        "cards:LastModifiedBy", new ResolvedField(IndexFields.LAST_MODIFIED_BY, SearchCondition.Type.TEXT, null),
        "statusFlags", new ResolvedField(IndexFields.STATUS_FLAGS, SearchCondition.Type.TEXT, null));

    /** A parsed condition, together with the questionnaire it targets and the OR group it belongs to, if any. */
    private static final class ParsedCondition
    {
        private final SearchCondition condition;

        private final String questionnaire;

        /** The OR group this condition shares with others, or {@code null} for a standalone (ANDed) condition. */
        private final String group;

        ParsedCondition(final SearchCondition condition, final String questionnaire, final String group)
        {
            this.condition = condition;
            this.questionnaire = questionnaire;
            this.group = group;
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
            final boolean showTotalRows = Boolean.parseBoolean(request.getParameter("showTotalRows"));
            final Paging paging = new Paging(offset, limit, showTotalRows);
            final SearchQuery query = buildQuery(request, index, paging);
            final SearchResults results = index.search(query);
            writeResponse(request, response, results, paging);
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
        final Paging paging)
    {
        final Session session = request.getResourceResolver().adaptTo(Session.class);
        final boolean subjectMode = request.getResource().isResourceType("cards/SubjectsHomepage");
        final SearchQuery query = new SearchQuery();
        final List<ParsedCondition> conditions = new ArrayList<>();
        // `filter*` carries the interactive column filters (with an explicit type), `field*` the fixed filters
        // baked into the table URL (questionnaire, subject, subject type, status flags), which omit the type.
        conditions.addAll(parseConditions(request, "filter", session, subjectMode));
        conditions.addAll(parseConditions(request, "field", session, subjectMode));
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
        query.withMaxHits((int) paging.scanLimit);
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
        final Map<String, List<SearchCondition>> orGroups = new LinkedHashMap<>();
        for (final ParsedCondition parsed : conditions) {
            if (subjectMode && parsed.questionnaire != null) {
                joinGroups.computeIfAbsent(parsed.questionnaire, k -> new ArrayList<>()).add(parsed.condition);
            } else if (parsed.group != null) {
                orGroups.computeIfAbsent(parsed.group, k -> new ArrayList<>()).add(parsed.condition);
            } else {
                query.withCondition(parsed.condition);
            }
        }
        for (final List<SearchCondition> group : orGroups.values()) {
            // A single-member group is just a plain ANDed condition
            if (group.size() == 1) {
                query.withCondition(group.get(0));
            } else {
                query.withAnyOf(group);
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
        // The subject filters depend on the searched entity: on subjects they match the subject itself, on forms the
        // form's own subject (raw `subject`) or the whole subject hierarchy (the `cards:Subject` filter chip).
        if (SUBJECT_IDENTIFIER.equals(name)) {
            return new ResolvedField(subjectMode ? IndexFields.UUID : IndexFields.RELATED_SUBJECTS,
                SearchCondition.Type.REFERENCE, null);
        }
        if ("subject".equals(name)) {
            return new ResolvedField(subjectMode ? IndexFields.UUID : IndexFields.SUBJECT,
                SearchCondition.Type.REFERENCE, null);
        }
        final ResolvedField metadata = name == null ? null : METADATA_FIELDS.get(name);
        return metadata != null ? metadata : resolveQuestion(name, type, session);
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
        final String[] groups = optionalGroups(request, prefix, names.length);
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
            final String group = groups == null || groups[i].isEmpty() ? null : groups[i];
            result.add(new ParsedCondition(condition, questionnaire, group));
        }
        return result;
    }

    /**
     * Reads the optional {@code <prefix>groups} parameter, which lets conditions be ORed together. Conditions sharing
     * a non-empty group id are combined with OR; distinct groups and ungrouped conditions are ANDed.
     *
     * @param request the current request
     * @param prefix the parameter name prefix, {@code filter}, {@code field} or {@code join}
     * @param expectedLength the number of names the groups must align with
     * @return the group ids aligned with the names, or {@code null} if the parameter is absent
     * @throws IllegalArgumentException if the groups are present but don't align with the names
     */
    private String[] optionalGroups(final SlingJakartaHttpServletRequest request, final String prefix,
        final int expectedLength)
    {
        final String[] groups = request.getParameterValues(prefix + "groups");
        if (groups != null && groups.length != expectedLength) {
            throw new IllegalArgumentException(
                "Invalid request, a " + prefix + " group must be provided for every " + prefix + " name");
        }
        return groups;
    }

    /**
     * Fetch and validate the four aligned condition parameters for the given prefix.
     *
     * @param request the current request
     * @param prefix the parameter name prefix, {@code filter}, {@code field} or {@code join}
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
        final String[] comparators = request.getParameterValues(prefix + "comparators");
        // The types array is optional: the fixed `field` filters send only names, comparators and values, and the
        // value type is then resolved from the field definition. When present, it must be aligned with the rest.
        String[] types = request.getParameterValues(prefix + "types");
        if (types == null) {
            types = new String[names.length];
        }
        final boolean missing = values == null || comparators == null;
        if (missing || names.length != values.length || names.length != types.length
            || names.length != comparators.length) {
            throw new IllegalArgumentException("Invalid request, the same number of " + prefix
                + " names, values and comparators must be provided");
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
                field.questionnaire, null));
        }
        return result;
    }

    /**
     * The pagination parameters of one request, and the derived limit on how many results are scanned. The index
     * lookup never applies an offset itself — results the user cannot read are only known once resolved, so the scan
     * always starts at the first result and the offset is applied while resolving.
     */
    private static final class Paging
    {
        /** The requested 0-based offset, the number of readable results to skip before the page. */
        private final long offset;

        /** The requested page size, the maximum number of results to return. */
        private final long limit;

        /** Whether the exact total number of readable results is wanted, at the cost of scanning them all. */
        private final boolean showTotalRows;

        /**
         * How many results are scanned at most: all of them when a total is wanted, one look-ahead batch otherwise,
         * never beyond {@link #MAX_SCANNED_HITS}.
         */
        private final long scanLimit;

        Paging(final long offset, final long limit, final boolean showTotalRows)
        {
            this.offset = offset;
            this.limit = limit;
            this.showTotalRows = showTotalRows;
            this.scanLimit = scanLimit(offset, limit, showTotalRows);
        }
    }

    /**
     * How many results to scan for a request, mirroring the {@code .paginate} servlet. When a total is not wanted,
     * scan up to the whole batch (ten pages) that contains the requested page, plus one extra result to tell "exactly
     * N" from "more than N"; when a total is wanted, scan everything. Either way never past the hard cap. For example,
     * a page size of 10 gives 101 at offset 0 and 301 at offset 110; a page size of 25 gives 751 at offset 500 and
     * 1001 at offset 501.
     *
     * @param offset the requested 0-based offset
     * @param limit the requested page size
     * @param showTotalRows whether the exact total is wanted, at the cost of scanning every result
     * @return the maximum number of results to scan
     */
    static long scanLimit(final long offset, final long limit, final boolean showTotalRows)
    {
        if (showTotalRows) {
            return MAX_SCANNED_HITS;
        }
        final long batch = (long) QUERY_SIZE_MULTIPLIER * Math.max(1, limit);
        final long lookAhead = ((long) Math.ceil((double) offset / batch) + 1) * batch + 1;
        return Math.min(MAX_SCANNED_HITS, lookAhead);
    }

    /** The running counts of a page of results while it is being written. */
    private static final class PageStats
    {
        /** Entities written to the current page. */
        private long returned;

        /** Entities readable by the requesting user that were scanned. */
        private long readable;
    }

    private void writeResponse(final SlingJakartaHttpServletRequest request,
        final SlingJakartaHttpServletResponse response, final SearchResults results, final Paging paging)
        throws IOException
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
            final PageStats stats = writeRows(jsonGen, request, results, selectors, paging);
            jsonGen.writeEnd();
            writeMetadata(jsonGen, request, results, stats, paging);
            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Resolve every retrieved result through the requesting user's session, count the ones the user can actually
     * read, and write those that fall in the requested page as the {@code rows} of the response. Results that do not
     * resolve are skipped without being counted, so the resulting count reflects what the user can access, not the
     * raw number of index matches.
     *
     * @param jsonGen the JSON generator writing the response
     * @param request the current request, used to resolve entities through the user's session
     * @param results the search results, holding the matching entity paths
     * @param selectors the resource selectors to append when resolving each entity
     * @param paging the pagination parameters
     * @return the page counts
     */
    private PageStats writeRows(final JsonGenerator jsonGen, final SlingJakartaHttpServletRequest request,
        final SearchResults results, final String selectors, final Paging paging)
    {
        final PageStats stats = new PageStats();
        for (final String path : results.getPaths()) {
            // A lightweight access check: an entity the current user cannot read does not resolve, and is not
            // counted. The costlier full serialization is done only for the entities that fall in the requested page.
            if (request.getResourceResolver().getResource(path) == null) {
                continue;
            }
            ++stats.readable;
            if (stats.readable > paging.offset && stats.returned < paging.limit) {
                final JsonObject json =
                    request.getResourceResolver().resolve(path + selectors).adaptTo(JsonObject.class);
                if (json != null) {
                    jsonGen.write(json);
                    ++stats.returned;
                }
            }
        }
        return stats;
    }

    /**
     * Write the response metadata after the {@code rows} array: the echoed request parameters, the row counts, the
     * search duration, and the actual Lucene query for diagnostics. The total is exact when the scan reached the end
     * of the results, and approximate when it stopped at the scan limit, in which case at least that many results
     * exist; the look-ahead scan carries one extra probe result past the batch, excluded from the reported total.
     *
     * @param jsonGen the JSON generator writing the response
     * @param request the current request, used to echo the {@code req} parameter
     * @param results the search results
     * @param stats the counts gathered while writing the rows
     * @param paging the pagination parameters
     */
    private void writeMetadata(final JsonGenerator jsonGen, final SlingJakartaHttpServletRequest request,
        final SearchResults results, final PageStats stats, final Paging paging)
    {
        final boolean approximate = results.getPaths().size() >= paging.scanLimit;
        final long total = approximate && !paging.showTotalRows && stats.readable >= paging.scanLimit
            ? stats.readable - 1 : stats.readable;
        jsonGen.write("req", StringUtils.defaultString(request.getParameter("req")));
        jsonGen.write("offset", paging.offset);
        jsonGen.write("limit", paging.limit);
        jsonGen.write("returnedrows", stats.returned);
        jsonGen.write("totalrows", total);
        jsonGen.write("totalIsApproximate", approximate);
        jsonGen.write("searchtimems", results.getSearchTimeMillis());
        if (results.getLuceneQuery() != null) {
            jsonGen.write("lucenequery", results.getLuceneQuery());
        }
    }
}
