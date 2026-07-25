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
package io.uhndata.cards.entityindex;

/**
 * The naming convention used for the fields of an indexed entity document. The JCR hierarchy of the entity is
 * flattened: each item (answer) contributes a group of fields named after the item's <em>key</em>, which is both the
 * UUID of the question being answered and, as a human-friendly alias, the path of the question relative to
 * {@code /Questionnaires}, e.g. {@code Patient information/date_of_birth}. Entity-level metadata is indexed under
 * {@code @}-prefixed field names, which cannot collide with either key form.
 * <p>
 * For a key {@code K}, the following fields may be present, depending on the type of the stored values:
 * </p>
 * <ul>
 * <li>{@code K} — the exact value(s), as untokenized strings, for equality checks</li>
 * <li>{@code K.text} — the value(s) as analyzed full text, for substring and fuzzy matches</li>
 * <li>{@code K.long} — whole-number values, also used for booleans (0/1) and dates (epoch milliseconds)</li>
 * <li>{@code K.double} — fractional values; whole numbers are also indexed here so mixed comparisons work</li>
 * <li>{@code K.note} — the analyzed text of the note accompanying the value</li>
 * <li>{@code K.sort} — a string sort key</li>
 * <li>{@code K.nsort} — a numeric sort key, present for numeric, boolean and date values</li>
 * </ul>
 *
 * @version $Id$
 * @since 0.9.41
 */
public final class IndexFields
{
    /** The JCR path of the entity, also the primary key of the document. Stored. */
    public static final String PATH = "@path";

    /** The jcr:uuid of the entity. Stored. */
    public static final String UUID = "@uuid";

    /** The primary node type of the entity, e.g. {@code cards:Form}. */
    public static final String TYPE = "@type";

    /** The UUID of the questionnaire answered by the entity. */
    public static final String QUESTIONNAIRE = "@questionnaire";

    /** The JCR path of the questionnaire answered by the entity, a human-friendly alias for the UUID. */
    public static final String QUESTIONNAIRE_PATH = "@questionnairePath";

    /** The UUID of the subject the entity belongs to. */
    public static final String SUBJECT = "@subject";

    /** The local identifier of the subject the entity belongs to, e.g. an MRN. Stored. */
    public static final String SUBJECT_IDENTIFIER = "@subjectIdentifier";

    /** The full hierarchical identifier of the subject the entity belongs to. */
    public static final String SUBJECT_FULL_IDENTIFIER = "@subjectFullIdentifier";

    /** The UUIDs of all the subjects the entity relates to: its subject and all the subject's ancestors. */
    public static final String RELATED_SUBJECTS = "@relatedSubjects";

    /** The status flags of the entity, e.g. {@code INCOMPLETE}. */
    public static final String STATUS_FLAGS = "@statusFlags";

    /** The entity creation date, an epoch milliseconds point named {@code @created}, also stored as ISO text. */
    public static final String CREATED = "@created";

    /** The user who created the entity. */
    public static final String CREATED_BY = "@createdBy";

    /** The entity last modification date, an epoch milliseconds point. */
    public static final String LAST_MODIFIED = "@lastModified";

    /** The user who last modified the entity. */
    public static final String LAST_MODIFIED_BY = "@lastModifiedBy";

    /** A catch-all analyzed text field aggregating all textual values, notes and identifiers of the entity. */
    public static final String FULLTEXT = "@fulltext";

    /** The UUIDs of all the questions that have an answer node in the entity, whether valued or not. */
    public static final String QUESTIONS = "@questions";

    /** The UUIDs of all the questions that have at least one actual value in the entity. */
    public static final String ANSWERED_QUESTIONS = "@answeredQuestions";

    /** Suffix for the analyzed full text version of a field. */
    public static final String TEXT_SUFFIX = ".text";

    /** Suffix for the lowercased, whole-value version of a field, used for case-insensitive {@code ILIKE} matching. */
    public static final String LOWER_SUFFIX = ".lower";

    /** Suffix for the whole-number version of a field. */
    public static final String LONG_SUFFIX = ".long";

    /** Suffix for the fractional-number version of a field. */
    public static final String DOUBLE_SUFFIX = ".double";

    /** Suffix for the analyzed note text of a field. */
    public static final String NOTE_SUFFIX = ".note";

    /** Suffix for the string sort key of a field. */
    public static final String SORT_SUFFIX = ".sort";

    /** Suffix for the numeric sort key of a field. */
    public static final String NSORT_SUFFIX = ".nsort";

    private IndexFields()
    {
        // Constants class, not to be instantiated
    }
}
