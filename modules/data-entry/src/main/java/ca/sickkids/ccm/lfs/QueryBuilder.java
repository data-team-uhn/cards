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
package ca.sickkids.ccm.lfs;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.ListIterator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.script.Bindings;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.scripting.sightly.pojo.Use;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A HTL Use-API that can run a JCR query and output the results as JSON. The query to execute is taken from the request
 * parameter {@code query}, and must be in the JCR-SQL2 syntax. To use this API, simply place the following code in a
 * HTL file:
 *
 * <p><tt>
 * &lt;sly data-sly-use.query="ca.sickkids.ccm.lfs.QueryBuilder"&gt;${query.content @ context='unsafe'}&lt;/sly&gt;
 * </tt></p>
 *
 * <p>Or, send a HTTP request to {@code /query?query=select%20*%20from%20[lfs:Form]}.</p>
 *
 * @version $Id$
 */
public class QueryBuilder implements Use
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryBuilder.class);

    private String content;

    private ResourceResolver resourceResolver;

    private SlingHttpServletRequest request;

    /* Whether or not the input should be escaped, if it is used in a contains() call. */
    private boolean shouldEscape;

    /**
     * Takes in an Iterator of Resource typed objects and returns a
     * ListIterator of Resource typed objects. Depending on the value of
     * augmentData, the returned ListIterator is either a simple copy of
     * the input Iterator or it is a copy of the input Iterator where the
     * Resource items are augmented with a queryMatch list describing how
     * the query string, qs, matches the given item. PLEASE NOTE: Running
     * this method consumes inputIterator and therefore any subsequent
     * calls to the .next() method of the inputIterator object will
     * invariably fail.
     *
     * @param inputIterator the Iterator of Resource typed objects to be used as input
     * @param augmentData false if data should simply be copied, true otherwise
     * @param qs the query string to augment matching Resource objects with
     * @return a ListIterator of potentially data-augmented Resource objects
     */
    private ListIterator<Resource> augmentIterator(Iterator<Resource> inputIterator, boolean augmentData, String qs)
    {
        ArrayList<Resource> outputList = new ArrayList<Resource>();
        Resource res;
        while (inputIterator.hasNext())
        {
            res = inputIterator.next();
            if (augmentData)
            {
                augmentResult(res, qs);
            }
            outputList.add(res);
        }
        return outputList.listIterator();
    }

    /**
     * Take in a Resource object and, if there is any contained form
     * data that matches with queryString, return a version of this
     * resource with a property, "queryMatch", describing how queryString
     * matches the given resource. PLEASE NOTE: The Resource object res,
     * will be modified if there are query matches.
     *
     * @param res the Resource object to check for query matchess
     * @param queryString the String object used as a query to res
     */
    private void augmentResult(Resource res, String queryString)
    {
        Iterator<Resource> reschildren;
        Resource selectedchild;
        reschildren = res.listChildren();
        while (reschildren.hasNext()) {
            selectedchild = reschildren.next();
            String resourcevalue = selectedchild.getValueMap().get("value", "");
            if (!"".equals(resourcevalue) && resourcevalue.toLowerCase().indexOf(queryString.toLowerCase()) > -1) {
                String resourcequestion = selectedchild.getValueMap().get("question", "");
                if (!"".equals(resourcequestion)) {
                    try
                    {
                        Node questionNode = selectedchild.adaptTo(Node.class);
                        questionNode = questionNode.getSession().getNodeByIdentifier(resourcequestion);
                        String matchType = questionNode.getProperty("text").getValue().toString();
                        LOGGER.info("Query: {}, Matches: {}, Type: {}",
                            queryString,
                            resourcevalue,
                            matchType);
                        int matchIndex = resourcevalue.toLowerCase().indexOf(queryString.toLowerCase());
                        String matchBefore = resourcevalue.substring(0, matchIndex);
                        String matchText = resourcevalue.substring(matchIndex, matchIndex + queryString.length());
                        String matchAfter = resourcevalue.substring(matchIndex + queryString.length());
                        String[] queryMatch = {
                            matchType,
                            matchBefore,
                            matchText,
                            matchAfter
                        };
                        Node resNode = res.adaptTo(Node.class);
                        resNode.setProperty("queryMatch", queryMatch);
                        //Break after 1st match
                        break;
                    } catch (RepositoryException ex) {
                        continue;
                    }
                }
            }
        }
    }

    @Override
    public void init(Bindings bindings)
    {
        this.request = (SlingHttpServletRequest) bindings.get("request");
        this.resourceResolver = (ResourceResolver) bindings.get("resolver");

        try {
            final String jcrQuery = this.request.getParameter("query");
            final String luceneQuery = this.request.getParameter("lucene");
            final String fullTextQuery = this.request.getParameter("fulltext");
            final String quickQuery = this.request.getParameter("quick");
            final String doNotEscape = this.request.getParameter("doNotEscapeQuery");
            final long limit = getLongValueOrDefault(this.request.getParameter("limit"), 10);
            final long offset = getLongValueOrDefault(this.request.getParameter("offset"), 0);
            this.shouldEscape = StringUtils.isBlank(doNotEscape) || !("true".equals(doNotEscape));

            // Try to use a JCR-SQL2 query first
            Iterator<Resource> results;
            ListIterator<Resource> augmentedResults;
            if (StringUtils.isNotBlank(jcrQuery)) {
                results = queryJCR(this.urlDecode(jcrQuery));
                augmentedResults = augmentIterator(results, false, "");
            } else if (StringUtils.isNotBlank(luceneQuery)) {
                results = queryLucene(this.urlDecode(luceneQuery));
                augmentedResults = augmentIterator(results, false, "");
            } else if (StringUtils.isNotBlank(fullTextQuery)) {
                results = fullTextSearch(this.urlDecode(fullTextQuery));
                augmentedResults = augmentIterator(results, false, "");
            } else if (StringUtils.isNotBlank(quickQuery)) {
                results = quickSearch(this.urlDecode(quickQuery));
                //Augment the `results` with the matches
                augmentedResults = augmentIterator(results, true, this.urlDecode(quickQuery));
            } else {
                results = Collections.emptyIterator();
                augmentedResults = augmentIterator(results, false, "");
            }

            // output the results into our content
            JsonObjectBuilder builder = Json.createObjectBuilder();
            long[] metadata = this.addNodes(builder, augmentedResults, offset, limit);
            this.addSummary(builder, this.request, metadata);
            this.content = builder.build().toString();
        } catch (Exception e) {
            this.content = "Unknown error: " + e.fillInStackTrace();
        }
    }

    /**
     * URL-decodes the given request parameter.
     *
     * @param param a URL-encoded request parameter
     * @return a decoded version of the input
     * @throws UnsupportedEncodingException should not be thrown unless UTF_8 is somehow not available
     */
    private String urlDecode(String param) throws UnsupportedEncodingException
    {
        return URLDecoder.decode(param, StandardCharsets.UTF_8.name());
    }

    /**
     * Finds content matching the given lucene query.
     *
     * @param query a lucene query
     * @return the content matching the query
     */
    private Iterator<Resource> queryLucene(String query) throws RepositoryException
    {
        // Wrap our lucene query in JCR-SQL2 syntax for the resource resolver to understand
        return queryJCR(
            String.format("select n.* from [nt:base] as n where native('lucene', '%s')"
                + " and n.'sling:resourceSuperType' = 'lfs/Resource'", query.replace("'", "''")));
    }

    /**
     * Finds content using the given full text search.
     *
     * @param query text to search
     *
     * @return the content matching the query
     */
    private Iterator<Resource> fullTextSearch(String query) throws RepositoryException
    {
        // Wrap our full-text query in JCR-SQL2 syntax for the resource resolver to understand
        return queryJCR(
            String.format("select n.* from [nt:base] as n where contains(*, '%s')", this.fullTextEscape(query)));
    }

    /**
     * Escapes the input query if this.shouldEscape is true.
     *
     * @param input text to escape
     *
     * @return an escaped version of the input
     */
    private String fullTextEscape(String input)
    {
        if (!this.shouldEscape) {
            return input;
        }

        // Escape sequence taken from https://jackrabbit.apache.org/archive/wiki/JCR/EncodingAndEscaping_115513396.html
        return input.replaceAll("([\\Q+-&|!(){}[]^\"~*?:\\/\\E])", "\\$1").replaceAll("'", "''");
    }

    /**
     * Finds [lfs:Form]s, [lfs:Subject]s, and [lfs:Questionnaire]s using the given full text search.
     * This performs the search in such a way that values in child nodes (e.g. lfs:Answers of an lfs:Form)
     * are aggregated to their parent.
     *
     * @param query text to search
     *
     * @return the content matching the query
     */
    private Iterator<Resource> quickSearch(String query) throws RepositoryException
    {
        final String[] toSearch = {"lfs:Form", "lfs:Subject", "lfs:Questionnaire"};
        final StringBuilder oakQuery = new StringBuilder();

        for (int i = 0; i < toSearch.length; i++) {
            oakQuery.append(
                String.format(
                    "select n.* from [%s] as n where contains(*, '*%s*')",
                    toSearch[i],
                    this.fullTextEscape(query)
                )
            );

            // Union interstitial terms together
            if (i + 1 != toSearch.length) {
                oakQuery.append(" union ");
            }
        }
        // Wrap our full-text query in JCR-SQL2 syntax for the resource resolver to understand
        return queryJCR(oakQuery.toString());
    }


    /**
     * Finds content matching the given JCR_SQL2 query.
     *
     * @param query a JCR-SQL2 query
     * @return the content matching the query
     */
    private Iterator<Resource> queryJCR(String query) throws RepositoryException
    {
        return this.resourceResolver.findResources(query, "JCR-SQL2");
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
    private void addSummary(final JsonObjectBuilder jsonGen, final SlingHttpServletRequest request, final long[] limits)
    {
        String req = request.getParameter("req");
        if (StringUtils.isBlank(req)) {
            req = "";
        }
        jsonGen.add("req", req);
        jsonGen.add("offset", limits[0]);
        jsonGen.add("limit", limits[1]);
        jsonGen.add("returnedrows", limits[2]);
        jsonGen.add("totalrows", limits[3]);
    }

    /**
     * Write the contents of the input nodes, subject to the an offset and a limit.
     *
     * @param jsonGen the JSON object generator where the results should be serialized
     * @param nodes an iterator over the nodes to serialize, which will be consumed
     * @param offset the requested offset, may be the default value of {0}
     * @param limit the requested limit, may be the default value of {10}
     * @return an array of size 4, giving the [0]: offset, [1]: limit, [2]: results, [3]: total matches
     */
    private long[] addNodes(final JsonObjectBuilder jsonGen, final Iterator<Resource> nodes,
        final long offset, final long limit)
    {
        final long[] counts = new long[4];
        counts[0] = offset;
        counts[1] = limit;
        counts[2] = 0;
        counts[3] = 0;

        long offsetCounter = offset < 0 ? 0 : offset;
        long limitCounter = limit < 0 ? 0 : limit;

        final JsonArrayBuilder builder = Json.createArrayBuilder();

        while (nodes.hasNext()) {
            Resource n = nodes.next();
            // Skip results up to the offset provided
            if (offsetCounter > 0) {
                --offsetCounter;
            // Count up to our limit
            } else if (limitCounter > 0) {
                builder.add(n.adaptTo(JsonObject.class));
                --limitCounter;
                ++counts[2];
            }
            // Count the total number of results
            ++counts[3];
        }

        jsonGen.add("rows", builder.build());

        return counts;
    }

    /**
     * Use the value given (usually from a request.getParameter()) or use a default value.
     *
     * @param stringValue the value to use if provided
     * @param defaultValue the value to use if stringValue is not given
     * @return Either stringValue, if provided, or defaultValue
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
     * Get the results of the query as a JSON array.
     *
     * @return a JsonArray with all the content matching the query
     */
    public String getContent()
    {
        return this.content;
    }
}
