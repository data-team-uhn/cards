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

final class StyledLine
{
    private final String text;

    private final float topY;

    private final float boldRatio;

    private final int wordCount;

    private final float startX;

    private final float monospaceRatio;

    private final boolean decorationCandidate;

    StyledLine(final String text, final float topY, final float boldRatio, final int wordCount,
        final float startX, final float monospaceRatio, final boolean decorationCandidate)
    {
        this.text = text;
        this.topY = topY;
        this.boldRatio = boldRatio;
        this.wordCount = wordCount;
        this.startX = startX;
        this.monospaceRatio = monospaceRatio;
        this.decorationCandidate = decorationCandidate;
    }

    String getText()
    {
        return this.text;
    }

    float getTopY()
    {
        return this.topY;
    }

    float getBoldRatio()
    {
        return this.boldRatio;
    }

    int getWordCount()
    {
        return this.wordCount;
    }

    float getStartX()
    {
        return this.startX;
    }

    float getMonospaceRatio()
    {
        return this.monospaceRatio;
    }

    boolean isDecorationCandidate()
    {
        return this.decorationCandidate;
    }
}
