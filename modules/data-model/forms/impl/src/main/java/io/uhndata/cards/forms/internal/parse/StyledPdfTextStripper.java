/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.forms.internal.parse;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

final class StyledPdfTextStripper extends PDFTextStripper
{
    /** Fraction of page height defining the header and footer decoration zones (top and bottom). */
    private static final float DECORATION_ZONE_FRACTION = 0.15f;

    private static final String[] MONOSPACE_FONT_FRAGMENTS = {
        "courier",
        "mono",
        "consolas",
        "menlo",
        "typewriter",
        "code",
    };

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
        this.writeText(document, new StringWriter());
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
            final float pageHeight = this.getCurrentPage().getMediaBox().getHeight();
            final boolean isDecoration = this.currentY < pageHeight * DECORATION_ZONE_FRACTION
                || this.currentY > pageHeight * (1.0f - DECORATION_ZONE_FRACTION);
            final float boldRatio = this.totalChars == 0
                ? 0.0f : (float) this.boldChars / (float) this.totalChars;
            final int wordCount = StringUtils.split(cleaned).length;
            final float monospaceRatio = this.totalChars == 0
                ? 0.0f : (float) this.monospaceChars / (float) this.totalChars;
            this.lines.add(new StyledLine(cleaned, this.currentY, boldRatio, wordCount,
                this.lineStartX, monospaceRatio, isDecoration));
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
