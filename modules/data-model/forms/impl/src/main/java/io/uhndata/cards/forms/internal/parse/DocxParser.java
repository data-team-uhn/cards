/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.forms.internal.parse;

/**
 * Parser for DOCX files. Delegates orchestration to {@link SimpleDocumentParser}, which parses through
 * {@link DoclingMarkdownGenerator}, and renders a PDF sibling of the document beside the parse output.
 *
 * @version $Id$
 */
public class DocxParser extends SimpleDocumentParser
{
    @Override
    protected void onDocumentBytes(final byte[] content, final String fileName, final String outputSubfolder)
    {
        // Skip when the DOC path is driving this parse: it converts the original DOC to PDF itself, so the
        // intermediate DOCX must not also be rendered.
        if (LibreOfficeConverter.isDocxPdfSuppressed()) {
            return;
        }
        LibreOfficeConverter.convertToPdfAsync(content, "docx", fileName, outputSubfolder);
    }
}
