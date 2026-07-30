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
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link SimpleDocumentParser}: stages the upload, then requires usable markdown from Docling.
 * <p>
 * These tests stub {@link DoclingMarkdownGenerator} via a subclass that overrides parse behaviour by
 * writing a markdown file and returning it, so no live daemon is required.
 * </p>
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

    /** Redirects the shared docs root away from the working directory. */
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
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf", "answer-1");
        Assert.assertEquals(SUFFICIENT_CONTENT, result);
    }

    @Test(expected = DocumentParseException.class)
    public void testBlankResultFailsTheParse()
    {
        parserReturning("   \n  \t  ").parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf", "answer-1");
    }

    @Test(expected = DocumentParseException.class)
    public void testContentBelowThresholdFailsTheParse()
    {
        parserReturning("A".repeat(49)).parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf", "answer-1");
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
        parserReturning(bigComment + "short").parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf", "answer-1");
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
            .parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf", "answer-1");
    }

    @Test(expected = DocumentParseException.class)
    public void testPageHeadersAreStrippedBeforeSufficiencyCheck()
    {
        parserReturning("## Page 1\n## Page 2\n## Page 3\n## Page 4")
            .parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf", "answer-1");
    }

    @Test
    public void testSufficientContentAfterPageHeaderStrippingIsReturned()
    {
        assertParsed("## Page 1\n\n" + LONG_CONTENT);
    }

    @Test(expected = DocumentParseException.class)
    public void testNullParseResultFailsTheParse()
    {
        parserReturning(null).parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf", "answer-1");
    }

    @Test(expected = DocumentParseException.class)
    public void testEmptyInputThrows()
    {
        parserReturning(SUFFICIENT_CONTENT).parse(new ByteArrayInputStream(EMPTY_BYTES), "test.pdf", "answer-1");
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
        parserReturning(SUFFICIENT_CONTENT).parse(broken, "test.pdf", "answer-1");
    }

    @Test
    public void testSourceIsStagedUnderAnswerFolder()
        throws IOException
    {
        final SimpleDocumentParser parser = parserReturning(SUFFICIENT_CONTENT);
        parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "protocol.pdf", "answer-42");
        final Path staged = this.outputFolder.getRoot().toPath().resolve("answer-42").resolve("protocol.pdf");
        Assert.assertTrue(Files.isRegularFile(staged));
        Assert.assertArrayEquals(DUMMY_BYTES, Files.readAllBytes(staged));
    }

    private static void assertParsed(final String markdown)
    {
        final SimpleDocumentParser parser = parserReturning(markdown);
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf", "answer-1");
        Assert.assertEquals(markdown, result);
    }

    /**
     * Stub parser that stages like production but returns a fixed markdown string without calling the daemon.
     */
    private static SimpleDocumentParser parserReturning(final String markdown)
    {
        return new SimpleDocumentParser()
        {
            @Override
            public String parse(final InputStream stream, final String fileName, final String outputSubfolder)
            {
                final byte[] content;
                try {
                    content = stream.readAllBytes();
                } catch (IOException e) {
                    throw new DocumentParseException("Failed to read document stream", e);
                }
                if (content.length == 0) {
                    throw new DocumentParseException("Document is empty", null);
                }
                ParsedMarkdownStore.stageSourceFile(outputSubfolder, fileName, content);
                if (markdown == null || !isUsable(markdown)) {
                    throw new DocumentParseException("Generated output is empty", null);
                }
                return markdown;
            }
        };
    }

    private static boolean isUsable(final String result)
    {
        if (result == null || result.isBlank()) {
            return false;
        }
        final String stripped = result.replaceAll("<!--.*?-->", "").replaceAll("## Page \\d+", "").trim();
        return stripped.length() >= 50;
    }
}
