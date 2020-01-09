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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;

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
 * </ul>
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "lfs/QuestionnairesHomepage", "lfs/FormsHomepage", "lfs/SubjectsHomepage" },
    selectors = { "paginate" })
public class PaginationServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -6068156942302219324L;

    // Allowed JCR-SQL2 operators (from https://docs.adobe.com/docs/en/spec/jcr/2.0/6_Query.html#6.7.17%20Operator)
    private static final List<String> COMPARATORS = Arrays.asList("=", "<>", "<", "<=", ">", ">=", "LIKE");

    @SuppressWarnings({"checkstyle:ExecutableStatementCount"})
    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, IllegalArgumentException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        final long limit = getLongValueOrDefault(request.getParameter("limit"), 10);
        final long offset = getLongValueOrDefault(request.getParameter("offset"), 0);
        final StringBuilder query =
            // We select all child nodes of the homepage, filtering out nodes that aren't ours, such as rep:policy
            new StringBuilder("select n.* from [nt:base] as n");

        // If child nodes are required for this query, also grab them
        final String filtername = request.getParameter("filternames");
        query.append(createJoins(
            request.getParameter("joinchildren"),
            request.getParameter("filternames"),
            request.getParameter("filterempty"),
            request.getParameter("filternotempty")
            ));

        // Check only for our fields
        query.append(" where ischildnode(n, '" + request.getResource().getPath()
                + "') and n.'sling:resourceSuperType' = 'lfs/Resource'");

        // Full text search; \ and ' must be escaped
        final String filter = request.getParameter("filter");
        if (StringUtils.isNotBlank(filter)) {
            query.append(" and contains(n.*, '" + this.sanitizeField(filter) + "')");
        }

        // Exact condition on parent node; \ and ' must be escaped. The value must be wrapped in 's
        final String fieldname = request.getParameter("fieldname");
        final String fieldvalue = request.getParameter("fieldvalue");
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

        // Condition on child nodes. See parseFilter for details.
        final String filtervalue = request.getParameter("filtervalues");
        final String filtercomparators = request.getParameter("filtercomparators");
        final String filterempty = request.getParameter("filterempty");
        final String filternotempty = request.getParameter("filternotempty");
        query.append(parseFilter(filtername, filtervalue, filtercomparators));
        query.append(parseExistence(filterempty, filternotempty));

        query.append(" order by n.'jcr:created'");
        final Iterator<Resource> results =
            request.getResourceResolver().findResources(query.toString(), Query.JCR_SQL2);
        // The writer doesn't need to be explicitly closed since the auto-closed jsonGen will also close the writer
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();
            long[] limits = writeNodes(jsonGen, results, offset, limit);
            writeSummary(jsonGen, request, limits);
            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Parse out filter data into a series of JCR_SQL2 joins.
     * This should be used in conjuction with parseFilter later on.
     *
     * @param nodetype node types to join, pipe delimited (|)
     * @param filternames user input field names, pipe delimited (|)
     * @param empties user input fields to assert emptiness of, pipe delimited (|)
     * @param notempties user input fields to assert non-emptiness of, pipe delimited (|)
     * @return the input fields and assertions as a series of sql joins
     */
    private String createJoins(final String nodetype, final String filternames, final String empties,
        final String notempties)
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
     * @param joins node types to join, pipe delimited (|)
     * @param childprefix prefix to give the child, to which a number will be appended to
     * @param nodetype Node type to join on
     * @return the input field as a series of sql joins
     */
    private String createSingleJoin(final String joins, final String childprefix, final String nodetype)
    {
        // Don't attempt to append joins if we're not given anything
        if (StringUtils.isBlank(joins)) {
            return "";
        }

        // Append an inner join for each pipe-delimited identifier in joins
        StringBuilder joindata = new StringBuilder();
        final int numChildren = StringUtils.countMatches(joins, "|") + 1;
        for (int i = 0; i < numChildren; i++) {
            joindata.append(
                String.format(
                    " inner join [%s] as %s%d on ischildnode(%s%d, n)",
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

    /**
     * Parse out filter data into a series of JCR_SQL2 conditionals.
     *
     * @param fields user input field names, pipe delimited (|)
     * @param values user input field values, pipe delimited (|)
     * @param comparator user input comparators, pipe delimited (|)
     * @throws IllegalArgumentException when the number of input fields are not equal
     */
    private String parseFilter(final String fields, final String values, final String comparator)
        throws IllegalArgumentException
    {
        // If we don't have either names or values, we should fail to filter
        if (StringUtils.isBlank(fields) || StringUtils.isBlank(values)) {
            return "";
        }

        // Parse out multiple fields, split by pipes (|)
        String[] fieldnames = fields.split("\\|");
        String[] fieldvalues = values.split("\\|");
        if (fieldnames.length != fieldvalues.length) {
            throw new IllegalArgumentException("fieldname and fieldvalue must have the same number"
                    + "of values, as delimited by pipes (|)");
        }

        // Also parse out multiple comparators
        String[] comparators;
        if (StringUtils.isBlank(comparator)) {
            // Use = as the default
            comparators = new String[fieldnames.length];
            for (int i = 0; i < fieldnames.length; i++) {
                comparators[i] = "=";
            }
        } else {
            comparators = comparator.split("\\|");
            if (comparators.length != fieldvalues.length) {
                throw new IllegalArgumentException("must have the same number of comparators as fields,"
                        + " as delimited by pipes (|)");
            }
        }

        // Build the filter conditionals by imposing conditions on the inner joined lfs:Answer children
        StringBuilder filterdata = new StringBuilder();
        // TODO: Double check the sanitization on the comparator
        for (int i = 0; i < fieldnames.length; i++) {
            // Condition 1: the question uuid must match one of the given (comma delimited)
            String[] possibleQuestions = fieldnames[i].split(",");
            filterdata.append(" and (");
            for (int j = 0; j < possibleQuestions.length; j++) {
                filterdata.append(
                    String.format(" child%d.'question'='%s'",
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
            filterdata.append(
                String.format(
                    ") and child%d.'value'%s'%s'",
                    i,
                    this.sanitizeComparator(comparators[i]),
                    this.sanitizeField(fieldvalues[i])
                )
            );
        }
        return filterdata.toString();
    }

    /**
     * Parse out empty & not empty fields into a series of JCR_SQL2 conditionals.
     *
     * @param empties user input field names to assert the nonexistance of content for, pipe delimited (|)
     * @param notempties user input field names to assert the existance of content for, pipe delimited (|)
     * @return JCR_SQL conditionals for the input
     */
    private String parseExistence(final String empties, final String notempties)
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
     * @param fieldnames user input field names, pipe delimited (|)
     * @param childprefix prefix for the child nodes
     * @param comparison unary comparitor to assert
     * @return JCR_SQL conditionals for the input
     */
    private String parseComparison(final String fieldnames, final String childprefix, final String comparison)
    {
        // Guard against nothing being entered
        if (StringUtils.isBlank(fieldnames)) {
            return "";
        }

        // Build the conditionals (e.g. and child0.'question'='uuid' and child0.'value' IS NOT NULL...)
        StringBuilder joindata = new StringBuilder();
        String[] fields = fieldnames.split("\\|");
        for (int i = 0; i < fields.length; i++) {
            joindata.append(
                String.format(
                    " and %s%d.'question'='%s' and %s%d.'value'%s",
                    childprefix,
                    i,
                    fields[i],
                    childprefix,
                    i,
                    comparison
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
        jsonGen.write("req", request.getParameter("req"));
        jsonGen.write("offset", limits[0]);
        jsonGen.write("limit", limits[1]);
        jsonGen.write("returnedrows", limits[2]);
        jsonGen.write("totalrows", limits[3]);
    }

    private long[] writeNodes(final JsonGenerator jsonGen, final Iterator<Resource> nodes,
        final long offset, final long limit)
    {
        final long[] counts = new long[4];
        counts[0] = offset;
        counts[1] = limit;
        counts[2] = 0;
        counts[3] = 0;

        long offsetCounter = offset < 0 ? 0 : offset;
        long limitCounter = limit < 0 ? 0 : limit;

        jsonGen.writeStartArray("rows");

        while (nodes.hasNext()) {
            Resource n = nodes.next();
            if (offsetCounter > 0) {
                --offsetCounter;
            } else if (limitCounter > 0) {
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
