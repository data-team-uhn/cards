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

import java.time.ZonedDateTime;
import java.util.Map;

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

import io.uhndata.cards.utils.DateUtils;

/**
 * Check that JCR select queries return a number of results that satisfies an expected condition. The list of checks is
 * defined as nodes in the repository, under {@code /libs/cards/healthcheck/queryCountChecks/}, with the following
 * properties:
 * <ul>
 * <li>{@code query} - a JCR-SQL2 select query to execute</li>
 * <li>{@code comparator} - one of {@code <}, {@code <=}, {@code =}, {@code >=}, {@code >}, {@code !=}</li>
 * <li>{@code compareAgainst} - long value to compare the row count against</li>
 * </ul>
 * The check passes when {@code actualCount comparator compareAgainst} is true. The query will only fetch
 * {@code compareAgainst+1} rows for performance reasons.
 * <p>
 * The query string may contain date placeholders resolved at execution time, which you can use in JCR SQL2 date
 * literals, e.g. {@code WHERE form.[jcr:created] > '${today}'}:
 * </p>
 * <ul>
 * <li>{@code ${yesterday}} - yesterday's date at midnight</li>
 * <li>{@code ${today}} - today's date at midnight</li>
 * <li>{@code ${tomorrow}} - tomorrow's date at midnight</li>
 * </ul>
 * <p>
 * Other CARDS modules should provide the actual checks to run.
 * </p>
 *
 * @version $Id$
 * @since 0.9.38
 */
@Component(
    service = HealthCheck.class,
    property = {
        HealthCheck.TAGS + "=cards",
        HealthCheck.NAME + "=CARDS query counts"
    },
    immediate = true)
public final class QueryCountHealthCheck implements HealthCheck
{
    /** JCR node where all the configurations are stored. */
    public static final String CONFIGURATION_PATH =
        "/libs/cards/healthcheck/queryCountChecks";

    /** Configuration property: JCR-SQL2 select query to execute. */
    public static final String QUERY_PROPERTY = "query";

    /**
     * Configuration property: comparison operator ({@code <}, {@code <=}, {@code =}, {@code >=}, {@code >},
     * {@code !=}).
     */
    public static final String COMPARATOR_PROPERTY = "comparator";

    /** Configuration property: the expected count to compare against. */
    public static final String COMPARE_AGAINST_PROPERTY = "compareAgainst";

    /**
     * Placeholder replaced with yesterday's date ({@code YYYY-MM-DD}, UTC) at query execution time.
     */
    public static final String YESTERDAY_PLACEHOLDER = "${yesterday}";

    /**
     * Placeholder replaced with today's date ({@code YYYY-MM-DD}, UTC) at query execution time.
     */
    public static final String TODAY_PLACEHOLDER = "${today}";

    /**
     * Placeholder replaced with yesterday's date ({@code YYYY-MM-DD}, UTC) at query execution time.
     */
    public static final String TOMORROW_PLACEHOLDER = "${tomorrow}";

    @FunctionalInterface
    private interface Checker
    {
        boolean check(long actualCount, long compareAgainst);
    }

    private static final Map<String, Checker> COMPARATORS = Map.of(
        "<", (a, b) -> a < b,
        "<=", (a, b) -> a <= b,
        "=", (a, b) -> a == b,
        ">=", (a, b) -> a >= b,
        ">", (a, b) -> a > b,
        "!=", (a, b) -> a != b);

    /** Default logger. */
    private static final Logger LOGGER =
        LoggerFactory.getLogger(QueryCountHealthCheck.class);

    /** The resource resolver factory. */
    @Reference
    private ResourceResolverFactory rrf;

    @Override
    public Result execute()
    {
        final FormattingResultLog result = new FormattingResultLog();
        int passed = 0;
        int failed = 0;
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "healthcheck"))) {
            final Session session = resolver.adaptTo(Session.class);
            if (!session.nodeExists(CONFIGURATION_PATH)) {
                result.info("No query count checks configured.");
                return new Result(result);
            }
            final NodeIterator configurations = session.getNode(CONFIGURATION_PATH).getNodes();
            while (configurations.hasNext()) {
                try {
                    final int checkResult = runSingleCheck(
                        configurations.nextNode(), result, session);
                    if (checkResult == 1) {
                        passed++;
                    } else {
                        failed++;
                    }
                } catch (RepositoryException e) {
                    LOGGER.error("Unexpected exception while running query count check", e);
                    result.healthCheckError("Cannot run count check: {}", e.getMessage(), e);
                }
            }
        } catch (LoginException | RepositoryException e) {
            result.healthCheckError(
                "Healthcheck module not set up properly: {}", e.getMessage());
        }
        result.info("{} query count checks passed"
            + (failed != 0 ? " and {} failed" : ""), passed, failed);
        return new Result(result);
    }

    private int runSingleCheck(final Node configuration,
        final FormattingResultLog result, final Session session)
        throws RepositoryException
    {
        final String query = resolveDatePlaceholders(configuration.getProperty(QUERY_PROPERTY).getString());
        final String comparator = configuration.getProperty(COMPARATOR_PROPERTY).getString();
        final long compareAgainst = configuration.getProperty(COMPARE_AGAINST_PROPERTY).getLong();

        if (!COMPARATORS.containsKey(comparator)) {
            result.healthCheckError(
                "Invalid comparator '{}' in configuration '{}'",
                comparator, configuration.getName());
            return -1;
        }

        final long limit = compareAgainst + 1;

        final Query jcrQuery = session.getWorkspace().getQueryManager().createQuery(query, Query.JCR_SQL2);
        jcrQuery.setLimit(limit);
        final RowIterator rows = jcrQuery.execute().getRows();
        long actualCount = 0;
        while (rows.hasNext()) {
            rows.nextRow();
            actualCount++;
        }

        if (COMPARATORS.get(comparator).check(actualCount, compareAgainst)) {
            result.debug("Count check passed for '{}'", configuration.getName());
            return 1;
        }
        result.critical(
            "Count check failed for '{}': result was {}, expected {} {}",
            configuration.getName(), actualCount, comparator, compareAgainst);
        return 0;
    }

    private String resolveDatePlaceholders(final String query)
    {
        return query
            .replace(YESTERDAY_PLACEHOLDER, DateUtils.toString(DateUtils.atMidnight(ZonedDateTime.now().minusDays(1))))
            .replace(TODAY_PLACEHOLDER, DateUtils.toString(DateUtils.atMidnight(ZonedDateTime.now())))
            .replace(TOMORROW_PLACEHOLDER, DateUtils.toString(DateUtils.atMidnight(ZonedDateTime.now().plusDays(1))));
    }
}
