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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final String DOUBLE_NEWLINE = "\n\n";

    private static final int DIAGRAM_MIN_FILLS = 6;

    private static final String[] TOC_SECTION_HEADINGS = {
        "TABLE OF CONTENTS",
        "TABLE OF CONTENT",
        "CONTENTS",
    };

    private static final String[] ABBREVIATION_SECTION_HEADINGS = {
        "LIST OF ABBREVIATIONS",
        "LIST OF ACRONYMS",
        "ABBREVIATIONS",
        "ACRONYMS",
    };

    private static final String[] TOC_TABLE_HEADER = {"Section", "Page"};

    private static final String[] ABBREVIATION_TABLE_HEADER = {"Abbreviation", "Meaning"};

    private static final Pattern TOC_ENTRY_PATTERN =
        Pattern.compile("^(.+?)\\s*\\.{4,}\\s*(\\d+)\\s*$");

    private static final Pattern ABBREVIATION_ENTRY_PATTERN =
        Pattern.compile("^([A-Z][A-Z0-9\\-\\.]{1,7})\\s+(.+)$");

    private static final int KEY_VALUE_KEY_MAX_WORDS = 5;

    private static final Pattern KEY_VALUE_ENTRY_PATTERN =
        Pattern.compile("^([A-Za-z][^:]{0,59}):\\s+(.+)$");

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
            final List<List<StyledLine>> allLines = new ArrayList<>();
            final List<List<TextToken>> allTokens = new ArrayList<>();
            this.loadAllPageLines(stripper, document, pageCount, allLines, allTokens);
            final Set<String> decorations = this.collectDecorations(allLines);
            final Set<String> seenDecorations = new HashSet<>();
            final SpecialSection[] currentSection = {SpecialSection.NONE};
            for (int page = 1; page <= pageCount; page++) {
                final List<StyledLine> rawLines = allLines.get(page - 1);
                final PDPage pdPage = document.getPage(page - 1);
                final RulingLineExtractor rulingData = extractRulingLinesSafely(pdPage, page, fileName);
                final List<DetectedTable> tables = detectPageTables(rulingData, tabulaParser, page,
                    fileName, allTokens.get(page - 1));
                final List<StyledLine> pageLines = this.filterDecorations(rawLines, decorations, seenDecorations);
                final String pageText = renderPage(pageLines, tables, currentSection);
                markdown.append("\n\n<!-- page: ").append(page).append(" -->\n")
                    .append("## Page ").append(page).append(DOUBLE_NEWLINE);
                if (StringUtils.isBlank(pageText)) {
                    markdown.append("_No extractable text on this page._\n");
                } else {
                    markdown.append(pageText).append('\n');
                }
            }
            return MarkdownCleanup.clean(markdown.toString());
        } finally {
            final long endTimestamp = System.currentTimeMillis();
            LOGGER.info("PDF markdown parsing finished for file '{}' at {} (total {} ms)",
                fileName, endTimestamp, endTimestamp - startTimestamp);
        }
    }

    private RulingLineExtractor extractRulingLinesSafely(final PDPage page, final int pageNum,
        final String fileName)
    {
        final RulingLineExtractor extractor = new RulingLineExtractor(page);
        try {
            extractor.extractLines(page);
        } catch (IOException e) {
            LOGGER.warn("Could not extract ruling lines from page {} of '{}': {}", pageNum, fileName,
                e.getMessage());
        }
        return extractor;
    }

    private String renderPage(final List<StyledLine> lines, final List<DetectedTable> tables,
        final SpecialSection[] currentSection)
    {
        final List<StyledLine> allNormalized = lines.stream()
            .map(this::normalizedCopy)
            .filter(line -> StringUtils.isNotBlank(line.getText()))
            .toList();
        final List<DetectedTable> sortedTables = new ArrayList<>(tables);
        sortedTables.sort((a, b) -> Float.compare(a.topY(), b.topY()));
        final List<StyledLine> proseLines = allNormalized.stream()
            .filter(l -> !isInsideAnyTable(l.getTopY(), sortedTables))
            .toList();
        if (proseLines.isEmpty() && sortedTables.isEmpty()) {
            return "";
        }
        final float baseGap = estimateBaseGap(proseLines);
        final StringBuilder output = new StringBuilder();
        final List<String[]> sectionRows = new ArrayList<>();
        final boolean[] emitted = new boolean[sortedTables.size()];
        renderProseLinesWithTables(proseLines, sortedTables, emitted, baseGap, output,
            sectionRows, currentSection);
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
        final StringBuilder output, final List<String[]> sectionRows,
        final SpecialSection[] currentSection)
    {
        final ListBuffer listBuffer = new ListBuffer();
        final CodeBuffer codeBuffer = new CodeBuffer();
        final StringBuilder paragraphBuffer = new StringBuilder();
        final List<String[]> keyValueRows = new ArrayList<>();
        for (int i = 0; i < proseLines.size(); i++) {
            final StyledLine current = proseLines.get(i);
            emitPendingTables(output, paragraphBuffer, listBuffer, codeBuffer, sortedTables, emitted,
                current.getTopY());
            final StyledLine previous = i > 0 ? proseLines.get(i - 1) : null;
            final StyledLine next = i + 1 < proseLines.size() ? proseLines.get(i + 1) : null;
            if (this.isHeading(current, previous, next, baseGap)) {
                this.flushAllBuffers(output, listBuffer, codeBuffer, paragraphBuffer,
                    keyValueRows, sectionRows, currentSection);
                this.activateSectionForHeading(current.getText(), currentSection);
                this.appendHeading(output, current.getText());
                continue;
            }
            if (currentSection[0] != SpecialSection.NONE) {
                final String[] entry = currentSection[0] == SpecialSection.TOC
                    ? this.parseTocEntry(current.getText())
                    : this.parseAbbreviationEntry(current.getText());
                if (entry != null) {
                    sectionRows.add(entry);
                    continue;
                }
            }
            if (this.isKeyValueEntry(current.getText(), next, keyValueRows)) {
                keyValueRows.add(this.parseKeyValueEntry(current.getText()));
                continue;
            }
            this.flushKeyValueRows(output, keyValueRows);
            if (this.isCodeLine(current)) {
                this.flushList(output, listBuffer);
                this.flushParagraph(output, paragraphBuffer);
                codeBuffer.addLine(current.getText());
                continue;
            }
            this.handleListOrParagraph(current, previous, baseGap, output, paragraphBuffer,
                listBuffer, codeBuffer);
        }
        this.flushAllBuffers(output, listBuffer, codeBuffer, paragraphBuffer,
            keyValueRows, sectionRows, currentSection);
    }

    private void handleListOrParagraph(final StyledLine current, final StyledLine previous,
        final float baseGap, final StringBuilder output, final StringBuilder paragraphBuffer,
        final ListBuffer listBuffer, final CodeBuffer codeBuffer)
    {
        final boolean startsList = this.startsWithListMarker(current.getText());
        final boolean continuesList = this.continuesListItem(current, previous, listBuffer, baseGap);
        if (startsList || continuesList) {
            this.flushCodeBlock(output, codeBuffer);
            this.flushParagraph(output, paragraphBuffer);
            if (startsList) {
                listBuffer.addItem(this.stripLeadingListMarker(current.getText()));
            } else {
                listBuffer.appendToLastItem(current.getText());
            }
            return;
        }
        this.flushList(output, listBuffer);
        this.flushCodeBlock(output, codeBuffer);
        if (previous != null && this.lineGap(previous, current) > baseGap * PARAGRAPH_GAP_RATIO) {
            this.flushParagraph(output, paragraphBuffer);
        }
        this.appendParagraphLine(paragraphBuffer, current.getText());
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
            if (lineDisplayY >= table.topY() - DetectedTable.CLUSTER_TOLERANCE
                && lineDisplayY <= table.bottomY() + DetectedTable.CLUSTER_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private List<DetectedTable> detectPageTables(final RulingLineExtractor rulingData,
        final TabulaPageParser tabulaParser, final int page,
        final String fileName, final List<TextToken> tokens)
    {
        if (rulingData.getNonRectFillCount() >= DIAGRAM_MIN_FILLS) {
            return Collections.emptyList();
        }
        final List<DetectedTable> result = tabulaParser.extractTables(page, fileName);
        if (result.isEmpty()) {
            return detectTables(rulingData.getLines(), tokens);
        }
        return result;
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
        if (rowYList.size() < TabulaPageParser.MIN_TABLE_ROWS + 1
            || colXList.size() < TabulaPageParser.MIN_TABLE_COLS + 1) {
            return Collections.emptyList();
        }
        final float[] rowY = toFloatArray(rowYList);
        final float[] colX = toFloatArray(colXList);
        final DetectedTable table = new DetectedTable(rowY, colX);
        for (TextToken token : tokens) {
            if (table.contains(token.getX(), token.getY())) {
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
                ? (double) ((line.getY1() + line.getY2()) / 2.0f)
                : (double) ((line.getX1() + line.getX2()) / 2.0f);
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
            if (sorted.get(i) - sorted.get(i - 1) <= DetectedTable.CLUSTER_TOLERANCE) {
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
        appendTableRow(sb, table.getRow(0), cols);
        sb.append('|');
        for (int c = 0; c < cols; c++) {
            sb.append("---|");
        }
        sb.append('\n');
        for (int r = 1; r < rows; r++) {
            appendTableRow(sb, table.getRow(r), cols);
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
        return new StyledLine(
            this.normalizeLineText(line.getText()),
            line.getTopY(),
            line.getBoldRatio(),
            line.getWordCount(),
            line.getStartX(),
            line.getMonospaceRatio(),
            false);
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
        return Math.abs(current.getTopY() - previous.getTopY());
    }

    private boolean isHeading(final StyledLine current, final StyledLine previous,
        final StyledLine next, final float baseGap)
    {
        if (current.getWordCount() == 0 || current.getWordCount() > HEADING_MAX_WORDS) {
            return false;
        }
        if (current.getBoldRatio() < HEADING_BOLD_RATIO) {
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
        if (this.startsWithListMarker(current.getText())) {
            return false;
        }
        if (current.getStartX() < previous.getStartX() - LIST_LINE_X_TOLERANCE) {
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
        if (line.getMonospaceRatio() >= CODE_MONOSPACE_RATIO) {
            return true;
        }
        return this.looksLikeCodeText(line.getText());
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

    private boolean isTocSectionHeading(final String text)
    {
        final String upper = text.trim().toUpperCase(Locale.ROOT);
        for (String heading : TOC_SECTION_HEADINGS) {
            if (upper.equals(heading)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAbbreviationSectionHeading(final String text)
    {
        final String upper = text.trim().toUpperCase(Locale.ROOT);
        for (String heading : ABBREVIATION_SECTION_HEADINGS) {
            if (upper.equals(heading)) {
                return true;
            }
        }
        return false;
    }

    private String[] parseTocEntry(final String text)
    {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        final Matcher matcher = TOC_ENTRY_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new String[]{matcher.group(1).trim(), matcher.group(2).trim()};
    }

    private String[] parseAbbreviationEntry(final String text)
    {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        final Matcher matcher = ABBREVIATION_ENTRY_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new String[]{matcher.group(1).trim(), matcher.group(2).trim()};
    }

    private void activateSectionForHeading(final String headingText,
        final SpecialSection[] currentSection)
    {
        if (this.isTocSectionHeading(headingText)) {
            currentSection[0] = SpecialSection.TOC;
        } else if (this.isAbbreviationSectionHeading(headingText)) {
            currentSection[0] = SpecialSection.ABBREVIATIONS;
        } else {
            currentSection[0] = SpecialSection.NONE;
        }
    }

    private void flushSectionRows(final StringBuilder output, final List<String[]> sectionRows,
        final SpecialSection[] currentSection)
    {
        if (sectionRows.isEmpty()) {
            return;
        }
        if (output.length() > 0) {
            output.append(DOUBLE_NEWLINE);
        }
        final String[] header = currentSection[0] == SpecialSection.TOC
            ? TOC_TABLE_HEADER : ABBREVIATION_TABLE_HEADER;
        output.append(this.sectionTableToMarkdown(sectionRows, header));
        sectionRows.clear();
    }

    private void flushAllBuffers(final StringBuilder output, final ListBuffer listBuffer,
        final CodeBuffer codeBuffer, final StringBuilder paragraphBuffer,
        final List<String[]> keyValueRows, final List<String[]> sectionRows,
        final SpecialSection[] currentSection)
    {
        this.flushList(output, listBuffer);
        this.flushCodeBlock(output, codeBuffer);
        this.flushParagraph(output, paragraphBuffer);
        this.flushKeyValueRows(output, keyValueRows);
        this.flushSectionRows(output, sectionRows, currentSection);
    }

    private String sectionTableToMarkdown(final List<String[]> rows, final String[] header)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append('|');
        for (String col : header) {
            sb.append(' ').append(col).append(" |");
        }
        sb.append('\n').append('|');
        for (int i = 0; i < header.length; i++) {
            sb.append("---|");
        }
        sb.append('\n');
        for (String[] row : rows) {
            sb.append('|');
            for (int c = 0; c < header.length; c++) {
                final String cell = c < row.length ? this.escapeCellText(row[c]) : " ";
                sb.append(' ').append(cell).append(" |");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private boolean isKeyValueLine(final String text)
    {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        final Matcher matcher = KEY_VALUE_ENTRY_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return false;
        }
        return StringUtils.split(matcher.group(1)).length <= KEY_VALUE_KEY_MAX_WORDS;
    }

    private boolean isKeyValueEntry(final String text, final StyledLine next,
        final List<String[]> keyValueRows)
    {
        if (!this.isKeyValueLine(text)) {
            return false;
        }
        return !keyValueRows.isEmpty() || (next != null && this.isKeyValueLine(next.getText()));
    }

    private String[] parseKeyValueEntry(final String text)
    {
        final Matcher matcher = KEY_VALUE_ENTRY_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new String[]{matcher.group(1).trim(), matcher.group(2).trim()};
    }

    private void flushKeyValueRows(final StringBuilder output, final List<String[]> keyValueRows)
    {
        if (keyValueRows.isEmpty()) {
            return;
        }
        if (output.length() > 0) {
            output.append(DOUBLE_NEWLINE);
        }
        final StringBuilder sb = new StringBuilder();
        for (String[] row : keyValueRows) {
            if (sb.length() > 0) {
                sb.append(DOUBLE_NEWLINE);
            }
            sb.append(row[0]).append(": ").append(row[1]);
        }
        output.append(sb.toString());
        keyValueRows.clear();
    }

    private String escapeComment(final String value)
    {
        if (value == null) {
            return "";
        }
        return value.replace("--", "—");
    }

    private String normalizeDecorationText(final String text)
    {
        return text.replaceAll("\\s+\\d+\\s*$", "").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> collectDecorations(final List<List<StyledLine>> allLines)
    {
        final Map<String, Integer> counts = new HashMap<>();
        int contentPages = 0;
        for (final List<StyledLine> pageLines : allLines) {
            final Set<String> seenThisPage = new HashSet<>();
            boolean hasContent = false;
            for (final StyledLine line : pageLines) {
                if (line.isDecorationCandidate()) {
                    seenThisPage.add(this.normalizeDecorationText(line.getText()));
                } else {
                    hasContent = true;
                }
            }
            if (hasContent) {
                contentPages++;
                for (final String text : seenThisPage) {
                    counts.merge(text, 1, Integer::sum);
                }
            }
        }
        final Set<String> result = new HashSet<>();
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= Math.max(2, contentPages - 1)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private List<StyledLine> filterDecorations(
        final List<StyledLine> lines,
        final Set<String> decorations,
        final Set<String> alreadySeen)
    {
        if (decorations.isEmpty()) {
            return lines;
        }
        final List<StyledLine> result = new ArrayList<>();
        for (final StyledLine line : lines) {
            final String normalized = this.normalizeDecorationText(line.getText());
            if (!decorations.contains(normalized) || alreadySeen.add(normalized)) {
                result.add(line);
            }
        }
        return result;
    }

    private void loadAllPageLines(final StyledPdfTextStripper stripper, final PDDocument document,
        final int pageCount, final List<List<StyledLine>> allLines, final List<List<TextToken>> allTokens)
        throws IOException
    {
        for (int page = 1; page <= pageCount; page++) {
            allLines.add(stripper.extractPageLines(document, page));
            allTokens.add(new ArrayList<>(stripper.getLastPageTokens()));
        }
    }

    // ---- Inner data types ----

    private enum SpecialSection
    {
        NONE, TOC, ABBREVIATIONS
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
            final boolean isNumbered = this.items.stream()
                .allMatch(s -> !s.isEmpty() && Character.isDigit(s.charAt(0)));
            for (String item : this.items) {
                if (list.length() > 0) {
                    list.append('\n');
                }
                if (!isNumbered) {
                    list.append("- ");
                }
                list.append(item);
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

}
