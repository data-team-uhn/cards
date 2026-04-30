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
package io.uhndata.cards.healthcheck.internal;

import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.RowIterator;

import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Check that JCR select queries return a number of results that satisfies an expected condition. The list of checks is
 * defined as nodes in the repository, under {@code /libs/cards/healthcheck/queryCountChecks/}, with:
 * <ul>
 * <li>{@code query} — a JCR-SQL2 select query to execute (e.g. {@code SELECT * FROM [cards:Form]})</li>
 * <li>{@code comparator} — one of {@code <}, {@code <=}, {@code =}, {@code >=}, {@code >}, {@code !=}</li>
 * <li>{@code expectedCount} — the long value to compare the row count against</li>
 * <li>{@code limit} — (optional) maximum rows to fetch before counting; defaults to 1</li>
 * </ul>
 * The check passes when {@code actualCount comparator expectedCount} is true. The limit caps how many rows are
 * retrieved, so set it to at least {@code expectedCount} when using {@code =}, {@code >=}, or {@code >} comparators.
 * Other CARDS modules should provide the actual checks to run.
 *
 * @version $Id$
 * @since 0.9.38
 */
@Component(service = HealthCheck.class, property = { HealthCheck.TAGS + "=cards",
    HealthCheck.NAME + "=CARDS query counts" }, immediate = true)
public class QueryCountHealthCheck implements HealthCheck
{
    /** JCR node where all the configurations are stored. */
    public static final String CONFIGURATION_PATH = "/libs/cards/healthcheck/queryCountChecks";

    /** Configuration property: JCR-SQL2 count query to execute. */
    public static final String QUERY_PROPERTY = "query";

    /** Configuration property: comparison operator ({@code <}, {@code <=}, {@code =}, {@code >=}, {@code >},
     *  {@code !=}). */
    public static final String COMPARATOR_PROPERTY = "comparator";

    /** Configuration property: the expected count to compare against. */
    public static final String EXPECTED_COUNT_PROPERTY = "expectedCount";

    /** Configuration property: maximum rows to fetch before counting; defaults to 1. */
    public static final String LIMIT_PROPERTY = "limit";

    public static final long DEFAULT_LIMIT = 1L;

    private static final Set<String> VALID_COMPARATORS = Set.of("<", "<=", "=", ">=", ">", "!=");

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryCountHealthCheck.class);

    @Reference
    private ResourceResolverFactory rrf;

    @Override
    public Result execute()
    {
        final FormattingResultLog result = new FormattingResultLog();
        int passed = 0;
        int failed = 0;
        try (ResourceResolver resolver =
            this.rrf.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "healthcheck"))) {
            final Session session = resolver.adaptTo(Session.class);
            if (!session.nodeExists(CONFIGURATION_PATH)) {
                result.warn("No query count checks configured, please check the system integrity");
                return new Result(result);
            }
            final NodeIterator configurations = session.getNode(CONFIGURATION_PATH).getNodes();
            while (configurations.hasNext()) {
                final Node configuration = configurations.nextNode();
                try {
                    final String query = configuration.getProperty(QUERY_PROPERTY).getString();
                    final String comparator = configuration.getProperty(COMPARATOR_PROPERTY).getString();
                    final long expectedCount = configuration.getProperty(EXPECTED_COUNT_PROPERTY).getLong();

                    if (!VALID_COMPARATORS.contains(comparator)) {
                        result.healthCheckError("Invalid comparator '{}' in configuration '{}'",
                            comparator, configuration.getName());
                        continue;
                    }

                    final long limit = configuration.hasProperty(LIMIT_PROPERTY)
                        ? configuration.getProperty(LIMIT_PROPERTY).getLong()
                        : DEFAULT_LIMIT;
                    final Query jcrQuery = session.getWorkspace().getQueryManager()
                        .createQuery(query, Query.JCR_SQL2);
                    jcrQuery.setLimit(limit);
                    final RowIterator rows = jcrQuery.execute().getRows();
                    long actualCount = 0;
                    while (rows.hasNext()) {
                        rows.nextRow();
                        actualCount++;
                    }

                    if (evaluate(actualCount, comparator, expectedCount)) {
                        result.debug("Count check passed for '{}': {} {} {} (actual: {})",
                            configuration.getName(), query, comparator, expectedCount, actualCount);
                        passed++;
                    } else {
                        result.critical("Count check failed for '{}': result of {} was {}, expected {} {}",
                            configuration.getName(), query, actualCount, comparator, expectedCount);
                        failed++;
                    }
                } catch (RepositoryException e) {
                    LOGGER.error("Unexpected exception while running query count check", e);
                    result.healthCheckError("Cannot run count check: {}", e.getMessage(), e);
                }
            }
        } catch (LoginException | RepositoryException e) {
            result.healthCheckError("Healthcheck module not set up properly: {}", e.getMessage());
        }
        result.info("{} query count checks passed" + (failed != 0 ? " and {} failed" : ""), passed, failed);
        return new Result(result);
    }

    private boolean evaluate(long actual, String comparator, long expected)
    {
        switch (comparator) {
            case "<": return actual < expected;
            case "<=": return actual <= expected;
            case "=": return actual == expected;
            case ">=": return actual >= expected;
            case ">": return actual > expected;
            case "!=": return actual != expected;
            default: return false;
        }
    }
}
