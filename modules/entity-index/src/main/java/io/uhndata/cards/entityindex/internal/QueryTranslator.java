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
package io.uhndata.cards.entityindex.internal;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.entityindex.IndexFields;
import io.uhndata.cards.entityindex.SearchCondition;
import io.uhndata.cards.entityindex.SearchQuery;
import io.uhndata.cards.utils.DateUtils;

/**
 * Translates a {@link SearchQuery} into a Lucene query over the flattened entity documents. All the criteria are
 * combined with AND. Note the semantics on item fields: an inequality ({@code <>}) also matches entities where the
 * item has no value at all, since "not equal to X" includes "does not have X"; range operators, on the other hand,
 * only match entities that do have a value, as an absent value is neither greater nor lower than the bound.
 *
 * @version $Id$
 * @since 0.9.41
 */
@SuppressWarnings({ "checkstyle:ClassDataAbstractionCoupling", "checkstyle:ClassFanOutComplexity" })
class QueryTranslator
{
    /**
     * Evaluates a cross-entity join against the index holding the joined entity's documents; implemented by the
     * index manager, which has access to the searchers of the other indexes.
     */
    interface JoinEvaluator
    {
        /**
         * Build a query matching entities related to an entity of the source index matching the given conditions.
         *
         * @param join the join to evaluate
         * @return a Lucene query
         * @throws IOException if evaluating the join against the source index fails
         */
        Query evaluate(SearchQuery.Join join) throws IOException;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryTranslator.class);

    private final Analyzer analyzer;

    QueryTranslator(final Analyzer analyzer)
    {
        this.analyzer = analyzer;
    }

    /**
     * Translate a search query into a Lucene query.
     *
     * @param query the search criteria
     * @param searcher the current index searcher, used to configure the native query parser
     * @param joins evaluator for the cross-entity joins in the query
     * @return a Lucene query
     * @throws IllegalArgumentException if the native query cannot be parsed
     * @throws IOException if evaluating a join fails
     */
    Query translate(final SearchQuery query, final IndexSearcher searcher, final JoinEvaluator joins)
        throws IOException
    {
        final BooleanQuery.Builder result = new BooleanQuery.Builder();
        boolean positive = false;
        for (final SearchCondition condition : query.getConditions()) {
            positive |= addConjunct(result, condition);
        }
        for (final List<SearchCondition> group : query.getDisjunctions()) {
            result.add(translateDisjunction(group), Occur.MUST);
            positive = true;
        }
        for (final SearchQuery.Join join : query.getSubjectJoins()) {
            result.add(joins.evaluate(join), Occur.MUST);
            positive = true;
        }
        if (StringUtils.isNotBlank(query.getFulltext())) {
            result.add(parseFulltext(query.getFulltext()), Occur.MUST);
            positive = true;
        }
        if (StringUtils.isNotBlank(query.getNativeQuery())) {
            result.add(parseNative(query.getNativeQuery(), searcher.getIndexReader()), Occur.MUST);
            positive = true;
        }
        // A purely negative query (only inequalities) matches nothing without a positive clause to subtract from, and
        // an empty query should match everything: in both cases a single match-all base is enough for the whole query.
        if (!positive) {
            result.add(new MatchAllDocsQuery(), Occur.MUST);
        }
        return result.build();
    }

    /**
     * Translate a group of conditions combined with AND, e.g. the conditions that a joined entity must match.
     *
     * @param conditions the conditions in the group
     * @return a Lucene query
     */
    Query translateGroup(final List<SearchCondition> conditions)
    {
        final BooleanQuery.Builder result = new BooleanQuery.Builder();
        boolean positive = false;
        for (final SearchCondition condition : conditions) {
            positive |= addConjunct(result, condition);
        }
        if (!positive) {
            result.add(new MatchAllDocsQuery(), Occur.MUST);
        }
        return result.build();
    }

