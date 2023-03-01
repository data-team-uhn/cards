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
package io.uhndata.cards;

import java.io.IOException;
import java.io.Writer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.json.Json;
import javax.json.JsonObject;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servlet that lists resources of a specific type, depending on which "homepage" resource the request is targeting.
 * <p>
 * This servlet supports the following parameters:
 * </p>
 * <ul>
 * <li><code>offset</code>: a 0-based number representing how many resources to skip; 0 by default</li>
 * <li><code>limit</code>: a number representing how many resources to include at most in the result; 10 by default</li>
 * <li><code>filter</code>: a (lucene-like) search term, such as {@code germline}, {@code cancer OR tumor},
 * {@code (*blastoma OR *noma OR tumor*) recurrent}; no filter set by default</li>
 * <li><code>includeallstatus</code>: if true, incomplete forms will be included. Otherwise, they will be excluded
 * unless searched for directly using {@code fieldname="statusFlags"}
 * </ul>
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/ResourceHomepage" },
    selectors = { "paginate" })
public class PaginationServlet extends SlingSafeMethodsServlet
{

    protected static final String FIELDNAME = "fieldname";
    protected static final String FIELDCOMPARATOR = "fieldcomparator";
    protected static final String FIELDVALUE = "fieldvalue";
    private static final Logger LOGGER = LoggerFactory.getLogger(PaginationServlet.class);

    private static final long serialVersionUID = -6068156942302219324L;

    private static final int QUERY_SIZE_MULTIPLIER = 10;

    // Allowed JCR-SQL2 operators (from https://docs.adobe.com/docs/en/spec/jcr/2.0/6_Query.html#6.7.17%20Operator)
    private static final List<String> COMPARATORS =
        Arrays.asList("=", "<>", "<", "<=", ">", ">=", "LIKE", "notes contain", "contains", " IS NULL",
            " IS NOT NULL");

    private static final String SUBJECT_IDENTIFIER = "cards:Subject";

    private static final String QUESTIONNAIRE_IDENTIFIER = "cards:Questionnaire";

    private static final String CREATED_DATE_IDENTIFIER = "cards:CreatedDate";

    /**
     * Various supported filter types.
     */
    protected enum FilterType
    {
        /** Regular filters on child node values. */
        CHILD("child", "filternames", null, false),
        /** IS NULL filters that check that a child node value is null. */
        EMPTY("empty", "filterempty", " IS NULL", true),
        /** IS NOT NULL filters that check that a child node value is not null. */
        NOT_EMPTY("notempty", "filternotempty", " IS NOT NULL", true);

        /** Prefix to use in query source names. */
        private final String sourcePrefix;

        /** The name of the request parameter holding the filters. */
        private final String parameterName;

        /** The default comparator to use, when no comparator is specified in the request. */
        private final String comparator;

        /** Whether this is a valueless filter, like "is null". */
        private final boolean valueless;

        FilterType(final String prefix, final String parameterName, final String comparator, final boolean valueless)
        {
            this.sourcePrefix = prefix;
            this.parameterName = parameterName;
            this.comparator = comparator;
            this.valueless = valueless;
        }
    }

    /**
     * A parsed filter, gathering together the field, comparator, value to compare against, type of the value, and
     * source name that the field belongs to.
     */
    protected static final class Filter
    {
        /**
         * The field name, may be the jcr:uuid of a question being answered, or a special value like the subject,
         * creator, creation date, or questionnaire.
         */
        private final String name;

        /**
         * The value to compare against, may be an empty string if no value is needed, e.g. for a "is null" filter.
         */
        private final String value;

        /** The type of the field/value, useful for special treatment of dates and booleans. */
        private final String type;

        /** The comparator, one of the COMPARATORS. */
        private final String comparator;

        /**
         * The JCR query source name that the filter applies to. While other fields are parsed from the request, this
         * will be filled in later when the query sources are generated.
         */
        private String source;

        /**
         * The JCR node type of the source, e.g. {@code cards:BooleanAnswer}. While other fields are parsed from the
         * request, this will be filled in later when the query sources are generated. Special filters will not have a
         * node type at all, since they apply to a special known source.
         */
        private String nodeType;

        Filter(final String name, final String value, final String type, final String comparator)
        {
            this.name = name;
            this.value = value;
            this.type = type;
            this.comparator = comparator;
        }

        String getName()
        {
            return this.name;
        }

        String getValue()
        {
            return this.value;
        }

