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
import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.RowIterator;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.Result.Status;
import org.apache.felix.hc.api.ResultLog.Entry;
import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.uhndata.cards.utils.DateUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QueryCountHealthCheck}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class QueryCountHealthCheckTest
{
    private static final String TEST_QUERY = "SELECT * FROM [cards:Form]";

    @Mock
    private ResourceResolverFactory rrf;

    @Mock
    private ResourceResolver rr;

    @Mock
    private Session session;

    @Mock
    private Workspace workspace;

    @Mock
    private QueryManager queryManager;

    @Mock
    private Query jcrQuery;

    @Mock
    private QueryResult queryResult;

    @Mock
    private RowIterator rowIterator;

    @Mock
    private Node configurations;

    @Mock
    private Node config1;

    @Mock
    private Property queryProp1;

    @Mock
    private Property comparatorProp1;

    @Mock
    private Property compareAgainstProp1;

    @Mock
    private Property limitProp1;

    @Mock
    private Node config2;

    @Mock
    private Property queryProp2;

    @Mock
    private Property comparatorProp2;

    @Mock
    private Property expectedProp2;

    @InjectMocks
    private QueryCountHealthCheck checker;

    @Before
    public void setup() throws LoginException, RepositoryException
    {
        when(this.rrf.getServiceResourceResolver(any())).thenReturn(this.rr);
        when(this.rr.adaptTo(Session.class)).thenReturn(this.session);
        when(this.session.nodeExists(QueryCountHealthCheck.CONFIGURATION_PATH)).thenReturn(true);
        when(this.session.getNode(QueryCountHealthCheck.CONFIGURATION_PATH)).thenReturn(this.configurations);

        when(this.session.getWorkspace()).thenReturn(this.workspace);
        when(this.workspace.getQueryManager()).thenReturn(this.queryManager);
        when(this.queryManager.createQuery(any(), eq(Query.JCR_SQL2))).thenReturn(this.jcrQuery);
        when(this.jcrQuery.execute()).thenReturn(this.queryResult);
        when(this.queryResult.getRows()).thenReturn(this.rowIterator);
        // Default: iterator yields one row → actualCount = 1
        when(this.rowIterator.hasNext()).thenReturn(true, false);

        when(this.config1.getProperty(QueryCountHealthCheck.QUERY_PROPERTY)).thenReturn(this.queryProp1);
        when(this.queryProp1.getString()).thenReturn(TEST_QUERY);
        when(this.config1.getProperty(QueryCountHealthCheck.COMPARATOR_PROPERTY)).thenReturn(this.comparatorProp1);
        when(this.comparatorProp1.getString()).thenReturn("=");
        when(this.config1.getProperty(QueryCountHealthCheck.COMPARE_AGAINST_PROPERTY))
            .thenReturn(this.compareAgainstProp1);
        when(this.compareAgainstProp1.getLong()).thenReturn(1L);
        when(this.config1.getName()).thenReturn("check1");

        when(this.config2.getProperty(QueryCountHealthCheck.QUERY_PROPERTY)).thenReturn(this.queryProp2);
        when(this.queryProp2.getString()).thenReturn(TEST_QUERY);
        when(this.config2.getProperty(QueryCountHealthCheck.COMPARATOR_PROPERTY)).thenReturn(this.comparatorProp2);
        when(this.comparatorProp2.getString()).thenReturn("=");
        when(this.config2.getProperty(QueryCountHealthCheck.COMPARE_AGAINST_PROPERTY)).thenReturn(this.expectedProp2);
        when(this.expectedProp2.getLong()).thenReturn(99L);
        when(this.config2.getName()).thenReturn("check2");
    }

    @Test
    public void testNoConfiguration() throws Exception
    {
        when(this.session.nodeExists(QueryCountHealthCheck.CONFIGURATION_PATH)).thenReturn(false);
        Result result = this.checker.execute();
        Assert.assertEquals(Status.OK, result.getStatus());
    }

    @Test
    public void testPassedCheck() throws Exception
    {
        // actualCount=1, comparator="=", expectedCount=1 → passes
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.OK, this.checker.execute().getStatus());
        verify(this.jcrQuery).setLimit(2L);
    }

    @Test
    public void testFailedCheck() throws Exception
    {
        // actualCount=1, comparator="=", expectedCount=99 → fails
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config2)));
        Assert.assertEquals(Status.CRITICAL, this.checker.execute().getStatus());
        verify(this.jcrQuery).setLimit(100L);
    }

    @Test
    public void testComparatorLessThan() throws Exception
    {
        when(this.comparatorProp1.getString()).thenReturn("<");

        when(this.compareAgainstProp1.getLong()).thenReturn(2L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.OK, this.checker.execute().getStatus());

        when(this.rowIterator.hasNext()).thenReturn(true, false);
        when(this.compareAgainstProp1.getLong()).thenReturn(1L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.CRITICAL, this.checker.execute().getStatus());
    }

    @Test
    public void testComparatorLessThanOrEqual() throws Exception
    {
        when(this.comparatorProp1.getString()).thenReturn("<=");

        when(this.compareAgainstProp1.getLong()).thenReturn(2L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.OK, this.checker.execute().getStatus());

        when(this.rowIterator.hasNext()).thenReturn(true, false);
        when(this.compareAgainstProp1.getLong()).thenReturn(1L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.OK, this.checker.execute().getStatus());

        when(this.rowIterator.hasNext()).thenReturn(true, false);
        when(this.compareAgainstProp1.getLong()).thenReturn(0L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.CRITICAL, this.checker.execute().getStatus());
    }

    @Test
    public void testComparatorGreaterThan() throws Exception
    {
        when(this.comparatorProp1.getString()).thenReturn(">");

        when(this.compareAgainstProp1.getLong()).thenReturn(0L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.OK, this.checker.execute().getStatus());

        when(this.rowIterator.hasNext()).thenReturn(true, false);
        when(this.compareAgainstProp1.getLong()).thenReturn(1L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.CRITICAL, this.checker.execute().getStatus());
    }

    @Test
    public void testComparatorGreaterThanOrEqual() throws Exception
    {
        when(this.comparatorProp1.getString()).thenReturn(">=");

        when(this.compareAgainstProp1.getLong()).thenReturn(0L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.OK, this.checker.execute().getStatus());

        when(this.rowIterator.hasNext()).thenReturn(true, false);
        when(this.compareAgainstProp1.getLong()).thenReturn(1L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.OK, this.checker.execute().getStatus());

        when(this.rowIterator.hasNext()).thenReturn(true, false);
        when(this.compareAgainstProp1.getLong()).thenReturn(2L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.CRITICAL, this.checker.execute().getStatus());
    }

    @Test
    public void testComparatorNotEqual() throws Exception
    {
        when(this.comparatorProp1.getString()).thenReturn("!=");

        when(this.compareAgainstProp1.getLong()).thenReturn(0L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.OK, this.checker.execute().getStatus());

        when(this.rowIterator.hasNext()).thenReturn(true, false);
        when(this.compareAgainstProp1.getLong()).thenReturn(1L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.CRITICAL, this.checker.execute().getStatus());
    }

    @Test
    public void testInvalidComparator() throws Exception
    {
        when(this.comparatorProp1.getString()).thenReturn("??");
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, this.checker.execute().getStatus());
    }

    @Test
    public void testZeroResults() throws Exception
    {
        // actualCount=0 with comparator "=" and expectedCount=0 should pass
        when(this.rowIterator.hasNext()).thenReturn(false);
        when(this.compareAgainstProp1.getLong()).thenReturn(0L);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.OK, this.checker.execute().getStatus());
    }

    @Test
    public void testDatePlaceholders() throws Exception
    {
        final String yesterday = DateUtils.toString(DateUtils.atMidnight(ZonedDateTime.now().minusDays(1)));
        final String today = DateUtils.toString(DateUtils.atMidnight(ZonedDateTime.now()));
        final String tomorrow = DateUtils.toString(DateUtils.atMidnight(ZonedDateTime.now().plusDays(1)));
        final String queryWithPlaceholders = "SELECT * FROM [cards:DateAnswer] WHERE [jcr:created] >= '"
            + QueryCountHealthCheck.YESTERDAY_PLACEHOLDER + "'"
            + " AND [value] >= '" + QueryCountHealthCheck.TODAY_PLACEHOLDER + "'"
            + " AND [value] < '" + QueryCountHealthCheck.TOMORROW_PLACEHOLDER + "'";
        final String expectedQuery = "SELECT * FROM [cards:DateAnswer] WHERE [jcr:created] >= '"
            + yesterday + "'"
            + " AND [value] >= '" + today + "'"
            + " AND [value] < '" + tomorrow + "'";
        when(this.queryProp1.getString()).thenReturn(queryWithPlaceholders);
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        this.checker.execute();
        verify(this.queryManager).createQuery(eq(expectedQuery), eq(Query.JCR_SQL2));
        verify(this.jcrQuery).setLimit(2L);
    }

    @Test
    public void testRepositoryException() throws Exception
    {
        when(this.config1.getProperty(any())).thenThrow(new PathNotFoundException("missing property"));
        when(this.configurations.getNodes()).thenReturn(new NodeIteratorAdapter(List.of(this.config1)));
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, this.checker.execute().getStatus());
    }

    @Test
    public void testLoginException() throws Exception
    {
        when(this.rrf.getServiceResourceResolver(any())).thenThrow(new LoginException("wrong login"));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.HEALTH_CHECK_ERROR, result.getStatus());
        Assert.assertNotNull(result.iterator().next().getMessage());
    }

    @Test
    public void testAggregation() throws Exception
    {
        when(this.configurations.getNodes())
            .thenReturn(new NodeIteratorAdapter(List.of(this.config1, this.config2)));
        Result result = this.checker.execute();
        Assert.assertEquals(Status.CRITICAL, result.getStatus());
        Iterator<Entry> entries = result.iterator();
        Assert.assertEquals(Status.OK, entries.next().getStatus());
        Assert.assertEquals(Status.CRITICAL, entries.next().getStatus());
        Assert.assertEquals(Status.OK, entries.next().getStatus());
        Assert.assertFalse(entries.hasNext());
    }
}
