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
import java.time.ZonedDateTime;
import java.util.Locale;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.join.JoinUtil;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.uhndata.cards.entityindex.IndexFields;
import io.uhndata.cards.entityindex.SearchCondition;
import io.uhndata.cards.entityindex.SearchCondition.Operator;
import io.uhndata.cards.entityindex.SearchCondition.Type;
import io.uhndata.cards.entityindex.SearchQuery;

/**
 * Unit tests for {@link QueryTranslator}, running translated queries against a small in-memory index.
 *
 * @version $Id$
 */
public class QueryTranslatorTest
{
    private static final String Q_NAME = "11111111-aaaa-bbbb-cccc-000000000001";

    private static final String Q_AGE = "11111111-aaaa-bbbb-cccc-000000000002";

    private static final String Q_DOB = "11111111-aaaa-bbbb-cccc-000000000003";

    private static final String SMITH = "Smith";

    private Directory directory;

    private Analyzer analyzer;

    private DirectoryReader reader;

    private IndexSearcher searcher;

    private QueryTranslator translator;

    @Before
    public void setup() throws IOException
    {
        this.directory = new ByteBuffersDirectory();
        this.analyzer = new FieldAwareAnalyzer();
        this.translator = new QueryTranslator(this.analyzer);
        try (IndexWriter writer = new IndexWriter(this.directory, new IndexWriterConfig(this.analyzer))) {
            // Alice Smith, 42, born 1980-06-15, with a note on the name
            writer.addDocument(entity("/Forms/f1", SMITH + " is a common name", SMITH, 42, "1980-06-15"));
            // Bob Jones, 30, born 1990-01-01
            writer.addDocument(entity("/Forms/f2", null, "Jones", 30, "1990-01-01"));
            // An entity with an unanswered name
            writer.addDocument(entity("/Forms/f3", null, null, 55, "1970-12-31"));
        }
        this.reader = DirectoryReader.open(this.directory);
        this.searcher = new IndexSearcher(this.reader);
    }

    @After
    public void teardown() throws IOException
    {
        this.reader.close();
        this.directory.close();
        this.analyzer.close();
    }

    @Test
    public void equalityOnTextMatchesExactValues() throws IOException
    {
        Assert.assertEquals(1, count(condition(Q_NAME, Operator.EQ, SMITH, Type.TEXT)));
        Assert.assertEquals(0, count(condition(Q_NAME, Operator.EQ, "smith", Type.TEXT)));
    }

    @Test
    public void inequalityMatchesDifferentAndMissingValues() throws IOException
    {
        // f2 has a different name and f3 has no name at all: both differ from "Smith", only f1 (Smith) is excluded
        Assert.assertEquals(2, count(condition(Q_NAME, Operator.NEQ, SMITH, Type.TEXT)));
    }

    @Test
    public void inequalitiesInAnAndShareASingleMatchAllBase() throws IOException
    {
        final SearchQuery query = new SearchQuery()
            .withCondition(new SearchCondition(Q_NAME, Operator.NEQ, SMITH, Type.TEXT))
            .withCondition(new SearchCondition(Q_AGE, Operator.NEQ, "30", Type.LONG));
        // The two inequalities are attached as negative clauses on one match-all base, not one base each
        final String lucene = this.translator.translate(query, this.searcher, this::selfJoin).toString();
        Assert.assertEquals(1, lucene.split("\\Q*:*\\E", -1).length - 1);
        // f1 (Smith) excluded by name, f2 (Jones, 30) excluded by age, only f3 (no name, 55) differs from both
        Assert.assertEquals(1, count(query));
    }

    @Test
    public void numericRanges() throws IOException
    {
        Assert.assertEquals(1, count(condition(Q_AGE, Operator.EQ, "42", Type.LONG)));
        Assert.assertEquals(2, count(condition(Q_AGE, Operator.GT, "35", Type.LONG)));
        Assert.assertEquals(2, count(condition(Q_AGE, Operator.LTE, "42", Type.LONG)));
        Assert.assertEquals(0, count(condition(Q_AGE, Operator.LT, "30", Type.LONG)));
        Assert.assertEquals(2, count(condition(Q_AGE, Operator.GTE, "35", Type.DOUBLE)));
    }

    @Test
    public void dateEqualityMatchesTheWholeDay() throws IOException
    {
        Assert.assertEquals(1, count(condition(Q_DOB, Operator.EQ, "1980-06-15", Type.DATE)));
        Assert.assertEquals(0, count(condition(Q_DOB, Operator.EQ, "1980-06-16", Type.DATE)));
        Assert.assertEquals(2, count(condition(Q_DOB, Operator.GT, "1975-01-01", Type.DATE)));
        Assert.assertEquals(1, count(condition(Q_DOB, Operator.LT, "1975-01-01", Type.DATE)));
    }

