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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects DOCX files that number their section headings with a shared Word multi-level list
 * ({@code ListParagraph} paragraphs on one {@code numId}) rather than with {@code Heading 1/2}
 * styles, which is the pattern that Docling mis-renders.
 * <p>
 * Docling groups every paragraph that shares a list {@code numId} into a single continuous list,
 * even when body paragraphs or tables sit between those items. When a document uses such a list for
 * its section outline, Docling builds one giant list group and parks the intervening prose after it,
 * scrambling the heading distribution and numbering. For those documents the Apache POI generator
 * ({@link DocxMarkdownGenerator}), which renders numbered paragraphs inline in document order, is a
 * safer choice than Docling.
 * </p>
 * <p>
 * The detector walks the body in document order and measures, per {@code numId}, how much plain
 * body text accumulates between two list items that share that id. A genuine outline-as-list packs
 * substantial prose between its items; a real numbered list (procedure steps, inclusion criteria)
 * does not. Heading/Title-styled paragraphs and bullet lists are ignored; a bare
 * {@code w:outlineLvl} on {@code ListParagraph}/{@code Body Text} is <em>not</em> treated as a
 * heading, because that is exactly the outline-as-list pattern Docling scrambles. Tables reset
 * the per-id continuity because Docling already splits its list groups at a table. The signal
 * therefore avoids the false positives of a naive title-length heuristic: legitimately numbered
 * {@code Heading 1/2} outlines, bullet lists interrupted by notes, and short numbered lists all
 * score below threshold.
 * </p>
 * <p>
 * The check is intentionally conservative and fails open — any parsing error yields {@code false}
 * so the caller keeps the default Docling path.
 * </p>
 *
 * @version $Id$
 */
