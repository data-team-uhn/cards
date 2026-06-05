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
package io.uhndata.cards.forms.internal.parse;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link DoclingFallbackMarkdownGenerator#isSufficient(String)}.
 *
 * @version $Id$
 */
public class DoclingFallbackMarkdownGeneratorTest
{
    private static final String LONG_CONTENT = "A".repeat(100);

    @Test
    public void testNullIsNotSufficient()
    {
        Assert.assertFalse(DoclingFallbackMarkdownGenerator.isSufficient(null));
    }

    @Test
    public void testEmptyStringIsNotSufficient()
    {
        Assert.assertFalse(DoclingFallbackMarkdownGenerator.isSufficient(""));
    }

    @Test
    public void testBlankStringIsNotSufficient()
    {
        Assert.assertFalse(DoclingFallbackMarkdownGenerator.isSufficient("   \n  \t  "));
    }

    @Test
    public void testContentBelowThresholdIsNotSufficient()
    {
        Assert.assertFalse(DoclingFallbackMarkdownGenerator.isSufficient("short"));
        Assert.assertFalse(DoclingFallbackMarkdownGenerator.isSufficient("A".repeat(49)));
    }

    @Test
    public void testContentAtThresholdIsSufficient()
    {
        Assert.assertTrue(DoclingFallbackMarkdownGenerator.isSufficient("A".repeat(50)));
    }

    @Test
    public void testContentAboveThresholdIsSufficient()
    {
        Assert.assertTrue(DoclingFallbackMarkdownGenerator.isSufficient(LONG_CONTENT));
    }

    @Test
    public void testHtmlCommentsAreStrippedBeforeCheck()
    {
        final String bigComment = "<!-- This comment is deliberately long enough to exceed fifty characters -->";
        Assert.assertFalse(DoclingFallbackMarkdownGenerator.isSufficient(bigComment + "short"));
    }

    @Test
    public void testSufficientContentWithSourceFileCommentIsSufficient()
    {
        final String withComment = "<!-- source_file: report.pdf -->\n" + LONG_CONTENT;
        Assert.assertTrue(DoclingFallbackMarkdownGenerator.isSufficient(withComment));
    }

    @Test
    public void testOnlyHtmlCommentsAreNotSufficient()
    {
        final String onlyComments = "<!-- source_file: report.pdf --><!-- page: 1 -->";
        Assert.assertFalse(DoclingFallbackMarkdownGenerator.isSufficient(onlyComments));
    }

    @Test
    public void testPageHeadersAreStrippedBeforeCheck()
    {
        final String onlyHeaders = "## Page 1\n## Page 2\n## Page 3\n## Page 4";
        Assert.assertFalse(DoclingFallbackMarkdownGenerator.isSufficient(onlyHeaders));
    }

    @Test
    public void testSufficientContentAfterPageHeaderStripping()
    {
        final String withPageHeader = "## Page 1\n\n" + LONG_CONTENT;
        Assert.assertTrue(DoclingFallbackMarkdownGenerator.isSufficient(withPageHeader));
    }

    @Test
    public void testTypicalDocumentOutputIsSufficient()
    {
        final String typical = "<!-- source_file: report.pdf -->\n\n"
            + "## Page 1\n\n"
            + "This is the introduction to the document with enough content to pass the threshold check.";
        Assert.assertTrue(DoclingFallbackMarkdownGenerator.isSufficient(typical));
    }
}
