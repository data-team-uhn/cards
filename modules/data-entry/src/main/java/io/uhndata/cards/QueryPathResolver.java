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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeTypeManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets a JCR-SQL2 query refer to a node by its path where a UUID is expected.
 * <p>
 * A reference property stores the UUID of the node it points to, and those UUIDs are generated when the node is
 * created, so they differ between instances. A query kept in the sources therefore cannot spell them out, and has to
 * name the node by its path instead, as in
 * {@code select * from [cards:Form] as form where form.questionnaire = '/Questionnaires/Visit information'}. This
 * turns such a path into the UUID that the property actually holds.
 * </p>
 * <p>
 * Only literals compared against a property that its node type declares as a {@code REFERENCE} or
 * {@code WEAKREFERENCE} are translated. Properties that hold a path as their real value, like the value of a
 * {@code cards:ResourceAnswer}, are left alone, as are paths passed to functions such as
 * {@code isdescendantnode(n, '/Forms')}.
 * </p>
 *
 * @version $Id$
 * @since 0.9.41
 */
public final class QueryPathResolver
{
    /** Matches a selector declared by the query, capturing its node type and, if present, its alias. */
    private static final Pattern QUERY_SELECTOR = Pattern.compile(
        "\\b(?:from|join)\\s++\\[([^\\]]++)\\]\\s*+(?:\\bas\\s++(\\[[^\\]]++\\]|[\\w:]++))?", Pattern.CASE_INSENSITIVE);

    /**
     * Matches a property compared to a string literal, capturing the selector, the property, and the literal. Only
     * comparisons are matched, which is what keeps a path passed to a function from being translated.
     */
    private static final Pattern PROPERTY_COMPARISON = Pattern.compile(
        "(?<!\\[)(?:(\\[[^\\]]++\\]|[\\w:]++)\\.)?(\\[[^\\]]++\\]|[\\w:]++)\\s*(?:=|<>|!=)\\s*+'(/(?:[^']|'')*+)'");

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryPathResolver.class);

    private QueryPathResolver()
    {
        // Prevent instantiation of a utility class
    }

    /**
     * Replaces the node paths that a query compares reference properties to with the UUID of the target node.
     *
     * @param session the session to look the node types and the referenced nodes up with
     * @param query a JCR-SQL2 query, which may compare reference properties to paths
     * @return the same query, with those paths replaced by the UUID of the node they point to; a path that doesn't
     *         resolve is reported in the log and left as it is
     * @throws RepositoryException if accessing the node types fails
     */
    public static String resolveReferencePaths(final Session session, final String query)
        throws RepositoryException
    {
        final Matcher comparisons = PROPERTY_COMPARISON.matcher(query);
        if (!comparisons.find()) {
            return query;
        }
        final Map<String, String> selectorTypes = getSelectorTypes(query);
        final NodeTypeManager nodeTypes = session.getWorkspace().getNodeTypeManager();

        final StringBuilder result = new StringBuilder();
        int copiedUpTo = 0;
        do {
            // A literal escapes a quote by doubling it, undo that before treating the value as a path
            final String path = comparisons.group(3).replace("''", "'");
            if (!isReferenceProperty(nodeTypes, selectorTypes, comparisons.group(1), comparisons.group(2))) {
                continue;
            }
            final String uuid = getUuid(session, path);
            if (uuid == null) {
                continue;
            }
            result.append(query, copiedUpTo, comparisons.start(3)).append(uuid);
            copiedUpTo = comparisons.end(3);
        } while (comparisons.find());
        return result.append(query, copiedUpTo, query.length()).toString();
    }

    /**
     * Collects the selectors declared by a query, so that a property can be looked up in the right node type.
     *
     * @param query a JCR-SQL2 query
     * @return a map from each selector name, which is its alias when it has one and its node type otherwise, to the
     *         node type it selects
     */
    private static Map<String, String> getSelectorTypes(final String query)
    {
        final Map<String, String> selectorTypes = new HashMap<>();
        final Matcher selectors = QUERY_SELECTOR.matcher(query);
        while (selectors.find()) {
            final String nodeType = selectors.group(1);
            final String alias = selectors.group(2);
            selectorTypes.put(unquoteName(alias == null ? nodeType : alias), nodeType);
        }
        return selectorTypes;
    }

    /**
     * Checks whether a property compared by a query is a reference to another node.
     *
     * @param nodeTypes the node type manager to look the definition up in
     * @param selectorTypes the node type selected by each of the query's selectors
     * @param selector the selector the property was qualified with, may be {@code null} for an unqualified property
     * @param property the name of the compared property
     * @return {@code true} if the node type declares this property as a reference
     * @throws RepositoryException if accessing the node types fails
     */
    private static boolean isReferenceProperty(final NodeTypeManager nodeTypes,
        final Map<String, String> selectorTypes, final String selector, final String property)
        throws RepositoryException
    {
        // An unqualified property is only unambiguous when the query declares a single selector
        final String nodeType = selector != null ? selectorTypes.get(unquoteName(selector))
            : selectorTypes.size() == 1 ? selectorTypes.values().iterator().next() : null;
        if (nodeType == null || !nodeTypes.hasNodeType(nodeType)) {
            return false;
        }
        final String propertyName = unquoteName(property);
        return Arrays.stream(nodeTypes.getNodeType(nodeType).getPropertyDefinitions())
            .filter(definition -> propertyName.equals(definition.getName()))
            .anyMatch(definition -> definition.getRequiredType() == PropertyType.REFERENCE
                || definition.getRequiredType() == PropertyType.WEAKREFERENCE);
    }

    /**
     * Looks up the UUID of the node at the given path.
     *
     * @param session the session to look the node up with
     * @param path the path of the referenced node
     * @return the UUID of that node, or {@code null} if there is no node there
     */
    private static String getUuid(final Session session, final String path)
    {
        try {
            if (!session.nodeExists(path)) {
                LOGGER.warn("Query refers to [{}], which doesn't exist; leaving the path as it is", path);
                return null;
            }
            final Node node = session.getNode(path);
            return node.getIdentifier();
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to read the identifier of [{}]: {}", path, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Strips the square brackets that a query may quote a selector or property name with.
     *
     * @param name a name as it appears in the query
     * @return the bare name
     */
    private static String unquoteName(final String name)
    {
        return name.startsWith("[") ? name.substring(1, name.length() - 1) : name;
    }
}