        String getComparator()
        {
            return this.comparator;
        }
    }

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException, IllegalArgumentException
    {
        try {
            final ResourceResolver resolver = request.getResourceResolver();
            final Session session = resolver.adaptTo(Session.class);

            // Parse the request to build a list of filters
            final Map<FilterType, List<Filter>> filters = parseFiltersFromRequest(request);

            final long limit = getLongValueOrDefault(request.getParameter("limit"), 10);
            final long offset = getLongValueOrDefault(request.getParameter("offset"), 0);

            // Check for special cases in request and return zero results if any
            if (checkForSpecialEmptyFilter(request, filters, response)) {
                // Write the empty response
                writeEmptyResponse(request, response, offset, limit);
                return;
            }

            // Get a QueryManager object
            final QueryManager queryManager = session.getWorkspace().getQueryManager();

            // Create the Query object
            Query filterQuery = queryManager.createQuery(createQuery(request, session, filters), "JCR-SQL2");

            // Get the results and write the response
            writeResponse(request, response, offset, limit, filterQuery);
        } catch (Exception e) {
            LOGGER.warn("Failed to execute query: {}", e.getMessage(), e);
            return;
        }
    }

    /**
     * Checks if any "is empty" filters for the mandatory data are specified, since such a query will never have any
     * results.
     *
     * @param request the current request
     * @param filters a list of filters
     * @param response the HTTP response
     * @return {@code true} if any mandatory special property has an "is empty" filter, {@code false} otherwise
     * @throws RepositoryException if accessing the repository fails
     */
    protected boolean checkForSpecialEmptyFilter(final SlingHttpServletRequest request,
        final Map<FilterType, List<Filter>> filters, final SlingHttpServletResponse response)
        throws RepositoryException
    {
        for (Filter filter : filters.getOrDefault(FilterType.EMPTY, new ArrayList<Filter>())) {
            if (SUBJECT_IDENTIFIER.equals(filter.name)
                || QUESTIONNAIRE_IDENTIFIER.equals(filter.name)
                || CREATED_DATE_IDENTIFIER.equals(filter.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Write an empty results response.
     *
     * @param request the current request
     * @param response the HTTP response
     * @param offset how many resources from the query results were skipped
     * @param limit how many resources from the query results to serialize, may be 0 if we only want a count of the
     *            resources
     * @throws IOException if failed or interrupted I/O operation
     * @throws RepositoryException if accessing the repository fails
     */
    private void writeEmptyResponse(final SlingHttpServletRequest request, final SlingHttpServletResponse response,
        final long offset, final long limit)
        throws IOException, RepositoryException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // The writer doesn't need to be explicitly closed since the auto-closed jsonGen will also close the writer
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            jsonGen.writeStartArray("rows").writeEnd();
            long[] limits = new long[] { offset, limit, 0, 0, 0 };
            writeSummary(jsonGen, request, limits);
            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Write the response.
     *
     * @param request the current request
     * @param response the HTTP response
     * @param offset how many resources from the query results were skipped
     * @param limit how many resources from the query results to serialize, may be 0 if we only want a count of the
     *            resources
     * @param query the query to execute
     * @throws IOException if failed or interrupted I/O operation
     * @throws RepositoryException if accessing the repository fails
     */
    private void writeResponse(final SlingHttpServletRequest request, final SlingHttpServletResponse response,
        final long offset, final long limit, final Query query)
        throws IOException, RepositoryException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // The writer doesn't need to be explicitly closed since the auto-closed jsonGen will also close the writer
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            jsonGen.writeStartArray("rows");
            long[] limits = writeResources(jsonGen, query, offset, limit, request);
            jsonGen.writeEnd();
            writeSummary(jsonGen, request, limits);
            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Returns a type of results to return, a node type like {@code cards:Form} or {@code cards:Subject}.
     *
     * @param request the current request
     * @return a node type string
     * @throws RepositoryException if accessing the repository fails
     */
    private String getNodeType(final SlingHttpServletRequest request) throws RepositoryException
    {
        final Node node = request.getResource().adaptTo(Node.class);
        return node.hasProperty("childNodeType") ? node.getProperty("childNodeType").getString()
            : request.getResource().getResourceType().replace('/', ':').replaceFirst("sHomepage$", "");
    }

    /**
     * Generates a JCR SQL Query from the request.
     *
     * @param request the current request
     * @param session a valid JCR session
     * @param filters a list of filters
     * @return a query that takes into account the requested filters
     * @throws RepositoryException if accessing the repository fails
     */
    protected String createQuery(final SlingHttpServletRequest request, Session session,
        final Map<FilterType, List<Filter>> filters) throws RepositoryException
    {
        // If we want this query to be fast, we need to use the exact nodetype requested.
        final String nodeType = getNodeType(request);

        // We select all nodes having the right type
        final StringBuilder query = new StringBuilder("select distinct n.* from [").append(nodeType).append("] as n");

        // The map that stores questionnaires uuids with prefixes in array and maps it to the
        // corresponding group of questions uuids with their prefixes (stored in a map)
        // To be used later on the filter query construction

        // Add sources
        query.append(
            getQuerySources(
                nodeType,
                filters,
                session));

        // Check only for the descendants of the requested homepage
        query.append(" where isdescendantnode(n, '" + request.getResource().getPath() + "')");

        // Full text search; \ and ' must be escaped
        final String fullTextFilter = request.getParameter("filter");
        if (StringUtils.isNotBlank(fullTextFilter)) {
            query.append(" and contains(n.*, '" + this.sanitizeValue(fullTextFilter) + "')");
        }

        // Exact condition on parent node; \ and ' must be escaped. The value must be wrapped in 's
        Map<String, String> fieldParameters = getSanitizedFieldParameters(request);
        if (StringUtils.isNotBlank(fieldParameters.get(FIELDNAME))) {
            query.append(String.format(
                " and n.'%s'%s'%s'",
                    fieldParameters.get(FIELDNAME),
                    fieldParameters.get(FIELDCOMPARATOR),
                    fieldParameters.get(FIELDVALUE)));
        }

        // TODO, if more request options are required: convert includeAllStatus into a request mode
        // backed by an enum
        final boolean includeAllStatus = Boolean.parseBoolean(request.getParameter("includeallstatus"));
        // Only display `INCOMPLETE` forms if we are explicitly checking the status of forms,
        // or if the user requested forms with all statuses
        if (!("statusFlags".equals(fieldParameters.get(FIELDNAME)) || includeAllStatus
                || nodeType.equals(SUBJECT_IDENTIFIER))) {
            query.append(" and not n.'statusFlags'='INCOMPLETE'");
        }

        // Conditions on child nodes
        query.append(getQueryConditions(nodeType, filters));

        // Results ordering
        final boolean sortDescending = Boolean.valueOf(request.getParameter("descending"));
        query.append(" order by n.'jcr:created'").append(sortDescending ? " DESC" : " ASC");

        // Force using the lucene indexes
        query.append(" option(index tag cards)");

        // All done!
        String finalquery = query.toString();
        LOGGER.debug("Computed final query: {}", finalquery);
        return finalquery;
    }

    /**
     * Get from the request field parameters, sanitize and parse them into a collection.
     *
     * @param request the current request
     * @return a map from field parameter name to field parameter value
     */
    protected Map<String, String> getSanitizedFieldParameters(final SlingHttpServletRequest request)
    {
        Map<String, String> sanitizedFilterParameters = new HashMap<>();
        sanitizedFilterParameters.put(FIELDNAME, this.sanitizeValue(request.getParameter(FIELDNAME)));
        sanitizedFilterParameters.put(FIELDVALUE, this.sanitizeValue(request.getParameter(FIELDVALUE)));
        sanitizedFilterParameters.put(FIELDCOMPARATOR, this.sanitizeComparator(request.getParameter(FIELDCOMPARATOR)));

        return sanitizedFilterParameters;
    }

    /**
     * Parse the request parameters into a collection of proper filters.
     *
     * @param request the current request
     * @return a map from filter types to a list of filters of that type; the resulting map may be empty, or it may have
     *         only some types of filters, depending on which filters are specified in the request
     * @throws IllegalArgumentException when the number of request parameters are not equal
     */
    protected Map<FilterType, List<Filter>> parseFiltersFromRequest(final SlingHttpServletRequest request)
        throws IllegalArgumentException
    {
        final Map<FilterType, List<Filter>> result = new HashMap<>();
        for (FilterType filterType : FilterType.values()) {
            final String[] filters = request.getParameterValues(filterType.parameterName);
            if (filters == null || filters.length == 0) {
                continue;
            }
            if (filterType.valueless) {
                result.put(filterType,
                    Arrays.asList(filters).stream().map(f -> new Filter(f, "", "", filterType.comparator))
                        .collect(Collectors.toList()));
            } else {
                // FIXME This only works with one filter, it should be refactored to use a common prefix + suffixes
                final String[] values = request.getParameterValues("filtervalues");
                final String[] types = request.getParameterValues("filtertypes");
                final String[] comparators = request.getParameterValues("filtercomparators");
                if (filters.length != values.length || types.length != comparators.length
                    || filters.length != comparators.length) {
                    throw new IllegalArgumentException(
                        "Invalid request, the same number of filter names, values, types and comparators"
                            + " must be provided");
                }
                final List<Filter> gatheredFilters = new LinkedList<>();
                for (int i = 0; i < filters.length; ++i) {
                    gatheredFilters.add(new Filter(filters[i], values[i], types[i], comparators[i]));
                }
                result.put(filterType, gatheredFilters);
            }
        }
        return result;
    }

    /**
     * Processes all the filters and creates the query's source.
     *
     * @param nodeType the type of results to return, a node type like {@code cards:Form} or {@code cards:Subject}
     * @param filters a list of filters
     * @param session the current JCR session
     * @return the query fragment listing all sub-sources except the main node type itself (the "join ..." part); empty
     *         if there are no filters for descendant nodes
     */
    private String getQuerySources(final String nodeType,
        final Map<FilterType, List<Filter>> filters, final Session session)
    {
        final Map<String, String> questionnairesToFormSource = new HashMap<>();
        final Map<String, List<Filter>> questionnairesToQuestions = new HashMap<>();

        // Resolve all questions to questionnaires and group them by questionnaires
        filters.forEach((type, values) -> mapFiltersToSources(nodeType, type, values, questionnairesToFormSource,
            questionnairesToQuestions, session));

        if (nodeType.equals(SUBJECT_IDENTIFIER)) {
            // Make joins per questionnaire/form, and per each question/answer in a form
            return createSubjectJoins(questionnairesToFormSource, questionnairesToQuestions);
        } else {
            // There should be only one questionnaire in the end, but there's no way to enforce this in the UI.
            // If there's more than one questionnaire involved, then no form will ever match.
            // Just assume that all the questions belong to the same questionnaire, and append joins for answers for
            // each question, regardless of the questionnaire
            return createFormJoins(questionnairesToFormSource, questionnairesToQuestions);
        }
    }

    /**
     * Maps filter names to source names in the JCR query. This also fills in the source member of each filter.
     *
     * @param nodeType the type of results to return, a node type like {@code cards:Form} or {@code cards:Subject}
     * @param filterType the type of filter to process
     * @param filters a list of filters
     * @param questionnairesToFormSource out parameter, maps from a questionnaire's UUID to a form source identifier, in
     *            the format {@code f1}
     * @param questionnairesToQuestions out parameter, maps from a questionnaire's UUID to a list of questions that
     *            belong to it
     * @param questionsToAnswerSource out parameter, maps from a question UUID to an answer source identifier, in the
     *            format {@code child1_1}, where {@code child} is the specified prefix, the first number is the form
     *            source number, and the second number is a counter of the sub-sources belonging to the same form
     * @param session the current JCR session
     */
    private void mapFiltersToSources(
        final String nodeType, final FilterType filterType, final List<Filter> filters,
        final Map<String, String> questionnairesToFormSource,
        final Map<String, List<Filter>> questionnairesToQuestions,
        final Session session)
    {
        if (filters == null) {
            return;
        }

        for (Filter filter : filters) {
            if (SUBJECT_IDENTIFIER.equals(filter.name) || CREATED_DATE_IDENTIFIER.equals(filter.name)) {
                // For special node filters, all we need to do is record the source name in the filter
                filter.source = "n";
                continue;
            }

            String questionnaire = "";
            if (QUESTIONNAIRE_IDENTIFIER.equals(filter.name)) {
                // When filtering explicitly by questionnaire, we already know the questionnaire for the filter
                questionnaire = filter.value;
            } else {
                // Otherwise, we look it up from the question
                questionnaire = getQuestionnaire(filter.name, session);
            }

            if (StringUtils.isNotBlank(questionnaire)) {
                // If this is the first time we encounter a questionnaire, add a new source for it
                questionnairesToFormSource.computeIfAbsent(questionnaire,
                    k -> "f" + (questionnairesToFormSource.size() + 1));

                if (QUESTIONNAIRE_IDENTIFIER.equals(filter.name)) {
                    if (SUBJECT_IDENTIFIER.equals(nodeType)) {
                        // The source for a questionnaire filter is one of the forms relating to the subject
                        filter.source = questionnairesToFormSource.get(questionnaire);
                    } else {
                        // The source for a questionnaire filter is the form node itself
                        filter.source = "n";
                    }
                    // Not a question, no need to update the list of questions
                    continue;
                }

                List<Filter> questions =
                    questionnairesToQuestions.computeIfAbsent(questionnaire, k -> new ArrayList<>());
                questions.add(filter);

                // Filters count for this questionnaire
                String fcount = Integer.toString(questions.size());
                // Update the source name in the filter, e.g. "childf1_1"
                filter.source = filterType.sourcePrefix + questionnairesToFormSource.get(questionnaire) + "_" + fcount;
                // Update the source node type in the filter, e.g. "cards:BooleanAnswer"
                filter.nodeType = getAnswerNodeType(filter, session);
            }
        }
    }

    private String getAnswerNodeType(final Filter filter, final Session session)
    {
        String valueType = filter.type;
        if (StringUtils.isBlank(valueType)) {
            try {
                valueType = session.getNodeByIdentifier(filter.name).getProperty("dataType").getString();
            } catch (RepositoryException e) {
                LOGGER.warn("{}", e.getMessage(), e);
            }
        }
        return StringUtils.isBlank(valueType) ? "cards:Answer"
            : "cards:" + valueType.substring(0, 1).toUpperCase(Locale.ROOT) + valueType.substring(1)
                + "Answer";
    }

    /**
     * Computes the joins needed for the filters, when the targeted node type is {@code cards:Form}. This means that for
     * each question a new {@code cards:Answer} source to the sources on the condition that it belongs to the targeted
     * node.
     *
     * @param questionnairesToFormSource maps from a questionnaire's UUID to a form source identifier
     * @param questionnairesToFilters maps from a questionnaire's UUID to a list of answer filters that belong to it
     * @return the query fragment listing all sub-sources except the main node type itself (the "join ..." part); empty
     *         if there are no filters for descendant nodes
     */
    private String createFormJoins(Map<String, String> questionnairesToFormSource,
        Map<String, List<Filter>> questionnairesToFilters)
    {
        StringBuilder joins = new StringBuilder();
        for (String questionnaire : questionnairesToFormSource.keySet()) {
            final List<Filter> filtersInQuestionnaire =
                questionnairesToFilters.getOrDefault(questionnaire, Collections.emptyList());
            for (Filter filter : filtersInQuestionnaire) {
                final String answerSource = filter.source;
                joins.append(
                    String.format(
                        " inner join [%s] as %s on %s.form = n.[jcr:uuid]",
                        filter.nodeType,
                        answerSource,
                        answerSource));
            }
        }

        return joins.toString();
    }

    /**
     * Computes the joins needed for the filters, when the targeted node type is {@code cards:Subject}. This means that
     * for each questionnaire involved in the filters a new {@code cards:Form} is added to the sources on the condition
     * that the targeted subject is one of its {@code relatedSubjects}, and for each question a new {@code cards:Answer}
     * source to the sources on the condition that it belongs to the correct form source.
     *
     * @param questionnairesToFormSource maps from a questionnaire's UUID to a form source identifier
     * @param questionnairesToFilters maps from a questionnaire's UUID to a list of answer filters that belong to it
     * @return the query fragment listing all sub-sources except the main node type itself (the "join ..." part); empty
     *         if there are no filters for descendant nodes
     */
    private String createSubjectJoins(final Map<String, String> questionnairesToFormSource,
        final Map<String, List<Filter>> questionnairesToFilters)
    {
        StringBuilder joins = new StringBuilder();

        for (String questionnaire : questionnairesToFormSource.keySet()) {
            final String formSource = questionnairesToFormSource.get(questionnaire);
            joins.append(
                String.format(
                    " inner join [cards:Form] as %s on n.[jcr:uuid] = %s.relatedSubjects",
                    formSource,
                    formSource));

            final List<Filter> filters =
                questionnairesToFilters.getOrDefault(questionnaire, Collections.emptyList());
            for (Filter filter : filters) {
                final String answerSource = filter.source;
                joins.append(
                    String.format(
                        " inner join [%s] as %s on %s.[jcr:uuid] = %s.form",
                        filter.nodeType,
                        answerSource,
                        formSource,
                        answerSource));
            }
        }

        return joins.toString();
    }

    /**
     * Generates a date comparison query that takes into account correct day boundaries. In the content repository dates
     * are stored as date-time values, using a fixed timezone, so ignoring the time part and only selecting dates that
     * would match the timezone from the client must be done by explicitly comparing against custom day start and day
     * end times.
     *
     * @param queryProperty a query property to compare in the format {@code n.'jcr:created'} or {@code child1_2.value}
     * @param operator the operator to use, one of {@code = <> < > <= or >=}
     * @param valueToCompare a date string to compare against, including a timezone offset, in the format
     *            {@code 2020-12-31T00:00-04:00}
     * @return a query fragment that imposes the correct conditions on the property, for example
     *         {@code (n.'jcr:created'>='2020-12-31T00:00:00-04:00' and n.'jcr:created'<'2021-01-01T00:00-04:00')}
     */
    private String generateDateCompareQuery(final String queryProperty, final String operator,
        final String valueToCompare)
    {
        //
        // thisDay = start of day in a custom timezone
        // nextDay = thisDay + 24h
        // IF (=) THEN CHECK (>= thisDay AND < nextDay)
        // IF (<>) THEN CHECK (< thisDay OR >= nextDay)
        // IF (<) THEN CHECK (< thisDay)
        // IF (>) THEN CHECK (>= nextDay)
        // IF (<=) THEN CHECK (< nextDay)
        // IF (>=) THEN CHECK (>= thisDay)
        //
        final ZonedDateTime thisDay = ZonedDateTime.parse(valueToCompare);
        final ZonedDateTime nextDay = thisDay.plusDays(1);
        final String nextDayStr = nextDay.toString();
        String compareQuery;
        switch (operator) {
            case "=":
                compareQuery = String.format("(%s>='%s' and %s<'%s')",
                    queryProperty,
                    valueToCompare,
                    queryProperty,
                    nextDayStr);
                break;
            case "<>":
                compareQuery = String.format("(%s<'%s' or %s>='%s')",
                    queryProperty,
                    valueToCompare,
                    queryProperty,
                    nextDayStr);
                break;
            case "<":
                compareQuery = String.format("(%s<'%s')",
                    queryProperty,
                    valueToCompare);
                break;
            case ">":
                compareQuery = String.format("(%s>='%s')",
                    queryProperty,
                    nextDayStr);
                break;
            case "<=":
                compareQuery = String.format("(%s<'%s')",
                    queryProperty,
                    nextDayStr);
                break;
            case ">=":
                compareQuery = String.format("(%s>='%s')",
                    queryProperty,
                    valueToCompare);
                break;
            default:
                compareQuery = null;
                break;
        }
        return compareQuery;
    }

    /**
     * Parse out filter data into a series of JCR_SQL2 conditionals.
     *
     * @param nodeType the targeted node type, e.g. {@code cards:Form} or {@code cards:Subject}
     * @param filters the list of filters
     */
    private String getQueryConditions(final String nodeType, final Map<FilterType, List<Filter>> filters)
    {
        StringBuilder conditions = new StringBuilder();

        for (Map.Entry<FilterType, List<Filter>> filtersOfType : filters.entrySet()) {
            for (Filter filter : filtersOfType.getValue()) {
                // Add special conditions for the target subject and the created date
                // When the targeted node type is subjects, the subject filter applies to the target node itself
                // When the targeted node type is forms, the subject filter applies to the form's relatedSubjects
                // property
                conditions.append(
                    addSpecialCondition(filter, nodeType.equals(SUBJECT_IDENTIFIER) ? "jcr:uuid" : "relatedSubjects"));

                // Add answer conditions
                conditions.append(addSingleCondition(filter));
            }
        }
        return conditions.toString();
    }

    /**
     * Converts a single special filter into a query condition, including the starting " and ". A special filter is one
     * that applies to a unique node property, not to an answer value, for example the related subject, the answered
     * questionnaire, or the node's creation date.
     *
     * @param filter a filter object
     * @param subjectProperty the property that references the subject, either {@code jcr:uuid} if the targeted node is
     *            actually a subject, or {@code reltedSubjects} if the targeted node is forms
     * @return a query condition, for example {@code and n.'questionnaire'='e6b97318-f464-4bb2-82e8-b13df62d33f3'}
     */
    private String addSpecialCondition(final Filter filter, final String subjectProperty)
    {
        StringBuilder filterdata = new StringBuilder();
        switch (filter.name) {
            case SUBJECT_IDENTIFIER:
                filterdata.append(
                    String.format(" and %s.'%s'%s'%s'",
                        filter.source,
                        subjectProperty,
                        this.sanitizeComparator(filter.comparator),
                        this.sanitizeValue(filter.value)));
                break;
            case QUESTIONNAIRE_IDENTIFIER:
                filterdata.append(
                    String.format(" and %s.'questionnaire'%s'%s'",
                        filter.source,
                        this.sanitizeComparator(filter.comparator),
                        this.sanitizeValue(filter.value)));
                break;
            case CREATED_DATE_IDENTIFIER:
                filterdata.append(" and ");
                filterdata.append(
                    generateDateCompareQuery(
                        "n.'jcr:created'",
                        this.sanitizeComparator(filter.comparator),
                        this.sanitizeValue(filter.value)));
                break;
            default:
                break;
        }
        return filterdata.toString();
    }

    /**
     * Converts a single value filter into a query condition, including the starting " and ", referencing the correct
     * question, and comparing against the filter value.
     *
     * @param filter a filter object
     * @return a query condition, for example {@code  and notemptyf1_4.'question'='b5a6163e-db5a-4deb-822e-5dbe57031627'
     *          and notemptyf1_4.'value' IS NOT NULL}
     */
    private String addSingleCondition(final Filter filter)
    {
        StringBuilder condition = new StringBuilder();

        if (SUBJECT_IDENTIFIER.equals(filter.name)
            || QUESTIONNAIRE_IDENTIFIER.equals(filter.name)
            || CREATED_DATE_IDENTIFIER.equals(filter.name)) {
            return "";
        }

        // Condition 1: the question uuid must match
        condition.append(
            String.format(
                " and %s.'question'='%s'",
                filter.source,
                this.sanitizeValue(filter.name)));

        // Condition 2: the value must match
        if ("contains".equals(filter.comparator)) {
            condition.append(
                String.format(
                    " and contains(%s.'value', '*%s*')",
                    filter.source,
                    this.sanitizeValue(filter.value)));
        } else if ("notes contain".equals(filter.comparator)) {
            condition.append(
                String.format(
                    " and contains(%s.'note', '*%s*')",
                    filter.source,
                    this.sanitizeValue(filter.value)));
        } else {
            condition.append(
                String.format(
                    " and %s.'value'%s" + (("date".equals(filter.type))
                        ? ("cast('%sT00:00:00.000"
                            + DateUtils.getTimezoneForDateString(this.sanitizeValue(filter.value))
                            + "' as date)")
                        : ("boolean".equals(filter.type)) ? "%s"
                            : StringUtils.isNotBlank(filter.value) ? "'%s'" : ""),
                    filter.source,
                    this.sanitizeComparator(filter.comparator),
                    this.sanitizeValue(filter.value)));
        }
        return condition.toString();
    }

    /**
     * Sanitize a field name or value for an input query by escaping special query characters.
     *
     * @param input the value to sanitize
     * @return a sanitized version of the input
     */
    private String sanitizeValue(String input)
    {
        return StringUtils.isEmpty(input) ? "" : input.replaceAll("['\\\\]", "\\\\$0");
    }

    /**
     * Sanitize a comparator for an input query by only accepting a predefined set of comparators, using {@code =}
     * instead of unknown comparators.
     *
     * @param comparator the comparator to sanitize
     * @return an accepted comparator, may be {@code =} if the specified comparator is not supported
     */
    private String sanitizeComparator(String comparator)
    {
        if (!COMPARATORS.contains(comparator)) {
            // Invalid comparator: return '='
            return "=";
        }
        return comparator;
    }

    /**
     * Write metadata about the request and response. This includes the number of returned and total matching nodes, and
     * copying some request parameters.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param request the current request
     * @param limits an array of values defining the range of the result: limits[0] is the 0-based offset, i.e. how many
     *            results were skipped; limits[1] is the requested limit, the maximum number of results to return;
     *            limits[2] is the number of results actually returned, equal to or less than limits[1]; limits[3] is an
     *            approximate number of total items that match the query
     */
    private void writeSummary(final JsonGenerator jsonGen, final SlingHttpServletRequest request, final long[] limits)
    {
        final boolean totalIsApproximate = limits[4] == 1;
        jsonGen.write("req", request.getParameter("req"));
        jsonGen.write("offset", limits[0]);
        jsonGen.write("limit", limits[1]);
        jsonGen.write("returnedrows", limits[2]);
        jsonGen.write("totalrows", limits[3]);
        jsonGen.write("totalIsApproximate", totalIsApproximate);
    }

    /**
     * Serialize the query results into the response JSON. Since JCR queries don't support an easy way to compute the
     * total number of matches, the query result may contain more resources than actually requested. This method also
     * accepts a limit, and only at most that many resources will actually be included in the response.
     *
     * @param jsonGen the JSON generator where the results should be serialized
     * @param query the query to execute
     * @param resultOffset how many resources from the query results were skipped
     * @param resultLimit how many resources from the query results to serialize, may be 0 if we only want a count of
     *            the resources
     * @param request the current request
     * @return an array of values defining the range of the result: limits[0] is the 0-based offset, i.e. how many
     *         results were skipped; limits[1] is the requested limit, the maximum number of results to return;
     *         limits[2] is the number of results actually returned, equal to or less than limits[1]; limits[3] is an
     *         approximate number of total items that match the query
     */
    private long[] writeResources(final JsonGenerator jsonGen, final Query query,
        final long resultOffset, final long resultLimit, final SlingHttpServletRequest request)
    {
        // Problem 1: Currently Oak does not support DISTINCT, so we must manually ensure uniqueness of the results.
        // Problem 2: Currently Oak does not support giving a total number of matches, so we must gauge it.
        // - Request results in batches of size (limit * QUERY_SIZE_MULTIPLIER).
        // - Deduplicate the results in a batch
        // - Keep a count of how many unique results were skipped so far, until the requested "offset" items are skipped
        // - After skipping "offset" items, output at most "limit" unique items to the resulting JSON
        // - After outputting "limit" items, keep counting how many new unique items are there until there are no more
        // results or encounter (limit + QUERY_SIZE_MULTIPLIER + 1) unique items (the termination condition)
        // - If all the items in the current batch have been processed without reaching the termination condition,
        // request the next batch
        final long[] counts = new long[] {
            // The requested offset
            resultOffset,
            // The requested number of items
            resultLimit,
            // The returned number of items
            0,
            // The total number of items matching the query, up to (QUERY_SIZE_MULTIPLIER * resultLimit + 1);
            0,
            // There are probably more results than seen
            0
        };
        // Which unique items have been seen so far in the query results
        final Set<String> seenResources = new HashSet<>();

        // Items in a query batch
        long batchSize = (QUERY_SIZE_MULTIPLIER * resultLimit);
        // Current batch start
        long batchStart = 0;
        // Termination condition limit: when to stop looking for new unique results
        long totalLimit = (((long) Math.ceil(((double) resultOffset) / ((double) batchSize))) + 1) * batchSize + 1;
        // How many more items to include in the output
        long limitCounter = resultLimit < 0 ? 0 : resultLimit;

        // How many results were returned by the query
        long itemsInBatch = 0;

        query.setLimit(batchSize + 1);
        do {
            query.setOffset(batchStart);

            // Execute the query
            try {
                final QueryResult filterResult = query.execute();
                final Iterator<Resource> results =
                    new ResourceIterator(request.getResourceResolver(), filterResult.getNodes());

                itemsInBatch = 0;
                while (results.hasNext()) {
                    ++itemsInBatch;
                    Resource n = results.next();
                    // If this resource was already seen, ignore it
                    if (!seenResources.contains(n.getPath())) {
                        seenResources.add(n.getPath());
                        // If we've passed the "offset" mark, and we didn't output "limit" items yet, include the
                        // resource in the output
                        if (seenResources.size() > resultOffset && limitCounter > 0) {
                            jsonGen.write(n.adaptTo(JsonObject.class));
                            --limitCounter;
                            ++counts[2];
                        }
                        ++counts[3];
                    }
                }
                batchStart += batchSize;
            } catch (RepositoryException e) {
                //
            }
        } while (counts[3] < totalLimit && itemsInBatch > batchSize);

        if (itemsInBatch > batchSize) {
            counts[4] = 1;
            // We included the "and more" extra result in the count, remove 1 to get back to a round number
            counts[3] = totalLimit - 1;
        }
        return counts;
    }

    /**
     * Convert a request parameter, which may be missing or invalid, into a proper long, with fallback to a default
     * value.
     *
     * @param stringValue the string to convert, expected to be a proper number, but may be {@code null} or not a proper
     *            number
     * @param defaultValue the default value to use if the input string cannot be converted to a number
     * @return a number, either the parsed input, if valid, or the default value
     */
    private long getLongValueOrDefault(final String stringValue, final long defaultValue)
    {
        long value = defaultValue;
        try {
            value = Long.parseLong(stringValue);
        } catch (NumberFormatException exception) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Retrieves the UUID of the questionnaire that the given question belongs to.
     *
     * @param questionUuid the UUID of a question
     * @param session the current JCR session
     * @return an UUID, if the input UUID does correspond to a valid, accessible question that belongs to a
     *         questionnaire, or the empty string otherwise
     */
    private String getQuestionnaire(String questionUuid, Session session)
    {
        if (session == null) {
            LOGGER.warn("Could not match questionnaire UUID {}: session not found.", questionUuid);
            return "";
        }

        try {
            Node parent = session.getNodeByIdentifier(questionUuid);
            while (parent.getParent() != null) {
                parent = parent.getParent();
                if (parent.isNodeType(QUESTIONNAIRE_IDENTIFIER)) {
                    return parent.getProperty("jcr:uuid").getString();
                }
            }
        } catch (ItemNotFoundException e) {
            LOGGER.debug("Questionnaire UUID {} is inaccessible", questionUuid, e);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to find questionnaire UUID {}", questionUuid, e);
        }
        return "";
    }
}
