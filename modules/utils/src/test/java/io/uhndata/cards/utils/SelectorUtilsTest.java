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

package io.uhndata.cards.utils;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link SelectorUtils}.
 *
 * @version $Id$
 */
public class SelectorUtilsTest
{
    @Test
    public void testSimpleParsing()
        throws Exception
    {
        Assert.assertEquals(List.of("a", "b", "c", "d"), SelectorUtils.parseSelectors("a.b.c.d"));
    }

    @Test
    public void testEmptyStringParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(), SelectorUtils.parseSelectors(""));
    }

    @Test
    public void testNullStringParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(), SelectorUtils.parseSelectors(null));
    }

    @Test
    public void testEmptySelectorsAreIgnored()
        throws Exception
    {
        Assert.assertEquals(List.of("a", "b", "c", "d"), SelectorUtils.parseSelectors("..a.b.c...d.."));
    }

    @Test
    public void testDotEscaping()
        throws Exception
    {
        Assert.assertEquals(List.of("a.b", "c.d"), SelectorUtils.parseSelectors("a\\.b...c\\.d"));
        Assert.assertEquals(List.of("a\\.b", "c\\.d"), SelectorUtils.parseSelectors("a\\\\\\.b...c\\\\\\.d"));
    }

    @Test
    public void testBackslashEscaping()
        throws Exception
    {
        Assert.assertEquals(List.of("a\\", "b", "c\\", "d"), SelectorUtils.parseSelectors("a\\\\.b...c\\\\.d"));
    }

    @Test
    public void testTrailingBackslash()
        throws Exception
    {
        Assert.assertEquals(List.of("a", "b", "c", "d\\"), SelectorUtils.parseSelectors("a.b.c.d\\"));
        Assert.assertEquals(List.of("a", "b", "c", "d\\"), SelectorUtils.parseSelectors("a.b.c.d\\\\"));
        Assert.assertEquals(List.of("a", "b", "c", "d\\\\"), SelectorUtils.parseSelectors("a.b.c.d\\\\\\"));
    }

    @Test
    public void testURLDecodingWhenParsing()
        throws Exception
    {
        Assert.assertEquals(List.of("csvReplaceColumnLabels: ID=", "csvReplaceColumnLabels:@=#", "csv"),
            SelectorUtils
                .parseSelectors(".csvReplaceColumnLabels:%20ID%3D.csvReplaceColumnLabels:%40%3D%23.csv"));
    }

    @Test
    public void testFullEscaping()
        throws Exception
    {
        Assert.assertEquals(List.of("a\\\\", "b\\.c\\", "d\\"),
            SelectorUtils.parseSelectors("a\\\\\\\\.b\\\\\\.c\\\\.d\\\\"));
    }

    @Test
    public void testSimpleOptionParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(Pair.of("a", "1"), Pair.of("b", "2")),
            SelectorUtils.parseOptions("dataFilter:", ".data.dataFilter:a=1.dataOption:c=3.dataFilter:b=2"));
    }

    @Test
    public void testEmptyStringOptionParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(), SelectorUtils.parseOptions("a", ""));
    }

    @Test
    public void testNullStringOptionParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(), SelectorUtils.parseOptions("a", null));
    }

    @Test
    public void testNullPrefixOptionParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(), SelectorUtils.parseOptions(null, ":b=c"));
    }

    @Test
    public void testEmptyPrefixOptionParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(), SelectorUtils.parseOptions("", ":b=c"));
    }

    @Test
    public void testMissinColonOptionParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(Pair.of("a", "1"), Pair.of("b", "2")),
            SelectorUtils.parseOptions("dataFilter", ".data.dataFilter:a=1.dataOption:c=3.dataFilter:b=2"));
    }

    @Test
    public void testOptionParsingUsesFirstEquals()
        throws Exception
    {
        Assert.assertEquals(List.of(Pair.of("a", "1=1"), Pair.of("b", "2=2")),
            SelectorUtils.parseOptions("dataFilter", ".data.dataFilter:a=1=1.dataFilter:b=2=2"));
    }

    @Test
    public void testEscapesInOptionParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(Pair.of("a.1", "1.1"), Pair.of("b=2", "2=2")),
            SelectorUtils.parseOptions("dataFilter", ".data.dataFilter:a\\.1=1\\.1.dataFilter:b\\=2=2\\=2"));
    }

    @Test
    public void testValuelessOptionParsing()
        throws Exception
    {
        Assert.assertEquals(List.of(Pair.of("a", ""), Pair.of("b=2", "")),
            SelectorUtils.parseOptions("dataFilter", ".data.dataFilter:a.dataFilter:b\\=2"));
    }

    @Test
    public void testRealOptionParsing()
        throws Exception
    {
        final String pathInfo = ".data"
            + ".deep"
            + ".dataOption:formSelectors=deep\\.-identify\\.simple"
            + ".dataFilter:createdAfter=2025-01-01T00:00:00\\.000-05:00"
            + ".dataFilter:status=SUBMITTED"
            + ".json";
        Assert.assertEquals(
            List.of("data",
                "deep",
                "dataOption:formSelectors=deep.-identify.simple",
                "dataFilter:createdAfter=2025-01-01T00:00:00.000-05:00",
                "dataFilter:status=SUBMITTED",
                "json"),
            SelectorUtils.parseSelectors(pathInfo));
        Assert.assertEquals(List.of(Pair.of("formSelectors", "deep.-identify.simple")),
            SelectorUtils.parseOptions("dataOption:", pathInfo));
        Assert.assertEquals(
            List.of(
                Pair.of("createdAfter", "2025-01-01T00:00:00.000-05:00"),
                Pair.of("status", "SUBMITTED")),
            SelectorUtils.parseOptions("dataFilter:", pathInfo));
    }

    @Test
    public void testURLDecodingWhenParsingOptions()
        throws Exception
    {
        Assert.assertEquals(List.of(Pair.of(" ID", ""), Pair.of("@", "#")),
            SelectorUtils
                .parseOptions("csvReplaceColumnLabels",
                    ".csvReplaceColumnLabels:%20ID%3D.csvReplaceColumnLabels:%40%3D%23.csv"));
    }

    @Test
    public void testParseToMap()
    {
        Assert.assertEquals(Map.of("formSelectors", "deep.-identify.simple", "descendantData", "2"),
            SelectorUtils.parseOptionsToMap("dataOption:",
                ".data"
                    + ".dataOption:formSelectors=deep\\.-identify\\.simple"
                    + ".dataOption:descendantData=true"
                    + ".dataOption:descendantData=5"
                    + ".dataOption:descendantData=2"));
    }
}
