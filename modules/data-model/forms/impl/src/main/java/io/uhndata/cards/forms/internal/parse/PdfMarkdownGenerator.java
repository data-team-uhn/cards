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
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Generate markdown output from PDF input.
 *
 * @version $Id$
 */
public class PdfMarkdownGenerator
{
    private static final float HEADING_BOLD_RATIO = 0.7f;

    private static final float HEADING_GAP_RATIO = 1.2f;

    private static final float PARAGRAPH_GAP_RATIO = 1.6f;

    private static final int HEADING_MAX_WORDS = 16;

    /**
     * Convert PDF content to markdown grouped by pages.
     *
     * @param stream the pdf stream
     * @param documentId identifier for the parsed document
     * @param fileName source file name
     * @return markdown text
     * @throws IOException when reading fails
     */
    public String toMarkdown(final InputStream stream, final String documentId, final String fileName)
        throws IOException
    {
        byte[] bytes = stream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            StyledPdfTextStripper stripper = new StyledPdfTextStripper();
            StringBuilder markdown = new StringBuilder();
            markdown.append("<!-- document_id: ").append(escapeComment(documentId)).append(" -->\n");
            markdown.append("<!-- source_file: ").append(escapeComment(fileName)).append(" -->\n");

            int pageCount = document.getNumberOfPages();
            for (int page = 1; page <= pageCount; page++) {
                List<StyledLine> pageLines = stripper.extractPageLines(document, page);
                String pageText = renderPage(pageLines);
                markdown.append("\n\n<!-- page: ").append(page).append(" -->\n");
                markdown.append("## Page ").append(page).append("\n\n");
                if (StringUtils.isBlank(pageText)) {
                    markdown.append("_No extractable text on this page._\n");
                } else {
                    markdown.append(pageText).append('\n');
                }
            }

            return markdown.toString().trim();
        }
    }

    private String renderPage(final List<StyledLine> lines)
    {
        final List<StyledLine> nonBlankLines = lines.stream()
            .map(this::normalizedCopy)
            .filter(line -> StringUtils.isNotBlank(line.text))
            .toList();
        if (nonBlankLines.isEmpty()) {
            return "";
        }
        final float baseGap = estimateBaseGap(nonBlankLines);
        final StringBuilder output = new StringBuilder();
        final StringBuilder paragraphBuffer = new StringBuilder();
        for (int i = 0; i < nonBlankLines.size(); i++) {
            final StyledLine current = nonBlankLines.get(i);
            final StyledLine previous = i > 0 ? nonBlankLines.get(i - 1) : null;
            final StyledLine next = i + 1 < nonBlankLines.size() ? nonBlankLines.get(i + 1) : null;
            if (isHeading(current, previous, next, baseGap)) {
                flushParagraph(output, paragraphBuffer);
                appendHeading(output, current.text);
                continue;
            }
            if (previous != null && lineGap(previous, current) > baseGap * PARAGRAPH_GAP_RATIO) {
                flushParagraph(output, paragraphBuffer);
            }
            appendParagraphLine(paragraphBuffer, current.text);
        }
        flushParagraph(output, paragraphBuffer);
        return output.toString().trim();
    }

    private StyledLine normalizedCopy(final StyledLine line)
    {
        final StyledLine copy = new StyledLine();
        copy.text = normalizeLineText(line.text);
        copy.topY = line.topY;
        copy.boldRatio = line.boldRatio;
        copy.wordCount = line.wordCount;
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
            output.append("\n\n");
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
        if (output.length() > 0) {
            output.append("\n\n");
        }
        output.append(paragraphBuffer.toString().trim());
        paragraphBuffer.setLength(0);
    }

    private String escapeComment(final String value)
    {
        if (value == null) {
            return "";
        }
        return value.replace("--", "—");
    }

    private static final class StyledLine
    {
        private String text = "";

        private float topY;

        private float boldRatio;

        private int wordCount;
    }

    private static final class StyledPdfTextStripper extends PDFTextStripper
    {
        private final List<StyledLine> lines = new ArrayList<>();

        private final StringBuilder currentText = new StringBuilder();

        private float currentY;

        private int totalChars;

        private int boldChars;

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
            this.currentText.setLength(0);
            this.totalChars = 0;
            this.boldChars = 0;
            this.setStartPage(page);
            this.setEndPage(page);
            this.writeText(document, new java.io.StringWriter());
            finalizeLine();
            return new ArrayList<>(this.lines);
        }

        @Override
        protected void writeString(final String text, final List<TextPosition> textPositions)
            throws IOException
        {
            if (this.currentText.length() == 0 && !textPositions.isEmpty()) {
                this.currentY = textPositions.get(0).getYDirAdj();
            }
            String reconstructed = reconstructTextWithSpacing(text, textPositions);
            this.currentText.append(reconstructed);
            this.totalChars += reconstructed.length();
            for (TextPosition position : textPositions) {
                final String fontName = StringUtils.defaultString(position.getFont().getName()).toLowerCase();
                if (fontName.contains("bold") || fontName.contains("black") || fontName.contains("heavy")
                    || fontName.contains("demi")) {
                    this.boldChars++;
                }
            }
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
                this.lines.add(line);
            }
            this.currentText.setLength(0);
            this.totalChars = 0;
            this.boldChars = 0;
            this.currentY = 0.0f;
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
            final List<String> tokens = new ArrayList<>();
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
                tokens.add(unicode);
                if (shouldInsertSpace(previous, position, result, adaptiveThreshold)) {
                    result.append(' ');
                }
                result.append(unicode);
                previous = position;
            }
            return new ReconstructionResult(result.toString(), tokens);
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
                    final float currentStartX = position.getXDirAdj();
                    final float gap = currentStartX - previousEndX;
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
            final float currentStartX = current.getXDirAdj();
            final float gap = currentStartX - previousEndX;
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

        private boolean shouldJoinTokensWithSpaces(final List<String> tokens)
        {
            if (tokens.size() <= 1) {
                return false;
            }
            int multiCharTokens = 0;
            for (String token : tokens) {
                if (token.length() > 1) {
                    multiCharTokens++;
                }
            }
            return multiCharTokens * 2 >= tokens.size();
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
