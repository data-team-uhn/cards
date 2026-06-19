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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link SimpleDocumentParser} template method orchestration.
 *
 * @version $Id$
 */
public class SimpleDocumentParserTest
{
    private static final String LONG_CONTENT = "A".repeat(100);

    private static final byte[] DUMMY_BYTES = "dummy content".getBytes(StandardCharsets.UTF_8);

    private static final String SUFFICIENT_CONTENT =
        "<!-- source_file: test.pdf -->\n\nThis paragraph has well over fifty characters of real content.";

    private static final String FALLBACK_CONTENT =
        "<!-- source_file: test.pdf -->\n\nFallback generator produced well over fifty characters of content.";

    private static final byte[] EMPTY_BYTES = new byte[0];

    @Test
    public void testSufficientPrimaryResultIsReturnedDirectly()
    {
        final SimpleDocumentParser parser = parserReturning(SUFFICIENT_CONTENT);
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertEquals(SUFFICIENT_CONTENT, result);
    }

    @Test
    public void testBlankPrimaryResultTriggersFallbackPath()
    {
        assertFallbackUsed("   \n  \t  ");
    }

    @Test
    public void testContentBelowThresholdTriggersFallbackPath()
    {
        assertFallbackUsed("short");
        assertFallbackUsed("A".repeat(49));
    }

    @Test
    public void testContentAtThresholdUsesPrimaryPath()
    {
        assertPrimaryUsed("A".repeat(50));
    }

    @Test
    public void testContentAboveThresholdUsesPrimaryPath()
    {
        assertPrimaryUsed(LONG_CONTENT);
    }

    @Test
    public void testHtmlCommentsAreStrippedBeforeSufficiencyCheck()
    {
        final String bigComment = "<!-- This comment is deliberately long enough to exceed fifty characters -->";
        assertFallbackUsed(bigComment + "short");
    }

    @Test
    public void testSufficientContentWithSourceFileCommentUsesPrimaryPath()
    {
        assertPrimaryUsed("<!-- source_file: report.pdf -->\n" + LONG_CONTENT);
    }

    @Test
    public void testOnlyHtmlCommentsTriggersFallbackPath()
    {
        assertFallbackUsed("<!-- source_file: report.pdf --><!-- page: 1 -->");
    }

    @Test
    public void testPageHeadersAreStrippedBeforeSufficiencyCheck()
    {
        assertFallbackUsed("## Page 1\n## Page 2\n## Page 3\n## Page 4");
    }

    @Test
    public void testSufficientContentAfterPageHeaderStrippingUsesPrimaryPath()
    {
        assertPrimaryUsed("## Page 1\n\n" + LONG_CONTENT);
    }

    @Test
    public void testTypicalDocumentOutputUsesPrimaryPath()
    {
        assertPrimaryUsed("<!-- source_file: report.pdf -->\n\n"
            + "## Page 1\n\n"
            + "This is the introduction to the document with enough content to pass the threshold check.");
    }

    @Test
    public void testEmptyPrimaryResultTriggersFallbackPath()
    {
        final boolean[] fallbackCalled = {false};
        final SimpleDocumentParser parser = parserWithFallback(primaryReturning(""), () -> fallbackCalled[0] = true);
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertTrue(fallbackCalled[0]);
        Assert.assertEquals(FALLBACK_CONTENT, result);
    }

    @Test
    public void testShortPrimaryResultTriggersFallbackPath()
    {
        final boolean[] fallbackCalled = {false};
        final SimpleDocumentParser parser = parserWithFallback(
            primaryReturning("too short"), () -> fallbackCalled[0] = true);
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertTrue(fallbackCalled[0]);
        Assert.assertEquals(FALLBACK_CONTENT, result);
    }

    @Test
    public void testPrimaryExceptionTriggersFallbackPath()
    {
        final boolean[] fallbackCalled = {false};
        final SimpleDocumentParser parser = parserWithFallback(primaryThrowing(), () -> fallbackCalled[0] = true);
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertTrue(fallbackCalled[0]);
        Assert.assertEquals(FALLBACK_CONTENT, result);
    }