    @Test
    public void emptinessChecks() throws IOException
    {
        Assert.assertEquals(1, count(condition(Q_NAME, Operator.IS_EMPTY, null, Type.TEXT)));
        Assert.assertEquals(2, count(condition(Q_NAME, Operator.IS_NOT_EMPTY, null, Type.TEXT)));
    }

    @Test
    public void caseInsensitiveLikeMatchesRegardlessOfCase() throws IOException
    {
        // f1 is "Smith": ILIKE ignores case, and translates the SQL wildcards % and _
        Assert.assertEquals(1, count(condition(Q_NAME, Operator.ILIKE, "smith", Type.TEXT)));
        Assert.assertEquals(1, count(condition(Q_NAME, Operator.ILIKE, "SMITH", Type.TEXT)));
        Assert.assertEquals(1, count(condition(Q_NAME, Operator.ILIKE, "smi%", Type.TEXT)));
        Assert.assertEquals(1, count(condition(Q_NAME, Operator.ILIKE, "%mith", Type.TEXT)));
        Assert.assertEquals(1, count(condition(Q_NAME, Operator.ILIKE, "sm_th", Type.TEXT)));
        Assert.assertEquals(0, count(condition(Q_NAME, Operator.ILIKE, "%zzz%", Type.TEXT)));
    }

    @Test
    public void negatedCaseInsensitiveLikeMatchesDifferentAndMissingValues() throws IOException
    {
        // f2 (Jones) differs and f3 has no name at all: both match "not ILIKE Smith", only f1 (Smith) is excluded
        Assert.assertEquals(2, count(condition(Q_NAME, Operator.NOT_ILIKE, "smith", Type.TEXT)));
    }

    @Test
    public void containsMatchesSubstringsOfWords() throws IOException
    {
        Assert.assertEquals(1, count(condition(Q_NAME, Operator.CONTAINS, "mit", Type.TEXT)));
        Assert.assertEquals(1, count(condition(Q_NAME, Operator.NOTES_CONTAIN, "common", Type.TEXT)));
        Assert.assertEquals(0, count(condition(Q_NAME, Operator.CONTAINS, "xyz", Type.TEXT)));
    }

    @Test
    public void multipleConditionsAreIntersected() throws IOException
    {
        final SearchQuery query = new SearchQuery()
            .withCondition(new SearchCondition(Q_AGE, Operator.GT, "25", Type.LONG))
            .withCondition(new SearchCondition(Q_DOB, Operator.LT, "1985-01-01", Type.DATE))
            .withCondition(new SearchCondition(Q_NAME, Operator.IS_NOT_EMPTY, null, Type.TEXT));
        Assert.assertEquals(1, this.searcher.count(this.translator.translate(query, this.searcher, this::selfJoin)));
    }

