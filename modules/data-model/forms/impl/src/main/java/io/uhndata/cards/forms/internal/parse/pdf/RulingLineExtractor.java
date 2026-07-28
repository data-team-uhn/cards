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

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

/**
 * Extracts straight horizontal and vertical line segments from a single page's content stream.
 * PDFBox applies the CTM before invoking moveTo/lineTo/appendRectangle, so coordinates arrive
 * in page space (y-up, bottom-left origin). They are converted to display space
 * (y-down, top-left origin) via: displayY = pageHeight - pdfY.
 */
final class RulingLineExtractor extends PDFGraphicsStreamEngine
{
    private final List<RulingLine> rulingLines = new ArrayList<>();

    private final List<float[]> currentSegments = new ArrayList<>();

    private int nonRectFillCount;

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
        this.nonRectFillCount++;
        this.currentSegments.clear();
    }

    @Override
    public void fillAndStrokePath(final int windingRule)
        throws IOException
    {
        this.nonRectFillCount++;
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

    int getNonRectFillCount()
    {
        return this.nonRectFillCount;
    }

    List<RulingLine> getLines()
    {
        return new ArrayList<>(this.rulingLines);
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
            if (line.length() >= RulingLine.RULING_MIN_LENGTH
                && (line.isHorizontal() || line.isVertical())) {
                this.rulingLines.add(line);
            }
        }
    }
}
