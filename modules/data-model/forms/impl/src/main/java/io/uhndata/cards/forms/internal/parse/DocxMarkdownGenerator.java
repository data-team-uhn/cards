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
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.ISDTContent;
import org.apache.poi.xwpf.usermodel.ISDTContents;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFEndnote;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFSDTContent;
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

    private static final String BODY_SECTION = "body";

    private static final String NEWLINE = "\n";

    private static final String BULLET_NUM_FMT = "bullet";

    private static final int MAX_NUMBERING_LEVEL = 9;

    private static final double HEADING_FONT_LEVEL1 = 15.0;

    private static final double HEADING_FONT_LEVEL2 = 13.0;

    private static final double HEADING_FONT_LEVEL3 = 12.0;

    private static final double HEADING_FONT_LEVEL4 = 11.0;

    private static final double HEADING_FONT_LEVEL5 = 10.0;

    private static final double HEADING_FONT_LEVEL6 = 9.0;

    private static final String CODE_FENCE = "```";

    private static final int HEADING_MAX_TEXT_LENGTH = 200;

    private static final double[] HEADING_FONT_THRESHOLDS = {
        HEADING_FONT_LEVEL1,
        HEADING_FONT_LEVEL2,
        HEADING_FONT_LEVEL3,
        HEADING_FONT_LEVEL4,
        HEADING_FONT_LEVEL5,
        HEADING_FONT_LEVEL6,
    };

    private static final String[] CODE_START_KEYWORDS = {
        "from ",
        "import ",
        "def ",
        "class ",
        "print(",
        "return ",
        "#",
    };

    private static final String[] CODE_IDENTIFIERS = {
        "DocumentConverter",
        "convert_single",
        "render_as_markdown",
    };

    /**
     * Convert DOCX content to markdown grouped by paragraph blocks.
     *
     * @param stream the docx stream
     * @param fileName source file name
     * @return markdown text
     * @throws IOException when reading fails
     */
    public String toMarkdown(final InputStream stream, final String fileName)
        throws IOException
    {
        final long startTimestamp = System.currentTimeMillis();
        LOGGER.info("DOCX markdown parsing started for file '{}' at {}", fileName, startTimestamp);
        StringBuilder markdown = new StringBuilder();
        markdown.append("<!-- source_file: ").append(this.escapeComment(fileName)).append(" -->").append(NEWLINE);

        try (XWPFDocument document = new XWPFDocument(stream)) {
            ParseState state = new ParseState(markdown);
            this.walkDocumentParts(document, state);
            return markdown.toString().trim();
        } finally {
            final long endTimestamp = System.currentTimeMillis();
            final long totalMilliseconds = endTimestamp - startTimestamp;
            LOGGER.info("DOCX markdown parsing finished for file '{}' at {} (total {} ms)",
                fileName, endTimestamp, totalMilliseconds);
        }
    }

    private void walkDocumentParts(final XWPFDocument document, final ParseState state)
    {
        for (XWPFHeader header : document.getHeaderList()) {
            this.walkBodyElements(header.getBodyElements(), state, "header");
        }
        this.walkBodyElements(document.getBodyElements(), state, BODY_SECTION);
        for (XWPFFooter footer : document.getFooterList()) {
            this.walkBodyElements(footer.getBodyElements(), state, "footer");
        }
        List<XWPFFootnote> footnotes = document.getFootnotes();
        if (footnotes != null) {
            for (XWPFFootnote footnote : footnotes) {
                this.walkBodyElements(footnote.getBodyElements(), state, "footnote");
            }
        }
        List<XWPFEndnote> endnotes = document.getEndnotes();
        if (endnotes != null) {
            for (XWPFEndnote endnote : endnotes) {
                this.walkBodyElements(endnote.getBodyElements(), state, "endnote");
            }
        }
    }

    private void walkBodyElements(final List<IBodyElement> elements, final ParseState state, final String section)
    {
        for (IBodyElement element : elements) {
            if (!(element instanceof XWPFParagraph) || !this.isCodeParagraph((XWPFParagraph) element)) {
                this.flushCodeBuffer(state, section);
            }
            if (!this.isTabularParagraphElement(element, section)) {
                this.flushTabularBuffer(state, section);
            }
            if (element instanceof XWPFParagraph) {
                this.renderParagraph((XWPFParagraph) element, state, section);
            } else if (element instanceof XWPFTable) {
                this.renderTable((XWPFTable) element, state, section);
            } else if (element instanceof XWPFSDT) {
                this.renderSdt((XWPFSDT) element, state, section);
            } else {
                this.renderUnsupportedElement(element, state);
            }
        }
        this.flushTabularBuffer(state, section);
        this.flushCodeBuffer(state, section);
    }

    private boolean isTabularParagraphElement(final IBodyElement element, final String section)
    {
        return element instanceof XWPFParagraph && this.isTabularParagraph((XWPFParagraph) element, section);
    }

    private void renderParagraph(final XWPFParagraph paragraph, final ParseState state, final String section)
    {
        if (this.isCodeParagraph(paragraph)) {
            this.flushTabularBuffer(state, section);
            String body = this.renderRuns(paragraph);
            if (StringUtils.isNotBlank(body)) {
                state.codeBuffer.addLine(body);
            }
            return;
        }
        this.flushCodeBuffer(state, section);
        if (this.isTabularParagraph(paragraph, section)) {
            state.tabularBuffer.addRow(this.splitTabularCells(paragraph));
            return;
        }
        String body = this.renderRuns(paragraph);
        String prefix = state.numberingTracker.nextPrefix(paragraph);
        String text;
        if (this.isBulletNumbering(paragraph)) {
            text = this.stripLeadingBulletMarker(body);
        } else {
            text = this.combineNumberingPrefix(prefix, body);
        }
        if (StringUtils.isBlank(text)) {
            return;
        }
        String content = this.formatParagraphContent(paragraph, text);
        this.appendBlock(state, section, content);
    }

    private String stripLeadingBulletMarker(final String text)
    {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        String trimmed = text.stripLeading();
        if (trimmed.isEmpty()) {
            return text;
        }
        char firstChar = trimmed.charAt(0);
        if (firstChar == '\u2022' || firstChar == '\u00B7' || firstChar == '\u25E6' || firstChar == '\u2013') {
            return trimmed.substring(1).stripLeading();
        }
        return text;
    }

    private String combineNumberingPrefix(final String prefix, final String body)
    {
        if (StringUtils.isBlank(prefix)) {
            return body;
        }
        String trimmedPrefix = prefix.trim();
        if (StringUtils.isBlank(body)) {
            return trimmedPrefix;
        }
        return trimmedPrefix + " " + body;
    }

    private String formatParagraphContent(final XWPFParagraph paragraph, final String text)
    {
        int headingLevel = this.getHeadingLevel(paragraph, text);
        if (headingLevel > 0) {
            return "#".repeat(headingLevel) + " " + text + NEWLINE;
        }
        BigInteger numId = paragraph.getNumID();
        if (numId != null) {
            int indent = paragraph.getNumIlvl() == null ? 0 : paragraph.getNumIlvl().intValue();
            String indentPrefix = "  ".repeat(Math.max(0, indent));
            if (this.isBulletNumbering(paragraph)) {
                return indentPrefix + "- " + text + NEWLINE;
            }
            return indentPrefix + text + NEWLINE;
        }
        return text + NEWLINE;
    }

    private void flushCodeBuffer(final ParseState state, final String section)
    {
        if (state.codeBuffer.isEmpty()) {
            return;
        }
        String codeBlock = state.codeBuffer.toMarkdown();
        state.codeBuffer.clear();
        this.appendBlock(state, section, codeBlock);
    }

    private String renderRuns(final XWPFParagraph paragraph)
    {
        StringBuilder output = new StringBuilder();
        List<IRunElement> runs = paragraph.getIRuns();
        for (int index = 0; index < runs.size(); index++) {
            boolean hyperlinkRendered = this.renderRunElement(paragraph, runs.get(index), output);
            if (hyperlinkRendered && this.needsSpaceAfterHyperlink(output, runs, index)) {
                output.append(' ');
            }
        }
        return this.ensureSpaceAfterMarkdownLinks(output.toString().trim());
    }

    private String ensureSpaceAfterMarkdownLinks(final String text)
    {
        return text.replaceAll("(\\[[^\\]]+\\]\\([^)]+\\))([\\p{L}])", "$1 $2");
    }

    private boolean needsSpaceAfterHyperlink(final StringBuilder output, final List<IRunElement> runs,
        final int currentIndex)
    {
        String nextText = this.findNextNonBlankRunText(runs, currentIndex);
        if (StringUtils.isBlank(nextText) || output.length() == 0) {
            return false;
        }
        char lastChar = output.charAt(output.length() - 1);
        if (Character.isWhitespace(lastChar)) {
            return false;
        }
        return !Character.isWhitespace(nextText.charAt(0));
    }

    private String findNextNonBlankRunText(final List<IRunElement> runs, final int currentIndex)
    {
        for (int index = currentIndex + 1; index < runs.size(); index++) {
            String text = this.getRunElementText(runs.get(index));
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return "";
    }

    private boolean renderRunElement(final XWPFParagraph paragraph, final IRunElement runElement,
        final StringBuilder output)
    {
        if (runElement instanceof XWPFHyperlinkRun) {
            return this.appendHyperlinkRun((XWPFHyperlinkRun) runElement, paragraph, output);
        }
        if (runElement instanceof XWPFRun) {
            this.appendStyledRun((XWPFRun) runElement, output);
        } else if (runElement instanceof XWPFSDT) {
            output.append(this.escapeMarkdown(((XWPFSDT) runElement).getContent().getText()));
        }
        return false;
    }

    private boolean appendHyperlinkRun(final XWPFHyperlinkRun linkRun, final XWPFParagraph paragraph,
        final StringBuilder output)
    {
        String label = this.escapeMarkdown(linkRun.text());
        String url = this.resolveHyperlinkUrl(linkRun, paragraph);
        if (StringUtils.isNotBlank(url)) {
            output.append('[').append(label).append("](").append(url).append(')');
            return true;
        }
        if (StringUtils.isNotBlank(label)) {
            output.append(label);
        }
        return false;
    }

    private String resolveHyperlinkUrl(final XWPFHyperlinkRun linkRun, final XWPFParagraph paragraph)
    {
        try {
            XWPFHyperlink link = linkRun.getHyperlink(paragraph.getDocument());
            if (link != null && link.getURL() != null) {
                return link.getURL();
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Unable to resolve DOCX hyperlink URL", exception);
        }
        return "";
    }

    private String getRunElementText(final IRunElement runElement)
    {
        if (runElement instanceof XWPFHyperlinkRun) {
            return ((XWPFHyperlinkRun) runElement).text();
        }
        if (runElement instanceof XWPFRun) {
            return ((XWPFRun) runElement).text();
        }
        if (runElement instanceof XWPFSDT) {
            return ((XWPFSDT) runElement).getContent().getText();
        }
        return "";
    }

    private void appendStyledRun(final XWPFRun run, final StringBuilder output)
    {
        String text = this.escapeMarkdown(run.text());
        if (StringUtils.isBlank(text)) {
            return;
        }
        if (run.isBold() && run.isItalic()) {
            output.append("***").append(text).append("***");
        } else if (run.isBold()) {
            output.append("**").append(text).append("**");
        } else if (run.isItalic()) {
            output.append('*').append(text).append('*');
        } else {
            output.append(text);
        }
    }

    private void renderTable(final XWPFTable table, final ParseState state, final String section)
    {
        this.flushTabularBuffer(state, section);
        this.flushCodeBuffer(state, section);
        if (!BODY_SECTION.equals(section)) {
            this.renderTableAsPlainText(table, state, section);
            return;
        }
        if (this.tableLooksLikeCode(table)) {
            this.renderTableAsCodeBlock(table, state, section);
            return;
        }
        String tableMarkdown = this.tableToMarkdown(table);
        if (StringUtils.isNotBlank(tableMarkdown)) {
            this.appendBlock(state, section, tableMarkdown + NEWLINE);
        }
    }

    private void renderTableAsPlainText(final XWPFTable table, final ParseState state, final String section)
    {
        String text = this.cleanText(table.getText());
        if (StringUtils.isNotBlank(text)) {
            this.appendBlock(state, section, text + NEWLINE);
        }
    }

    private boolean tableLooksLikeCode(final XWPFTable table)
    {
        int codeCells = 0;
        int totalCells = 0;
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                totalCells++;
                if (this.cellLooksLikeCode(cell)) {
                    codeCells++;
                }
            }
        }
        return totalCells > 0 && codeCells * 2 >= totalCells;
    }

    private boolean cellLooksLikeCode(final XWPFTableCell cell)
    {
        for (IBodyElement element : cell.getBodyElements()) {
            if (element instanceof XWPFParagraph && this.isCodeParagraph((XWPFParagraph) element)) {
                return true;
            }
        }
        return this.looksLikeCodeText(cell.getText());
    }

    private void renderTableAsCodeBlock(final XWPFTable table, final ParseState state, final String section)
    {
        List<String> lines = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = this.renderCellForCode(cell);
                if (StringUtils.isNotBlank(cellText)) {
                    lines.add(cellText);
                }
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        StringBuilder code = new StringBuilder();
        code.append(CODE_FENCE).append(NEWLINE);
        for (String line : lines) {
            code.append(line).append(NEWLINE);
        }
        code.append(CODE_FENCE).append(NEWLINE);
        this.appendBlock(state, section, code.toString());
    }

    private String renderCellForCode(final XWPFTableCell cell)
    {
        StringBuilder cellText = new StringBuilder();
        for (IBodyElement element : cell.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                String paragraphText = this.renderRuns((XWPFParagraph) element);
                if (StringUtils.isNotBlank(paragraphText)) {
                    if (cellText.length() > 0) {
                        cellText.append(NEWLINE);
                    }
                    cellText.append(paragraphText);
                }
            }
        }
        return cellText.toString().trim();
    }

    private boolean isTabularParagraph(final XWPFParagraph paragraph, final String section)
    {
        if (!BODY_SECTION.equals(section) || this.getHeadingLevel(paragraph, paragraph.getText()) > 0) {
            return false;
        }
        if (this.isCodeParagraph(paragraph)) {
            return false;
        }
        return paragraph.getText().contains("\t");
    }

    private String[] splitTabularCells(final XWPFParagraph paragraph)
    {
        return paragraph.getText().split("\t", -1);
    }

    private void flushTabularBuffer(final ParseState state, final String section)
    {
        if (state.tabularBuffer.isEmpty()) {
            return;
        }
        List<String[]> rows = state.tabularBuffer.getRows();
        state.tabularBuffer.clear();
        if (this.looksLikeTabularTable(rows)) {
            String tableMarkdown = this.buildMarkdownTable(rows);
            if (StringUtils.isNotBlank(tableMarkdown)) {
                this.appendBlock(state, section, tableMarkdown + NEWLINE);
            }
            return;
        }
        if (this.rowsLookLikeCodeBlock(rows)) {
            this.renderRowsAsCodeBlock(rows, state, section);
            return;
        }
        this.renderPlainTextRows(rows, state, section);
    }

    private void renderRowsAsCodeBlock(final List<String[]> rows, final ParseState state, final String section)
    {
        this.flushCodeBuffer(state, section);
        StringBuilder code = new StringBuilder();
        code.append(CODE_FENCE).append(NEWLINE);
        for (String[] row : rows) {
            String line = this.joinRowCellsAsCodeLine(row);
            if (StringUtils.isNotBlank(line)) {
                code.append(line).append(NEWLINE);
            }
        }
        code.append(CODE_FENCE).append(NEWLINE);
        this.appendBlock(state, section, code.toString());
    }

    private String joinRowCellsAsCodeLine(final String[] cells)
    {
        StringBuilder line = new StringBuilder();
        for (String cell : cells) {
            String trimmed = cell.trim();
            if (StringUtils.isBlank(trimmed)) {
                continue;
            }
            if (line.length() > 0) {
                line.append('\t');
            }
            line.append(trimmed);
        }
        return line.toString();
    }

    private boolean rowsLookLikeCodeBlock(final List<String[]> rows)
    {
        if (rows.isEmpty()) {
            return false;
        }
        int codeLikeRows = 0;
        for (String[] row : rows) {
            if (this.looksLikeCodeText(this.joinRowCellsAsCodeLine(row))) {
                codeLikeRows++;
            }
        }
        return codeLikeRows * 2 >= rows.size();
    }

    private boolean looksLikeTabularTable(final List<String[]> rows)
    {
        if (rows.isEmpty()) {
            return false;
        }
        int maxColumns = 0;
        int nonEmptyCells = 0;
        for (String[] row : rows) {
            maxColumns = Math.max(maxColumns, row.length);
            nonEmptyCells += this.countNonEmptyCells(row);
        }
        if (rows.size() >= 2 && maxColumns >= 2 && nonEmptyCells >= 3 && !this.rowsLookLikeCodeBlock(rows)) {
            return true;
        }
        return rows.size() == 1 && maxColumns >= 3 && this.countNonEmptyCells(rows.get(0)) >= 2
            && !this.looksLikeCodeText(this.joinRowCellsAsCodeLine(rows.get(0)));
    }

    private int countNonEmptyCells(final String[] cells)
    {
        int count = 0;
        for (String cell : cells) {
            if (StringUtils.isNotBlank(cell)) {
                count++;
            }
        }
        return count;
    }

    private void renderPlainTextRows(final List<String[]> rows, final ParseState state, final String section)
    {
        for (String[] row : rows) {
            String text = this.joinRowCells(row);
            if (StringUtils.isNotBlank(text)) {
                this.appendBlock(state, section, text + NEWLINE);
            }
        }
    }

    private String joinRowCells(final String[] cells)
    {
        StringBuilder joined = new StringBuilder();
        for (String cell : cells) {
            String trimmed = cell.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                if (joined.length() > 0) {
                    joined.append(' ');
                }
                joined.append(trimmed);
            }
        }
        return joined.toString();
    }

    private String buildMarkdownTable(final List<String[]> rows)
    {
        if (rows.isEmpty()) {
            return "";
        }
        int columnCount = 0;
        for (String[] row : rows) {
            columnCount = Math.max(columnCount, row.length);
        }
        if (columnCount == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            this.appendMarkdownTableRow(sb, rows.get(rowIndex), columnCount);
            if (rowIndex == 0) {
                this.appendMarkdownTableSeparator(sb, columnCount);
            }
        }
        return sb.toString().trim();
    }

    private void appendMarkdownTableRow(final StringBuilder sb, final String[] cells, final int columnCount)
    {
        sb.append('|');
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            String cellText = columnIndex < cells.length ? this.escapeTableCell(cells[columnIndex].trim()) : "";
            sb.append(' ').append(cellText).append(" |");
        }
        sb.append('\n');
    }

    private void appendMarkdownTableSeparator(final StringBuilder sb, final int columnCount)
    {
        sb.append('|');
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            sb.append(" --- |");
        }
        sb.append('\n');
    }

    private String tableToMarkdown(final XWPFTable table)
    {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return "";
        }
        int columnCount = this.getEffectiveColumnCount(rows);
        if (columnCount == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            this.appendWordTableRow(sb, rows.get(rowIndex), columnCount);
            if (rowIndex == 0) {
                this.appendMarkdownTableSeparator(sb, columnCount);
            }
        }
        return sb.toString().trim();
    }

    private int getEffectiveColumnCount(final List<XWPFTableRow> rows)
    {
        int maxColumns = 0;
        for (XWPFTableRow row : rows) {
            int columns = 0;
            for (XWPFTableCell cell : row.getTableCells()) {
                columns += this.getCellGridSpan(cell);
            }
            maxColumns = Math.max(maxColumns, columns);
        }
        return maxColumns;
    }

    private void appendWordTableRow(final StringBuilder sb, final XWPFTableRow row, final int columnCount)
    {
        sb.append('|');
        int writtenColumns = 0;
        for (XWPFTableCell cell : row.getTableCells()) {
            String cellText = this.renderCell(cell);
            int span = this.getCellGridSpan(cell);
            sb.append(' ').append(cellText).append(" |");
            writtenColumns++;
            for (int index = 1; index < span; index++) {
                sb.append(" |");
                writtenColumns++;
            }
        }
        while (writtenColumns < columnCount) {
            sb.append(" |");
            writtenColumns++;
        }
        sb.append('\n');
    }

    private int getCellGridSpan(final XWPFTableCell cell)
    {
        if (cell.getCTTc() == null || cell.getCTTc().getTcPr() == null
            || !cell.getCTTc().getTcPr().isSetGridSpan()) {
            return 1;
        }
        BigInteger span = cell.getCTTc().getTcPr().getGridSpan().getVal();
        if (span == null) {
            return 1;
        }
        return Math.max(span.intValue(), 1);
    }

    private String renderCell(final XWPFTableCell cell)
    {
        StringBuilder cellMarkdown = new StringBuilder();
        for (IBodyElement element : cell.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                String text = this.renderRuns((XWPFParagraph) element);
                if (StringUtils.isNotBlank(text)) {
                    if (cellMarkdown.length() > 0) {
                        cellMarkdown.append("<br>");
                    }
                    cellMarkdown.append(text);
                }
            } else if (element instanceof XWPFTable) {
                cellMarkdown.append(' ')
                    .append(this.cleanText(((XWPFTable) element).getText()).replace(NEWLINE, " "));
            }
        }
        return this.escapeTableCell(cellMarkdown.toString().trim());
    }

    private void renderSdt(final XWPFSDT sdt, final ParseState state, final String section)
    {
        this.flushTabularBuffer(state, section);
        this.flushCodeBuffer(state, section);
        List<ISDTContents> elements = this.getSdtContentElements(sdt);
        if (!elements.isEmpty()) {
            this.walkSdtElements(elements, state, section);
            return;
        }
        String text = sdt.getContent().getText();
        if (StringUtils.isNotBlank(text)) {
            this.appendBlock(state, section, this.cleanText(text) + NEWLINE);
        }
    }

    private void walkSdtElements(final List<ISDTContents> elements, final ParseState state, final String section)
    {
        for (ISDTContents element : elements) {
            if (!(element instanceof XWPFParagraph) || !this.isCodeParagraph((XWPFParagraph) element)) {
                this.flushCodeBuffer(state, section);
            }
            if (!(element instanceof XWPFParagraph) || !this.isTabularParagraph((XWPFParagraph) element, section)) {
                this.flushTabularBuffer(state, section);
            }
            this.renderSdtElement(element, state, section);
        }
        this.flushTabularBuffer(state, section);
        this.flushCodeBuffer(state, section);
    }

    private void renderSdtElement(final ISDTContents element, final ParseState state, final String section)
    {
        if (element instanceof XWPFParagraph) {
            this.renderParagraph((XWPFParagraph) element, state, section);
        } else if (element instanceof XWPFTable) {
            this.renderTable((XWPFTable) element, state, section);
        } else if (element instanceof XWPFSDT) {
            this.renderSdt((XWPFSDT) element, state, section);
        }
    }

    private List<ISDTContents> getSdtContentElements(final XWPFSDT sdt)
    {
        ISDTContent content = sdt.getContent();
        if (!(content instanceof XWPFSDTContent)) {
            return List.of();
        }
        try {
            Field bodyElementsField = XWPFSDTContent.class.getDeclaredField("bodyElements");
            bodyElementsField.setAccessible(true);
            Object value = bodyElementsField.get(content);
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<ISDTContents> elements = (List<ISDTContents>) value;
                return elements;
            }
        } catch (ReflectiveOperationException exception) {
            LOGGER.debug("Unable to access SDT content elements", exception);
        }
        return List.of();
    }

    private void renderUnsupportedElement(final IBodyElement element, final ParseState state)
    {
        LOGGER.debug("Unsupported DOCX body element: {}", element.getElementType());
        state.markdown.append(NEWLINE).append(NEWLINE).append("<!-- unsupported body element: ")
            .append(element.getElementType())
            .append(" -->").append(NEWLINE).append(NEWLINE);
    }

    private void appendBlock(final ParseState state, final String section, final String content)
    {
        if (StringUtils.isBlank(content)) {
            return;
        }
        state.blockIndex++;
        state.markdown.append(NEWLINE).append(NEWLINE).append("<!-- block: ").append(state.blockIndex);
        if (!BODY_SECTION.equals(section)) {
            state.markdown.append(" section: ").append(section);
        }
        state.markdown.append(" -->").append(NEWLINE);
        state.markdown.append(content);
    }

    private int getHeadingLevel(final XWPFParagraph paragraph, final String text)
    {
        if (this.shouldExcludeFromHeading(paragraph, text)) {
            return 0;
        }
        return this.resolveHeadingLevel(paragraph);
    }

    private boolean shouldExcludeFromHeading(final XWPFParagraph paragraph, final String text)
    {
        return this.isBibliographyStyle(paragraph) || this.looksLikeReferenceEntry(text)
            || this.isLongProseParagraph(text);
    }

    private int resolveHeadingLevel(final XWPFParagraph paragraph)
    {
        int outlineLevel = this.getOutlineLevelFromProperties(paragraph);
        if (outlineLevel > 0) {
            return outlineLevel;
        }
        int fontLevel = this.getHeadingLevelFromFontSize(paragraph);
        if (fontLevel > 0 && this.isHeadingLikeByFormatting(paragraph)) {
            return fontLevel;
        }
        int styleLevel = this.getHeadingLevelFromStyleName(paragraph);
        if (styleLevel > 0) {
            return styleLevel;
        }
        if (this.isOutlineNumberedHeading(paragraph)) {
            return this.getLevelFromNumIlvl(paragraph);
        }
        return 0;
    }

    private boolean looksLikeReferenceEntry(final String text)
    {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("[") || trimmed.length() < 3) {
            return false;
        }
        int closingBracket = trimmed.indexOf(']');
        if (closingBracket <= 1) {
            return false;
        }
        for (int index = 1; index < closingBracket; index++) {
            if (!Character.isDigit(trimmed.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean isLongProseParagraph(final String text)
    {
        return StringUtils.isNotBlank(text) && text.trim().length() > HEADING_MAX_TEXT_LENGTH;
    }

    private boolean isBibliographyStyle(final XWPFParagraph paragraph)
    {
        String style = paragraph.getStyle();
        if (style == null) {
            return false;
        }
        String normalized = style.toLowerCase();
        return normalized.contains("bibliograph") || normalized.contains("reference")
            || normalized.contains("citation");
    }

    private int getHeadingLevelFromStyleName(final XWPFParagraph paragraph)
    {
        String style = paragraph.getStyle();
        if (style == null) {
            return 0;
        }
        String normalized = style.toLowerCase();
        if (normalized.startsWith("heading")) {
            return this.getHeadingLevelFromHeadingStyle(normalized);
        }
        if (normalized.contains("title")) {
            return 1;
        }
        return this.headingLevelFromStyle(normalized);
    }

    private int getOutlineLevelFromProperties(final XWPFParagraph paragraph)
    {
        if (paragraph.getCTPPr() == null || !paragraph.getCTPPr().isSetOutlineLvl()) {
            return 0;
        }
        BigInteger outlineLevel = paragraph.getCTPPr().getOutlineLvl().getVal();
        if (outlineLevel == null) {
            return 0;
        }
        return Math.min(Math.max(outlineLevel.intValue() + 1, 1), 6);
    }

    private int getLevelFromNumIlvl(final XWPFParagraph paragraph)
    {
        int level = paragraph.getNumIlvl() == null ? 0 : paragraph.getNumIlvl().intValue();
        return Math.min(Math.max(level + 1, 1), 6);
    }

    private boolean isOutlineNumberedHeading(final XWPFParagraph paragraph)
    {
        if (paragraph.getNumID() == null || this.isBulletNumbering(paragraph)) {
            return false;
        }
        String levelText = paragraph.getNumLevelText();
        return levelText != null && levelText.contains("%");
    }

    private boolean isBulletNumbering(final XWPFParagraph paragraph)
    {
        return BULLET_NUM_FMT.equals(StringUtils.defaultString(paragraph.getNumFmt()).toLowerCase());
    }

    private boolean isHeadingLikeByFormatting(final XWPFParagraph paragraph)
    {
        if (this.getOutlineLevelFromProperties(paragraph) > 0) {
            return true;
        }
        if (this.isOutlineNumberedHeading(paragraph) && this.hasBoldRun(paragraph)) {
            return true;
        }
        double fontSize = this.getParagraphFontSize(paragraph);
        return fontSize >= HEADING_FONT_LEVEL3 && this.hasBoldRun(paragraph);
    }

    private boolean hasBoldRun(final XWPFParagraph paragraph)
    {
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.isBold()) {
                return true;
            }
        }
        return false;
    }

    private double getParagraphFontSize(final XWPFParagraph paragraph)
    {
        double maxSize = -1;
        for (XWPFRun run : paragraph.getRuns()) {
            Double size = run.getFontSizeAsDouble();
            if (size != null && size > 0 && size > maxSize) {
                maxSize = size;
            }
        }
        return maxSize;
    }

    private int getHeadingLevelFromFontSize(final XWPFParagraph paragraph)
    {
        double fontSize = this.getParagraphFontSize(paragraph);
        for (int index = 0; index < HEADING_FONT_THRESHOLDS.length; index++) {
            if (fontSize >= HEADING_FONT_THRESHOLDS[index]) {
                return index + 1;
            }
        }
        return 0;
    }

    private int getHeadingLevelFromHeadingStyle(final String normalizedStyle)
    {
        String levelString = normalizedStyle.replace("heading", "").trim();
        if (StringUtils.isNumeric(levelString)) {
            int level = Integer.parseInt(levelString);
            if (level >= 1 && level <= 6) {
                return level;
            }
        }
        int extractedLevel = this.headingLevelFromStyle(normalizedStyle);
        if (extractedLevel > 0) {
            return extractedLevel;
        }
        return 1;
    }

    private int headingLevelFromStyle(final String normalizedStyle)
    {
        for (int level = 1; level <= 6; level++) {
            if (normalizedStyle.contains("heading" + level) || normalizedStyle.contains("heading " + level)) {
                return level;
            }
        }
        return 0;
    }

    private boolean isCodeParagraph(final XWPFParagraph paragraph)
    {
        String style = paragraph.getStyle();
        if (style != null && style.toLowerCase().contains("code")) {
            return true;
        }
        for (XWPFRun run : paragraph.getRuns()) {
            String font = run.getFontFamily();
            if (font == null) {
                continue;
            }
            String normalizedFont = font.toLowerCase();
            if (normalizedFont.contains("courier")
                || normalizedFont.contains("consolas")
                || normalizedFont.contains("mono")
                || normalizedFont.contains("menlo")) {
                return true;
            }
        }
        return this.looksLikeCodeText(paragraph.getText());
    }

    private boolean looksLikeCodeText(final String text)
    {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String trimmed = text.trim();
        if (this.startsWithCodeKeyword(trimmed)) {
            return true;
        }
        return this.containsKnownCodeIdentifier(trimmed) || this.looksLikeAssignmentCall(trimmed);
    }

    private boolean startsWithCodeKeyword(final String trimmed)
    {
        for (String keyword : CODE_START_KEYWORDS) {
            if (trimmed.startsWith(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKnownCodeIdentifier(final String trimmed)
    {
        for (String identifier : CODE_IDENTIFIERS) {
            if (trimmed.contains(identifier)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeAssignmentCall(final String trimmed)
    {
        return trimmed.contains("=") && trimmed.contains("(") && trimmed.contains(")");
    }

    private String escapeMarkdown(final String text)
    {
        if (text == null) {
            return "";
        }
        return text
            .replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("]", "\\]");
    }

    private String escapeTableCell(final String text)
    {
        return text
            .replace("|", "\\|")
            .replace(NEWLINE, "<br>")
            .trim();
    }

    private String cleanText(final String text)
    {
        if (text == null) {
            return "";
        }
        return text
            .replace("\r\n", NEWLINE)
            .replace("\r", NEWLINE)
            .replaceAll("[ \\t]+", " ")
            .replaceAll("\\n{3,}", NEWLINE + NEWLINE)
            .trim();
    }

    private String escapeComment(final String value)
    {
        if (value == null) {
            return "";
        }
        return value.replace("--", "—");
    }

    private static final class ParseState
    {
        private final StringBuilder markdown;

        private final NumberingTracker numberingTracker;

        private final TabularBuffer tabularBuffer;

        private final CodeBuffer codeBuffer;

        private int blockIndex;

        private ParseState(final StringBuilder markdown)
        {
            this.markdown = markdown;
            this.numberingTracker = new NumberingTracker();
            this.tabularBuffer = new TabularBuffer();
            this.codeBuffer = new CodeBuffer();
            this.blockIndex = 0;
        }
    }

    private static final class CodeBuffer
    {
        private final List<String> lines = new ArrayList<>();

        private void addLine(final String line)
        {
            this.lines.add(line);
        }

        private boolean isEmpty()
        {
            return this.lines.isEmpty();
        }

        private void clear()
        {
            this.lines.clear();
        }

        private String toMarkdown()
        {
            StringBuilder code = new StringBuilder();
            code.append(CODE_FENCE).append(NEWLINE);
            for (String line : this.lines) {
                code.append(line).append(NEWLINE);
            }
            code.append(CODE_FENCE).append(NEWLINE);
            return code.toString();
        }
    }

    private static final class TabularBuffer
    {
        private final List<String[]> rows = new ArrayList<>();

        private void addRow(final String[] cells)
        {
            this.rows.add(cells);
        }

        private boolean isEmpty()
        {
            return this.rows.isEmpty();
        }

        private List<String[]> getRows()
        {
            return this.rows;
        }

        private void clear()
        {
            this.rows.clear();
        }
    }

    private static final class NumberingTracker
    {
        private final Map<BigInteger, int[]> countersByNumId = new HashMap<>();

        private String nextPrefix(final XWPFParagraph paragraph)
        {
            if (!this.isValidNumberedParagraph(paragraph)) {
                return "";
            }
            int level = this.getNumberingLevel(paragraph);
            int[] counters = this.getCounters(paragraph.getNumID());
            this.updateCounters(counters, level, paragraph.getNumStartOverride());
            return this.resolveNumberingText(paragraph.getNumLevelText(), counters, level);
        }

        private boolean isValidNumberedParagraph(final XWPFParagraph paragraph)
        {
            if (paragraph.getNumID() == null) {
                return false;
            }
            if (StringUtils.isBlank(paragraph.getNumLevelText())) {
                return false;
            }
            int level = this.getNumberingLevel(paragraph);
            return level >= 0 && level < MAX_NUMBERING_LEVEL;
        }

        private int getNumberingLevel(final XWPFParagraph paragraph)
        {
            return paragraph.getNumIlvl() == null ? 0 : paragraph.getNumIlvl().intValue();
        }

        private int[] getCounters(final BigInteger numId)
        {
            return this.countersByNumId.computeIfAbsent(numId, id -> new int[MAX_NUMBERING_LEVEL]);
        }

        private void updateCounters(final int[] counters, final int level, final BigInteger startOverride)
        {
            if (startOverride != null && counters[level] == 0) {
                counters[level] = startOverride.intValue();
            } else {
                counters[level]++;
            }
            for (int deeperLevel = level + 1; deeperLevel < MAX_NUMBERING_LEVEL; deeperLevel++) {
                counters[deeperLevel] = 0;
            }
        }

        private String resolveNumberingText(final String levelText, final int[] counters, final int level)
        {
            String resolved = levelText;
            for (int counterLevel = 0; counterLevel <= level; counterLevel++) {
                resolved = resolved.replace("%" + (counterLevel + 1), Integer.toString(counters[counterLevel]));
            }
            return resolved.trim();
        }
    }
}
