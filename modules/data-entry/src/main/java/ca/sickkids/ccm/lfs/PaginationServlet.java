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
import java.util.Iterator;

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

    @SuppressWarnings({"checkstyle:ExecutableStatementCount", "checkstyle:MultipleStringLiterals"})
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
        // TODO: joinchildren should be sanitized
        final String joinchildren = request.getParameter("joinchildren");
        final String filtername = request.getParameter("filternames");
        if (StringUtils.isNotBlank(joinchildren)) {
            // Determine how many children we need for this query
            final int fields = StringUtils.countMatches(filtername, "|");
            for (int i = 0; i <= fields; i++) {
                String childname = "child" + Integer.toString(i);
                query.append(" inner join [" + joinchildren + "] as " + childname
                        + " on ischildnode(" + childname + ", n)");
            }
        }

        // Check only for our fields
        query.append(" where ischildnode(n, '" + request.getResource().getPath()
                + "') and n.'sling:resourceSuperType' = 'lfs/Resource'");

        // Full text search; \ and ' must be escaped
        final String filter = request.getParameter("filter");
        if (StringUtils.isNotBlank(filter)) {
            query.append(" and contains(n.*, '" + this.sanitize(filter) + "')");
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
            query.append(" and n.'" + this.sanitize(fieldname) + "'"
                    + fieldcomparator + "'" + this.sanitize(fieldvalue) + "'");
        }

        // Condition on child nodes. See parseFilter for details.
        final String filtervalue = request.getParameter("filtervalues");
        final String filtercomparators = request.getParameter("filtercomparators");
        if (StringUtils.isNotBlank(filtername)) {
            query.append(parseFilter(filtername, filtervalue, filtercomparators));
        }

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

        // Build the filter conditionals by left outer joining on the lfs:Answer children
        StringBuilder filterdata = new StringBuilder();
        // TODO: Double check the sanitization on the comparator
        for (int i = 0; i < fieldnames.length; i++) {
            // Condition 1: the question uuid must match one of the given (comma delimited)
            String[] possibleQuestions = fieldnames[i].split(",");
            filterdata.append(" and (");
            for (int j = 0; j < possibleQuestions.length; j++) {
                filterdata.append(" child" + Integer.toString(i) + ".'question'='"
                        + this.sanitize(possibleQuestions[j]) + "'");
                // Add an 'or' if there are more possible conditions
                if (j + 1 != possibleQuestions.length) {
                    filterdata.append(" or");
                }
            }

            // Condition 2: the value must exactly match
            filterdata.append(") and child" + Integer.toString(i) + ".'value'" + this.sanitize(comparators[i]) + "'"
                    + this.sanitize(fieldvalues[i]) + "'");
        }
        return filterdata.toString();
    }

    /**
     * Sanitize a field name for an input query.
     *
     * @param fieldname the field name to sanitize
     * @return a sanitized version of the input
     */
    private String sanitize(String fieldname)
    {
        return fieldname.replaceAll("['\\\\]", "\\\\$0");
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