    /**
     * Add one condition of an AND group to a query builder. An inequality is attached directly as a negative
     * ({@code MUST_NOT}) clause, so a single match-all base suffices for the whole group instead of wrapping every
     * inequality in its own; all other conditions are positive ({@code MUST}) clauses.
     *
     * @param builder the query builder for the AND group
     * @param condition the condition to add
     * @return whether a positive clause was added, i.e. one that can match documents on its own
     */
    private boolean addConjunct(final BooleanQuery.Builder builder, final SearchCondition condition)
    {
        final Query negated = negatedClause(condition);
        if (negated != null) {
            builder.add(negated, Occur.MUST_NOT);
            return false;
        }
        builder.add(translateCondition(condition), Occur.MUST);
        return true;
    }

    /**
     * For a negative operator, the positive query to subtract from the group; {@code null} for positive operators.
     * An inequality subtracts the matching-value query, a {@code NOT ILIKE} subtracts the {@code ILIKE} match.
     *
     * @param condition the condition to translate
     * @return the query to attach as a negative clause, or {@code null} if the operator is positive
     */
    private Query negatedClause(final SearchCondition condition)
    {
        return switch (condition.getOperator()) {
            case NEQ -> equalityQuery(condition);
            case NOT_ILIKE -> likeQuery(condition);
            default -> null;
        };
    }

    private Query translateDisjunction(final List<SearchCondition> group)
    {
        if (group.isEmpty()) {
            return new MatchAllDocsQuery();
        }
        final BooleanQuery.Builder result = new BooleanQuery.Builder();
        for (final SearchCondition condition : group) {
            result.add(translateCondition(condition), Occur.SHOULD);
        }
        return result.build();
    }

    private Query translateCondition(final SearchCondition condition)
    {
        return switch (condition.getOperator()) {
            case IS_EMPTY -> new BooleanQuery.Builder()
                .add(new TermQuery(new Term(IndexFields.QUESTIONS, condition.getField())), Occur.MUST)
                .add(hasValueQuery(condition.getField()), Occur.MUST_NOT)
                .build();
            case IS_NOT_EMPTY -> hasValueQuery(condition.getField());
            case CONTAINS -> tokenWildcards(condition.getField() + IndexFields.TEXT_SUFFIX, condition.getValue());
            case ILIKE -> likeQuery(condition);
            case NOTES_CONTAIN -> tokenWildcards(condition.getField() + IndexFields.NOTE_SUFFIX,
                condition.getValue());
            case NEQ -> negateQuery(equalityQuery(condition));
            case NOT_ILIKE -> negateQuery(likeQuery(condition));
            default -> compareValue(condition);
        };
    }

    /**
     * A case-insensitive {@code LIKE} match, implemented as a wildcard query over the lowercased whole-value field:
     * the value is lowercased and its SQL wildcards ({@code %}, {@code _}) are translated to the Lucene ones
     * ({@code *}, {@code ?}), while literal wildcard characters are escaped.
     *
     * @param condition the condition to translate
     * @return a Lucene query
     */
    private Query likeQuery(final SearchCondition condition)
    {
        final String pattern =
            sqlLikeToWildcard(StringUtils.defaultString(condition.getValue()).toLowerCase(Locale.ROOT));
        return new WildcardQuery(new Term(condition.getField() + IndexFields.LOWER_SUFFIX, pattern));
    }

