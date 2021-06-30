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

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

/**
 * Builder for {@link VocabularyDescription} objects.
 *
 * @version $Id$
 */
public class VocabularyDescriptionBuilder
{
    private static final class DefaultVocabularyDescription implements VocabularyDescription
    {
        private String identifier;

        private String name;

        private String description;

        private String version;

        private String source;

        private String sourceFormat;

        private String website;

        private String citation;

        @Override
        public String getIdentifier()
        {
            return this.identifier;
        }

        @Override
        public String getName()
        {
            return this.name;
        }

        @Override
        public String getDescription()
        {
            return this.description;
        }

        @Override
        public String getVersion()
        {
            return this.version;
        }

        @Override
        public String getSource()
        {
            return this.source;
        }

        @Override
        public String getSourceFormat()
        {
            return this.sourceFormat;
        }

        @Override
        public String getWebsite()
        {
            return this.website;
        }

        @Override
        public String getCitation()
        {
            return this.citation;
        }
    }

    private DefaultVocabularyDescription desc = new DefaultVocabularyDescription();

    /**
     * Sets the identifier of the vocabulary.
     *
     * @param identifier the identifier to use, a simple string
     * @return this object, for method call chaining
     * @see VocabularyDescription#getIdentifier
     */
    public VocabularyDescriptionBuilder withIdentifier(final String identifier)
    {
        this.desc.identifier = identifier;
        return this;
    }

    /**
     * Sets the official name of the vocabulary.
     *
     * @param name the name to use, a string
     * @return this object, for method call chaining
     * @see VocabularyDescription#getName
     */
    public VocabularyDescriptionBuilder withName(final String name)
    {
        this.desc.name = name;
        return this;
    }

    /**
     * Sets a longer description of the vocabulary, specifying its contents, purpose, provenance, or other kinds of
     * information.
     *
     * @param description the description to use, a long string
     * @return this object, for method call chaining
     * @see VocabularyDescription#getDescription
     */
    public VocabularyDescriptionBuilder withDescription(final String description)
    {
        this.desc.description = description;
        return this;
    }

    /**
     * Sets the version of the vocabulary that is described.
     *
     * @param version the version to use, a short string
     * @return this object, for method call chaining
     * @see VocabularyDescription#getVersion
     */
    public VocabularyDescriptionBuilder withVersion(final String version)
    {
        this.desc.version = version;
        return this;
    }

    /**
     * Sets the location where the sources for this vocabulary were/will be fetched from.
     *
     * @param source the source to use, usually an URL
     * @return this object, for method call chaining
     * @see VocabularyDescription#getSource
     */
    public VocabularyDescriptionBuilder withSource(final String source)
    {
        this.desc.source = source;
        return this;
    }

    /**
     * Sets the format of the vocabulary source. Since the format is case sensitive and all source parsers have an
     * uppercase name, the stored format will be uppercased.
     *
     * @param sourceFormat the format to use, a short identifier like {@code OWL} or {@code OBO}
     * @return this object, for method call chaining
     * @see VocabularyDescription#getSource
     */
    public VocabularyDescriptionBuilder withSourceFormat(final String sourceFormat)
    {
        this.desc.sourceFormat = StringUtils.defaultString(sourceFormat).toUpperCase(Locale.ROOT);
        return this;
    }

    /**
     * Sets the official website url for the vocabulary.
     *
     * @param website the vocabulary website to use
     * @return this object, for method call chaining
     * @see VocabularyDescription#getWebsite
     */
    public VocabularyDescriptionBuilder withWebsite(final String website)
    {
        this.desc.website = website;
        return this;
    }

    /**
     * Sets the recommended (or required) citation for the vocabulary.
     *
     * @param citation the citation to use
     * @return this object, for method call chaining
     * @see VocabularyDescription#getCitation
     */
    public VocabularyDescriptionBuilder withCitation(final String citation)
    {
        this.desc.citation = citation;
        return this;
    }

    /**
     * Creates and returns a {@link VocabularyDescription} with the parameters set so far. The state of the builder is
     * reset, so further calls to this builder will affect the next object to be built.
     *
     * @return a {@link VocabularyDescription} object
     */
    public VocabularyDescription build()
    {
        DefaultVocabularyDescription result = this.desc;
        this.desc = new DefaultVocabularyDescription();
        return result;
    }
}
