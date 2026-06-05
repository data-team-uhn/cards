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
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link PdfMarkdownGenerator} using programmatically constructed PDF streams.
 *
 * @version $Id$
 */
public class PdfMarkdownGeneratorTest
{
    private final PdfMarkdownGenerator generator = new PdfMarkdownGenerator();

    @Test
    public void testSourceFileCommentIsPresent() throws IOException
    {
        final String markdown = this.generator.toMarkdown(pdfWithText("Hello"), "report.pdf");
        Assert.assertTrue(markdown.contains("<!-- source_file: report.pdf -->"));
    }

    @Test
    public void testPageHeaderIsPresent() throws IOException
    {
        final String markdown = this.generator.toMarkdown(pdfWithText("Hello"), "test.pdf");
        Assert.assertTrue(markdown.contains("## Page 1"));
    }

    @Test
    public void testTextIsExtracted() throws IOException
    {
        final String markdown = this.generator.toMarkdown(pdfWithText("Hello World"), "test.pdf");
        Assert.assertTrue(markdown.contains("Hello World"));
    }

    @Test
    public void testEmptyPageProducesNoTextMessage() throws IOException
    {
        final String markdown = this.generator.toMarkdown(emptyPdf(), "test.pdf");
        Assert.assertTrue(markdown.contains("_No extractable text on this page._"));
    }

    @Test
    public void testMultiplePagesProduceMultiplePageHeaders() throws IOException
    {
        final String markdown = this.generator.toMarkdown(
            pdfWithPages("First page text", "Second page text"), "test.pdf");
        Assert.assertTrue(markdown.contains("## Page 1"));
        Assert.assertTrue(markdown.contains("## Page 2"));
        Assert.assertTrue(markdown.contains("First page text"));
        Assert.assertTrue(markdown.contains("Second page text"));
    }

    @Test
    public void testFileNameWithSpecialCharsIsEscapedInComment() throws IOException
    {
        final String markdown = this.generator.toMarkdown(pdfWithText("x"), "my--report.pdf");
        Assert.assertTrue(markdown.contains("<!-- source_file: my—report.pdf -->"));
    }

    @Test
    public void testCorruptStreamThrowsIOException()
    {
        final byte[] garbage = new byte[]{0x25, 0x50, 0x44, 0x46, 0x00};
        try {
            this.generator.toMarkdown(new ByteArrayInputStream(garbage), "bad.pdf");
            Assert.fail("Expected IOException for corrupt PDF stream");
        } catch (IOException e) {
            Assert.assertNotNull(e);
        }
    }

    private static ByteArrayInputStream pdfWithText(final String text) throws IOException
    {
        return pdfWithPages(text);
    }

    private static ByteArrayInputStream pdfWithPages(final String... pageTexts) throws IOException
    {
        try (PDDocument doc = new PDDocument()) {
            for (final String text : pageTexts) {
                addPage(doc, text);
            }
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new ByteArrayInputStream(baos.toByteArray());
        }
    }

    private static ByteArrayInputStream emptyPdf() throws IOException
    {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new ByteArrayInputStream(baos.toByteArray());
        }
    }

    private static void addPage(final PDDocument doc, final String text) throws IOException
    {
        final PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(100, 700);
            content.showText(text);
            content.endText();
        }
    }
}
