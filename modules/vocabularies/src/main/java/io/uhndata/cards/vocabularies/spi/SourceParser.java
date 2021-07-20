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

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Service that can do the actual parsing of a vocabulary source into vocabulary terms. Each implementation knows how to
 * handle a specific {@link VocabularyDescription#getSourceFormat() source format}. This service will be used by
 * {@link VocabularyIndexer} when indexing a vocabulary.
 *
 * @version $Id$
 */
public interface SourceParser
{
    /**
     * Checks whether the given source format can be parsed by this parser. This is only a theoretical check, since all
     * that is verified is the declared format of the source, which in reality may not correspond to the actual source
     * file.
     *
     * @param format a short string identifying the source format, usually {@code OWL} or {@code OBO}
     * @return {@code true} if this parser can theoretically parse a source in the declared format, {@code false}
     *         otherwise
     */
    boolean canParse(String format);

    /**
     * Does the actual parsing: extracts term data from the source file, builds {@code VocabularyTermSource}, and passes
     * them to the consumer. It is the responsibility of the consumer to transform the {@code VocabularyTermSource} into
     * a JCR node and store it in the repository.
     *
     * @param source a local temporary file where the source is stored
     * @param vocabularyDescription the description of the vocabulary where the names of special properties are defined
     * @param consumer method which will actually store each parsed term
     * @throws VocabularyIndexException if parsing the source fails
     * @throws IOException if accessing the source file fails
     */
    void parse(File source, VocabularyDescription vocabularyDescription, Consumer<VocabularyTermSource> consumer)
        throws VocabularyIndexException, IOException;
}
