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
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Property;
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

        String finalquery = createQuery(request);

        Iterator<Resource> results;
        //Using a QueryManager doesn't always work, but it is faster
        Session session = null;
        try {
            //Get a QueryManager object
            ResourceResolver resolver = request.getResourceResolver();
            session = resolver.adaptTo(Session.class);
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

    @SuppressWarnings({"checkstyle:ExecutableStatementCount"})
    private String createQuery(final SlingHttpServletRequest request)
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
        final StringBuilder query =
            // We select all child nodes of the homepage having the right type
            new StringBuilder("select n.* from [").append(nodeType).append("] as n");

        // If child nodes are required for this query, also grab them
        final String[] filternames = request.getParameterValues("filternames");
        query.append(createJoins(
            request.getParameter("joinchildren"),
            request.getParameterValues("filternames"),
            request.getParameterValues("filterempty"),
            request.getParameterValues("filternotempty")
            ));

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
        // TODO, if more request options are required: convert includeAllStatus into a request mode
        // backed by an an enum, map or other such collection.
        final boolean includeAllStatus = Boolean.parseBoolean(request.getParameter("includeallstatus"));
        String fieldcomparator = request.getParameter("fieldcomparator");
        if (StringUtils.isNotBlank(fieldname)) {
            if (StringUtils.isBlank(fieldcomparator)) {
                // Default comparator is =
                fieldcomparator = "=";
            }
            query.append(
                String.format(
                    " and n.'%s'%s'%s'",
                    this.sanitizeField(fieldname),
                    this.sanitizeComparator(fieldcomparator),
                    this.sanitizeField(fieldvalue)
                )
            );
        }
        // Only display `INCOMPLETE` forms if we are explicitly checking the status of forms,
        // or if the user requested forms with all statuses
        if (!("statusFlags".equals(fieldname) || includeAllStatus)) {
            query.append(" and not n.'statusFlags'='INCOMPLETE'");
        }

        // Condition on child nodes. See parseFilter for details.
        final String[] filtervalues = request.getParameterValues("filtervalues");
        final String[] filtertypes = request.getParameterValues("filtertypes");
        final String[] filtercomparators = request.getParameterValues("filtercomparators");
        final String[] filterempty = request.getParameterValues("filterempty");
        final String[] filternotempty = request.getParameterValues("filternotempty");
        final boolean sortDescending = Boolean.valueOf(request.getParameter("descending"));
        query.append(parseFilter(filternames, filtervalues, filtertypes, filtercomparators));
        query.append(parseExistence(filterempty, filternotempty));
        query.append(" order by n.'jcr:created'").append(sortDescending ? " DESC" : " ASC");
        String finalquery = query.toString();
        LOGGER.debug("Computed final query: {}", finalquery);

        return finalquery;
    }

    /**
     * Parse out filter data into a series of JCR_SQL2 joins. This should be used in conjunction with parseFilter later
     * on.
     *
     * @param nodetype node types to join
     * @param filternames user input field names
     * @param empties user input fields to assert emptiness of
     * @param notempties user input fields to assert non-emptiness of
     * @return the input fields and assertions as a series of sql joins
     */
    private String createJoins(final String nodetype, final String[] filternames, final String[] empties,
        final String[] notempties)
    {
        if (StringUtils.isBlank(nodetype)) {
            // Unknown join type: do not parse
            return "";
        }

        String sanitizednodetype = nodetype.replaceAll("[\\\\\\]]", "\\\\$0");
        StringBuilder joindata = new StringBuilder();

        // Parse out the fields to later impose conditions on
        joindata.append(createSingleJoin(filternames, "child", sanitizednodetype));

        // Parse out the fields to assert the nonexistence of
        joindata.append(createSingleJoin(empties, "empty", sanitizednodetype));

        // Parse out the fields to assert the existence of
        joindata.append(createSingleJoin(notempties, "notempty", sanitizednodetype));

        return joindata.toString();
    }

    /**
     * Parse out filter data into a series of JCR_SQL2 joins.
     *
     * @param joins node types to join
     * @param childprefix prefix to give the child, to which a number will be appended to
     * @param nodetype Node type to join on
     * @return the input field as a series of sql joins
     */
    private String createSingleJoin(final String[] joins, final String childprefix, final String nodetype)
    {
        // Don't attempt to append joins if we're not given anything
        if (joins == null) {
            return "";
        }

        // Append an inner join for each pipe-delimited identifier in joins
        StringBuilder joindata = new StringBuilder();
        for (int i = 0; i < joins.length; i++) {
            // Skip this join if it is on cards:Subject, which does not require a child inner join
            if (SUBJECT_IDENTIFIER.equals(joins[i])
                || QUESTIONNAIRE_IDENTIFIER.equals(joins[i])
                || CREATED_DATE_IDENTIFIER.equals(joins[i])) {
                continue;
            }

            joindata.append(
                String.format(
                    " inner join [%s] as %s%d on isdescendantnode(%s%d, n)",
                    nodetype,
                    childprefix,
                    i,
                    childprefix,
                    i
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
    @SuppressWarnings({"checkstyle:CyclomaticComplexity", "checkstyle:NPathComplexity"})
    private String parseFilter(final String[] fields, final String[] values,
        final String[] types, final String[] comparator) throws IllegalArgumentException
    {
        // If we don't have either names or values, we should fail to filter
        if (fields == null || values == null) {
            return "";
        }

        // Parse out multiple fields, split by pipes (|)
        if (fields.length != values.length) {
            throw new IllegalArgumentException("fieldname and fieldvalue must have the same number of values");
        }

        // Also parse out multiple comparators
        String[] comparators;
        if (comparator == null) {
            // Use = as the default
            comparators = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                comparators[i] = "=";
            }
        } else {
            comparators = comparator;
            if (comparators.length != values.length) {
                throw new IllegalArgumentException("must have the same number of comparators as fields,");
            }
        }

        // Build the filter conditionals by imposing conditions on the inner joined cards:Answer children
        StringBuilder filterdata = new StringBuilder();
        // TODO: Double check the sanitization on the comparator
        for (int i = 0; i < fields.length; i++) {
            // If the question is cards:Subject, we match on the parent rather than the child
            if (SUBJECT_IDENTIFIER.equals(fields[i])) {
                filterdata.append(
                    String.format(" and n.'subject'%s'%s'",
                        this.sanitizeComparator(comparators[i]),
                        this.sanitizeField(values[i])
                    )
                );
            } else if (QUESTIONNAIRE_IDENTIFIER.equals(fields[i])) {
                filterdata.append(
                    String.format(" and n.'questionnaire'%s'%s'",
                        this.sanitizeComparator(comparators[i]),
                        this.sanitizeField(values[i])
                    )
                );
            } else if (CREATED_DATE_IDENTIFIER.equals(fields[i])) {
                filterdata.append(" and ");
                filterdata.append(
                    generateDateCompareQuery(
                        "n.'jcr:created'",
                        this.sanitizeField(values[i]),
                        this.sanitizeComparator(comparators[i])
                    )
                );
            } else {
                // Condition 1: the question uuid must match one of the given (comma delimited)
                String[] possibleQuestions = fields[i].split(",");
                filterdata.append(" and (");
                for (int j = 0; j < possibleQuestions.length; j++) {
                    filterdata.append(
                        String.format(
                            " child%d.'question'='%s'",
                            i,
                            this.sanitizeField(possibleQuestions[j])
                        )
                    );
                    // Add an 'or' if there are more possible conditions
                    if (j + 1 != possibleQuestions.length) {
                        filterdata.append(" or");
                    }
                }
                // Condition 2: the value must exactly match
                if (comparators[i].equals("notes contain")) {
                    filterdata.append(
                        String.format(
                            ") and contains(child%d.'note', '*%s*')",
                            i,
                            this.sanitizeField(values[i])
                        )
                    );
                } else {
                    filterdata.append(
                        String.format(
                            ") and child%d.'value'%s" + (("date".equals(types[i]))
                                ? ("cast('%sT00:00:00.000"
                                + new SimpleDateFormat("XXX").format(new Date()) + "' as date)")
                                : ("boolean".equals(types[i])) ? "%s" : "'%s'"),
                            i,
                            this.sanitizeComparator(comparators[i]),
                            this.sanitizeField(values[i])
                        )
                    );
                }
            }
        }
        return filterdata.toString();
    }

    /**
     * Parse out empty & not empty fields into a series of JCR_SQL2 conditionals.
     *
     * @param empties user input field names to assert the nonexistance of content for
     * @param notempties user input field names to assert the existance of content for
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
     * @param comparison unary comparitor to assert
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
            String sanitizedFieldName = sanitizeField(fieldnames[i]);
            // cards:Subject is handled differently, since it is on the Form itself
            if (fieldnames[i].equals(SUBJECT_IDENTIFIER)) {
                joindata.append(
                    String.format(
                        " and n.'subject'%s",
                        comparison
                    )
                );
            } else if (fieldnames[i].equals(QUESTIONNAIRE_IDENTIFIER)) {
                joindata.append(
                    String.format(
                        " and n.'questionnaire'%s",
                        comparison
                    )
                );
            } else {
                final String[] possibleQuestions = fieldnames[i].split(",");
                joindata.append(" and (");
                for (int j = 0; j < possibleQuestions.length; j++) {
                    joindata.append(
                        String.format(
                            " %s%d.'question'='%s' and %s%d.'value'%s",
                            childprefix,
                            i,
                            this.sanitizeField(possibleQuestions[j]),
                            childprefix,
                            i,
                            comparison
                        )
                    );
                    // Add an 'or' if there are more possible conditions
                    if (j + 1 != possibleQuestions.length) {
                        joindata.append(" or");
                    }
                }
                joindata.append(")");
            }
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
}
