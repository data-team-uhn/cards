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

import java.util.Collection;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;

/**
 * Describes a term parsed from a vocabulary source. A few common properties are available as explicit individual
 * methods, and all the properties defined for the term can be accessed using the generic {@link #getAllProperties}
 * method. As a minimum, each term should have an identifier and a label.
 *
 * @version $Id$
 */
public class VocabularyTermSource
{
    private final String id;

    private final String label;

    private final String[] parents;

    private final String[] ancestors;

    private final MultiValuedMap<String, String> allProperties;

    private final String uri;

    /**
     * Constructor passing all required information.
     *
     * @param id the identifier, see {@link #getId()}
     * @param label the label, see {@link #getLabel()}
     * @param parents the parents, see {@link #getParents()}
     * @param ancestors the ancestors, see {@link #getAncestors()}
     * @param allProperties all the term properties, see {@link #getAllProperties()}
     */
    public VocabularyTermSource(final String id, final String label, final String[] parents, final String[] ancestors,
        final MultiValuedMap<String, String> allProperties)
    {
        this(id, label, parents, ancestors, allProperties, null);
    }

    /**
     * Constructor passing all required information.
     *
     * @param id the identifier, see {@link #getId()}
     * @param label the label, see {@link #getLabel()}
     * @param parents the parents, see {@link #getParents()}
     * @param ancestors the ancestors, see {@link #getAncestors()}
     * @param allProperties all the term properties, see {@link #getAllProperties()}
     * @param uri the URI of the term from the OWL file
     */
    public VocabularyTermSource(final String id, final String label, final String[] parents, final String[] ancestors,
        final MultiValuedMap<String, String> allProperties, final String uri)
    {
        this.id = id;
        this.label = StringUtils.defaultString(label, id);
        this.parents = parents;
        this.ancestors = ancestors;
        this.allProperties = allProperties;
        this.uri = uri;
    }

    /**
     * Gets the (mandatory) term identifier, in the format {@code <vocabulary prefix>:<term id>}, for example
     * {@code HP:0002066} or {@code MIM:260540}.
     *
     * @return the term identifier, a short string
     */
    public String getId()
    {
        return this.id;
    }

    /**
     * Gets the preferred human-readable term label, for example {@code Gait ataxia}.
     *
     * @return the term name, or {@code null} if the term doesn't have a preferred label
     */
    public String getLabel()
    {
        return this.label;
    }

    /**
     * Gets the human-readable term description, usually a longer phrase or paragraph that describes the term.
     *
     * @return the term description, or {@code null} if the term doesn't have a description
     */
    public String getDescription()
    {
        final Collection<String> allDefs = this.allProperties.get("def");
        if (allDefs != null && !allDefs.isEmpty()) {
            return allDefs.iterator().next();
        }
        return null;
    }

    /**
     * Gets the parents (direct ancestors) of this term.
     *
     * @return a set of identifiers, or an empty set if the term doesn't have any ancestors in the vocabulary
     */
    public String[] getParents()
    {
        return this.parents;
    }

    /**
     * Gets the ancestors (both direct and indirect ancestors) of this term.
     *
     * @return a set of identifiers, or an empty set if the term doesn't have any ancestors in the vocabulary
     */
    public String[] getAncestors()
    {
        return this.ancestors;
    }

    /**
     * Returns all the properties of this term. This will also include all the other specific properties, such as the
     * {@link #getLabel() label} or {@link #getParents() parents}.
     *
     * @return a multi-valued map, where the key is the property name, with one or more values associated with each key
     */
    public MultiValuedMap<String, String> getAllProperties()
    {
        return this.allProperties;
    }

    /**
     * Gets the URI of this term.
     *
     * @return the URI for this VocabularyTerm if it is available
     */
    public String getURI()
    {
        if (this.uri == null) {
            return "";
        }
        return this.uri;
    }

}
