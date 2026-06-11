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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import technology.tabula.ObjectExtractor;
import technology.tabula.Table;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

/**
 * Encapsulates Tabula table extraction for a single PDF document, hiding ObjectExtractor and
 * extraction algorithms from the outer class to keep its coupling count within limits.
 */
final class TabulaPageParser
{
    /** Minimum row count for a detected grid to qualify as a table (2 rows needs 3 H lines). */
    static final int MIN_TABLE_ROWS = 2;

    /** Minimum column count for a detected grid to qualify as a table (2 cols needs 3 V lines). */
    static final int MIN_TABLE_COLS = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(TabulaPageParser.class);

    /** Y-coordinate tolerance (points) for deduplicating overlapping tabula table detections. */
    private static final float TABLE_DEDUP_TOLERANCE = 10.0f;

    private final ObjectExtractor extractor;

    private final SpreadsheetExtractionAlgorithm algorithm;

    private final BasicExtractionAlgorithm basicAlgorithm;

    TabulaPageParser(final PDDocument document)
    {
        this.extractor = new ObjectExtractor(document);
        this.algorithm = new SpreadsheetExtractionAlgorithm();
        this.basicAlgorithm = new BasicExtractionAlgorithm();
    }

    List<DetectedTable> extractTables(final int pageNum, final String fileName)
    {
        try {
            final technology.tabula.Page tabulaPage = this.extractor.extract(pageNum);
            if (tabulaPage == null) {
                return Collections.emptyList();
            }
            List<Table> tabulaTables = this.algorithm.extract(tabulaPage);
            if (tabulaTables.isEmpty()) {
                tabulaTables = this.basicAlgorithm.extract(tabulaPage);
            }
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
