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
 * Tests for {@link FileParserFactory} routing logic.
 *
 * @version $Id$
 */
public class FileParserFactoryTest
{
    private final FileParserFactory factory = new FileParserFactory();

    @Test
    public void testPdfExtensionReturnsPdfParser()
    {
        final FileParser parser = this.factory.getParser("report.pdf");
        Assert.assertNotNull(parser);
        Assert.assertEquals("PdfParser", parser.getClass().getSimpleName());
    }

    @Test
    public void testPdfExtensionIsCaseInsensitive()
    {
        Assert.assertNotNull(this.factory.getParser("report.PDF"));
        Assert.assertNotNull(this.factory.getParser("report.Pdf"));
    }

    @Test
    public void testDocxExtensionReturnsDocxParser()
    {
        final FileParser parser = this.factory.getParser("report.docx");
        Assert.assertNotNull(parser);
        Assert.assertEquals("DocxParser", parser.getClass().getSimpleName());
    }

    @Test
    public void testDocxExtensionIsCaseInsensitive()
    {
        Assert.assertNotNull(this.factory.getParser("report.DOCX"));
        Assert.assertNotNull(this.factory.getParser("report.Docx"));
    }

    @Test
    public void testDocExtensionReturnsDocParser()
    {
        final FileParser parser = this.factory.getParser("report.doc");
        Assert.assertNotNull(parser);
        Assert.assertEquals("DocParser", parser.getClass().getSimpleName());
    }

    @Test
    public void testDocExtensionIsCaseInsensitive()
    {
        Assert.assertNotNull(this.factory.getParser("report.DOC"));
        Assert.assertNotNull(this.factory.getParser("report.Doc"));
    }

    @Test
    public void testUnsupportedExtensionReturnsNull()
    {
        Assert.assertNull(this.factory.getParser("report.txt"));
        Assert.assertNull(this.factory.getParser("report.xlsx"));
        Assert.assertNull(this.factory.getParser("report.pptx"));
        Assert.assertNull(this.factory.getParser("report.html"));
        Assert.assertNull(this.factory.getParser("report.csv"));
    }

    @Test
    public void testEmptyFileNameReturnsNull()
    {
        Assert.assertNull(this.factory.getParser(""));
    }

    @Test
    public void testBlankFileNameReturnsNull()
    {
        Assert.assertNull(this.factory.getParser("   "));
    }

    @Test
    public void testFileNameWithoutExtensionReturnsNull()
    {
        Assert.assertNull(this.factory.getParser("nodothere"));
    }

    @Test
    public void testFileNameWithSpacesInNameIsHandled()
    {
        Assert.assertNotNull(this.factory.getParser("my document.pdf"));
        Assert.assertNotNull(this.factory.getParser("my document.docx"));
    }
}
