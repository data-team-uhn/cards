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
package io.uhndata.cards.forms.internal.extraction;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Verifies that an extracted evidence quote actually occurs in the section text it claims to come from. Both
 * sides are normalized first — the reserved parse markers ({@code <-- page: N-->}, {@code [section:sNNN]},
 * {@code <TOC start>}/{@code <TOC end>}) are removed, all whitespace runs collapse to a single space, and case
 * is folded — because those markers interrupt sentences and the model's verbatim quote may differ from the
 * source only in incidental whitespace. A quote counts as verified when the normalized quote is a substring of
 * the normalized section text. This is the code-side half of the injection defense: an injected instruction
 * cannot fabricate evidence that survives this check.
 *
 * @version $Id$
 */
public final class QuoteVerifier
{
    /** The shortest normalized quote worth verifying; anything shorter matches too easily to be evidence. */
    static final int MIN_QUOTE_LENGTH = 6;

    private static final Pattern MARKER =
        Pattern.compile("<-- page: \\d+-->|\\[section:s\\d+\\]|<TOC start>|<TOC end>");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private QuoteVerifier()
    {
        // Utility class, never instantiated.
    }

    /**
     * Whether the given quote occurs, after normalization, in the given section text.
     *
     * @param quote the extracted evidence quote
     * @param sectionText the full Markdown text of the section the quote claims to come from
     * @return {@code true} when the normalized quote is a non-trivial substring of the normalized section text
     */
    public static boolean verify(final String quote, final String sectionText)
    {
        if (quote == null || sectionText == null) {
            return false;
        }
        final String normalizedQuote = normalize(quote);
        if (normalizedQuote.length() < MIN_QUOTE_LENGTH) {
            return false;
        }
        return normalize(sectionText).contains(normalizedQuote);
    }

    private static String normalize(final String text)
    {
        final String withoutMarkers = MARKER.matcher(text).replaceAll(" ");
        return WHITESPACE.matcher(withoutMarkers).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
