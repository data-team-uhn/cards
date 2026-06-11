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

final class RulingLine
{
    /** Maximum deviation from a perfectly straight line for a segment to count as a ruling line. */
    static final float RULING_TOLERANCE = 1.0f;

    /** Minimum length in points for a segment to count as a ruling line. */
    static final float RULING_MIN_LENGTH = 10.0f;

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

    float getX1()
    {
        return this.x1;
    }

    float getY1()
    {
        return this.y1;
    }

    float getX2()
    {
        return this.x2;
    }

    float getY2()
    {
        return this.y2;
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
