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
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
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
 * <li><tt>offset</tt>: a 0-based number representing how many resources to skip; 0 by default</li>
 * <li><tt>limit</tt>: a number representing how many resources to include at most in the result; 10 by default</li>
 * <li><tt>filter</tt>: a (lucene-like) search term, such as {@code germline}, {@code cancer OR tumor},
 * {@code (*blastoma OR *noma OR tumor*) recurrent}; no filter set by default</li>
 * <li><tt>includeallstatus</tt>: if true, incomplete forms will be included. Otherwise, they will be excluded unless
 * searched for directly using `fieldname="statusFlags"`
 * </ul>
 *
 * @version $Id$
 */
@SuppressWarnings({"checkstyle:MultipleStringLiterals"})
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/QuestionnairesHomepage", "cards/FormsHomepage", "cards/SubjectsHomepage",
        "cards/SubjectTypesHomepage" },
    selectors = { "paginate" })
public class PaginationServlet extends SlingSafeMethodsServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DataImportServlet.class);

    private static final long serialVersionUID = -6068156942302219324L;

    private static final int QUERY_SIZE_MULTIPLIER = 10;

    // Allowed JCR-SQL2 operators (from https://docs.adobe.com/docs/en/spec/jcr/2.0/6_Query.html#6.7.17%20Operator)
    private static final List<String> COMPARATORS =
        Arrays.asList("=", "<>", "<", "<=", ">", ">=", "LIKE", "notes contain");

    private static final String SUBJECT_IDENTIFIER = "cards:Subject";
    private static final String QUESTIONNAIRE_IDENTIFIER = "cards:Questionnaire";
    private static final String CREATED_DATE_IDENTIFIER = "cards:CreatedDate";

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, IllegalArgumentException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        final long limit = getLongValueOrDefault(request.getParameter("limit"), 10);
        final long offset = getLongValueOrDefault(request.getParameter("offset"), 0);

        ResourceResolver resolver = request.getResourceResolver();
        Session session = resolver.adaptTo(Session.class);
        String finalquery = createQuery(request, session);

        Iterator<Resource> results;
        //Using a QueryManager doesn't always work, but it is faster
        try {
            //Get a QueryManager object
            Workspace workspace = session.getWorkspace();
            QueryManager queryManager = workspace.getQueryManager();

            //Create the Query object
            Query filterQuery = queryManager.createQuery(finalquery, "JCR-SQL2");

            //Set the limit and offset here to improve query performance
            filterQuery.setLimit((QUERY_SIZE_MULTIPLIER * limit) + 1);
            filterQuery.setOffset(offset);

            //Execute the query
            QueryResult filterResult = filterQuery.execute();
            results = new ResourceIterator(request.getResourceResolver(), filterResult.getNodes());
        } catch (Exception e) {
            return;
        }

        // The writer doesn't need to be explicitly closed since the auto-closed jsonGen will also close the writer
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            long[] limits = writeResources(jsonGen, results, offset, limit);
            writeSummary(jsonGen, request, limits);
            jsonGen.writeEnd().flush();
        }
    }

    @SuppressWarnings({"checkstyle:ExecutableStatementCount", "checkstyle:NPathComplexity",
        "checkstyle:CyclomaticComplexity"})
    private String createQuery(final SlingHttpServletRequest request, Session session)
    {
        // If we want this query to be fast, we need to use the exact nodetype requested.
        final Node node = request.getResource().adaptTo(Node.class);
        String nodeType = "";
        try {
            Property type = node.getProperty("childNodeType");
            nodeType = type.getString();
        } catch (Exception e) {
            nodeType = request.getResource().getResourceType().replace('/', ':').replaceFirst("sHomepage$", "");
        }
        // We select all child nodes of the homepage having the right type
        final StringBuilder query = new StringBuilder("select n.* from [").append(nodeType).append("] as n");

        // If child nodes are required for this query, also grab them
        final String[] filternames = request.getParameterValues("filternames");
        final String[] filterempty = request.getParameterValues("filterempty");
        final String[] filternotempty = request.getParameterValues("filternotempty");
        final String joinNodetype = request.getParameter("joinchildren");

        // The map that stores questionnaires uuids with prefixes in array and maps it to the
        // corresponding group of questions uuids with their prefixes (stored in a map)
        // To be used later on the filter query construction
        Map<String, String> questionnairesToPrefix = new HashMap<>();
        Map<String, List<String>> questionnairesToFilters = new HashMap<>();
        Map<String, String> filtersToPrefix = new HashMap<>();

        // Add joints
        if (StringUtils.isNotBlank(joinNodetype)) {
            String sanitizednodetype = joinNodetype.replaceAll("[\\\\\\]]", "\\\\$0");
            if (nodeType.equals(SUBJECT_IDENTIFIER)) {
                // resolve all questions to questionnaires and group them by questionnaires
                getQuestionnairesMaps(questionnairesToPrefix, questionnairesToFilters, filtersToPrefix, filternames,
                    "child", session);
                getQuestionnairesMaps(questionnairesToPrefix, questionnairesToFilters, null, filterempty,
                    "empty", session);
                getQuestionnairesMaps(questionnairesToPrefix, questionnairesToFilters, null, filternotempty,
                    "notempty", session);

                // make joints per questionnaire
                query.append(createSubjectJoins(sanitizednodetype, questionnairesToPrefix, questionnairesToFilters,
                    filtersToPrefix));
            } else {
                query.append(createJoins(sanitizednodetype, filternames, filterempty, filternotempty));
            }
        }

        // Check only for the descendants of the requested homepage
        query.append(" where isdescendantnode(n, '" + request.getResource().getPath() + "')");

        // Full text search; \ and ' must be escaped
        final String filter = request.getParameter("filter");
        if (StringUtils.isNotBlank(filter)) {
            query.append(" and contains(n.*, '" + this.sanitizeField(filter) + "')");
        }

        // Exact condition on parent node; \ and ' must be escaped. The value must be wrapped in 's
        final String fieldname = request.getParameter("fieldname");
        final String fieldvalue = request.getParameter("fieldvalue");
        String fieldcomparator = this.sanitizeComparator(request.getParameter("fieldcomparator"));
        if (StringUtils.isNotBlank(fieldname)) {
            query.append(
                String.format(
                    " and n.'%s'%s'%s'",
                    this.sanitizeField(fieldname),
                    fieldcomparator,
                    this.sanitizeField(fieldvalue)
                )
            );
        }

        // TODO, if more request options are required: convert includeAllStatus into a request mode
        // backed by an an enum, map or other such collection.
        final boolean includeAllStatus = Boolean.parseBoolean(request.getParameter("includeallstatus"));
        // Only display `INCOMPLETE` forms if we are explicitly checking the status of forms,
        // or if the user requested forms with all statuses
        if (!("statusFlags".equals(fieldname) || includeAllStatus || nodeType.equals(SUBJECT_IDENTIFIER))) {
            query.append(" and not n.'statusFlags'='INCOMPLETE'");
        }

        // Condition on child nodes. See parseFilter for details.
        final String[] filtervalues = request.getParameterValues("filtervalues");
        final String[] filtertypes = request.getParameterValues("filtertypes");
        final String[] filtercomparators = request.getParameterValues("filtercomparators");
        final boolean sortDescending = Boolean.valueOf(request.getParameter("descending"));
        query.append(
            parseFilter(
                filternames,
                filtervalues,
                filtertypes,
                filtercomparators,
                nodeType,
                questionnairesToPrefix,
                questionnairesToFilters,
                filtersToPrefix
            )
        );
        // For subject query case we parse existence together with other filters in previous step
        if (!nodeType.equals(SUBJECT_IDENTIFIER)) {
            query.append(parseExistence(filterempty, filternotempty));
        }
        query.append(" order by n.'jcr:created'").append(sortDescending ? " DESC" : " ASC");
        String finalquery = query.toString();
        LOGGER.debug("Computed final query: {}", finalquery);

        return finalquery;
    }

    private void getQuestionnairesMaps(Map<String, String> questionnairesToPrefix,
            Map<String, List<String>> questionnairesToFilters, Map<String, String> filtersToPrefix,
            final String[] uuids, final String prefix, Session session)
    {
        if (uuids == null) {
            return;
        }

        for (int i = 0; i < uuids.length; i++) {
            // Skip this join if it is on nodes that do not require a child inner join
            if (SUBJECT_IDENTIFIER.equals(uuids[i]) || CREATED_DATE_IDENTIFIER.equals(uuids[i])) {
                continue;
            }

            String nodeUUID = "";
            // if we need to filter subjects by the any questionnaire filter - we need to store it for a joint
            if (QUESTIONNAIRE_IDENTIFIER.equals(uuids[i])) {
                nodeUUID = uuids[i];
            } else {
                nodeUUID = getQuestionnaire(uuids[i], session);
            }

            if (StringUtils.isNotBlank(nodeUUID)) {

                if (!questionnairesToPrefix.containsKey(nodeUUID)) {
                    String qcount = Integer.toString(questionnairesToPrefix.size() + 1);
                    // <uuid> -> "f1" for joints and "and (f1.questionnaire = '5564b6da-35d3-4049..')
                    questionnairesToPrefix.put(nodeUUID, "f" + qcount);
                }

                if (!(QUESTIONNAIRE_IDENTIFIER.equals(uuids[i]))) {
                    List<String> questions = questionnairesToFilters.getOrDefault(nodeUUID, new ArrayList<String>());
                    questions.add(uuids[i]);
                    // for joints to add answers and for filters
                    questionnairesToFilters.put(nodeUUID, questions);

                    // filters count for this questionnaire
                    String fcount = Integer.toString(questions.size());
                    // <uuid> -> "childf1_1"
                    filtersToPrefix.put(uuids[i], prefix + questionnairesToPrefix.get(nodeUUID) + "_" + fcount);
                }
            }
        }
    }

    /**
     * Parse out filter data into a series of JCR_SQL2 joins. This should be used in conjunction with parseFilter later
     * on.
     *
     * @param nodeType node types to join
     * @param filternames user input field names
     * @param empties user input fields to assert emptiness of
     * @param notempties user input fields to assert non-emptiness of
     * @return the input fields and assertions as a series of sql joins
     */
    private String createJoins(final String nodeType, final String[] filternames, final String[] empties,
        final String[] notempties)
    {
        StringBuilder joindata = new StringBuilder();

        // Parse out the fields to later impose conditions on
        joindata.append(createSingleJoin(filternames, "child", nodeType, "n"));

        // Parse out the fields to assert the nonexistence of
        joindata.append(createSingleJoin(empties, "empty", nodeType, "n"));

        // Parse out the fields to assert the existence of
        joindata.append(createSingleJoin(notempties, "notempty", nodeType, "n"));

        return joindata.toString();
    }

    private String createSubjectJoins(final String nodetype, Map<String, String> questionnairesToPrefix,
            Map<String, List<String>> questionnairesToFilters, Map<String, String> filtersToPrefix)
    {
        StringBuilder joindata = new StringBuilder();

        for (String uuid : questionnairesToPrefix.keySet()) {
            String prefix = questionnairesToPrefix.get(uuid);
            joindata.append(
                String.format(
                    " inner join [cards:Form] as %s on n.[jcr:uuid] = %s.relatedSubjects",
                    prefix,
                    prefix
                ));

            List<String> questions = questionnairesToFilters.get(uuid);
            for (String quuid : questions) {
                joindata.append(
                    String.format(
                        " inner join [%s] as %s on isdescendantnode(%s, %s)",
                        nodetype,
                        filtersToPrefix.get(quuid),
                        filtersToPrefix.get(quuid),
                        prefix
                    ));
            }
        }

        return joindata.toString();
    }

    /**
     * Parse out filter data into a series of JCR_SQL2 joins.
     *
     * @param joins node types to join
     * @param childprefix prefix to give the child, to which a number will be appended to
     * @param nodetype Node type to join on
     * @param parentPrefix parent node
     * @return the input field as a series of SQL joins
     */
    private String createSingleJoin(final String[] joins, final String childprefix, final String nodetype,
        final String parentPrefix)
    {
        // Don't attempt to append joins if we're not given anything
        if (joins == null) {
            return "";
        }

        // Append an inner join for each pipe-delimited identifier in joins
        StringBuilder joindata = new StringBuilder();
        for (int i = 0; i < joins.length; i++) {
            // Skip this join if it is on nodes that do not require a child inner join
            if (SUBJECT_IDENTIFIER.equals(joins[i])
                || QUESTIONNAIRE_IDENTIFIER.equals(joins[i])
                || CREATED_DATE_IDENTIFIER.equals(joins[i])) {
                continue;
            }

            joindata.append(
                String.format(
                    " inner join [%s] as %s%d on isdescendantnode(%s%d, %s)",
                    nodetype,
                    childprefix,
                    i,
                    childprefix,
                    i,
                    parentPrefix
                )
            );
        }

        return joindata.toString();
    }

    private String generateDateCompareQuery(String jcrVariable, String thisDayStr, String operator)
    {
        /*
         * IF (=) THEN CHECK (>= day AND < nextDay)
         * IF (<>) THEN CHECK (< day OR >= nextDay)
         * IF (<) THEN CHECK (< day)
         * IF (>) THEN CHECK (>= nextDay)
         * IF (<=) THEN CHECK (< nextDay)
         * IF (>=) THEN CHECK (>= day)
         */
        final ZonedDateTime thisDay = ZonedDateTime.parse(thisDayStr);
        final ZonedDateTime nextDay = thisDay.plusDays(1);
        final String nextDayStr = nextDay.toString();
        String compareQuery;
        switch (operator) {
            case "=":
                compareQuery = String.format("(%s>='%s' and %s<'%s')",
                    jcrVariable,
                    thisDayStr,
                    jcrVariable,
                    nextDayStr
                );
                break;
            case "<>":
                compareQuery = String.format("(%s<'%s' or %s>='%s')",
                    jcrVariable,
                    thisDayStr,
                    jcrVariable,
                    nextDayStr
                );
                break;
            case "<":
                compareQuery = String.format("(%s<'%s')",
                    jcrVariable,
                    thisDayStr
                );
                break;
            case ">":
                compareQuery = String.format("(%s>='%s')",
                    jcrVariable,
                    nextDayStr
                );
                break;
            case "<=":
                compareQuery = String.format("(%s<'%s')",
                    jcrVariable,
                    nextDayStr
                );
                break;
            case ">=":
                compareQuery = String.format("(%s>='%s')",
                    jcrVariable,
                    thisDayStr
                );
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
     * @param fields user input field names
     * @param values user input field values
     * @param comparator user input comparators
     * @throws IllegalArgumentException when the number of input fields are not equal
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity", "checkstyle:NPathComplexity", "checkstyle:ParameterNumber"})
    private String parseFilter(final String[] fields, final String[] values, final String[] types,
        final String[] comparator, final String nodeType, Map<String, String> questionnairesToPrefix,
        final Map<String, List<String>> questionnairesToFilters,
        final Map<String, String> filtersToPrefix) throws IllegalArgumentException
    {
        // If we don't have either names or values, we should fail to filter
        if (fields == null || values == null) {
            return "";
        }

        if (fields.length != values.length) {
            throw new IllegalArgumentException("fieldname and fieldvalue must have the same number of values");
        }

        if (comparator != null && comparator.length != values.length) {
            throw new IllegalArgumentException("comparators and fieldvalue must have the same number of values");
        }

        String[] comparators = comparator;
        if (comparator == null) {
            // Use = as the default
            comparators = new String[fields.length];
            Arrays.fill(comparators, "=");
        }

        // Build the filter conditionals by imposing conditions on the inner joined cards:Answer children
        StringBuilder filterdata = new StringBuilder();
        // TODO: Double check the sanitation on the comparator

        if (nodeType.equals(SUBJECT_IDENTIFIER)) {
            // Add filters on subject uuid, questionnaire or date separately before other filters
            for (int i = 0; i < fields.length; i++) {
                filterdata.append(
                    addSpeacialFilter(
                        fields[i],
                        values[i],
                        comparators[i],
                        questionnairesToPrefix.get(fields[i]),
                        "jcr:uuid"
                    )
                );
            }
            // Add other question filters
            for (String questionnaire : questionnairesToFilters.keySet()) {

                // add questionnaire filter, e.g. "f1.questionnaire = '5564b6da-...-f01f20493fcc'"
                filterdata.append(
                    String.format(" and %s.'questionnaire'='%s'",
                        questionnairesToPrefix.get(questionnaire),
                        this.sanitizeField(questionnaire)
                    )
                );

                // add corresponding question filters, e.g. " and (a1_1.question='e18f7f4...e871f' and a1_1.value = 1)"
                for (String question : questionnairesToFilters.get(questionnaire)) {

                    String value = "";
                    String fcomparator = "";
                    String type = "";

                    String prefix = filtersToPrefix.get(question);
                    if (prefix.startsWith("child")) {
                        int i = Arrays.asList(fields).indexOf(question);
                        value = values[i];
                        fcomparator = comparators[i];
                        type = types[i];
                    } else if (prefix.startsWith("empty")) {
                        fcomparator = " IS NULL";
                    } else {
                        fcomparator = " IS NOT NULL";
                    }

                    filterdata.append(
                        addSingleFilter(
                            question,
                            value,
                            fcomparator,
                            type,
                            filtersToPrefix.get(question)
                        )
                    );
                }
            }
        } else {
            for (int i = 0; i < fields.length; i++) {
                filterdata.append(
                    addSpeacialFilter(
                        fields[i],
                        values[i],
                        comparators[i],
                        "n",
                        "subject"
                    )
                );
                filterdata.append(
                    addSingleFilter(
                        fields[i],
                        values[i],
                        comparators[i],
                        types[i],
                        "child" + i
                    )
                );
            }
        }
        return filterdata.toString();
    }

    private String addSpeacialFilter(final String filter, final String value, final String comparator,
        final String prefix, final String subjectSelector)
    {
        StringBuilder filterdata = new StringBuilder();
        switch (filter) {
            case SUBJECT_IDENTIFIER:
                filterdata.append(
                    String.format(" and n.'%s'%s'%s'",
                        subjectSelector,
                        this.sanitizeComparator(comparator),
                        this.sanitizeField(value)
                    )
                );
                break;
            case QUESTIONNAIRE_IDENTIFIER:
                filterdata.append(
                    String.format(" and %s.'questionnaire'%s'%s'",
                        prefix,
                        this.sanitizeComparator(comparator),
                        this.sanitizeField(value)
                    )
                );
                break;
            case CREATED_DATE_IDENTIFIER:
                filterdata.append(" and ");
                filterdata.append(
                    generateDateCompareQuery(
                        "n.'jcr:created'",
                        this.sanitizeField(value),
                        this.sanitizeComparator(comparator)
                    )
                );
                break;
            default:
                filterdata.append("");
                break;
        }
        return filterdata.toString();
    }

    private String addSingleFilter(final String filter, final String value, final String comparator, final String type,
            final String prefix)
    {
        StringBuilder filterdata = new StringBuilder();

        if (SUBJECT_IDENTIFIER.equals(filter)
            || QUESTIONNAIRE_IDENTIFIER.equals(filter)
            || CREATED_DATE_IDENTIFIER.equals(filter)) {
            return "";
        }

        // Condition 1: the question uuid must match one of the given (comma delimited)
        String[] possibleQuestions = filter.split(",");
        filterdata.append(" and (");
        for (int j = 0; j < possibleQuestions.length; j++) {
            filterdata.append(
                String.format(
                    " %s.'question'='%s'",
                    prefix,
                    this.sanitizeField(possibleQuestions[j])
                )
            );
            // Add an 'or' if there are more possible conditions
            if (j + 1 != possibleQuestions.length) {
                filterdata.append(" or");
            }
        }
        // Condition 2: the value must match
        if ("contain".equals(comparator)) {
            filterdata.append(
                String.format(
                    ") and contains(%s.'value', '*%s*')",
                    prefix,
                    this.sanitizeField(value)
                )
            );
        } else if ("notes contain".equals(comparator)) {
            filterdata.append(
                String.format(
                    ") and contains(%s.'note', '*%s*')",
                    prefix,
                    this.sanitizeField(value)
                )
            );
        } else {
            filterdata.append(
                String.format(
                    ") and %s.'value'%s" + (("date".equals(type))
                        ? ("cast('%sT00:00:00.000"
                        + new SimpleDateFormat("XXX").format(new Date()) + "' as date)")
                        : ("boolean".equals(type)) ? "%s" : "'%s'"),
                    prefix,
                    this.sanitizeComparator(comparator),
                    this.sanitizeField(value)
                )
            );
        }
        return filterdata.toString();
    }

    /**
     * Parse out empty & not empty fields into a series of JCR_SQL2 conditionals.
     *
     * @param empties user input field names to assert the nonexistence of content for
     * @param notempties user input field names to assert the existence of content for
     * @return JCR_SQL conditionals for the input
     */
    private String parseExistence(final String[] empties, final String[] notempties)
        throws IllegalArgumentException
    {
        StringBuilder joindata = new StringBuilder();
        joindata.append(parseComparison(empties, "empty", " IS NULL"));
        joindata.append(parseComparison(notempties, "notempty", " IS NOT NULL"));
        return joindata.toString();
    }

    /**
     * Parse out a field and its unary comparison into a series of JCR_SQL2 conditionals.
     *
     * @param fieldnames user input field names
     * @param childprefix prefix for the child nodes
     * @param comparison unary comparator to assert
     * @return JCR_SQL conditionals for the input
     */
    private String parseComparison(final String[] fieldnames, final String childprefix, final String comparison)
    {
        // If no comparison is entered, do nothing
        if (fieldnames == null) {
            return "";
        }

        // Build the conditionals (e.g. and child0.'question'='uuid' and child0.'value' IS NOT NULL...)
        StringBuilder joindata = new StringBuilder();
        for (int i = 0; i < fieldnames.length; i++) {
            joindata.append(
                addSpeacialFilter(
                    fieldnames[i],
                    "",
                    comparison,
                    "n",
                    "subject"
                )
            );
            joindata.append(
                addSingleFilter(
                    fieldnames[i],
                    "",
                    comparison,
                    "",
                    childprefix + i
                )
            );
        }
        return joindata.toString();
    }

    /**
     * Sanitize a field name for an input query.
     *
     * @param fieldname the field name to sanitize
     * @return a sanitized version of the input
     */
    private String sanitizeField(String fieldname)
    {
        return fieldname.replaceAll("['\\\\]", "\\\\$0");
    }

    /**
     * Sanitize a comparator for an input query.
     *
     * @param comparator the comparator to sanitize
     * @return a sanitized version of the input
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
     * @param offset the requested offset, may be the default value of {0}
     * @param limit the requested limit, may be the default value of {10}
     * @param returnedNodes the number of matching nodes included in the response, may be {@code 0} if no nodes were
     *            returned
     * @param totalMatchingNodes the total number of accessible nodes matching the request, may be {@code 0} if no nodes
     *            match the filters, or the current user cannot access the nodes
     */
    private void writeSummary(final JsonGenerator jsonGen, final SlingHttpServletRequest request, final long[] limits)
    {
        final boolean totalIsApproximate = (limits[3] > (QUERY_SIZE_MULTIPLIER * limits[1]));
        jsonGen.write("req", request.getParameter("req"));
        jsonGen.write("offset", limits[0]);
        jsonGen.write("limit", limits[1]);
        jsonGen.write("returnedrows", limits[2]);
        jsonGen.write("totalrows", totalIsApproximate
            ? ((QUERY_SIZE_MULTIPLIER * limits[1]) + limits[0]) : (limits[0] + limits[3]));
        jsonGen.write("totalIsApproximate", totalIsApproximate);
    }

    private long[] writeResources(final JsonGenerator jsonGen, final Iterator<Resource> nodes,
        final long offset, final long limit)
    {
        final long[] counts = new long[4];
        counts[0] = offset;
        counts[1] = limit;
        counts[2] = 0;
        counts[3] = 0;

        long limitCounter = limit < 0 ? 0 : limit;

        jsonGen.writeStartArray("rows");

        while (nodes.hasNext()) {
            Resource n = nodes.next();
            if (limitCounter > 0) {
                jsonGen.write(n.adaptTo(JsonObject.class));
                --limitCounter;
                ++counts[2];
            }
            ++counts[3];
        }

        jsonGen.writeEnd();

        return counts;
    }

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
                if (parent.isNodeType("cards:Questionnaire")) {
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