public class DocxListOutlineRiskDetector
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DocxListOutlineRiskDetector.class);

    private static final String BULLET_NUM_FMT = "bullet";

    /** Minimum body characters between two same-id items for the gap to count as a displacement event. */
    private static final long MIN_EVENT_CHARS = 120L;

    /** Minimum number of displacement events required before the cumulative-mass rule can fire. */
    private static final int MIN_EVENTS = 2;

    /** Minimum cumulative displaced body characters required by the cumulative-mass rule. */
    private static final long MIN_TOTAL_DISPLACED = 600L;

    /** A single gap this large is on its own strong enough evidence of an outline-as-list. */
    private static final long LARGE_SINGLE_DISPLACED = 2000L;

    /**
     * Decide whether the given DOCX numbers its section outline with a shared multi-level list in a
     * way that Docling is known to scramble.
     *
     * @param content the raw DOCX bytes
     * @param fileName the source file name, used only for logging
     * @return {@code true} when the Apache POI generator should be preferred over Docling for this
     *         document; {@code false} when Docling is safe or the document cannot be inspected
     */
    public boolean hasListOutlineRisk(final byte[] content, final String fileName)
    {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            final RiskAccumulator accumulator = this.analyze(document);
            final boolean risky = accumulator.isRisky();
            if (risky) {
                LOGGER.info(
                    "DOCX '{}' numbers sections with a shared multi-level list (events={}, displaced={} chars);"
                    + " preferring Apache POI over Docling",
                    fileName, accumulator.getEvents(), accumulator.getTotalDisplaced());
            }
            return risky;
        } catch (IOException | RuntimeException | LinkageError e) {
            LOGGER.debug("DOCX outline-risk detection failed for '{}', keeping default parser: {}",
                fileName, e.getMessage());
            return false;
        }
    }

    private RiskAccumulator analyze(final XWPFDocument document)
    {
        final XWPFStyles styles = document.getStyles();
        final Map<String, Boolean> headingStyleCache = new HashMap<>();
        final RiskAccumulator accumulator = new RiskAccumulator();
        for (IBodyElement element : document.getBodyElements()) {
            this.processElement(element, accumulator, styles, headingStyleCache);
        }
        return accumulator;
    }

    private void processElement(final IBodyElement element, final RiskAccumulator accumulator,
        final XWPFStyles styles, final Map<String, Boolean> headingStyleCache)
    {
        if (element instanceof XWPFTable || element instanceof XWPFSDT) {
            // Docling already breaks its list groups at a table (and structured-document-tag content is
            // opaque), so treat both as boundaries that reset per-id continuity.
            accumulator.resetBaselines();
            return;
        }
        if (element instanceof XWPFParagraph) {
            this.processParagraph((XWPFParagraph) element, accumulator, styles, headingStyleCache);
        }
    }

    private void processParagraph(final XWPFParagraph paragraph, final RiskAccumulator accumulator,
        final XWPFStyles styles, final Map<String, Boolean> headingStyleCache)
    {
        final String text = paragraph.getText();
        if (StringUtils.isBlank(text)) {
            return;
        }
        final BigInteger numId = paragraph.getNumID();
        if (numId == null || numId.signum() == 0) {
            // Plain, unnumbered paragraph: this is the body text that a mis-grouped list displaces.
            accumulator.addBody(text.trim().length());
            return;
        }
        if (this.isHeadingStyled(paragraph, styles, headingStyleCache)) {
            // Docling renders Heading/Title-styled paragraphs as headings, not as list items, so a
            // shared numId on a real Heading 1/2 outline does not trigger the grouping bug.
            return;
        }
        final String numberingFormat = paragraph.getNumFmt();
        if (BULLET_NUM_FMT.equalsIgnoreCase(numberingFormat)) {
            // Bullet lists are not outline-risk items. A null format still counts: POI often cannot
            // resolve numFmt for outline-as-list paragraphs that nonetheless share a numId.
            return;
        }
        accumulator.recordTrackedItem(numId);
    }

    private boolean isHeadingStyled(final XWPFParagraph paragraph, final XWPFStyles styles,
        final Map<String, Boolean> headingStyleCache)
    {
        // Only Heading/Title *styles* are safe for Docling. A bare w:outlineLvl on ListParagraph
        // or Body Text is common in outline-as-list documents and must still be tracked — Docling
        // groups those by numId and displaces the intervening body text.
        final String styleId = paragraph.getStyle();
        if (StringUtils.isBlank(styleId)) {
            return false;
        }
        return headingStyleCache.computeIfAbsent(styleId, id -> this.resolveHeadingStyle(id, styles));
    }

    private boolean resolveHeadingStyle(final String styleId, final XWPFStyles styles)
    {
        if (this.isHeadingOrTitleToken(styleId)) {
            return true;
        }
        if (styles == null) {
            return false;
        }
        final XWPFStyle style = styles.getStyle(styleId);
        if (style == null) {
            return false;
        }
        return this.isHeadingOrTitleToken(style.getName());
    }

    private boolean isHeadingOrTitleToken(final String value)
    {
        if (value == null) {
            return false;
        }
        final String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("heading") || normalized.contains("title");
    }

    /**
     * Mutable tally of displacement evidence gathered while walking the document body.
     */
    private static final class RiskAccumulator
    {
        private final Map<BigInteger, Long> baselineBodyCharsByNumId = new HashMap<>();

        private long bodyChars;

        private int events;

        private long totalDisplaced;

        private long maxSingleDisplaced;

        private void addBody(final int length)
        {
            this.bodyChars += length;
        }

        private void resetBaselines()
        {
            this.baselineBodyCharsByNumId.clear();
        }

        private void recordTrackedItem(final BigInteger numId)
        {
            final Long baseline = this.baselineBodyCharsByNumId.get(numId);
            if (baseline != null) {
                final long displaced = this.bodyChars - baseline;
                if (displaced >= MIN_EVENT_CHARS) {
                    this.events++;
                    this.totalDisplaced += displaced;
                    this.maxSingleDisplaced = Math.max(this.maxSingleDisplaced, displaced);
                }
            }
            this.baselineBodyCharsByNumId.put(numId, this.bodyChars);
        }

        private int getEvents()
        {
            return this.events;
        }

        private long getTotalDisplaced()
        {
            return this.totalDisplaced;
        }

        private boolean isRisky()
        {
            return this.events >= MIN_EVENTS && this.totalDisplaced >= MIN_TOTAL_DISPLACED
                || this.maxSingleDisplaced >= LARGE_SINGLE_DISPLACED;
        }
    }
}
