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
package io.uhndata.cards.forms.internal.parse.pdf;

/** Coordinate tolerance (points) for assigning text tokens to cells and deduplication. */
final class DetectedTable
{
    /** Coordinate tolerance (points) for clustering parallel ruling lines and assigning text to cells. */
    static final float CLUSTER_TOLERANCE = 3.0f;

    /** Maximum characters in a single table cell; more suggests prose was captured as a table row. */
    private static final int MAX_CELL_CHARS = 300;

    /** Maximum fraction of total table text allowed in a single cell before the row is prose-like. */
    private static final float MAX_CELL_TEXT_RATIO = 0.7f;

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

    String[] getRow(final int row)
    {
        return this.cells[row];
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
        final int row = findInterval(this.rowY, token.getY());
        final int col = findInterval(this.colX, token.getX());
        if (row >= 0 && col >= 0) {
            final String current = this.cells[row][col];
            if (current.isEmpty()) {
                this.cells[row][col] = token.getText();
            } else {
                this.cells[row][col] = current + " " + token.getText();
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
