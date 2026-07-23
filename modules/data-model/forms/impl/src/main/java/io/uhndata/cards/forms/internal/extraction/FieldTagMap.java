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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * The code-side join (Stage 1.15) that turns each field's rubric affinities — read at runtime from the
 * questionnaire section being extracted (every question's {@code tags} property, carried on
 * {@link ProposalExtractionService.FieldSpec#tags()}), never hardcoded here — together with a chunk's tags into
 * that chunk's {@code extraction_hints}: the field keys the chunk is a candidate source for. No LLM call, just
 * a join recomputed whenever a chunk's tags change.
 * <p>
 * The load-bearing rule is <em>fail-open</em>: a chunk that is untagged, flagged {@code uncertain}, or whose
 * tag was only {@code heading}-derived is a wildcard — it hints every field and stays eligible for Stage 1.2
 * sweeps. A field configured with no rubric tags likewise matches every chunk (conservative: never starve a
 * field of candidate chunks). Only a confident, content-based ({@code fulltext} or {@code deep}) tag can
 * exclude a chunk from a field. Without this the pipeline would silently fail closed, letting one wrong
 * heading-based tag hide a chunk a field's answer actually lives in.
 * </p>
 *
 * @version $Id$
 */
public final class FieldTagMap
{
    /** Tag bases trusted enough to exclude a chunk from a field it does not match. */
    private static final String BASIS_FULLTEXT = "fulltext";

    private static final String BASIS_DEEP = "deep";

    private FieldTagMap()
    {
        // Utility class, never instantiated.
    }

    /**
     * Compute a chunk's {@code extraction_hints}: the field keys it is a candidate source for. A wildcard
     * chunk hints every field; otherwise a field is hinted when it has no configured rubric tags or when its
     * tags intersect the chunk's tags.
     *
     * @param chunkTags the chunk's rubric tags
     * @param tagBasis how the chunk's tag was derived ({@code heading}, {@code fulltext} or {@code deep})
     * @param uncertain whether the chunk's tag is a weak or truncation-filled guess
     * @param fieldTags the {@code field key -> rubric tags} mapping for the fields being extracted, in field
     *            order (a field mapped to an empty set matches every chunk)
     * @return the hinted field keys, in the given field order
     */
    public static List<String> hintsForChunk(final List<String> chunkTags, final String tagBasis,
        final boolean uncertain, final Map<String, Set<String>> fieldTags)
    {
        final List<String> hints = new ArrayList<>();
        final boolean wildcard = isWildcard(chunkTags, tagBasis, uncertain);
        for (final Map.Entry<String, Set<String>> field : fieldTags.entrySet()) {
            if (wildcard || matches(field.getValue(), chunkTags)) {
                hints.add(field.getKey());
            }
        }
        return hints;
    }

    /**
     * Whether a chunk is a fail-open wildcard: untagged, uncertain, or tagged only from its heading. Such a
     * chunk can never be excluded from a field, only its content-based tags can rule it out.
     *
     * @param chunkTags the chunk's rubric tags
     * @param tagBasis how the chunk's tag was derived
     * @param uncertain whether the tag is a weak or truncation-filled guess
     * @return {@code true} when the chunk hints every field
     */
    public static boolean isWildcard(final List<String> chunkTags, final String tagBasis, final boolean uncertain)
    {
        final boolean contentBased = BASIS_FULLTEXT.equals(tagBasis) || BASIS_DEEP.equals(tagBasis);
        return uncertain || !contentBased || chunkTags == null || chunkTags.isEmpty();
    }

    private static boolean matches(final Set<String> fieldTags, final List<String> chunkTags)
    {
        if (fieldTags == null || fieldTags.isEmpty()) {
            return true;
        }
        if (chunkTags == null) {
            return false;
        }
        for (final String tag : chunkTags) {
            if (StringUtils.isNotBlank(tag) && fieldTags.contains(tag.strip())) {
                return true;
            }
        }
        return false;
    }
}
