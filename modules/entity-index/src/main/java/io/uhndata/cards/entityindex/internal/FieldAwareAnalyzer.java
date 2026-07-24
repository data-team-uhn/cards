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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import io.uhndata.cards.entityindex.IndexFields;

/**
 * Routes analysis by field: the analyzed text fields ({@code *.text}, {@code *.note}, {@code @fulltext}) get standard
 * word splitting and lowercasing, while all other fields hold exact keywords which must be left untouched, both at
 * indexing and at query parsing time. Without this, a parsed query term targeting a keyword field would be
 * lowercased and would never match.
 *
 * @version $Id$
 * @since 0.9.41
 */
class FieldAwareAnalyzer extends DelegatingAnalyzerWrapper
{
    private final Analyzer text = new StandardAnalyzer();

    private final Analyzer keyword = new KeywordAnalyzer();

    FieldAwareAnalyzer()
    {
        super(PER_FIELD_REUSE_STRATEGY);
    }

    @Override
    protected Analyzer getWrappedAnalyzer(final String fieldName)
    {
        if (fieldName == null || fieldName.endsWith(IndexFields.TEXT_SUFFIX)
            || fieldName.endsWith(IndexFields.NOTE_SUFFIX) || IndexFields.FULLTEXT.equals(fieldName)) {
            return this.text;
        }
        return this.keyword;
    }
}
