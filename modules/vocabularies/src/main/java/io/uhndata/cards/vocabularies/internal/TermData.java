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
package io.uhndata.cards.vocabularies.internal;

import java.util.Collection;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

/**
 * A datatype that contains a LinkedHashMap object which maps: String -> Collection[String]. It is used to store the
 * information of a Vocabulary Term prior to ancestor propagation. The below is copied from OboParser.java .
 *
 * @version $Id$
 */
public class TermData
{
    /** Key for fetching ID. */
    public static final String ID_FIELD_NAME = "id";

    /** Key for fetching Label. */
    public static final String LABEL_FIELD_NAME = "name";

    /** Key for fetching Parents. */
    public static final String PARENT_FIELD_NAME = "is_a";

    /** Key for fetching Ancestors. */
    public static final String TERM_CATEGORY_FIELD_NAME = "term_category";

    private MultiValuedMap<String, String> properties = new HashSetValuedHashMap<>();

    /**
     * Returns the identifier of this term, if it is set.
     *
     * @return the identifier, or {@code null} if one isn't set yet
     */
    public String getId()
    {
        return getValue(ID_FIELD_NAME);
    }

    /**
     * Returns the label of this term, if it is set. If no label is set yet, {@code null} is returned. If multiple
     * labels are set, which isn't allowed by the specification and shouldn't happen, then any of the labels may be
     * returned.
     *
     * @return the label, or {@code null} if one isn't set yet
     */
    public String getLabel()
    {
        return getValue(LABEL_FIELD_NAME);
    }

    /**
     * Return all properties of this term, including the identifier and label.
     *
     * @return MultiValuedMap[String: String] that has all the properties of the VocabularyTerm represented by this.
     */
    public MultiValuedMap<String, String> getAllProperties()
    {
        return this.properties;
    }

    /**
     * Checks if a certain tag is defined for this term.
     *
     * @param key the tag to be checked
     * @return true if at least one value is set for the tag
     */
    public Boolean hasKey(String key)
    {
        return this.properties.containsKey(key);
    }

    /**
     * Returns all the values set for a tag.
     *
     * @param key the tag to be retrieved
     * @return the values set for the tag, may be empty if the tag is not yet set
     */
    public Collection<String> getAllValues(String key)
    {
        return this.properties.get(key);
    }

    /**
     * Retrieves a value set for a specified tag. If no value is set, {@code null} is returned. Given that there's no
     * order defined for multiple values set for a tag, any of the values may be returned.
     *
     * @param key the tag to be retrieved
     * @return one of the values, as a string, or {@code null} if no value is yet set for the requested tag
     */
    public String getValue(final String key)
    {
        final Collection<String> values = this.properties.get(key);
        if (values != null && !values.isEmpty()) {
            return values.iterator().next();
        }
        return null;
    }

    /**
     * Add a value to the collection of Strings corresponding to the key. Given that the collection is a set, if the
     * value was already set for this tag, nothing is changed.
     *
     * @param key the tag to be modified
     * @param value a new value to be added to the tag
     */
    public void addTo(String key, String value)
    {
        this.properties.put(key, value);
    }

    /**
     * Add a Collection of Strings to the collection of Strings corresponding to the key. Given that the collection is a
     * set, some of the "new" values may already have been present and re-adding them will not have any effect.
     *
     * @param key the tag to be modified
     * @param values new values that are to be added to the tag
     */
    public void addTo(String key, Collection<String> values)
    {
        values.forEach(value -> this.addTo(key, value));
    }
}
