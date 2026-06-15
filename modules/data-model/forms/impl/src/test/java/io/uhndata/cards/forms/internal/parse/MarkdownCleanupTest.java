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
 * Tests for {@link MarkdownCleanup}.
 *
 * @version $Id$
 */
public class MarkdownCleanupTest
{
    @Test
    public void testNullInputReturnsEmptyString()
    {
        Assert.assertEquals("", MarkdownCleanup.clean(null));
    }

    @Test
    public void testBlankInputIsUnchanged()
    {
        Assert.assertEquals("   ", MarkdownCleanup.clean("   "));
    }

    @Test
    public void testCollapsesExcessiveBlankLines()
    {
        final String input = "Line one\n\n\n\nLine two";
        Assert.assertEquals("Line one\n\nLine two", MarkdownCleanup.clean(input));
    }

    @Test
    public void testRemovesEmptyHeadings()
    {
        final String input = "Intro\n##\n## _\n###\nContent";
        Assert.assertEquals("Intro\nContent", MarkdownCleanup.clean(input));
    }

    @Test
    public void testRemovesGarbageLines()
    {
        final String input = "Title\n||||||\n______\n...\n...\nBody";
        Assert.assertEquals("Title\nBody", MarkdownCleanup.clean(input));
    }

    @Test
    public void testPreservesValidHeadingsAndContent()
    {
        final String input = "## Real heading\n\nParagraph text.";
        Assert.assertEquals("## Real heading\n\nParagraph text.", MarkdownCleanup.clean(input));
    }
}
