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

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import technology.tabula.ObjectExtractor;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

/**
 * Generate markdown output from PDF input. Detects tables from ruling lines drawn in the PDF
 * and renders them as GitHub-Flavored Markdown tables with the first row as a header.
 *
 * @version $Id$
 */
public class PdfMarkdownGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PdfMarkdownGenerator.class);

    private static final float HEADING_BOLD_RATIO = 0.7f;

    private static final float HEADING_GAP_RATIO = 1.2f;

    private static final float PARAGRAPH_GAP_RATIO = 1.6f;

    private static final int HEADING_MAX_WORDS = 16;

    private static final float LIST_LINE_X_TOLERANCE = 4.0f;

    private static final float LIST_CONTINUATION_GAP_RATIO = 1.35f;

    private static final String INLINE_LIST_SEPARATOR = "; – ";

    private static final String CODE_FENCE = "```";

    private static final float CODE_MONOSPACE_RATIO = 0.7f;

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

    private static final char[] BULLET_CHARACTERS = {
        '\u2022',
        '\u00B7',
        '\u25AA',
        '\u25E6',
        '\u25CF',
        '\u2219',
        '\u2013',
        '\u2014',
        '*',
    };

    private static final String[] MONOSPACE_FONT_FRAGMENTS = {
        "courier",
        "mono",
        "consolas",
        "menlo",
        "typewriter",
        "code",
    };

    /** Maximum deviation from a perfectly straight line for a segment to count as a ruling line. */
    private static final float RULING_TOLERANCE = 1.0f;

    /** Minimum length in points for a segment to count as a ruling line. */
    private static final float RULING_MIN_LENGTH = 10.0f;

    /** Coordinate tolerance for clustering parallel ruling lines and assigning text to cells. */
    private static final float CLUSTER_TOLERANCE = 3.0f;

    /** Minimum row intervals for a detected grid to qualify as a table (2 rows needs 3 H lines). */
    private static final int MIN_TABLE_ROWS = 2;

    /** Minimum column intervals for a detected grid to qualify as a table (2 cols needs 3 V lines). */
    private static final int MIN_TABLE_COLS = 2;

    /** Maximum characters in a single table cell; more suggests prose was captured as a table row. */
    private static final int MAX_CELL_CHARS = 300;

    /** Maximum fraction of total table text allowed in a single cell before the row is prose-like. */
    private static final float MAX_CELL_TEXT_RATIO = 0.7f;

    /** Y-coordinate tolerance (points) for deduplicating overlapping tabula table detections. */
    private static final float TABLE_DEDUP_TOLERANCE = 10.0f;

    private static final String DOUBLE_NEWLINE = "\n\n";

    /**
     * Convert PDF content to markdown grouped by pages.
     *
     * @param stream the pdf stream
     * @param fileName source file name
     * @return markdown text
     * @throws IOException when reading fails
     */
    public String toMarkdown(final InputStream stream, final String fileName)
        throws IOException
    {
        final long startTimestamp = System.currentTimeMillis();
        LOGGER.info("PDF markdown parsing started for file '{}' at {}", fileName, startTimestamp);
        final byte[] bytes = stream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            final StyledPdfTextStripper stripper = new StyledPdfTextStripper();
            final StringBuilder markdown = new StringBuilder();
            markdown.append("<!-- source_file: ").append(escapeComment(fileName)).append(" -->\n");

            final int pageCount = document.getNumberOfPages();
            // TabulaPageParser owns the ObjectExtractor. Its close() delegates to PDDocument.close(),
            // so we intentionally do not close it — the enclosing try-with-resources handles that.
            final TabulaPageParser tabulaParser = new TabulaPageParser(document);
            for (int page = 1; page <= pageCount; page++) {
                final List<StyledLine> pageLines = stripper.extractPageLines(document, page);
                List<DetectedTable> tables = tabulaParser.extractTables(page, fileName);
                if (tables.isEmpty()) {
                    final PDPage pdPage = document.getPage(page - 1);
                    final List<RulingLine> rulingLines = extractRulingLinesSafely(pdPage, page, fileName);
                    final List<TextToken> tokens = stripper.getLastPageTokens();
                    tables = detectTables(rulingLines, tokens);
                }
                final String pageText = renderPage(pageLines, tables);
                markdown.append("\n\n<!-- page: ").append(page).append(" -->\n");
                markdown.append("## Page ").append(page).append(DOUBLE_NEWLINE);
                if (StringUtils.isBlank(pageText)) {
                    markdown.append("_No extractable text on this page._\n");
                } else {
                    markdown.append(pageText).append('\n');
                }
            }
            return markdown.toString().trim();
        } finally {
            final long endTimestamp = System.currentTimeMillis();
            LOGGER.info("PDF markdown parsing finished for file '{}' at {} (total {} ms)",
                fileName, endTimestamp, endTimestamp - startTimestamp);
        }
    }

    private List<RulingLine> extractRulingLinesSafely(final PDPage page, final int pageNum,
        final String fileName)
    {
        try {
            final RulingLineExtractor extractor = new RulingLineExtractor(page);
            return extractor.extractLines(page);
        } catch (IOException e) {
            LOGGER.warn("Could not extract ruling lines from page {} of '{}': {}", pageNum, fileName,
                e.getMessage());
            return Collections.emptyList();
        }
    }

    private String renderPage(final List<StyledLine> lines, final List<DetectedTable> tables)
    {
        final List<StyledLine> allNormalized = lines.stream()
            .map(this::normalizedCopy)
            .filter(line -> StringUtils.isNotBlank(line.text))
            .toList();
        final List<DetectedTable> sortedTables = new ArrayList<>(tables);
        sortedTables.sort((a, b) -> Float.compare(a.topY(), b.topY()));
        final List<StyledLine> proseLines = allNormalized.stream()
            .filter(l -> !isInsideAnyTable(l.topY, sortedTables))
            .toList();
        if (proseLines.isEmpty() && sortedTables.isEmpty()) {
            return "";
        }
        final float baseGap = estimateBaseGap(proseLines);
        final StringBuilder output = new StringBuilder();
        final StringBuilder paragraphBuffer = new StringBuilder();
        final boolean[] emitted = new boolean[sortedTables.size()];
        renderProseLinesWithTables(proseLines, sortedTables, emitted, baseGap, output, paragraphBuffer);
        flushParagraph(output, paragraphBuffer);
        for (int t = 0; t < sortedTables.size(); t++) {
            if (!emitted[t]) {
                if (output.length() > 0) {
                    output.append(DOUBLE_NEWLINE);
                }
                output.append(renderTableMarkdown(sortedTables.get(t)));
            }
        }
        return output.toString().trim();
    }

    private void renderProseLinesWithTables(final List<StyledLine> proseLines,
        final List<DetectedTable> sortedTables, final boolean[] emitted, final float baseGap,
        final StringBuilder output, final StringBuilder paragraphBuffer)
    {
        final ListBuffer listBuffer = new ListBuffer();
        final CodeBuffer codeBuffer = new CodeBuffer();
        for (int i = 0; i < proseLines.size(); i++) {
            final StyledLine current = proseLines.get(i);
            emitPendingTables(output, paragraphBuffer, listBuffer, codeBuffer, sortedTables, emitted,
                current.topY);
            final StyledLine previous = i > 0 ? proseLines.get(i - 1) : null;
            final StyledLine next = i + 1 < proseLines.size() ? proseLines.get(i + 1) : null;
            if (this.isHeading(current, previous, next, baseGap)) {
                this.flushList(output, listBuffer);
                this.flushCodeBlock(output, codeBuffer);
                this.flushParagraph(output, paragraphBuffer);
                this.appendHeading(output, current.text);
                continue;
            }
            if (this.isCodeLine(current)) {
                this.flushList(output, listBuffer);
                this.flushParagraph(output, paragraphBuffer);
                codeBuffer.addLine(current.text);
                continue;
            }
            this.handleListOrParagraph(current, previous, baseGap, output, paragraphBuffer,
                listBuffer, codeBuffer);
        }
        this.flushList(output, listBuffer);
        this.flushCodeBlock(output, codeBuffer);
    }

    private void handleListOrParagraph(final StyledLine current, final StyledLine previous,
        final float baseGap, final StringBuilder output, final StringBuilder paragraphBuffer,
        final ListBuffer listBuffer, final CodeBuffer codeBuffer)
    {
        final boolean startsList = this.startsWithListMarker(current.text);
        final boolean continuesList = this.continuesListItem(current, previous, listBuffer, baseGap);
        if (startsList || continuesList) {
            this.flushCodeBlock(output, codeBuffer);
            this.flushParagraph(output, paragraphBuffer);
            if (startsList) {
                listBuffer.addItem(this.stripLeadingListMarker(current.text));
            } else {
                listBuffer.appendToLastItem(current.text);
            }
            return;
        }
        this.flushList(output, listBuffer);
        this.flushCodeBlock(output, codeBuffer);
        if (previous != null && this.lineGap(previous, current) > baseGap * PARAGRAPH_GAP_RATIO) {
            this.flushParagraph(output, paragraphBuffer);
        }
        this.appendParagraphLine(paragraphBuffer, current.text);
    }

    private void emitPendingTables(final StringBuilder output, final StringBuilder paragraphBuffer,
        final ListBuffer listBuffer, final CodeBuffer codeBuffer, final List<DetectedTable> tables,
        final boolean[] emitted, final float upToY)
    {
        for (int t = 0; t < tables.size(); t++) {
            if (!emitted[t] && tables.get(t).topY() < upToY) {
                this.flushList(output, listBuffer);
                this.flushCodeBlock(output, codeBuffer);
                this.flushParagraph(output, paragraphBuffer);
                if (output.length() > 0) {
                    output.append(DOUBLE_NEWLINE);
                }
                output.append(this.renderTableMarkdown(tables.get(t)));
                emitted[t] = true;
            }
        }
    }

    private boolean isInsideAnyTable(final float lineDisplayY, final List<DetectedTable> tables)
    {
        for (DetectedTable table : tables) {
            if (lineDisplayY >= table.topY() - CLUSTER_TOLERANCE
                && lineDisplayY <= table.bottomY() + CLUSTER_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    // ---- Tabula table parser ----

    /**
     * Encapsulates tabula table extraction for a single PDF document, hiding ObjectExtractor and
     * SpreadsheetExtractionAlgorithm from the outer class to keep its coupling count within limits.
     */
    private static final class TabulaPageParser
    {
        private final ObjectExtractor extractor;

        private final SpreadsheetExtractionAlgorithm algorithm;

        TabulaPageParser(final PDDocument document)
        {
            this.extractor = new ObjectExtractor(document);
            this.algorithm = new SpreadsheetExtractionAlgorithm();
        }

        List<DetectedTable> extractTables(final int pageNum, final String fileName)
        {
            try {
                final technology.tabula.Page tabulaPage = this.extractor.extract(pageNum);
                if (tabulaPage == null) {
                    return Collections.emptyList();
                }
                final List<Table> tabulaTables = this.algorithm.extract(tabulaPage);
                if (tabulaTables.isEmpty()) {
                    return Collections.emptyList();
                }
                final List<DetectedTable> result = new ArrayList<>();
                for (Table tabulaTable : tabulaTables) {
                    DetectedTable detected = convertTabulaTable(tabulaTable);
                    if (detected != null) {
                        detected = detected.trimLeadingProseRows();
                    }
                    if (detected != null) {
                        result.add(detected);
                    }
                }
                return deduplicateByTopY(result);
            } catch (RuntimeException | NoClassDefFoundError e) {
                LOGGER.warn("Tabula table extraction failed on page {} of '{}': {}", pageNum, fileName,
                    e.getMessage());
                return Collections.emptyList();
            }
        }

        private static DetectedTable convertTabulaTable(final Table tabulaTable)
        {
            final int rowCount = tabulaTable.getRowCount();
            final int colCount = tabulaTable.getColCount();
            if (rowCount < MIN_TABLE_ROWS || colCount < MIN_TABLE_COLS) {
                return null;
            }
            final float top = tabulaTable.getTop();
            final float bottom = tabulaTable.getBottom();
            final float left = tabulaTable.getLeft();
            final float right = tabulaTable.getRight();
            final float rowHeight = (bottom - top) / rowCount;
            final float colWidth = (right - left) / colCount;
            final float[] rowY = new float[rowCount + 1];
            final float[] colX = new float[colCount + 1];
            for (int i = 0; i <= rowCount; i++) {
                rowY[i] = top + i * rowHeight;
            }
            for (int i = 0; i <= colCount; i++) {
                colX[i] = left + i * colWidth;
            }
            final DetectedTable result = new DetectedTable(rowY, colX);
            for (int r = 0; r < rowCount; r++) {
                for (int c = 0; c < colCount; c++) {
                    final String text = tabulaTable.getCell(r, c).getText();
                    result.setCell(r, c, text != null ? text.trim() : "");
                }
            }
            return result;
        }

        private static List<DetectedTable> deduplicateByTopY(final List<DetectedTable> tables)
        {
            final List<DetectedTable> unique = new ArrayList<>();
            for (DetectedTable table : tables) {
                if (!isTopYDuplicate(table, unique)) {
                    unique.add(table);
                }
            }
            return unique;
        }

        private static boolean isTopYDuplicate(final DetectedTable candidate,
            final List<DetectedTable> accepted)
        {
            for (DetectedTable existing : accepted) {
                if (Math.abs(candidate.topY() - existing.topY()) < TABLE_DEDUP_TOLERANCE) {
                    return true;
                }
            }
            return false;
        }
    }

    private List<DetectedTable> detectTables(final List<RulingLine> rulingLines,
        final List<TextToken> tokens)
    {
        final List<RulingLine> horizontals = new ArrayList<>();
        final List<RulingLine> verticals = new ArrayList<>();
        for (RulingLine line : rulingLines) {
            if (line.isHorizontal()) {
                horizontals.add(line);
            } else if (line.isVertical()) {
                verticals.add(line);
            }
        }
        if (horizontals.isEmpty() || verticals.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Float> rowYList = clusterLineCoordinates(horizontals, true);
        final List<Float> colXList = clusterLineCoordinates(verticals, false);
        if (rowYList.size() < MIN_TABLE_ROWS + 1 || colXList.size() < MIN_TABLE_COLS + 1) {
            return Collections.emptyList();
        }
        final float[] rowY = toFloatArray(rowYList);
        final float[] colX = toFloatArray(colXList);
        final DetectedTable table = new DetectedTable(rowY, colX);
        for (TextToken token : tokens) {
            if (table.contains(token.x, token.y)) {
                table.assignToken(token);
            }
        }
        table.propagateSpans();
        final List<DetectedTable> result = new ArrayList<>();
        result.add(table);
        return result;
    }

    private List<Float> clusterLineCoordinates(final List<RulingLine> lines, final boolean byY)
    {
        final List<Double> coords = new ArrayList<>();
        for (RulingLine line : lines) {
            final double coord = byY
                ? (double) ((line.y1 + line.y2) / 2.0f)
                : (double) ((line.x1 + line.x2) / 2.0f);
            coords.add(coord);
        }
        return clusterValues(coords);
    }

    private List<Float> clusterValues(final List<Double> values)
    {
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        final List<Float> clusters = new ArrayList<>();
        double clusterSum = sorted.get(0);
        int clusterCount = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) - sorted.get(i - 1) <= CLUSTER_TOLERANCE) {
                clusterSum += sorted.get(i);
                clusterCount++;
            } else {
                clusters.add((float) (clusterSum / clusterCount));
                clusterSum = sorted.get(i);
                clusterCount = 1;
            }
        }
        clusters.add((float) (clusterSum / clusterCount));
        return clusters;
    }

    private float[] toFloatArray(final List<Float> list)
    {
        final float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private String renderTableMarkdown(final DetectedTable table)
    {
        final int cols = table.colCount();
        final int rows = table.rowCount();
        if (rows == 0 || cols == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        appendTableRow(sb, table.cells[0], cols);
        sb.append('|');
        for (int c = 0; c < cols; c++) {
            sb.append("---|");
        }
        sb.append('\n');
        for (int r = 1; r < rows; r++) {
            appendTableRow(sb, table.cells[r], cols);
        }
        return sb.toString().trim();
    }

    private void appendTableRow(final StringBuilder sb, final String[] row, final int cols)
    {
        sb.append('|');
        for (int c = 0; c < cols; c++) {
            final String cell = c < row.length ? escapeCellText(row[c]) : " ";
            sb.append(' ').append(cell).append(" |");
        }
        sb.append('\n');
    }

    private String escapeCellText(final String text)
    {
        if (StringUtils.isBlank(text)) {
            return " ";
        }
        return text.replace("|", "\\|").replaceAll("\\s+", " ").trim();
    }

    private StyledLine normalizedCopy(final StyledLine line)
    {
        final StyledLine copy = new StyledLine();
        copy.text = normalizeLineText(line.text);
        copy.topY = line.topY;
        copy.boldRatio = line.boldRatio;
        copy.wordCount = line.wordCount;
        copy.startX = line.startX;
        copy.monospaceRatio = line.monospaceRatio;
        return copy;
    }

    private String normalizeLineText(final String text)
    {
        if (text == null) {
            return "";
        }
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
            .replace('\n', ' ')
            .replaceAll("[ \\t]+", " ")
            .trim();
    }

    private float estimateBaseGap(final List<StyledLine> lines)
    {
        if (lines.size() < 2) {
            return 12.0f;
        }
        final List<Float> gaps = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            final float gap = lineGap(lines.get(i - 1), lines.get(i));
            if (gap > 0) {
                gaps.add(gap);
            }
        }
        if (gaps.isEmpty()) {
            return 12.0f;
        }
        Collections.sort(gaps);
        return gaps.get(gaps.size() / 2);
    }

    private float lineGap(final StyledLine previous, final StyledLine current)
    {
        return Math.abs(current.topY - previous.topY);
    }

    private boolean isHeading(final StyledLine current, final StyledLine previous,
        final StyledLine next, final float baseGap)
    {
        if (current.wordCount == 0 || current.wordCount > HEADING_MAX_WORDS) {
            return false;
        }
        if (current.boldRatio < HEADING_BOLD_RATIO) {
            return false;
        }
        final float gapBefore = previous == null ? baseGap : lineGap(previous, current);
        final float gapAfter = next == null ? baseGap : lineGap(current, next);
        return gapBefore > baseGap * HEADING_GAP_RATIO || gapAfter > baseGap * HEADING_GAP_RATIO;
    }

    private void appendHeading(final StringBuilder output, final String headingText)
    {
        if (output.length() > 0) {
            output.append(DOUBLE_NEWLINE);
        }
        output.append("### ").append("**").append(headingText).append("**");
    }

    private void appendParagraphLine(final StringBuilder paragraphBuffer, final String lineText)
    {
        if (paragraphBuffer.length() == 0) {
            paragraphBuffer.append(lineText);
        } else if (paragraphBuffer.charAt(paragraphBuffer.length() - 1) == '-') {
            paragraphBuffer.deleteCharAt(paragraphBuffer.length() - 1);
            paragraphBuffer.append(lineText);
        } else {
            paragraphBuffer.append(' ').append(lineText);
        }
    }

    private void flushParagraph(final StringBuilder output, final StringBuilder paragraphBuffer)
    {
        if (paragraphBuffer.length() == 0) {
            return;
        }
        final String text = paragraphBuffer.toString().trim();
        paragraphBuffer.setLength(0);
        if (this.looksLikeInlineEnDashList(text)) {
            if (output.length() > 0) {
                output.append(DOUBLE_NEWLINE);
            }
            this.appendInlineEnDashList(output, text);
            return;
        }
        if (output.length() > 0) {
            output.append(DOUBLE_NEWLINE);
        }
        output.append(text);
    }

    private void flushList(final StringBuilder output, final ListBuffer listBuffer)
    {
        if (listBuffer.isEmpty()) {
            return;
        }
        if (output.length() > 0) {
            output.append(DOUBLE_NEWLINE);
        }
        output.append(listBuffer.toMarkdown());
        listBuffer.clear();
    }

    private void flushCodeBlock(final StringBuilder output, final CodeBuffer codeBuffer)
    {
        if (codeBuffer.isEmpty()) {
            return;
        }
        if (output.length() > 0) {
            output.append(DOUBLE_NEWLINE);
        }
        output.append(codeBuffer.toMarkdown());
        codeBuffer.clear();
    }

    private void appendInlineEnDashList(final StringBuilder output, final String text)
    {
        final String[] parts = text.split(INLINE_LIST_SEPARATOR);
        for (String part : parts) {
            this.appendMarkdownListLine(output, this.stripLeadingListMarker(part.trim()));
        }
    }

    private void appendMarkdownListLine(final StringBuilder output, final String itemText)
    {
        if (StringUtils.isBlank(itemText)) {
            return;
        }
        if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
            output.append('\n');
        }
        output.append("- ").append(itemText);
    }

    private boolean looksLikeInlineEnDashList(final String text)
    {
        return text.indexOf(INLINE_LIST_SEPARATOR) >= 0;
    }

    private boolean startsWithListMarker(final String text)
    {
        final String trimmed = text.trim();
        if (trimmed.length() < 2) {
            return false;
        }
        if (this.isBulletCharacter(trimmed.charAt(0))) {
            return true;
        }
        if (this.isOrderedListLine(trimmed)) {
            return true;
        }
        return trimmed.startsWith("- ") && Character.isLetter(trimmed.charAt(2));
    }

    private boolean continuesListItem(final StyledLine current, final StyledLine previous,
        final ListBuffer listBuffer, final float baseGap)
    {
        if (listBuffer.isEmpty() || previous == null) {
            return false;
        }
        if (this.startsWithListMarker(current.text)) {
            return false;
        }
        if (Math.abs(current.startX - previous.startX) > LIST_LINE_X_TOLERANCE) {
            return false;
        }
        return this.lineGap(previous, current) <= baseGap * LIST_CONTINUATION_GAP_RATIO;
    }

    private String stripLeadingListMarker(final String text)
    {
        final String trimmed = text.trim();
        if (trimmed.length() >= 2 && this.isBulletCharacter(trimmed.charAt(0))) {
            return trimmed.substring(1).trim();
        }
        if (trimmed.startsWith("- ")) {
            return trimmed.substring(2).trim();
        }
        return trimmed;
    }

    private boolean isBulletCharacter(final char character)
    {
        for (char bullet : BULLET_CHARACTERS) {
            if (character == bullet) {
                return true;
            }
        }
        return false;
    }

    private boolean isOrderedListLine(final String text)
    {
        final String trimmed = text.trim();
        int index = 0;
        while (index < trimmed.length() && Character.isDigit(trimmed.charAt(index))) {
            index++;
        }
        if (index == 0 || index >= trimmed.length()) {
            return false;
        }
        final char marker = trimmed.charAt(index);
        if (marker != '.' && marker != ')') {
            return false;
        }
        return index + 1 < trimmed.length() && Character.isWhitespace(trimmed.charAt(index + 1));
    }

    private boolean isCodeLine(final StyledLine line)
    {
        if (line.monospaceRatio >= CODE_MONOSPACE_RATIO) {
            return true;
        }
        return this.looksLikeCodeText(line.text);
    }

    private boolean looksLikeCodeText(final String text)
    {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        final String trimmed = text.trim();
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

    private String escapeComment(final String value)
    {
        if (value == null) {
            return "";
        }
        return value.replace("--", "—");
    }

    // ---- Inner data types ----

    private static final class RulingLine
    {
        private final float x1;

        private final float y1;

        private final float x2;

        private final float y2;

        RulingLine(final float x1, final float y1, final float x2, final float y2)
        {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        boolean isHorizontal()
        {
            return Math.abs(this.y2 - this.y1) < RULING_TOLERANCE;
        }

        boolean isVertical()
        {
            return Math.abs(this.x2 - this.x1) < RULING_TOLERANCE;
        }

        float length()
        {
            final float dx = this.x2 - this.x1;
            final float dy = this.y2 - this.y1;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }

    private static final class TextToken
    {
        private final float x;

        private final float y;

        private final String text;

        TextToken(final float x, final float y, final String text)
        {
            this.x = x;
            this.y = y;
            this.text = text;
        }
    }

    private static final class DetectedTable
    {
        private final float[] rowY;

        private final float[] colX;

        private final String[][] cells;

        DetectedTable(final float[] rowY, final float[] colX)
        {
            this.rowY = rowY;
            this.colX = colX;
            this.cells = new String[rowY.length - 1][colX.length - 1];
            for (int r = 0; r < this.cells.length; r++) {
                for (int c = 0; c < this.cells[r].length; c++) {
                    this.cells[r][c] = "";
                }
            }
        }

        float topY()
        {
            return this.rowY[0];
        }

        float bottomY()
        {
            return this.rowY[this.rowY.length - 1];
        }

        int rowCount()
        {
            return this.rowY.length - 1;
        }

        int colCount()
        {
            return this.colX.length - 1;
        }

        boolean contains(final float x, final float y)
        {
            return x >= this.colX[0] - CLUSTER_TOLERANCE
                && x <= this.colX[this.colX.length - 1] + CLUSTER_TOLERANCE
                && y >= this.rowY[0] - CLUSTER_TOLERANCE
                && y <= this.rowY[this.rowY.length - 1] + CLUSTER_TOLERANCE;
        }

        void setCell(final int row, final int col, final String text)
        {
            if (row >= 0 && row < this.cells.length && col >= 0 && col < this.cells[row].length) {
                this.cells[row][col] = text;
            }
        }

        void assignToken(final TextToken token)
        {
            final int row = findInterval(this.rowY, token.y);
            final int col = findInterval(this.colX, token.x);
            if (row >= 0 && col >= 0) {
                final String current = this.cells[row][col];
                if (current.isEmpty()) {
                    this.cells[row][col] = token.text;
                } else {
                    this.cells[row][col] = current + " " + token.text;
                }
            }
        }

        private int findInterval(final float[] boundaries, final float value)
        {
            for (int i = 0; i < boundaries.length - 1; i++) {
                if (value >= boundaries[i] - CLUSTER_TOLERANCE
                    && value < boundaries[i + 1] + CLUSTER_TOLERANCE) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * For each row, finds islands of non-empty cells surrounded by empty cells and propagates
         * the island's merged content into those neighbouring empty cells. This handles PDF spanning
         * header cells whose text is positioned near the centre of a multi-column span, causing
         * the individual words to land in different columns while adjacent columns stay empty.
         */
        void propagateSpans()
        {
            for (int r = 0; r < this.rowCount(); r++) {
                this.propagateRowSpans(this.cells[r]);
            }
        }

        private void propagateRowSpans(final String[] row)
        {
            int emptyCount = 0;
            for (String cell : row) {
                if (cell.isEmpty()) {
                    emptyCount++;
                }
            }
            if (emptyCount == 0) {
                return;
            }
            final String[] original = row.clone();
            int c = 0;
            while (c < original.length) {
                if (original[c].isEmpty()) {
                    c++;
                    continue;
                }
                final int islandStart = c;
                final int islandEnd = this.findIslandEnd(original, islandStart);
                c = islandEnd + 1;
                final int leftStart = this.findLeftExtent(original, islandStart);
                final int rightEnd = this.findRightExtent(original, islandEnd);
                if (leftStart == islandStart && rightEnd == islandEnd) {
                    continue;
                }
                final String mergedText = this.mergeRange(original, islandStart, islandEnd);
                this.fillSpanWithMerged(row, leftStart, rightEnd, islandStart, islandEnd, mergedText);
            }
        }

        private int findIslandEnd(final String[] row, final int start)
        {
            int end = start;
            while (end + 1 < row.length && !row[end + 1].isEmpty()) {
                end++;
            }
            return end;
        }

        private int findLeftExtent(final String[] original, final int islandStart)
        {
            int leftStart = islandStart;
            while (leftStart > 0 && original[leftStart - 1].isEmpty()) {
                leftStart--;
            }
            return leftStart;
        }

        private int findRightExtent(final String[] original, final int islandEnd)
        {
            int rightEnd = islandEnd;
            while (rightEnd + 1 < original.length && original[rightEnd + 1].isEmpty()) {
                rightEnd++;
            }
            return rightEnd;
        }

        private String mergeRange(final String[] row, final int start, final int end)
        {
            final StringBuilder sb = new StringBuilder();
            for (int i = start; i <= end; i++) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(row[i]);
            }
            return sb.toString().trim();
        }

        private void fillSpanWithMerged(final String[] row, final int leftStart, final int rightEnd,
            final int islandStart, final int islandEnd, final String mergedText)
        {
            for (int i = leftStart; i <= rightEnd; i++) {
                if (row[i].isEmpty()) {
                    row[i] = mergedText;
                }
            }
            for (int i = islandStart; i <= islandEnd; i++) {
                row[i] = mergedText;
            }
        }

        /**
         * Returns a copy of this table with leading rows removed where a single cell dominates the
         * content (prose captured above the actual grid). Returns {@code null} if all rows are prose.
         */
        DetectedTable trimLeadingProseRows()
        {
            int firstGoodRow = this.rowCount();
            for (int r = 0; r < this.rowCount(); r++) {
                if (!isProseRow(this.cells[r])) {
                    firstGoodRow = r;
                    break;
                }
            }
            if (firstGoodRow == 0) {
                return this;
            }
            if (firstGoodRow >= this.rowCount()) {
                return null;
            }
            final int newRowCount = this.rowCount() - firstGoodRow;
            final float[] newRowY = new float[newRowCount + 1];
            System.arraycopy(this.rowY, firstGoodRow, newRowY, 0, newRowCount + 1);
            final DetectedTable trimmed = new DetectedTable(newRowY, this.colX);
            for (int r = 0; r < newRowCount; r++) {
                for (int c = 0; c < this.colCount(); c++) {
                    trimmed.setCell(r, c, this.cells[firstGoodRow + r][c]);
                }
            }
            return trimmed;
        }

        private static boolean isProseRow(final String[] row)
        {
            int totalChars = 0;
            int maxCellChars = 0;
            for (String cell : row) {
                totalChars += cell.length();
                if (cell.length() > maxCellChars) {
                    maxCellChars = cell.length();
                }
            }
            if (totalChars == 0 || maxCellChars == 0) {
                return false;
            }
            return maxCellChars > MAX_CELL_CHARS
                || (float) maxCellChars / (float) totalChars > MAX_CELL_TEXT_RATIO;
        }
    }

    // ---- Ruling line extractor ----

    /**
     * Extracts straight horizontal and vertical line segments from a PDF page's content stream.
     * PDFBox applies the CTM before invoking moveTo/lineTo/appendRectangle, so coordinates arrive
     * in page space (y-up, bottom-left origin). They are converted to display space
     * (y-down, top-left origin) via: displayY = pageHeight - pdfY.
     */
    private static final class RulingLineExtractor extends PDFGraphicsStreamEngine
    {
        private final List<RulingLine> rulingLines = new ArrayList<>();

        private final List<float[]> currentSegments = new ArrayList<>();

        private float currentX;

        private float currentY;

        private float pathStartX;

        private float pathStartY;

        private final float pageHeight;

        RulingLineExtractor(final PDPage page)
        {
            super(page);
            this.pageHeight = page.getMediaBox().getHeight();
        }

        List<RulingLine> extractLines(final PDPage page)
            throws IOException
        {
            this.rulingLines.clear();
            this.currentSegments.clear();
            this.processPage(page);
            return new ArrayList<>(this.rulingLines);
        }

        @Override
        public void appendRectangle(final Point2D p0, final Point2D p1,
            final Point2D p2, final Point2D p3)
            throws IOException
        {
            addSegment((float) p0.getX(), (float) p0.getY(), (float) p1.getX(), (float) p1.getY());
            addSegment((float) p1.getX(), (float) p1.getY(), (float) p2.getX(), (float) p2.getY());
            addSegment((float) p2.getX(), (float) p2.getY(), (float) p3.getX(), (float) p3.getY());
            addSegment((float) p3.getX(), (float) p3.getY(), (float) p0.getX(), (float) p0.getY());
        }

        @Override
        public void moveTo(final float x, final float y)
            throws IOException
        {
            this.currentX = x;
            this.currentY = y;
            this.pathStartX = x;
            this.pathStartY = y;
        }

        @Override
        public void lineTo(final float x, final float y)
            throws IOException
        {
            addSegment(this.currentX, this.currentY, x, y);
            this.currentX = x;
            this.currentY = y;
        }

        @Override
        public void curveTo(final float x1, final float y1, final float x2, final float y2,
            final float x3, final float y3)
            throws IOException
        {
            this.currentX = x3;
            this.currentY = y3;
        }

        @Override
        public Point2D getCurrentPoint()
            throws IOException
        {
            return new Point2D.Float(this.currentX, this.currentY);
        }

        @Override
        public void closePath()
            throws IOException
        {
            addSegment(this.currentX, this.currentY, this.pathStartX, this.pathStartY);
            this.currentX = this.pathStartX;
            this.currentY = this.pathStartY;
        }

        @Override
        public void endPath()
            throws IOException
        {
            this.currentSegments.clear();
        }

        @Override
        public void strokePath()
            throws IOException
        {
            emitRulingLines();
            this.currentSegments.clear();
        }

        @Override
        public void fillPath(final int windingRule)
            throws IOException
        {
            this.currentSegments.clear();
        }

        @Override
        public void fillAndStrokePath(final int windingRule)
            throws IOException
        {
            // Thin filled rectangles are also used as table borders
            emitRulingLines();
            this.currentSegments.clear();
        }

        @Override
        public void clip(final int windingRule)
            throws IOException
        {
            // Clip paths are consumed by the graphics state — not rendered as lines
        }

        @Override
        public void drawImage(final PDImage image)
            throws IOException
        {
            // Images do not contribute ruling lines
        }

        @Override
        public void shadingFill(final COSName shadingName)
            throws IOException
        {
            // Gradient shading does not contribute ruling lines
        }

        private void addSegment(final float x1, final float y1, final float x2, final float y2)
        {
            this.currentSegments.add(new float[]{x1, y1, x2, y2});
        }

        private void emitRulingLines()
        {
            for (float[] seg : this.currentSegments) {
                // Convert y-up page space to y-down display space
                final float displayY1 = this.pageHeight - seg[1];
                final float displayY2 = this.pageHeight - seg[3];
                final RulingLine line = new RulingLine(seg[0], displayY1, seg[2], displayY2);
                if (line.length() >= RULING_MIN_LENGTH && (line.isHorizontal() || line.isVertical())) {
                    this.rulingLines.add(line);
                }
            }
        }
    }

    // ---- Text extraction ----

    private static final class StyledLine
    {
        private String text = "";

        private float topY;

        private float boldRatio;

        private int wordCount;

        private float startX;

        private float monospaceRatio;
    }

    private static final class ListBuffer
    {
        private final List<String> items = new ArrayList<>();

        private void addItem(final String text)
        {
            if (StringUtils.isNotBlank(text)) {
                this.items.add(text);
            }
        }

        private void appendToLastItem(final String text)
        {
            if (this.items.isEmpty() || StringUtils.isBlank(text)) {
                return;
            }
            final int lastIndex = this.items.size() - 1;
            this.items.set(lastIndex, this.items.get(lastIndex) + " " + text.trim());
        }

        private boolean isEmpty()
        {
            return this.items.isEmpty();
        }

        private void clear()
        {
            this.items.clear();
        }

        private String toMarkdown()
        {
            final StringBuilder list = new StringBuilder();
            for (String item : this.items) {
                if (list.length() > 0) {
                    list.append('\n');
                }
                list.append("- ").append(item);
            }
            return list.toString();
        }
    }

    private static final class CodeBuffer
    {
        private final List<String> lines = new ArrayList<>();

        private void addLine(final String line)
        {
            if (StringUtils.isNotBlank(line)) {
                this.lines.add(line.trim());
            }
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
            final StringBuilder code = new StringBuilder();
            code.append(CODE_FENCE).append('\n');
            for (String line : this.lines) {
                code.append(line).append('\n');
            }
            code.append(CODE_FENCE);
            return code.toString();
        }
    }

    private static final class StyledPdfTextStripper extends PDFTextStripper
    {
        private final List<StyledLine> lines = new ArrayList<>();

        private final List<TextToken> tokens = new ArrayList<>();

        private final StringBuilder currentText = new StringBuilder();

        private float currentY;

        private int totalChars;

        private int boldChars;

        private int monospaceChars;

        private float lineStartX;

        StyledPdfTextStripper()
            throws IOException
        {
            this.setSortByPosition(true);
            this.setIndentThreshold(2.0f);
            this.setDropThreshold(2.5f);
            this.setSpacingTolerance(0.5f);
            this.setAverageCharTolerance(0.3f);
        }

        List<StyledLine> extractPageLines(final PDDocument document, final int page)
            throws IOException
        {
            this.lines.clear();
            this.tokens.clear();
            this.currentText.setLength(0);
            this.totalChars = 0;
            this.boldChars = 0;
            this.monospaceChars = 0;
            this.lineStartX = 0.0f;
            this.setStartPage(page);
            this.setEndPage(page);
            this.writeText(document, new java.io.StringWriter());
            finalizeLine();
            return new ArrayList<>(this.lines);
        }

        List<TextToken> getLastPageTokens()
        {
            return new ArrayList<>(this.tokens);
        }

        @Override
        protected void writeString(final String text, final List<TextPosition> textPositions)
            throws IOException
        {
            if (this.currentText.length() == 0 && !textPositions.isEmpty()) {
                final TextPosition firstPosition = textPositions.get(0);
                this.currentY = firstPosition.getYDirAdj();
                this.lineStartX = firstPosition.getXDirAdj();
            }
            final String reconstructed = reconstructTextWithSpacing(text, textPositions);
            this.currentText.append(reconstructed);
            this.totalChars += reconstructed.length();
            for (TextPosition position : textPositions) {
                this.updateFontStats(position);
            }
            this.collectWordTokens(textPositions);
        }

        @Override
        protected void writeLineSeparator()
            throws IOException
        {
            finalizeLine();
        }

        @Override
        protected void writeWordSeparator()
            throws IOException
        {
            appendSpace(this.currentText);
        }

        private void finalizeLine()
        {
            final String cleaned = this.currentText.toString().trim();
            if (StringUtils.isNotBlank(cleaned)) {
                final StyledLine line = new StyledLine();
                line.text = cleaned;
                line.topY = this.currentY;
                line.boldRatio = this.totalChars == 0 ? 0.0f : (float) this.boldChars / (float) this.totalChars;
                line.wordCount = StringUtils.split(cleaned).length;
                line.startX = this.lineStartX;
                line.monospaceRatio = this.totalChars == 0 ? 0.0f
                    : (float) this.monospaceChars / (float) this.totalChars;
                this.lines.add(line);
            }
            this.currentText.setLength(0);
            this.totalChars = 0;
            this.boldChars = 0;
            this.monospaceChars = 0;
            this.currentY = 0.0f;
            this.lineStartX = 0.0f;
        }

        private void collectWordTokens(final List<TextPosition> positions)
        {
            final StringBuilder wordText = new StringBuilder();
            float wordStartX = 0.0f;
            float wordY = 0.0f;
            TextPosition prev = null;
            for (TextPosition pos : positions) {
                final String ch = StringUtils.defaultString(pos.getUnicode());
                if (StringUtils.isBlank(ch) || isColumnGap(prev, pos)) {
                    if (wordText.length() > 0) {
                        this.tokens.add(new TextToken(wordStartX, wordY, wordText.toString()));
                        wordText.setLength(0);
                    }
                }
                if (!StringUtils.isBlank(ch)) {
                    if (wordText.length() == 0) {
                        wordStartX = pos.getXDirAdj();
                        wordY = pos.getYDirAdj();
                    }
                    wordText.append(ch);
                    prev = pos;
                }
            }
            if (wordText.length() > 0) {
                this.tokens.add(new TextToken(wordStartX, wordY, wordText.toString()));
            }
        }

        private boolean isColumnGap(final TextPosition previous, final TextPosition current)
        {
            if (previous == null) {
                return false;
            }
            final float gap = current.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
            final float spaceWidth = Math.max(previous.getWidthOfSpace(), current.getWidthOfSpace());
            return spaceWidth > 0.0f && gap > spaceWidth * 0.5f;
        }

        private void updateFontStats(final TextPosition position)
        {
            final String fontName = StringUtils.defaultString(position.getFont().getName()).toLowerCase();
            if (fontName.contains("bold") || fontName.contains("black") || fontName.contains("heavy")
                || fontName.contains("demi")) {
                this.boldChars++;
            }
            if (this.isMonospaceFont(fontName)) {
                this.monospaceChars++;
            }
        }

        private boolean isMonospaceFont(final String fontName)
        {
            for (String fragment : MONOSPACE_FONT_FRAGMENTS) {
                if (fontName.contains(fragment)) {
                    return true;
                }
            }
            return false;
        }

        private String reconstructTextWithSpacing(final String text, final List<TextPosition> textPositions)
        {
            if (shouldKeepTextAsIs(text)) {
                return text;
            }
            if (textPositions == null || textPositions.isEmpty()) {
                return "";
            }
            final ReconstructionResult reconstruction = buildReconstruction(textPositions);
            if (shouldKeepTextAsIs(reconstruction.text)) {
                return reconstruction.text;
            }
            if (shouldJoinTokensWithSpaces(reconstruction.tokens)) {
                return String.join(" ", reconstruction.tokens);
            }
            return reconstruction.text;
        }

        private ReconstructionResult buildReconstruction(final List<TextPosition> textPositions)
        {
            final StringBuilder result = new StringBuilder();
            final List<String> resultTokens = new ArrayList<>();
            final float adaptiveThreshold = calculateAdaptiveGapThreshold(textPositions);
            TextPosition previous = null;
            for (TextPosition position : textPositions) {
                final String unicode = StringUtils.defaultString(position.getUnicode());
                if (unicode.isEmpty()) {
                    continue;
                }
                if (isWhitespaceToken(unicode)) {
                    appendSpace(result);
                    previous = position;
                    continue;
                }
                if (StringUtils.isBlank(unicode)) {
                    continue;
                }
                resultTokens.add(unicode);
                if (shouldInsertSpace(previous, position, result, adaptiveThreshold)) {
                    result.append(' ');
                }
                result.append(unicode);
                previous = position;
            }
            return new ReconstructionResult(result.toString(), resultTokens);
        }

        private float calculateAdaptiveGapThreshold(final List<TextPosition> textPositions)
        {
            final List<Float> gaps = new ArrayList<>();
            TextPosition previous = null;
            for (TextPosition position : textPositions) {
                final String unicode = StringUtils.defaultString(position.getUnicode());
                if (unicode.isEmpty() || isWhitespaceToken(unicode) || StringUtils.isBlank(unicode)) {
                    continue;
                }
                if (previous != null) {
                    final float previousEndX = previous.getXDirAdj() + previous.getWidthDirAdj();
                    final float nextStartX = position.getXDirAdj();
                    final float gap = nextStartX - previousEndX;
                    if (gap > 0.0f) {
                        gaps.add(gap);
                    }
                }
                previous = position;
            }
            if (gaps.size() < 3) {
                return -1.0f;
            }
            Collections.sort(gaps);
            final float median = gaps.get(gaps.size() / 2);
            final int percentileIndex = Math.min(gaps.size() - 1, Math.max(0, (int) (gaps.size() * 0.9f)));
            final float p90 = gaps.get(percentileIndex);
            return median + (p90 - median) * 0.5f;
        }

        private boolean isWhitespaceToken(final String unicode)
        {
            for (int i = 0; i < unicode.length(); i++) {
                if (!Character.isWhitespace(unicode.charAt(i))) {
                    return false;
                }
            }
            return !unicode.isEmpty();
        }

        private void appendSpace(final StringBuilder result)
        {
            if (result.length() == 0 || result.charAt(result.length() - 1) == ' ') {
                return;
            }
            result.append(' ');
        }

        private boolean shouldKeepTextAsIs(final String text)
        {
            return hasEmbeddedSpaces(text) && !hasJoinedWordRuns(text);
        }

        private boolean hasEmbeddedSpaces(final String text)
        {
            return StringUtils.contains(text, ' ');
        }

        private boolean hasJoinedWordRuns(final String text)
        {
            int letterRun = 0;
            for (int i = 0; i < text.length(); i++) {
                if (Character.isLetter(text.charAt(i))) {
                    letterRun++;
                    if (letterRun >= 20) {
                        return true;
                    }
                } else {
                    letterRun = 0;
                }
            }
            return false;
        }

        private boolean shouldInsertSpace(final TextPosition previous, final TextPosition current,
            final StringBuilder result, final float adaptiveThreshold)
        {
            if (previous == null || result.length() == 0 || result.charAt(result.length() - 1) == ' ') {
                return false;
            }
            final float previousEndX = previous.getXDirAdj() + previous.getWidthDirAdj();
            final float nextStartX = current.getXDirAdj();
            final float gap = nextStartX - previousEndX;
            final float threshold = adaptiveThreshold > 0.0f
                ? adaptiveThreshold
                : calculateGapThreshold(previous, current);
            return gap > threshold;
        }

        private float calculateGapThreshold(final TextPosition previous, final TextPosition current)
        {
            final float spaceWidth = Math.max(previous.getWidthOfSpace(), current.getWidthOfSpace());
            final float averageCharWidth = Math.max(0.1f,
                (previous.getWidthDirAdj() + current.getWidthDirAdj()) / 2.0f);
            final float fallbackSpaceWidth = Math.max(1.0f, averageCharWidth * 0.6f);
            final float effectiveSpaceWidth = spaceWidth > 0.0f ? spaceWidth : fallbackSpaceWidth;
            return Math.max(0.02f, effectiveSpaceWidth * 0.02f);
        }

        private boolean shouldJoinTokensWithSpaces(final List<String> resultTokens)
        {
            if (resultTokens.size() <= 1) {
                return false;
            }
            int multiCharTokens = 0;
            for (String token : resultTokens) {
                if (token.length() > 1) {
                    multiCharTokens++;
                }
            }
            return multiCharTokens * 2 >= resultTokens.size();
        }

        private static final class ReconstructionResult
        {
            private final String text;

            private final List<String> tokens;

            ReconstructionResult(final String text, final List<String> tokens)
            {
                this.text = text;
                this.tokens = tokens;
            }
        }
    }
}
