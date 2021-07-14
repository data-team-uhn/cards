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
package io.uhndata.cards.vocabularies.spi;

/**
 * Contains information about a vocabulary.
 *
 * @version $Id$
 */
public interface VocabularyDescription
{
    /**
     * The identifier of the vocabulary, a unique short string, usually an acronym.
     *
     * @return a simple string, for example {@code NCIT}, {@code OMIM}, or {@code HGNC}
     */
    String getIdentifier();

    /**
     * The official name of the vocabulary.
     *
     * @return a string, for example {@code National Cancer Institute Thesaurus}, may be {@code null} or empty
     */
    String getName();

    /**
     * A longer description of the vocabulary, specifying its contents, purpose, provenance, or other kinds of
     * information.
     *
     * @return a long string, may be {@code null} or empty if no description is provided
     */
    String getDescription();

    /**
     * The version of the vocabulary that is indexed.
     *
     * @return a version identifier, or {@code null} if the version cannot be determined; the format of the version is
     *         not specified, since each vocabulary may have its own release naming scheme
     */
    String getVersion();

    /**
     * The location where the sources for this vocabulary were fetched from.
     *
     * @return the string containing the URL from which the vocabulary source was obtained
     */
    String getSource();

    /**
     * The format of the original source parsed for this vocabulary.
     *
     * @return a short string identifying the source format, usually {@code OWL} or {@code OBO}
     */
    String getSourceFormat();

    /**
     * The official website url for the vocabulary.
     *
     * @return a String representation of the url for the vocabulary website, may be {@code null}
     */
    String getWebsite();

    /**
     * A recommended (or required) citation for the vocabulary.
     *
     * @return a citation for the vocabulary, may be {@code null}
     */
    String getCitation();
}
