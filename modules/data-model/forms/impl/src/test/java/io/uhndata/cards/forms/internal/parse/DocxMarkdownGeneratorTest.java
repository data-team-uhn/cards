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

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link DocxMarkdownGenerator} using programmatically constructed DOCX streams.
 *
 * @version $Id$
 */
public class DocxMarkdownGeneratorTest
{
    private final DocxMarkdownGenerator generator = new DocxMarkdownGenerator();

    @Test
    public void testSourceFileCommentIsPresent() throws IOException
    {
        final String markdown = this.generator.toMarkdown(docxWithText("Hello"), "report.docx");
        Assert.assertTrue(markdown.contains("<!-- source_file: report.docx -->"));
    }

    @Test
    public void testPlainParagraphTextIsExtracted() throws IOException
    {
        final String markdown = this.generator.toMarkdown(docxWithText("Hello World"), "test.docx");
        Assert.assertTrue(markdown.contains("Hello World"));
    }

    @Test
    public void testBoldRunIsFormattedWithDoubleAsterisks() throws IOException
    {
        final String markdown = this.generator.toMarkdown(docxWithBold("Bold text"), "test.docx");
        Assert.assertTrue(markdown.contains("**Bold text**"));
    }

    @Test
    public void testItalicRunIsFormattedWithSingleAsterisks() throws IOException
    {
        final String markdown = this.generator.toMarkdown(docxWithItalic("Italic text"), "test.docx");
        Assert.assertTrue(markdown.contains("*Italic text*"));
    }

    @Test
    public void testLargeBoldParagraphIsDetectedAsHeading() throws IOException
    {
        final String markdown = this.generator.toMarkdown(docxWithLargeBold("My Heading"), "test.docx");
        Assert.assertTrue(markdown.contains("# My Heading"));
    }

    @Test
    public void testTableProducesMarkdownPipeRows() throws IOException
    {
        final String markdown = this.generator.toMarkdown(docxWithTable(), "test.docx");
        Assert.assertTrue(markdown.contains("|"));
        Assert.assertTrue(markdown.contains("---"));
        Assert.assertTrue(markdown.contains("Header 1"));
        Assert.assertTrue(markdown.contains("Cell 1"));
    }

    @Test
    public void testEmptyDocumentContainsOnlySourceComment() throws IOException
    {
        try (XWPFDocument doc = new XWPFDocument()) {
            final String markdown = this.generator.toMarkdown(toStream(doc), "empty.docx");
            Assert.assertTrue(markdown.contains("<!-- source_file: empty.docx -->"));
        }
    }

    @Test
    public void testFileNameWithSpecialCharsIsEscapedInComment() throws IOException
    {
        final String markdown = this.generator.toMarkdown(docxWithText("x"), "my--report.docx");
        Assert.assertTrue(markdown.contains("<!-- source_file: my—report.docx -->"));
    }

    private static ByteArrayInputStream docxWithText(final String text) throws IOException
    {
        try (XWPFDocument doc = new XWPFDocument()) {
            final XWPFParagraph para = doc.createParagraph();
            final XWPFRun run = para.createRun();
            run.setText(text);
            return toStream(doc);
        }
    }

    private static ByteArrayInputStream docxWithBold(final String text) throws IOException
    {
        try (XWPFDocument doc = new XWPFDocument()) {
            final XWPFParagraph para = doc.createParagraph();
            final XWPFRun run = para.createRun();
            run.setBold(true);
            run.setText(text);
            return toStream(doc);
        }
    }

    private static ByteArrayInputStream docxWithItalic(final String text) throws IOException
    {
        try (XWPFDocument doc = new XWPFDocument()) {
            final XWPFParagraph para = doc.createParagraph();
            final XWPFRun run = para.createRun();
            run.setItalic(true);
            run.setText(text);
            return toStream(doc);
        }
    }

    private static ByteArrayInputStream docxWithLargeBold(final String text) throws IOException
    {
        try (XWPFDocument doc = new XWPFDocument()) {
            final XWPFParagraph para = doc.createParagraph();
            para.setStyle("Heading1");
            final XWPFRun run = para.createRun();
            run.setText(text);
            return toStream(doc);
        }
    }

    private static ByteArrayInputStream docxWithTable() throws IOException
    {
        try (XWPFDocument doc = new XWPFDocument()) {
            final XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Header 1");
            table.getRow(0).getCell(1).setText("Header 2");
            table.getRow(1).getCell(0).setText("Cell 1");
            table.getRow(1).getCell(1).setText("Cell 2");
            return toStream(doc);
        }
    }

    private static ByteArrayInputStream toStream(final XWPFDocument doc) throws IOException
    {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.write(baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }
}
