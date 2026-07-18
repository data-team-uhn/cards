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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * The static {@code field key → rubric tags} map for the intake fields and the code-side join that turns it,
 * together with a section's tags, into each section's {@code extraction_hints} — the field keys that section is
 * a candidate source for. This is Stage 1.15 of the pipeline: no LLM call, just a join recomputed whenever a
 * section's tags change.
 * <p>
 * The load-bearing rule is <em>fail-open</em>: a section that is untagged, flagged {@code uncertain}, or whose
 * tag was only {@code heading}-derived is a wildcard — it hints every field and stays eligible for Stage 1.2
 * sweeps. Only a confident, content-based ({@code fulltext} or {@code deep}) tag can exclude a section from a
 * field. Without this the pipeline would silently fail closed, letting one wrong heading-based tag hide a
 * section a field's answer actually lives in.
 * </p>
 *
 * @version $Id$
 */
public final class FieldTagMap
{
    /** Tag bases trusted enough to exclude a section from a field it does not match. */
    private static final String BASIS_FULLTEXT = "fulltext";

    private static final String BASIS_DEEP = "deep";

    /**
     * The intake fields' rubric affinities. A field absent from this map is treated as matching every section
     * (conservative: never starve an unknown field of candidate sections).
     */
    private static final Map<String, Set<String>> FIELD_TAGS = Map.ofEntries(
        Map.entry("study_title", Set.of("B.1")),
        Map.entry("study_category", Set.of("B.1", "B.4")),
        Map.entry("study_category_flags", Set.of("B.4", "B.7", "B.9", "B.14")),
        Map.entry("regulatory_sponsor", Set.of("B.1")),
        Map.entry("lead_institution", Set.of("B.1")),
        Map.entry("lead_investigator", Set.of("B.1")),
        Map.entry("lead_investigator_email", Set.of("B.1")),
        Map.entry("participating_institutions", Set.of("B.1", "B.5")),
        Map.entry("participating_investigators", Set.of("B.1", "B.5")),
        Map.entry("study_drug_device", Set.of("B.7")));

    private FieldTagMap()
    {
        // Utility class, never instantiated.
    }

    /**
     * The rubric tags a field is associated with, or {@code null} when the field is not mapped (matches every
     * section).
     *
     * @param field the field key
     * @return the field's rubric tags, or {@code null} when unmapped
     */
    public static Set<String> tagsFor(final String field)
    {
        return FIELD_TAGS.get(field);
    }

    /**
     * Compute a section's {@code extraction_hints}: the field keys it is a candidate source for. A wildcard
     * section hints every field; otherwise a field is hinted when it is unmapped or when its rubric tags
     * intersect the section's tags.
     *
     * @param sectionTags the section's rubric tags
     * @param tagBasis how the section's tag was derived ({@code heading}, {@code fulltext} or {@code deep})
     * @param uncertain whether the section's tag is a weak or truncation-filled guess
     * @param fieldKeys the field keys being extracted
     * @return the hinted field keys, in the given field order
     */
    public static List<String> hintsForSection(final List<String> sectionTags, final String tagBasis,
        final boolean uncertain, final Collection<String> fieldKeys)
    {
        final List<String> hints = new ArrayList<>();
        final boolean wildcard = isWildcard(sectionTags, tagBasis, uncertain);
        for (final String field : fieldKeys) {
            if (wildcard || matches(field, sectionTags)) {
                hints.add(field);
            }
        }
        return hints;
    }

    /**
     * Whether a section is a fail-open wildcard: untagged, uncertain, or tagged only from its heading. Such a
     * section can never be excluded from a field, only its content-based tags can rule it out.
     *
     * @param sectionTags the section's rubric tags
     * @param tagBasis how the section's tag was derived
     * @param uncertain whether the tag is a weak or truncation-filled guess
     * @return {@code true} when the section hints every field
     */
    public static boolean isWildcard(final List<String> sectionTags, final String tagBasis, final boolean uncertain)
    {
        final boolean contentBased = BASIS_FULLTEXT.equals(tagBasis) || BASIS_DEEP.equals(tagBasis);
        return uncertain || !contentBased || sectionTags == null || sectionTags.isEmpty();
    }

    private static boolean matches(final String field, final List<String> sectionTags)
    {
        final Set<String> fieldTags = FIELD_TAGS.get(field);
        if (fieldTags == null) {
            return true;
        }
        if (sectionTags == null) {
            return false;
        }
        for (final String tag : sectionTags) {
            if (StringUtils.isNotBlank(tag) && fieldTags.contains(tag.strip())) {
                return true;
            }
        }
        return false;
    }
}