    private String sqlLikeToWildcard(final String pattern)
    {
        final StringBuilder result = new StringBuilder(pattern.length());
        for (int i = 0; i < pattern.length(); i++) {
            final char c = pattern.charAt(i);
            switch (c) {
                case '%' -> result.append('*');
                case '_' -> result.append('?');
                case '*', '?', '\\' -> result.append('\\').append(c);
                default -> result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * A query matching entities where the given field has at least one value. For item fields this is an exact lookup
     * in the list of answered questions; metadata fields always have a value, checked with an existence wildcard.
     *
     * @param field the field to check
     * @return a Lucene query
     */
    private Query hasValueQuery(final String field)
    {
        if (field.startsWith("@")) {
            return new WildcardQuery(new Term(field, "*"));
        }
        return new TermQuery(new Term(IndexFields.ANSWERED_QUESTIONS, field));
    }

    /**
     * The query matching entities whose field equals the condition's value, used both as the positive part of an
     * equality and as the negated part of an inequality.
     *
     * @param condition the condition supplying the field, value and type
     * @return a Lucene query
     */
    private Query equalityQuery(final SearchCondition condition)
    {
        return compareValue(new SearchCondition(condition.getField(), SearchCondition.Operator.EQ,
            condition.getValue(), condition.getType()));
    }

    /**
     * Wrap a positive query as a standalone negation: any entity that does not match it, including entities where the
     * field has no value at all. This mirrors {@code not condition}: "not equal to X" / "not like X" includes "does
     * not have X". A match-all base is needed because a purely negative query matches nothing; inside an AND group
     * {@link #addConjunct} avoids it by attaching the negation to the group's own base instead.
     *
     * @param positive the query to negate
     * @return a Lucene query matching everything the given query does not
     */
    private Query negateQuery(final Query positive)
    {
        return new BooleanQuery.Builder()
            .add(new MatchAllDocsQuery(), Occur.MUST)
            .add(positive, Occur.MUST_NOT)
            .build();
    }

    private Query compareValue(final SearchCondition condition)
    {
        try {
            return switch (condition.getType()) {
                case LONG -> longQuery(condition);
                case DOUBLE -> doubleQuery(condition);
                case DATE -> dateQuery(condition);
                default -> textQuery(condition);
            };
        } catch (final IllegalArgumentException e) {
            LOGGER.debug("Unparsable filter value [{}] for {}: {}", condition.getValue(), condition.getField(),
                e.getMessage());
            return new MatchNoDocsQuery();
        }
    }

    private Query longQuery(final SearchCondition condition)
    {
        final String field = condition.getField() + IndexFields.LONG_SUFFIX;
        final long value = parseLong(condition.getValue());
        return switch (condition.getOperator()) {
            case LT -> LongPoint.newRangeQuery(field, Long.MIN_VALUE, Math.addExact(value, -1));
            case LTE -> LongPoint.newRangeQuery(field, Long.MIN_VALUE, value);
            case GT -> LongPoint.newRangeQuery(field, Math.addExact(value, 1), Long.MAX_VALUE);
            case GTE -> LongPoint.newRangeQuery(field, value, Long.MAX_VALUE);
            default -> LongPoint.newExactQuery(field, value);
        };
    }

    private Query doubleQuery(final SearchCondition condition)
    {
        final String field = condition.getField() + IndexFields.DOUBLE_SUFFIX;
        final double value = Double.parseDouble(condition.getValue());
        return switch (condition.getOperator()) {
            case LT -> DoublePoint.newRangeQuery(field, Double.NEGATIVE_INFINITY, Math.nextDown(value));
            case LTE -> DoublePoint.newRangeQuery(field, Double.NEGATIVE_INFINITY, value);
            case GT -> DoublePoint.newRangeQuery(field, Math.nextUp(value), Double.POSITIVE_INFINITY);
            case GTE -> DoublePoint.newRangeQuery(field, value, Double.POSITIVE_INFINITY);
            default -> DoublePoint.newExactQuery(field, value);
        };
    }

    /**
     * Dates are compared with whole-day precision: equality means any time within the day, mirroring the behavior of
     * the JCR date filters.
     *
     * @param condition the condition to translate
     * @return a Lucene query
     */
    private Query dateQuery(final SearchCondition condition)
    {
        final String field = condition.getField() + IndexFields.LONG_SUFFIX;
        final ZonedDateTime parsed = DateUtils.parseDateTime(condition.getValue());
        if (parsed == null) {
            LOGGER.debug("Unparsable date filter value [{}] for {}", condition.getValue(), condition.getField());
            return new MatchNoDocsQuery();
        }
        final long dayStart = DateUtils.atMidnight(parsed).toInstant().toEpochMilli();
        final long nextDayStart = DateUtils.atMidnight(parsed).plusDays(1).toInstant().toEpochMilli();
        return switch (condition.getOperator()) {
            case LT -> LongPoint.newRangeQuery(field, Long.MIN_VALUE, dayStart - 1);
            case LTE -> LongPoint.newRangeQuery(field, Long.MIN_VALUE, nextDayStart - 1);
            case GT -> LongPoint.newRangeQuery(field, nextDayStart, Long.MAX_VALUE);
            case GTE -> LongPoint.newRangeQuery(field, dayStart, Long.MAX_VALUE);
            default -> LongPoint.newRangeQuery(field, dayStart, nextDayStart - 1);
        };
    }

    private Query textQuery(final SearchCondition condition)
    {
        final String field = condition.getField();
        final String value = StringUtils.defaultString(condition.getValue());
        return switch (condition.getOperator()) {
            case LT -> new TermRangeQuery(field, null, new BytesRef(value), false, false);
            case LTE -> new TermRangeQuery(field, null, new BytesRef(value), false, true);
            case GT -> new TermRangeQuery(field, new BytesRef(value), null, false, false);
            case GTE -> new TermRangeQuery(field, new BytesRef(value), null, true, false);
            default -> new TermQuery(new Term(field, value));
        };
    }

    private long parseLong(final String value)
    {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            // Booleans may be sent as true/false instead of 1/0
            if ("true".equalsIgnoreCase(value)) {
                return 1;
            }
            if ("false".equalsIgnoreCase(value)) {
                return 0;
            }
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Build a query matching values containing all the words of the filter, even as substrings of longer words.
     *
     * @param field the analyzed text field to search in
     * @param value the words to look for
     * @return a Lucene query
     */
    private Query tokenWildcards(final String field, final String value)
    {
        final BooleanQuery.Builder result = new BooleanQuery.Builder();
        boolean empty = true;
        try (TokenStream tokens = this.analyzer.tokenStream(field, StringUtils.defaultString(value))) {
            final CharTermAttribute term = tokens.addAttribute(CharTermAttribute.class);
            tokens.reset();
            while (tokens.incrementToken()) {
                result.add(new WildcardQuery(new Term(field, "*" + term.toString() + "*")), Occur.MUST);
                empty = false;
            }
            tokens.end();
        } catch (final IOException e) {
            // Cannot happen when analyzing an in-memory string
            LOGGER.warn("Unexpected analysis failure: {}", e.getMessage(), e);
        }
        return empty ? new MatchNoDocsQuery() : result.build();
    }

    private Query parseFulltext(final String fulltext)
    {
        final QueryParser parser = new QueryParser(IndexFields.FULLTEXT, this.analyzer);
        try {
            return parser.parse(fulltext);
        } catch (final org.apache.lucene.queryparser.classic.ParseException e) {
            // Not a valid query, just look for the text as-is
            try {
                return parser.parse(QueryParser.escape(fulltext));
            } catch (final org.apache.lucene.queryparser.classic.ParseException inner) {
                LOGGER.debug("Unparsable fulltext filter [{}]: {}", fulltext, inner.getMessage());
                return new MatchNoDocsQuery();
            }
        }
    }

    /**
     * Parse a native Lucene query. Numeric subfields present in the index are configured as points, so range queries
     * like {@code [3 TO 5]} work on them.
     *
     * @param nativeQuery the query to parse, in the standard Lucene syntax
     * @param reader the current index reader
     * @return a Lucene query
     * @throws IllegalArgumentException if the query cannot be parsed
     */
    private Query parseNative(final String nativeQuery, final IndexReader reader)
    {
        final StandardQueryParser parser = new StandardQueryParser(this.analyzer);
        parser.setPointsConfigMap(gatherPointedFields(reader));
        try {
            return parser.parse(nativeQuery, IndexFields.FULLTEXT);
        } catch (final org.apache.lucene.queryparser.flexible.core.QueryNodeException e) {
            throw new IllegalArgumentException("Invalid query: " + e.getMessage(), e);
        }
    }

    private Map<String, PointsConfig> gatherPointedFields(final IndexReader reader)
    {
        final Map<String, PointsConfig> result = new HashMap<>();
        final PointsConfig longs = new PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Long.class);
        final PointsConfig doubles = new PointsConfig(NumberFormat.getNumberInstance(Locale.ROOT), Double.class);
        for (final LeafReaderContext leaf : reader.leaves()) {
            leaf.reader().getFieldInfos().forEach(info -> {
                if (info.name.endsWith(IndexFields.LONG_SUFFIX)) {
                    result.put(info.name, longs);
                } else if (info.name.endsWith(IndexFields.DOUBLE_SUFFIX)) {
                    result.put(info.name, doubles);
                }
            });
        }
        return result;
    }
}