    @Test(expected = DocumentParseException.class)
    public void testEmptyInputThrows()
    {
        final SimpleDocumentParser parser = parserReturning(SUFFICIENT_CONTENT);
        parser.parse(new ByteArrayInputStream(EMPTY_BYTES), "test.pdf");
    }

    @Test(expected = DocumentParseException.class)
    public void testStreamReadFailureThrows()
    {
        final SimpleDocumentParser parser = parserReturning(SUFFICIENT_CONTENT);
        final InputStream broken = new InputStream()
        {
            @Override
            public int read()
                throws IOException
            {
                throw new IOException("simulated read failure");
            }
        };
        parser.parse(broken, "test.pdf");
    }

    @Test
    public void testDifferentFileNamesArePassedThrough()
    {
        final String[] capturedName = new String[1];
        final SimpleDocumentParser parser = new SimpleDocumentParser()
        {
            @Override
            protected String runPrimaryGenerator(final byte[] content, final String fileName)
            {
                capturedName[0] = fileName;
                return SUFFICIENT_CONTENT;
            }

            @Override
            protected String runFallbackGenerator(final byte[] content, final String fileName)
            {
                return "";
            }
        };
        parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "my-document.pdf");
        Assert.assertEquals("my-document.pdf", capturedName[0]);
    }

    @Test
    public void testStreamBytesArePassedToPrimaryGenerator()
    {
        final byte[] input = "specific content".getBytes(StandardCharsets.UTF_8);
        final byte[][] capturedContent = new byte[1][];
        final SimpleDocumentParser parser = new SimpleDocumentParser()
        {
            @Override
            protected String runPrimaryGenerator(final byte[] content, final String fileName)
            {
                capturedContent[0] = content;
                return SUFFICIENT_CONTENT;
            }

            @Override
            protected String runFallbackGenerator(final byte[] content, final String fileName)
            {
                return "";
            }
        };
        parser.parse(new ByteArrayInputStream(input), "test.pdf");
        Assert.assertArrayEquals(input, capturedContent[0]);
    }

    private static void assertPrimaryUsed(final String primaryResult)
    {
        final SimpleDocumentParser parser = parserReturning(primaryResult);
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertEquals(primaryResult, result);
    }

    private static void assertFallbackUsed(final String primaryResult)
    {
        final boolean[] fallbackCalled = {false};
        final SimpleDocumentParser parser = parserWithFallback(primaryReturning(primaryResult),
            () -> fallbackCalled[0] = true);
        parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertTrue(fallbackCalled[0]);
    }

    private static SimpleDocumentParser parserReturning(final String primaryResult)
    {
        return primaryReturning(primaryResult);
    }

    private static SimpleDocumentParser primaryReturning(final String primaryResult)
    {
        return new SimpleDocumentParser()
        {
            @Override
            protected String runPrimaryGenerator(final byte[] content, final String fileName)
            {
                return primaryResult;
            }

            @Override
            protected String runFallbackGenerator(final byte[] content, final String fileName)
            {
                return FALLBACK_CONTENT;
            }
        };
    }

    private static SimpleDocumentParser primaryThrowing()
    {
        return new SimpleDocumentParser()
        {
            @Override
            protected String runPrimaryGenerator(final byte[] content, final String fileName)
            {
                throw new IllegalStateException("simulated primary failure");
            }

            @Override
            protected String runFallbackGenerator(final byte[] content, final String fileName)
            {
                return FALLBACK_CONTENT;
            }
        };
    }

    private static SimpleDocumentParser parserWithFallback(final SimpleDocumentParser primaryParser,
        final Runnable onFallback)
    {
        return new SimpleDocumentParser()
        {
            @Override
            protected String runPrimaryGenerator(final byte[] content, final String fileName)
            {
                return primaryParser.runPrimaryGenerator(content, fileName);
            }

            @Override
            protected String runFallbackGenerator(final byte[] content, final String fileName)
            {
                onFallback.run();
                return FALLBACK_CONTENT;
            }
        };
    }
}
