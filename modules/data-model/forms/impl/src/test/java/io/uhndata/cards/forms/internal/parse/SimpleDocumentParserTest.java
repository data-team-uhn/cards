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
    private static final byte[] DUMMY_BYTES = "dummy content".getBytes(StandardCharsets.UTF_8);

    private static final String SUFFICIENT_CONTENT =
        "<!-- source_file: test.pdf -->\n\nThis paragraph has well over fifty characters of real content.";

    @Test
    public void testSufficientPrimaryResultIsReturnedDirectly()
    {
        final SimpleDocumentParser parser = parserReturning(SUFFICIENT_CONTENT);
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertEquals(SUFFICIENT_CONTENT, result);
    }

    @Test
    public void testEmptyPrimaryResultTriggersFallbackPath()
    {
        final SimpleDocumentParser parser = parserReturning("");
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertEquals("", result);
    }

    @Test
    public void testShortPrimaryResultTriggersFallbackPath()
    {
        final SimpleDocumentParser parser = parserReturning("too short");
        final String result = parser.parse(new ByteArrayInputStream(DUMMY_BYTES), "test.pdf");
        Assert.assertEquals("", result);
    }

    @Test
    public void testStreamReadFailureReturnsEmpty()
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
        final String result = parser.parse(broken, "test.pdf");
        Assert.assertEquals("", result);
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
        };
        parser.parse(new ByteArrayInputStream(input), "test.pdf");
        Assert.assertArrayEquals(input, capturedContent[0]);
    }

    private static SimpleDocumentParser parserReturning(final String primaryResult)
    {
        return new SimpleDocumentParser()
        {
            @Override
            protected String runPrimaryGenerator(final byte[] content, final String fileName)
            {
                return primaryResult;
            }
        };
    }
}
