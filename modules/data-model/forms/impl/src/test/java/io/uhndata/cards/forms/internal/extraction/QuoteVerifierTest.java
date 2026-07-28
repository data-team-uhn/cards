/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.forms.internal.extraction;

import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link QuoteVerifier}, the code-side half of the extraction injection defense.
 *
 * <p>The page-marker spellings are covered explicitly because the marker must be stripped before comparison: a
 * marker left in the chunk text sits in the middle of a sentence and breaks an otherwise valid quote match. Only
 * the exact emitted form {@code <!-- page: N -->} is stripped; a document written before the Python and Java
 * generators were unified carries the no-space {@code <!-- page: N-->} and must be re-parsed, which the tests
 * below pin so the boundary is explicit rather than discovered in production.</p>
 *
 * @version $Id$
 */
public class QuoteVerifierTest
{
    private static final String QUOTE = "participants will be randomized in a one to one ratio";

    @Test
    public void verifyAcceptsAnExactQuote()
    {
        Assert.assertTrue(QuoteVerifier.verify(QUOTE, "Methods. " + QUOTE + " Outcomes follow."));
    }

    @Test
    public void verifyIsCaseInsensitive()
    {
        Assert.assertTrue(QuoteVerifier.verify(QUOTE.toUpperCase(Locale.ROOT), "Text. " + QUOTE));
    }

    @Test
    public void verifyCollapsesWhitespaceDifferences()
    {
        Assert.assertTrue(
            QuoteVerifier.verify("participants   will\tbe\n randomized", "participants will be randomized"));
    }

    @Test
    public void verifyRejectsAQuoteThatIsNotPresent()
    {
        Assert.assertFalse(QuoteVerifier.verify(QUOTE, "An entirely unrelated chunk of protocol text."));
    }

    @Test
    public void verifyRejectsNullInputs()
    {
        Assert.assertFalse(QuoteVerifier.verify(null, "some text"));
        Assert.assertFalse(QuoteVerifier.verify(QUOTE, null));
    }

    @Test
    public void verifyRejectsATooShortQuote()
    {
        // Shorter than MIN_QUOTE_LENGTH once normalized: matches too easily to be evidence.
        Assert.assertFalse(QuoteVerifier.verify("the", "the study"));
    }

    @Test
    public void verifyStripsTheCanonicalPageMarkerFromTheChunk()
    {
        final String chunk = "participants will be randomized\n<!-- page: 12 -->\nin a one to one ratio";
        Assert.assertTrue(QuoteVerifier.verify(QUOTE, chunk));
    }

    @Test
    public void verifyDoesNotStripTheLegacyNoSpacePageMarker()
    {
        final String chunk = "participants will be randomized\n<!-- page: 12-->\nin a one to one ratio";
        Assert.assertFalse(QuoteVerifier.verify(QUOTE, chunk));
    }

    @Test
    public void verifyDoesNotStripOddlySpacedPageMarkers()
    {
        final String chunk = "participants will be randomized\n<!--  page:  12  -->\nin a one to one ratio";
        Assert.assertFalse(QuoteVerifier.verify(QUOTE, chunk));
    }

    @Test
    public void verifyStillMatchesAQuoteInsideOnePageOfALegacyDocument()
    {
        // The marker only breaks quotes that span it; text on one side still verifies normally.
        final String chunk = "lead in\n<!-- page: 12-->\n" + QUOTE + " and then more prose";
        Assert.assertTrue(QuoteVerifier.verify(QUOTE, chunk));
    }

    @Test
    public void verifyStripsChunkMarkers()
    {
        final String chunk = "participants will be randomized [chunk:chunk007] in a one to one ratio";
        Assert.assertTrue(QuoteVerifier.verify(QUOTE, chunk));
    }

    @Test
    public void verifyStripsMarkersFromTheQuoteSideToo()
    {
        final String quoteWithMarker = "participants will be randomized <!-- page: 12 --> in a one to one ratio";
        Assert.assertTrue(QuoteVerifier.verify(quoteWithMarker, "Lead in. " + QUOTE + " Trailing."));
    }

    @Test
    public void verifyDoesNotStripAnUnrelatedHtmlComment()
    {
        // Only the reserved markers are removed; other comments stay and so must still match literally.
        Assert.assertFalse(QuoteVerifier.verify(QUOTE, "participants will be randomized <!-- note --> ratio"));
    }
}