    @Test
    public void nativeQueriesSupportRangesOnPoints() throws IOException
    {
        // Special characters in field names — dashes, @, spaces, slashes — must be escaped;
        // keyword fields must keep their exact case
        final SearchQuery query = new SearchQuery()
            .withNativeQuery(Q_AGE.replace("-", "\\-") + ".long:[35 TO 60] AND \\@statusFlags:INCOMPLETE");
        Assert.assertEquals(2, this.searcher.count(this.translator.translate(query, this.searcher, this::selfJoin)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNativeQueriesAreRejected() throws IOException
    {
        this.translator.translate(new SearchQuery().withNativeQuery("field:[unclosed TO"), this.searcher,
            this::selfJoin);
    }

    @Test
    public void disjunctionsMatchAnyOfTheGroup() throws IOException
    {
        final SearchQuery query = new SearchQuery()
            .withAnyOf(java.util.List.of(
                new SearchCondition(Q_NAME, Operator.EQ, SMITH, Type.TEXT),
                new SearchCondition(Q_NAME, Operator.EQ, "Jones", Type.TEXT)));
        Assert.assertEquals(2, this.searcher.count(this.translator.translate(query, this.searcher, this::selfJoin)));
        // Combined with a regular condition, the group is intersected with it
        query.withCondition(new SearchCondition(Q_AGE, Operator.GT, "35", Type.LONG));
        Assert.assertEquals(1, this.searcher.count(this.translator.translate(query, this.searcher, this::selfJoin)));
    }

    @Test
    public void subjectJoinsMatchEntitiesOfTheSameSubject() throws IOException
    {
        // All entities whose subject also has an entity with the name Smith: f1 itself, and f3 of the same subject
        final SearchQuery query = new SearchQuery()
            .withSubjectJoin(java.util.List.of(new SearchCondition(Q_NAME, Operator.EQ, SMITH, Type.TEXT)));
        Assert.assertEquals(2, this.searcher.count(this.translator.translate(query, this.searcher, this::selfJoin)));
        // Restricted to unnamed entities, only the sibling f3 remains
        query.withCondition(new SearchCondition(Q_NAME, Operator.IS_EMPTY, null, Type.TEXT));
        Assert.assertEquals(1, this.searcher.count(this.translator.translate(query, this.searcher, this::selfJoin)));
    }

    @Test
    public void emptyQueryMatchesEverything() throws IOException
    {
        Assert.assertEquals(3, this.searcher.count(
            this.translator.translate(new SearchQuery(), this.searcher, this::selfJoin)));
    }

    @Test
    public void fulltextSearchesTheWholeEntity() throws IOException
    {
        Assert.assertEquals(1, this.searcher.count(
            this.translator.translate(new SearchQuery().withFulltext("common"), this.searcher,
                this::selfJoin)));
        // An unparsable fulltext query falls back to escaped terms instead of failing
        Assert.assertEquals(0, this.searcher.count(
            this.translator.translate(new SearchQuery().withFulltext("~~ AND ("), this.searcher,
                this::selfJoin)));
    }

    @Test
    public void unparsableValuesMatchNothing() throws IOException
    {
        Assert.assertEquals(0, count(condition(Q_AGE, Operator.EQ, "notanumber", Type.LONG)));
        Assert.assertEquals(0, count(condition(Q_DOB, Operator.EQ, "notadate", Type.DATE)));
        // Booleans sent as true/false are still understood: true parses as 1, so all ages are >= it
        Assert.assertEquals(3, count(condition(Q_AGE, Operator.GTE, "true", Type.LONG)));
    }

    /**
     * Evaluates joins against this same index, tying entities through their shared related subjects, the same way
     * the index manager evaluates same-index joins.
     */
    private Query selfJoin(final SearchQuery.Join join) throws IOException
    {
        return JoinUtil.createJoinQuery(IndexFields.RELATED_SUBJECTS, true, IndexFields.RELATED_SUBJECTS,
            this.translator.translateGroup(join.getConditions()), this.searcher, ScoreMode.None);
    }

    private SearchQuery condition(final String field, final Operator operator, final String value, final Type type)
    {
        return new SearchQuery().withCondition(new SearchCondition(field, operator, value, type));
    }

    private int count(final SearchQuery query) throws IOException
    {
        return this.searcher.count(this.translator.translate(query, this.searcher, this::selfJoin));
    }

    private Document entity(final String path, final String note, final String name, final long age,
        final String birthDate)
    {
        final Document doc = new Document();
        doc.add(new StringField(IndexFields.PATH, path, Store.YES));
        doc.add(new StringField(IndexFields.STATUS_FLAGS, "INCOMPLETE", Store.NO));
        // f1 and f3 belong to the same subject, f2 to another one
        final String subject = "/Forms/f2".equals(path) ? "subject-2" : "subject-1";
        doc.add(new StringField(IndexFields.RELATED_SUBJECTS, subject, Store.NO));
        doc.add(new org.apache.lucene.document.SortedSetDocValuesField(IndexFields.RELATED_SUBJECTS,
            new org.apache.lucene.util.BytesRef(subject)));
        if (name != null) {
            doc.add(new StringField(IndexFields.QUESTIONS, Q_NAME, Store.NO));
            doc.add(new StringField(IndexFields.ANSWERED_QUESTIONS, Q_NAME, Store.NO));
            doc.add(new StringField(Q_NAME, name, Store.NO));
            doc.add(new StringField(Q_NAME + IndexFields.LOWER_SUFFIX, name.toLowerCase(Locale.ROOT), Store.NO));
            doc.add(new TextField(Q_NAME + IndexFields.TEXT_SUFFIX, name, Store.NO));
            doc.add(new TextField(IndexFields.FULLTEXT, name, Store.NO));
        } else {
            // The question exists in the entity but has no value
            doc.add(new StringField(IndexFields.QUESTIONS, Q_NAME, Store.NO));
        }
        if (note != null) {
            doc.add(new TextField(Q_NAME + IndexFields.NOTE_SUFFIX, note, Store.NO));
            doc.add(new TextField(IndexFields.FULLTEXT, note, Store.NO));
        }
        doc.add(new StringField(IndexFields.QUESTIONS, Q_AGE, Store.NO));
        doc.add(new StringField(IndexFields.ANSWERED_QUESTIONS, Q_AGE, Store.NO));
        doc.add(new LongPoint(Q_AGE + IndexFields.LONG_SUFFIX, age));
        doc.add(new DoublePoint(Q_AGE + IndexFields.DOUBLE_SUFFIX, age));
        doc.add(new StringField(IndexFields.QUESTIONS, Q_DOB, Store.NO));
        doc.add(new StringField(IndexFields.ANSWERED_QUESTIONS, Q_DOB, Store.NO));
        doc.add(new LongPoint(Q_DOB + IndexFields.LONG_SUFFIX,
            ZonedDateTime.parse(birthDate + "T12:00:00Z").toInstant().toEpochMilli()));
        return doc;
    }
}
