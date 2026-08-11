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
package io.uhndata.cards.clarity.importer;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the query that {@link ClarityImportTask} looks existing subjects up with.
 *
 * @version $Id$
 */
public class ClarityImportTaskTest
{
    @Test
    public void createSubjectMatchQueryWithoutParentMatchesOnTheIdentifierAlone()
    {
        Assert.assertEquals(
            "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='V1'"
                + " option (index tag property)",
            ClarityImportTask.createSubjectMatchQuery("V1", null));
    }

    @Test
    public void createSubjectMatchQueryWithParentAlsoMatchesOnTheHierarchy()
    {
        Assert.assertEquals(
            "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='V1'"
                + " AND ISCHILDNODE(subject, '/Subjects/P1')"
                + " option (index tag property)",
            ClarityImportTask.createSubjectMatchQuery("V1", "/Subjects/P1"));
    }

    /**
     * The point of CARDS-2808: two patients may use the same visit identifier, and the visit must then only be looked
     * for under the patient the row belongs to.
     */
    @Test
    public void createSubjectMatchQueryDistinguishesTheSameIdentifierUnderDifferentParents()
    {
        final String underP1 = ClarityImportTask.createSubjectMatchQuery("V1", "/Subjects/P1");
        final String underP2 = ClarityImportTask.createSubjectMatchQuery("V1", "/Subjects/P2");
        Assert.assertNotEquals(underP1, underP2);
        Assert.assertTrue(underP1.contains("ISCHILDNODE(subject, '/Subjects/P1')"));
        Assert.assertTrue(underP2.contains("ISCHILDNODE(subject, '/Subjects/P2')"));
    }

    @Test
    public void createSubjectMatchQueryEscapesQuotesInTheIdentifier()
    {
        Assert.assertEquals(
            "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='O''Brien'"
                + " option (index tag property)",
            ClarityImportTask.createSubjectMatchQuery("O'Brien", null));
    }

    @Test
    public void createSubjectMatchQueryEscapesQuotesInTheParentPath()
    {
        Assert.assertTrue(ClarityImportTask.createSubjectMatchQuery("V1", "/Subjects/O'Brien")
            .contains("ISCHILDNODE(subject, '/Subjects/O''Brien')"));
    }
}
