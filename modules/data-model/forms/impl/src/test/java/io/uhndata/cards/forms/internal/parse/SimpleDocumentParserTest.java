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
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link SimpleDocumentParser} orchestration: Docling is the only processor, and an unreachable
 * daemon or insufficient output fails the parse.
 *
 * @version $Id$
 */
public class SimpleDocumentParserTest
{
    private static final String OUTPUT_DIR_PROPERTY = "cards.parse.output.dir";

    private static final String LONG_CONTENT = "A".repeat(100);

    private static final byte[] DUMMY_BYTES = "dummy content".getBytes(StandardCharsets.UTF_8);

    private static final String SUFFICIENT_CONTENT =
        "<!-- source_file: test.pdf -->\n\nThis paragraph has well over fifty characters of real content.";

    private static final byte[] EMPTY_BYTES = new byte[0];

    /** Redirects the markdown store's output away from the working directory. */
    @Rule
    public TemporaryFolder outputFolder = new TemporaryFolder();

    private String previousOutputDir;

    @Before
    public void redirectParseOutput()
    {
        this.previousOutputDir = System.getProperty(OUTPUT_DIR_PROPERTY);
        System.setProperty(OUTPUT_DIR_PROPERTY, this.outputFolder.getRoot().getAbsolutePath());
    }

    @After
    public void restoreParseOutput()
    {
        if (this.previousOutputDir == null) {
            System.clearProperty(OUTPUT_DIR_PROPERTY);
        } else {
            System.setProperty(OUTPUT_DIR_PROPERTY, this.previousOutputDir);
        }
    }

    @Test
    public void testSufficientResultIsReturned()
    {
        final SimpleDocumentParser parser = parserReturning(SUFFICIENT_CONTENT);
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertEquals(SUFFICIENT_CONTENT, result);
    }

    @Test(expected = DocumentParseException.class)
    public void testBlankResultFailsTheParse()
    {
        parserReturning("   \n  \t  ").parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
    }

    @Test(expected = DocumentParseException.class)
    public void testContentBelowThresholdFailsTheParse()
    {
        parserReturning("A".repeat(49)).parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
    }

    @Test
    public void testContentAtThresholdIsReturned()
    {
        assertParsed("A".repeat(50));
    }

    @Test
    public void testContentAboveThresholdIsReturned()
    {
        assertParsed(LONG_CONTENT);
    }

    @Test(expected = DocumentParseException.class)
    public void testHtmlCommentsAreStrippedBeforeSufficiencyCheck()
    {
        final String bigComment = "<!-- This comment is deliberately long enough to exceed fifty characters -->";
        parserReturning(bigComment + "short").parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
    }

    @Test
    public void testSufficientContentWithSourceFileCommentIsReturned()
    {
        assertParsed("<!-- source_file: report.pdf -->\n" + LONG_CONTENT);
    }

    @Test(expected = DocumentParseException.class)
    public void testOnlyHtmlCommentsFailsTheParse()
    {
        parserReturning("<!-- source_file: report.pdf --><!-- page: 1 -->")
            .parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
    }

    @Test(expected = DocumentParseException.class)
    public void testPageHeadersAreStrippedBeforeSufficiencyCheck()
    {
        parserReturning("## Page 1\n## Page 2\n## Page 3\n## Page 4")
            .parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
    }

    @Test
    public void testSufficientContentAfterPageHeaderStrippingIsReturned()
    {
        assertParsed("## Page 1\n\n" + LONG_CONTENT);
    }

    @Test(expected = DocumentParseException.class)
    public void testNullParseResultFailsTheParse()
    {
        parserReturningDocument(null).parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
    }

    @Test(expected = DocumentParseException.class)
    public void testGeneratorExceptionFailsTheParse()
    {
        final SimpleDocumentParser parser = new SimpleDocumentParser()
        {
            @Override
            protected DoclingParseClient.ParsedDocument runPrimaryParse(final byte[] content, final String fileName)
            {
                throw new IllegalStateException("simulated Docling failure");
            }
        };
        parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
    }

    @Test(expected = DocumentParseException.class)
    public void testEmptyInputThrows()
    {
        parserReturning(SUFFICIENT_CONTENT).parse(new ByteArrayInputStream(EMPTY_BYTES), "test.pdf");
    }

    @Test(expected = DocumentParseException.class)
    public void testStreamReadFailureThrows()
    {
        final InputStream broken = new InputStream()
        {
            @Override
            public int read()
                throws IOException
            {
                throw new IOException("simulated read failure");
            }
        };
        parserReturning(SUFFICIENT_CONTENT).parse(broken, "test.pdf");
    }

    @Test
    public void testDifferentFileNamesArePassedThrough()
    {
        final String[] capturedName = new String[1];
        final SimpleDocumentParser parser = new SimpleDocumentParser()
        {
            @Override
            protected DoclingParseClient.ParsedDocument runPrimaryParse(final byte[] content, final String fileName)
            {
                capturedName[0] = fileName;
                return document(SUFFICIENT_CONTENT);
            }
        };
        parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "my-document.pdf");
        Assert.assertEquals("my-document.pdf", capturedName[0]);
    }

    @Test
    public void testStreamBytesArePassedToGenerator()
    {
        final byte[] input = "specific content".getBytes(StandardCharsets.UTF_8);
        final byte[][] capturedContent = new byte[1][];
        final SimpleDocumentParser parser = new SimpleDocumentParser()
        {
            @Override
            protected DoclingParseClient.ParsedDocument runPrimaryParse(final byte[] content, final String fileName)
            {
                capturedContent[0] = content;
                return document(SUFFICIENT_CONTENT);
            }
        };
        parser.parse(new ByteArrayInputStream(input), "test.pdf");
        Assert.assertArrayEquals(input, capturedContent[0]);
    }

    private static void assertParsed(final String doclingResult)
    {
        final SimpleDocumentParser parser = parserReturning(doclingResult);
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertEquals(doclingResult, result);
    }

    private static DoclingParseClient.ParsedDocument document(final String markdown)
    {
        return new DoclingParseClient.ParsedDocument(markdown, false, null, null, List.of(), "");
    }

    private static SimpleDocumentParser parserReturning(final String markdown)
    {
        return parserReturningDocument(document(markdown));
    }

    private static SimpleDocumentParser parserReturningDocument(final DoclingParseClient.ParsedDocument parsed)
    {
        return new SimpleDocumentParser()
        {
            @Override
            protected DoclingParseClient.ParsedDocument runPrimaryParse(final byte[] content, final String fileName)
            {
                return parsed;
            }
        };
    }
}
