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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate markdown output from DOCX input.
 *
 * @version $Id$
 */
public class DocxMarkdownGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DocxMarkdownGenerator.class);

    /**
     * Convert DOCX content to markdown grouped by paragraph blocks.
     *
     * @param stream the docx stream
     * @param documentId identifier for the parsed document
     * @param fileName source file name
     * @return markdown text
     * @throws IOException when reading fails
     */
    public String toMarkdown(final InputStream stream, final String documentId, final String fileName)
        throws IOException
    {
        final long startTimestamp = System.currentTimeMillis();
        LOGGER.info("DOCX markdown parsing started for document '{}' and file '{}' at {}", documentId, fileName,
            startTimestamp);
        StringBuilder markdown = new StringBuilder();
        markdown.append("<!-- document_id: ").append(escapeComment(documentId)).append(" -->\n");
        markdown.append("<!-- source_file: ").append(escapeComment(fileName)).append(" -->\n");

        try (XWPFDocument document = new XWPFDocument(stream)) {
            int blockIndex = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph paragraph = (XWPFParagraph) element;
                    String text = cleanText(paragraph.getText());
                    if (StringUtils.isBlank(text)) {
                        continue;
                    }
                    blockIndex++;
                    markdown.append("\n\n<!-- block: ").append(blockIndex).append(" -->\n");
                    int headingLevel = getHeadingLevel(paragraph);
                    if (headingLevel > 0) {
                        markdown.append("#".repeat(headingLevel)).append(' ').append(text).append('\n');
                    } else {
                        markdown.append(text).append('\n');
                    }
                } else if (element instanceof XWPFTable) {
                    String tableMarkdown = tableToMarkdown((XWPFTable) element);
                    if (StringUtils.isNotBlank(tableMarkdown)) {
                        blockIndex++;
                        markdown.append("\n\n<!-- block: ").append(blockIndex).append(" -->\n");
                        markdown.append(tableMarkdown).append('\n');
                    }
                }
            }

            return markdown.toString().trim();
        } finally {
            final long endTimestamp = System.currentTimeMillis();
            final long totalMilliseconds = endTimestamp - startTimestamp;
            LOGGER.info("DOCX markdown parsing finished for document '{}' and file '{}' at {} (total {} ms)",
                documentId, fileName, endTimestamp, totalMilliseconds);
        }
    }

    private String tableToMarkdown(final XWPFTable table)
    {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean headerSeparatorWritten = false;
        for (XWPFTableRow row : rows) {
            List<XWPFTableCell> cells = row.getTableCells();
            sb.append("| ");
            for (XWPFTableCell cell : cells) {
                sb.append(cleanText(cell.getText()).replace("|", "\\|")).append(" | ");
            }
            sb.append('\n');
            if (!headerSeparatorWritten) {
                headerSeparatorWritten = true;
                sb.append("|");
                for (int i = 0; i < cells.size(); i++) {
                    sb.append("---|");
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private int getHeadingLevel(final XWPFParagraph paragraph)
    {
        String style = StringUtils.defaultString(paragraph.getStyle()).toLowerCase();
        if (style.startsWith("heading")) {
            String levelString = style.replace("heading", "").trim();
            if (StringUtils.isNumeric(levelString)) {
                int level = Integer.parseInt(levelString);
                if (level >= 1 && level <= 6) {
                    return level;
                }
            }
            return 1;
        }
        return 0;
    }

    private String cleanText(final String text)
    {
        if (text == null) {
            return "";
        }
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replaceAll("[ \\t]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private String escapeComment(final String value)
    {
        if (value == null) {
            return "";
        }
        return value.replace("--", "—");
    }
}
