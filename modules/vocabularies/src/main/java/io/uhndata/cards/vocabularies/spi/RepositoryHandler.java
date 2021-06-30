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

/**
 * Interacts with a remote repository for vocabularies, such as the BioOntology RESTful service.
 *
 * @version $Id$
 */
public interface RepositoryHandler
{
    /**
     * Provides a short name of the repository that this handler interacts with.
     *
     * @return a short string, such as {@code BioOntology}
     */
    String getRepositoryName();

    /**
     * Provides a longer description of the repository that this handler interacts with.
     *
     * @return a description
     */
    String getRepositoryDescription();

    /**
     * Retrieves information about a vocabulary from the repository, either a specific version, or the latest version
     * available.
     *
     * @param identifier the identifier of the vocabulary to describe, should be a valid, case-sensitive identifier of a
     *            vocabulary available in the repository
     * @param version an optional version, may be {@code null} or empty if the latest version is requested
     * @return a vocabulary description
     * @throws IllegalArgumentException if the requested vocabulary or the requested version are not available in the
     *             repository
     * @throws IOException if accessing the repository failed
     */
    VocabularyDescription getVocabularyDescription(String identifier, String version)
        throws IllegalArgumentException, IOException;

    /**
     * Downloads the source for the specified vocabulary into a temporary local file. Although the file is marked as
     * temporary and will be deleted when the server closes, it is recommended that the file be manually deleted once
     * the vocabulary is parsed.
     *
     * @param vocabulary a vocabulary description obtained from this handler by calling
     *            {@link #getVocabularyDescription}
     * @return the location of the temporary file holding the downloaded vocabulary source
     * @throws IllegalArgumentException if the vocabulary description is invalid
     * @throws IOException if downloading the sources failed, either when fetching the file from its original location,
     *             or when storing it locally
     */
    File downloadVocabularySource(VocabularyDescription vocabulary) throws IllegalArgumentException, IOException;
}
