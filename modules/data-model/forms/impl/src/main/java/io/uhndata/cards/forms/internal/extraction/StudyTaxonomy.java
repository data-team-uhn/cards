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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/**
 * The controlled vocabularies of the Research Study Description taxonomy and the ICH-GCP protocol-structure
 * rubrics, used to enum-validate intake output code-side — invalid category, sub-flag, institution or rubric
 * values are dropped rather than triggering a re-call, per the pipeline's injection-defense rule that only
 * whitelisted values survive. The values mirror {@code PIQuestionnaire.xml}'s {@code categoriesDocument} and the
 * {@code protocol_structure_glossary}.
 *
 * @version $Id$
 */
public final class StudyTaxonomy
{
    /** The six canonical top-category labels; exactly one is a valid {@code study_category}. */
    public static final Set<String> CATEGORIES = ordered(
        "Investigator-Initiated Prospective (Full Study)",
        "Investigator-Initiated Prospective (Integrated)",
        "Investigator-Initiated Prospective (Single Site, External Recruitment)",
        "Retrospective Data/Material or Non-Clinical Study",
        "Collaborative Clinical Research Support Services",
        "Other");

    /** The union of all sub-flag labels valid for some top category. */
    public static final Set<String> CATEGORY_FLAGS = ordered(
        "Health Canada Regulated",
        "Non-regulated",
        "Data/Material Transfer",
        "Retrospective Data/Material Transfer",
        "Visiting Personnel",
        "Collaborative Retrospective Clinical Data",
        "Collaborative Retrospective Human Materials",
        "Other");

    /** The four canonical institution labels valid for {@code lead_institution}/{@code participating_institutions}. */
    public static final Set<String> INSTITUTIONS = ordered(
        "Centre for Addiction and Mental Health (CAMH)",
        "Sunnybrook Health Sciences Centre (SRI)",
        "Unity Health Toronto (UHT)",
        "University Health Network (UHN)");

    /** The valid protocol-structure rubric tags B.1 through B.17. */
    public static final Set<String> RUBRIC_TAGS = rubricTags();

    private StudyTaxonomy()
    {
        // Utility class, never instantiated.
    }

    /**
     * Whether a value is one of the six canonical top-category labels.
     *
     * @param value the candidate label
     * @return {@code true} when the value is a canonical category label
     */
    public static boolean isCategory(final String value)
    {
        return value != null && CATEGORIES.contains(value.strip());
    }

    /**
     * Filter a comma-separated list to the values that appear in the given controlled vocabulary, preserving
     * order and dropping duplicates and unknown entries.
     *
     * @param commaSeparated the raw comma-separated value
     * @param vocabulary the controlled vocabulary to retain
     * @return the retained values as a comma-separated string, or {@code null} when none remain
     */
    public static String filter(final String commaSeparated, final Set<String> vocabulary)
    {
        if (StringUtils.isBlank(commaSeparated)) {
            return null;
        }
        final String retained = Arrays.stream(commaSeparated.split(","))
            .map(String::strip)
            .filter(vocabulary::contains)
            .distinct()
            .collect(Collectors.joining(", "));
        return retained.isBlank() ? null : retained;
    }

    /**
     * Retain only valid rubric tags from a list.
     *
     * @param tags the candidate tags
     * @return the tags that are valid rubric labels, in order, duplicates removed
     */
    public static List<String> validRubricTags(final List<String> tags)
    {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
            .map(tag -> tag == null ? "" : tag.strip())
            .filter(RUBRIC_TAGS::contains)
            .distinct()
            .collect(Collectors.toList());
    }

    private static Set<String> rubricTags()
    {
        final Set<String> tags = new LinkedHashSet<>();
        for (int number = 1; number <= 17; number++) {
            tags.add("B." + number);
        }
        return Collections.unmodifiableSet(tags);
    }

    private static Set<String> ordered(final String... values)
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }
}
